package com.yuzufin.app.domain

/** Tipo de aviso registrado en la campanilla. */
enum class NotificationKind {
    INCOME_PENDING, INCOME_AUTO, INCOME_SOON, BILL, LOAN_OWED, LOAN_OWE, SAVINGS, BUDGET, OTHER;

    companion object {
        fun fromName(name: String?): NotificationKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

data class AppNotification(
    val id: Long,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    /** Milisegundos desde 1970. */
    val createdAt: Long,
    val read: Boolean,
)
