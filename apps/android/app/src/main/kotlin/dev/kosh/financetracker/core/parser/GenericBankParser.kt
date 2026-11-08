package dev.kosh.financetracker.core.parser

import dev.kosh.financetracker.core.model.ParsedTransaction
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.RawFinancialEvent
import dev.kosh.financetracker.core.model.TransactionDirection
import java.math.BigDecimal

/** One money amount found in a message. */
data class MoneyMention(val currency: String, val amount: BigDecimal, val start: Int, val isBalance: Boolean)

/**
 * Finds amounts with their currency in English-language bank messages from any country: symbols
 * ($, £, €, ¥, ₹, S$, A$ ...), ISO codes (USD, EUR, AED ...), and the format each country uses.
 */
object MessageMoney {
    /** A bare $ sign could be US, Australian, Canadian, Singapore, NZ or Hong Kong dollars. The app resolves it from a setting. */
    const val BARE_DOLLAR = "$"

    private val SYMBOLS = mapOf(
        "US$" to "USD", "A$" to "AUD", "AU$" to "AUD", "C$" to "CAD", "CA$" to "CAD", "S$" to "SGD", "NZ$" to "NZD", "HK$" to "HKD",
        "R$" to "BRL", "$" to BARE_DOLLAR, "£" to "GBP", "€" to "EUR", "¥" to "JPY", "₹" to "INR", "Rs." to "INR", "Rs" to "INR",
    )
    private val CODES = listOf(
        "USD", "EUR", "GBP", "AUD", "CAD", "NZD", "SGD", "HKD", "AED", "SAR", "QAR", "KWD", "BHD", "OMR", "JPY", "CHF", "ZAR", "SEK", "NOK",
        "DKK", "MXN", "BRL", "INR", "PHP", "THB", "MYR", "IDR", "NGN", "KES", "PLN", "CZK", "TRY", "EGP", "PKR", "BDT", "LKR",
    )
    private const val NUM = """\d{1,3}(?:[.,\s]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?"""
    private val symbolFirst = Regex("""(?<![A-Za-z])(US\$|AU\$|CA\$|NZ\$|HK\$|A\$|C\$|S\$|R\$|Rs\.?|[$£€¥₹])\s?($NUM)""")
    private val codeFirst = Regex("""(?<![A-Za-z])(${CODES.joinToString("|")})\s?($NUM)""")
    private val codeLast = Regex("""($NUM)\s?(${CODES.joinToString("|")})(?![A-Za-z])""")
    private val balanceWords = Regex("""(?i)(avail(?:able)?|balance|bal\b|limit|remaining|outstanding)[^0-9$£€¥₹]{0,25}$""")

    fun find(body: String): List<MoneyMention> {
        val found = ArrayList<MoneyMention>()
        fun add(cur: String, num: String, start: Int) {
            val v = MoneyNumbers.parseAuto(num) ?: return
            if (v.signum() <= 0) return
            val before = body.substring(maxOf(0, start - 32), start)
            found += MoneyMention(cur, v, start, balanceWords.containsMatchIn(before))
        }
        symbolFirst.findAll(body).forEach { add(SYMBOLS[it.groupValues[1].let { s -> if (s.startsWith("Rs")) "Rs" else s }] ?: return@forEach, it.groupValues[2], it.range.first) }
        codeFirst.findAll(body).forEach { add(it.groupValues[1], it.groupValues[2], it.range.first) }
        codeLast.findAll(body).forEach { add(it.groupValues[2], it.groupValues[1], it.range.first) }
        return found.distinctBy { it.start }.sortedBy { it.start }
    }
}

/**
 * Keeps OTPs, offers and reminders out of the ledger. These are the messages that contain amounts
 * but are not completed transactions, so recording them would invent spending.
 */
object MessageNoise {
    private val otp = Regex("""(?i)\b(otp|one[- ]time (?:password|passcode|pin)|verification code|security code|passcode|do not share|never share)\b""")
    private val promo = Regex("""(?i)\b(pre[- ]?approved|apply now|limited[- ]time|congratulations|you(?:'ve| have) won|lucky draw|\d+% (?:off|cashback)|loan offer|upgrade your card|click (?:here|the link)|special offer|rewards? points? (?:expire|offer))\b""")
    private val completed = Regex("""(?i)\b(?:has been|was|were|is)\s+(?:debited|credited|charged|withdrawn|deposited|posted)\b|\b(?:debited|credited|charged|withdrawn)\s+(?:by|with|for|from|to|on)\b|\b(?:purchase|spent|payment)\b[^.]{0,60}\b(?:at|to|on)\b""")

