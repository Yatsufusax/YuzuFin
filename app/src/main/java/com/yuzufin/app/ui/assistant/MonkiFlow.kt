package com.yuzufin.app.ui.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.rememberAppContainer
import kotlinx.coroutines.launch

/** Id en `dismissedHints`: la presentación se ve sola una vez; "Volver a mostrar los consejos" la repite. */
private const val STORY_ID = "monki_story"

private enum class MonkiStage { STORY, HOME, CHAT }

/**
 * Lo que se ve al abrir Monki AI: la primera vez, la presentación en historias; después, el inicio
 * con atajos, y desde ahí el chat. El [viewModel] es el mismo en las tres, así la charla no se pierde
 * al volver al inicio.
 */
@Composable
fun MonkiFlow(viewModel: MonkiViewModel, onExit: () -> Unit) {
    val container = rememberAppContainer()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var stage by rememberSaveable { mutableStateOf<MonkiStage?>(null) }

    // Hasta leer las preferencias no se muestra nada, para que no parpadee entre la historia y el inicio.
    val loaded = settings
    if (loaded == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val current = stage ?: if (STORY_ID in loaded.dismissedHints) MonkiStage.HOME else MonkiStage.STORY

    BackHandler(enabled = current == MonkiStage.CHAT) { stage = MonkiStage.HOME }

    Crossfade(targetState = current, label = "monki_stage") { target ->
        when (target) {
            MonkiStage.STORY -> MonkiStoryScreen(
                currencyCode = loaded.currencyCode,
                onFinish = {
                    stage = MonkiStage.HOME
                    scope.launch { container.settingsRepository.dismissHint(STORY_ID) }
                },
            )
            MonkiStage.HOME -> MonkiHomeScreen(
                viewModel = viewModel,
                userName = loaded.userName,
                currencyCode = loaded.currencyCode,
                onBack = onExit,
                onOpenChat = { stage = MonkiStage.CHAT },
                onShowStory = { stage = MonkiStage.STORY },
            )
            MonkiStage.CHAT -> MonkiChatScreen(
                viewModel = viewModel,
                onBack = { stage = MonkiStage.HOME },
            )
        }
    }
}
