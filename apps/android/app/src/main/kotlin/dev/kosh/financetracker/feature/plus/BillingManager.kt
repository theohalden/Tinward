package dev.kosh.financetracker.feature.plus

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One way to subscribe, with the price Google Play reports for the user's country. */
data class PlusOffer(val label: String, val price: String, val period: String, internal val details: ProductDetails, internal val token: String)

/**
 * Tinward Plus through Google Play Billing. There is no Tinward account or server: the purchase
 * belongs to the user's Google account, and this class only asks Play what is active. Prices are
 * never hard-coded; they are whatever Play returns for the user's region.
 *
 * Needs the app to be installed from Play (or a Play test track) with a subscription whose id is
 * [PRODUCT_ID] set up in Play Console. Without that the paywall says so plainly instead of pretending.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlements: Entitlements,
) : PurchasesUpdatedListener {
    private var client: BillingClient? = null
    private val _offers = MutableStateFlow<List<PlusOffer>>(emptyList())
    val offers: StateFlow<List<PlusOffer>> = _offers.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private fun connect(onReady: () -> Unit) {
        val c = client ?: BillingClient.newBuilder(context).setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build().also { client = it }
        if (c.isReady) { onReady(); return }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
                else _message.value = "Google Play purchases are not available on this phone right now."
            }
            override fun onBillingServiceDisconnected() { client = null }
        })
    }

    /** Asks Play what is active and what can be bought. Safe to call often; quietly does nothing offline. */
    fun refresh() {
        _busy.value = true
        connect {
            val c = client ?: return@connect
            c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases)
                loadOffers()
            }
        }
    }

    private fun loadOffers() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()),
        ).build()
        c.queryProductDetailsAsync(params) { result, details ->
            _busy.value = false
            val product = details.firstOrNull()
            if (result.responseCode != BillingClient.BillingResponseCode.OK || product == null) {
                _offers.value = emptyList()
                _message.value = "Tinward Plus cannot be bought from this build yet: it is not linked to a Google Play subscription."
                return@queryProductDetailsAsync
            }
            _message.value = null
            _offers.value = product.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
                val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                PlusOffer(
                    label = if (offer.basePlanId.contains("year", true)) "Yearly" else "Monthly",
                    price = phase.formattedPrice, period = if (phase.billingPeriod.contains("Y")) "per year" else "per month",
                    details = product, token = offer.offerToken,
                )
            }
        }
    }

    fun purchase(activity: Activity, offer: PlusOffer) {
        val c = client ?: return refresh()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details).setOfferToken(offer.token).build()),
        ).build()
        val r = c.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) _message.value = "Could not start the purchase. Try again in a moment."
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> _message.value = null
            else -> _message.value = "The purchase did not go through. You have not been charged unless Google Play says so."
        }
    }

    private fun handle(purchases: List<Purchase>) {
        val active = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains(PRODUCT_ID) }
        // A pending purchase (e.g. cash payment) grants nothing until Play marks it purchased.
        if (active != null && !active.isAcknowledged) {
            client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(active.purchaseToken).build()) { }
        }
        entitlements.onPlayResult(active != null, active?.products?.firstOrNull())
    }

    companion object {
        /** Create a subscription with this id in Play Console, with monthly and yearly base plans. */
        const val PRODUCT_ID = "tinward_plus"
    }
}
