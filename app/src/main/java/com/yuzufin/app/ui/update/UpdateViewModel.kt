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
    data object Error : UpdateState
}

/**
 * Busca, descarga e instala actualizaciones publicadas en GitHub. Vive a nivel de la
 * actividad, así que lo comparten el aviso al abrir la app y la pantalla de Ajustes.
 */
class UpdateViewModel(
    private val checker: UpdateChecker,
    private val downloader: ApkDownloader,
    val currentVersion: AppVersion,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Si el aviso emergente está abierto (solo lo abre la búsqueda automática al iniciar). */
    private val _dialogOpen = MutableStateFlow(false)
    val dialogOpen: StateFlow<Boolean> = _dialogOpen.asStateFlow()

    private var startupCheckDone = false

    /** Búsqueda silenciosa al abrir la app: no molesta si no hay novedades o falla la red. */
    fun checkOnStartup() {
        if (startupCheckDone) return
        startupCheckDone = true
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val latest = checker.fetchLatest()
                if (latest != null && latest.buildNumber > currentVersion.code) {
                    _state.value = UpdateState.Available(latest)
                    if (silent) _dialogOpen.value = true
                } else {
                    _state.value = UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = if (silent) UpdateState.Idle else UpdateState.Error
            }
        }
    }

    fun download() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
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
                _state.value = UpdateState.Error
            }
        }
    }

    /** La interfaz ya abrió el instalador (o la pantalla de permiso). */
    fun onInstallLaunched() {
        _state.update { s ->
            if (s is UpdateState.ReadyToInstall) s.copy(installLaunched = true) else s
        }
        _dialogOpen.value = false
    }

    fun dismissDialog() {
        _dialogOpen.value = false
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                UpdateViewModel(container.updateChecker, container.apkDownloader, container.appVersion)
            }
        }
    }
}
