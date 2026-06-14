package com.local.smsllm.domain

/** Expense/income categories used for classifying SMS transactions. */
enum class Category(val id: String, val label: String, val emoji: String) {
    FOOD("food", "Food & Dining", "🍽️"),
    GROCERIES("groceries", "Groceries", "🛒"),
    SHOPPING("shopping", "Shopping", "🛍️"),
    TRANSPORT("transport", "Transport", "🚌"),
    FUEL("fuel", "Fuel", "⛽"),
    BILLS_UTILITIES("bills_utilities", "Bills & Utilities", "💡"),
    RECHARGE("recharge", "Recharge", "📱"),
    RENT("rent", "Rent", "🏠"),
    EMI_LOAN("emi_loan", "EMI & Loan", "🏦"),
    INVESTMENT("investment", "Investments", "📈"),
    INSURANCE("insurance", "Insurance", "🛡️"),
    HEALTH("health", "Health & Medical", "🏥"),
    EDUCATION("education", "Education", "🎓"),
    ENTERTAINMENT("entertainment", "Entertainment", "🎬"),
    TRAVEL("travel", "Travel", "✈️"),
    TRANSFER("transfer", "Transfer (P2P)", "💸"),
    INCOME_SALARY("income_salary", "Income & Salary", "💰"),
    REFUND_CASHBACK("refund_cashback", "Refund & Cashback", "↩️"),
    CASH_ATM("cash_atm", "Cash & ATM", "🏧"),
    OTHER("other", "Other", "❓");

    companion object {
        /** Returns the [Category] whose [id] matches [id], or null if not found or input is null. */
        fun fromId(id: String?): Category? = id?.let { target ->
            entries.firstOrNull { it.id == target }
        }
    }
}
