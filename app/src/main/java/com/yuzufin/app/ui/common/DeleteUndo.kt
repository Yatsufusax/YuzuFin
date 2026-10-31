package com.yuzufin.app.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import com.yuzufin.app.data.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Tiempo que el usuario tiene para tocar “Deshacer” antes de que el borrado sea definitivo. */
private const val UNDO_WINDOW_MS = 5_000L

/**
 * Borrado con deshacer: el movimiento se oculta al instante (saldo y listas ya no lo cuentan) y
 * aparece el aviso “Movimiento borrado · Deshacer”. Si pasa el tiempo sin tocarlo, recién ahí se
 * borra de verdad (con sus fotos). Si la app se cierra antes, el movimiento simplemente sigue ahí.
 */
class DeleteUndo(
    private val finance: FinanceRepository,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val message: String,
    private val undoLabel: String,
) {
    fun delete(id: Long) {
        finance.hideTransaction(id)
        // Un borrado anterior todavía en espera se confirma ya, para no acumular avisos.
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                snackbar.showSnackbar(
                    message = message,
                    actionLabel = undoLabel,
                    withDismissAction = false,
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                finance.showTransaction(id)
            } else {
                finance.deleteTransaction(id)
                finance.showTransaction(id)
            }
        }
    }
}

/** Disponible dentro de la pantalla principal; `null` en vistas previas o pruebas. */
val LocalDeleteUndo = staticCompositionLocalOf<DeleteUndo?> { null }
