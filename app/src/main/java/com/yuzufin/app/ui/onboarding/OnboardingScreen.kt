package com.yuzufin.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.settings.AppearanceCard
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.yuzufin.app.ui.components.DateField
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.Mascot
import com.yuzufin.app.ui.components.MascotPose
import com.yuzufin.app.ui.components.mascotForCurrency
import com.yuzufin.app.ui.components.MoneaCard
import com.yuzufin.app.ui.theme.Forest
import com.yuzufin.app.ui.theme.MintTile
import java.time.LocalDate

@StringRes
private fun currencyNameRes(currency: CurrencyInfo): Int = when (currency.code) {
    "PYG" -> R.string.currency_pyg
    "USD" -> R.string.currency_usd
    "ARS" -> R.string.currency_ars
    "BRL" -> R.string.currency_brl
    "EUR" -> R.string.currency_eur
    else -> R.string.currency_pyg
}

/** Configuración inicial: moneda y punto de partida financiero. Dos pasos cortos. */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.step > 0) { viewModel.back() }

    // Sin esto el texto sin color propio sale negro, y en modo oscuro no se ve.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MintTile),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("M", color = Forest, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = stringResource(R.string.onboarding_step, state.step + 1, 2),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { (state.step + 1) / 2f },
                    modifier = Modifier.fillMaxWidth(),
                )

                MascotHeader(state)

                if (state.step == 0) {
                    // Quien viene de otro teléfono puede restaurar su copia antes de completar nada.
                    RestoreBackupCard()
                    CurrencyStep(state, viewModel)
                    ThemeStep()
                } else {
                    StartStep(state, viewModel)
                    RestoreBackupCard()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.step > 0) {
                    OutlinedButton(onClick = viewModel::back, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.action_back))
                    }
                }
                Button(
                    onClick = if (state.step == 0) viewModel::next else viewModel::finish,
                    enabled = !state.saving,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        stringResource(
                            if (state.step == 0) R.string.onboarding_continue else R.string.onboarding_start,
                        ),
                    )
                }
            }
        }
    }
}

/** El monito reacciona a la moneda: tereré con la de Paraguay, mate con la de Argentina, café con las demás. */
@Composable
private fun MascotHeader(state: OnboardingState) {
    val pose = if (state.step == 0) mascotForCurrency(state.currency.code) else MascotPose.PLANNING
    val suffix = if (state.userName.isBlank()) "" else ", ${state.userName.trim()}"
    val text = if (state.step == 0) {
        stringResource(
            when (state.currency.code) {
                "PYG" -> R.string.onboarding_mascot_pyg
                "ARS" -> R.string.onboarding_mascot_ars
                else -> R.string.onboarding_mascot_other
            },
            suffix,
        )
    } else {
        stringResource(R.string.onboarding_mascot_step2)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Mascot(pose, size = 130.dp, autoClip = true)
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun CurrencyStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Text(stringResource(R.string.onboarding_name_title), style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = state.userName,
        onValueChange = viewModel::onNameChange,
        label = { Text(stringResource(R.string.onboarding_name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        supportingText = { Text(stringResource(R.string.onboarding_name_note)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.onboarding_currency_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.onboarding_currency_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MoneaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        CurrencyInfo.supported.forEach { currency ->
            val selected = currency.code == state.currency.code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { viewModel.onCurrencyChange(currency) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = selected, onClick = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(currencyNameRes(currency)), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = currency.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(currency.symbol, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    Text(
        stringResource(R.string.onboarding_currency_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Elegir el color (y claro u oscuro) de la app desde el principio. Se guarda al tocar, así que
 * toda la pantalla cambia de color en el momento y se ve cómo queda.
 */
@Composable
private fun ThemeStep() {
    val repository = rememberAppContainer().settingsRepository
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val current = settings ?: return

    Text(stringResource(R.string.onboarding_theme_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.onboarding_theme_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AppearanceCard(
        palette = current.themePalette,
        mode = current.themeMode,
        textScale = current.textScale,
        onPaletteChange = { value -> scope.launch { repository.setThemePalette(value) } },
        onModeChange = { value -> scope.launch { repository.setThemeMode(value) } },
        onTextScaleChange = { value -> scope.launch { repository.setTextScale(value) } },
    )
}

@Composable
private fun StartStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val today = LocalDate.now()
    Text(stringResource(R.string.onboarding_start_title), style = MaterialTheme.typography.headlineSmall)

    ModeCard(
        selected = state.mode == StartMode.FROM_TODAY,
        titleRes = R.string.onboarding_mode_today,
        bodyRes = R.string.onboarding_mode_today_body,
        onClick = { viewModel.onModeChange(StartMode.FROM_TODAY) },
    )
    ModeCard(
        selected = state.mode == StartMode.WITH_HISTORY,
        titleRes = R.string.onboarding_mode_history,
        bodyRes = R.string.onboarding_mode_history_body,
        onClick = { viewModel.onModeChange(StartMode.WITH_HISTORY) },
    )

    if (state.mode == StartMode.WITH_HISTORY) {
        DateField(
            label = stringResource(R.string.onboarding_start_date),
            date = state.startDate,
            minDate = LocalDate.of(2000, 1, 1),
            maxDate = today,
            onDateChange = viewModel::onStartDateChange,
        )
    }

    val amount = state.amountMinor
    OutlinedTextField(
        value = state.amountText,
        onValueChange = viewModel::onAmountChange,
        label = {
            Text(
                stringResource(
                    if (state.mode == StartMode.FROM_TODAY) R.string.onboarding_amount_today
                    else R.string.onboarding_amount_history,
                ),
            )
        },
        prefix = { Text("${state.currency.symbol} ") },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (state.currency.fractionDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
        ),
        singleLine = true,
        isError = state.showErrors && amount == null,
        supportingText = {
            when {
                state.showErrors && amount == null ->
                    Text(stringResource(R.string.onboarding_amount_required))
                amount != null -> Text(MoneyFormatter.format(amount, state.currency))
            }
        },
        textStyle = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.onboarding_optional_later),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeCard(
    selected: Boolean,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
