package com.yuzufin.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.data.update.ApkDownloader
import com.yuzufin.app.data.update.AppVersion
import com.yuzufin.app.data.update.UpdateChecker
import com.yuzufin.app.data.update.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File, val installLaunched: Boolean) : UpdateState
    data class DownloadFailed(val info: UpdateInfo) : UpdateState
    data object Error : UpdateState
}

/**
 * Busca, descarga e instala las actualizaciones. Las actualizaciones son obligatorias: al
 * encontrar una versión nueva empieza a descargarla y la interfaz muestra una ventana que no se
 * puede cerrar hasta instalarla. Vive a nivel de la actividad.
 */
class UpdateViewModel(
    private val checker: UpdateChecker,
    private val downloader: ApkDownloader,
    val currentVersion: AppVersion,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var busy = false

    /**
     * Busca cada minuto mientras la app está abierta. Si no hay conexión el intento falla en
     * silencio y se espera más (hasta 5 minutos) antes de volver a probar. Se detiene mientras
     * hay una actualización pendiente.
     */
    suspend fun pollLoop() {
        var wait = POLL_MS
        while (true) {
            if (!busy && !updatePending(_state.value)) {
                wait = try {
                    busy = true
                    applyResult(fetchAvailable())
                    POLL_MS
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    minOf(wait * 2, MAX_POLL_MS)
                } finally {
                    busy = false
                }
            }
            delay(wait)
        }
    }

    /** Búsqueda manual desde Ajustes: sí muestra el progreso y los errores. */
    fun check() {
        if (busy || updatePending(_state.value)) return
        busy = true
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                applyResult(fetchAvailable())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Error
            } finally {
                busy = false
            }
        }
    }

    private suspend fun fetchAvailable(): UpdateInfo? {
        val build = checker.latestBuildNumber() ?: return null
        if (build <= currentVersion.code) return null
        return checker.detailsFor(build, currentVersion.name)
    }

    private fun applyResult(info: UpdateInfo?) {
        if (info == null) {
            _state.update { s ->
                if (s is UpdateState.Idle || s is UpdateState.Checking || s is UpdateState.Error) UpdateState.UpToDate else s
            }
        } else {
            _state.value = UpdateState.Available(info)
            download()
        }
    }

    /** Descarga la versión nueva. También sirve para reintentar si falló. */
    fun download() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.DownloadFailed -> s.info
            else -> return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val file = downloader.download(info) { progress ->
                    _state.value = UpdateState.Downloading(info, progress)
                }
                _state.value = UpdateState.ReadyToInstall(info, file, installLaunched = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.DownloadFailed(info)
            }
        }
    }

    /** La interfaz ya abrió el instalador (o la pantalla de permiso). */
    fun onInstallLaunched() {
        _state.update { s ->
            if (s is UpdateState.ReadyToInstall) s.copy(installLaunched = true) else s
        }
    }

    private fun updatePending(s: UpdateState) =
        s is UpdateState.Available || s is UpdateState.Downloading ||
            s is UpdateState.ReadyToInstall || s is UpdateState.DownloadFailed

    companion object {
        private const val POLL_MS = 60_000L
        private const val MAX_POLL_MS = 300_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                UpdateViewModel(container.updateChecker, container.apkDownloader, container.appVersion)
            }
        }
    }
}
