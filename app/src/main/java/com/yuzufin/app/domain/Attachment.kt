package com.yuzufin.app.domain

/** Foto adjunta a un movimiento ([fileName] es el archivo dentro de la carpeta privada de la app). */
data class Attachment(val id: Long, val transactionId: Long, val fileName: String)

/** Máximo de fotos por movimiento. */
const val MAX_ATTACHMENTS = 6
