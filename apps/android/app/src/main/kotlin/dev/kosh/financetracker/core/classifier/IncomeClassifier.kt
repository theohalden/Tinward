package dev.kosh.financetracker.core.classifier

/**
 * "Credit does not automatically mean income" (finance integrity rule). A random
 * UPI credit from a person is real money received, but it isn't Income in the
 * headline sense — only recognizable salary/payroll credits are. Anything else
 * stays TransactionType.UNKNOWN until a real classifier (Phase 8) or a user
 * correction resolves it, rather than silently inflating the Income figure.
 */
object IncomeClassifier {

    private val SALARY_KEYWORDS = listOf(
        "salary", "sal credit", "sal.credit", "payroll", "wages", "stipend", "sal cr",
    )

    /** Whole-word matches, so "denomination" never reads as "nomina". */
    private val SALARY_WORDS = Regex(
        """\b(gehalt|lohn|salaire|paie|nomina|n\u00f3mina|stipendio|salario|sal\u00e1rio|salaris|loon|vencimiento)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isLikelySalary(merchant: String?, rawSourceText: String?): Boolean {
        val haystack = "${merchant.orEmpty()} ${rawSourceText.orEmpty()}".lowercase()
        return SALARY_KEYWORDS.any { haystack.contains(it) } || SALARY_WORDS.containsMatchIn(haystack)
    }
}