    /** A failure word anywhere means the money did not move, whatever else the message says. */
    private val failed = Regex("""(?i)\b(failed|declined|unsuccessful|unable to|could not|not (?:be )?processed|rejected|insufficient funds)\b""")

    fun isNoise(body: String): Boolean {
        if (failed.containsMatchIn(body)) return true
        val done = completed.containsMatchIn(body)
        if (otp.containsMatchIn(body) && !done) return true
        if (promo.containsMatchIn(body) && !done) return true
        return false
    }
}

/**
 * Fallback parser for English bank and card alerts from outside India, in any currency. It is
 * deliberately conservative: it records only when it finds an amount, a clear direction and a
 * completed-transaction cue; anything else is left unread rather than guessed. It does not claim
 * to cover every bank. Coverage is whatever the fixture tests demonstrate.
 */
class GenericBankParser : TransactionParser {
    private val debitCue = Regex("""(?i)\b(spent|purchase[d]?|debited|debit|withdrawn|withdrawal|paid|payment (?:to|of)|charged|charge|sent|transferred to|transfer to|bought|pos|used (?:for|at))\b""")
    private val creditCue = Regex("""(?i)\b(credited|credit of|deposit(?:ed)?|received|refund(?:ed)?|salary|payroll|transferred from|transfer from)\b""")
    private val suffixRegex = Regex("""(?i)(?:ending(?:\s+in|\s+with)?|a/c|acct\.?|account|card|\bx{1,}|\*{2,})\s*[:#]?\s*[x*•.\s]*(\d{4})\b""")
    private val merchantAt = Regex("""(?i)\bat\s+([A-Za-z0-9][A-Za-z0-9&'.\-* /]{1,38}?)(?=\s+(?:on|using|with|for|ref|via|from|card|has|was|have|is|will|and|\.|,|$)|[.,;]|$)""")
    private val merchantTo = Regex("""(?i)\b(?:to|from)\s+([A-Za-z][A-Za-z0-9&'.\-* /]{1,38}?)(?=\s+(?:on|using|via|ref|from|card|a/c|acct|account|has|was|have|is|will|and|\.|,|$)|[.,;]|$)""")
    private val merchantLabel = Regex("""(?i)\bmerchant\s*[:\-]\s*([A-Za-z0-9][A-Za-z0-9&'.\-* /]{1,38}?)(?=\s{2,}|[.,;]|$)""")

    override fun canParse(event: RawFinancialEvent): Boolean {
        val body = event.rawText
        if (MessageNoise.isNoise(body) || TransactionMessageStatus.isNonCompleted(body)) return false
        if (MessageMoney.find(body).none { !it.isBalance }) return false
        return debitCue.containsMatchIn(body) || creditCue.containsMatchIn(body)
    }

    override fun parse(event: RawFinancialEvent): ParsedTransaction? {
        val body = event.rawText
        val money = MessageMoney.find(body)
        val txn = money.firstOrNull { !it.isBalance } ?: return null
        val balance = money.firstOrNull { it.isBalance && it.currency == txn.currency }?.amount

        val d = debitCue.find(body)?.range?.first; val c = creditCue.find(body)?.range?.first
        val direction = when {
            d != null && c != null -> if (d < c) TransactionDirection.DEBIT else TransactionDirection.CREDIT
            d != null -> TransactionDirection.DEBIT
            c != null -> TransactionDirection.CREDIT
            else -> TransactionDirection.UNKNOWN
        }
        val suffix = suffixRegex.find(body)?.groupValues?.get(1)
        val merchant = (merchantLabel.find(body) ?: merchantAt.find(body) ?: merchantTo.find(body))?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ' ')
            ?.takeIf { it.length >= 2 && !it.equals("your account", true) && !it.contains("account", true) }
        val sender = event.sender?.trim()?.takeIf { it.length in 3..20 && it.all { ch -> ch.isLetter() || ch == ' ' } }?.lowercase()?.replaceFirstChar { it.uppercase() }

        return ParsedTransaction(
            amount = txn.amount, direction = direction, accountSuffix = suffix, merchantRaw = merchant, transactionRef = ParserFields.transactionRef(body),
            paymentMethod = if (body.contains("atm", true)) PaymentMethod.CASH else if (body.contains("card", true)) PaymentMethod.CARD else PaymentMethod.UNKNOWN,
            balanceAfter = balance, timestamp = event.timestamp, bank = sender, cardSuffix = suffix.takeIf { body.contains("card", true) },
            confidence = ParserFields.confidence(true, direction != TransactionDirection.UNKNOWN, suffix != null, merchant != null, false) - 0.1,
            currency = txn.currency,
        )
    }
}
