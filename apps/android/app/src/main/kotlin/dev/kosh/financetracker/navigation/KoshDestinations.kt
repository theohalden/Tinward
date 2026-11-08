package dev.kosh.financetracker.navigation

sealed class KoshDestination(val route: String) {
    object Overview : KoshDestination("overview")
    object Trail : KoshDestination("trail")
    object Plan : KoshDestination("plan")
    object Accounts : KoshDestination("accounts")
    object Insights : KoshDestination("insights")
    object Review : KoshDestination("review")
    object Add : KoshDestination("add")
    object Settings : KoshDestination("settings")
    object Deleted : KoshDestination("deleted")
    object Edit : KoshDestination("edit/{id}") {
        fun createRoute(id: Long) = "edit/$id"
    }
    object AccountDetail : KoshDestination("account/{id}") {
        fun createRoute(id: Long) = "account/$id"
    }
    object AccountForm : KoshDestination("account_form/{id}") {
        fun createRoute(id: Long = 0) = "account_form/$id"
    }
    object Detail : KoshDestination("detail/{transactionId}") {
        const val ARG_TRANSACTION_ID = "transactionId"
        fun createRoute(transactionId: Long) = "detail/$transactionId"
    }
}

/** Tabs shown in the bottom bar (the centre "+" opens [KoshDestination.Add]). */
val BOTTOM_NAV_DESTINATIONS = listOf(
    KoshDestination.Overview,
    KoshDestination.Trail,
    KoshDestination.Plan,
    KoshDestination.Accounts,
)

/** Routes that render full-screen without the bottom bar. */
val FULL_SCREEN_ROUTES = setOf(
    KoshDestination.Detail.route,
    KoshDestination.Add.route,
    KoshDestination.Settings.route,
    KoshDestination.Deleted.route,
    KoshDestination.Edit.route,
    KoshDestination.AccountDetail.route,
    KoshDestination.AccountForm.route,
)
