package com.yuzufin.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.yuzufin.app.data.AttachmentFileStore
import com.yuzufin.app.data.AttachmentRepository
import com.yuzufin.app.data.BackupManager
import com.yuzufin.app.data.BudgetRepository
import com.yuzufin.app.data.CurrencyConverter
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.IncomeRepository
import com.yuzufin.app.data.RecurringRepository
import com.yuzufin.app.data.NotificationRepository
import com.yuzufin.app.data.ObligationsRepository
import com.yuzufin.app.data.SavingsRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.update.ApkDownloader
import com.yuzufin.app.export.VoucherBuilder
import com.yuzufin.app.data.update.AppVersion
import com.yuzufin.app.data.update.UpdateChecker

/** Crea y comparte las piezas de la app (base de datos, repositorios). Sin librerías de inyección. */
/** Datos que viven mientras el proceso de la app está vivo (no se guardan). */
class SessionInfo {
    /** Si ya se calculó cuántos días pasaron desde la última vez que se abrió la app. */
    @Volatile var checked: Boolean = false

    /** Días sin abrir la app antes de esta sesión (0 si es la primera vez o se abrió hoy). */
    @Volatile var daysAway: Int = 0
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val attachmentFileStore: AttachmentFileStore by lazy { AttachmentFileStore(appContext) }
    val attachmentRepository: AttachmentRepository by lazy { AttachmentRepository(database, attachmentFileStore) }
    val financeRepository: FinanceRepository by lazy { FinanceRepository(database, attachmentFileStore) }
    val incomeRepository: IncomeRepository by lazy { IncomeRepository(database) }
    val recurringRepository: RecurringRepository by lazy { RecurringRepository(database) }
    val obligationsRepository: ObligationsRepository by lazy { ObligationsRepository(database) }
    val savingsRepository: SavingsRepository by lazy { SavingsRepository(database, financeRepository) }
    val voucherBuilder: VoucherBuilder by lazy { VoucherBuilder(appContext, financeRepository, attachmentRepository, settingsRepository) }
    val backupManager: BackupManager by lazy { BackupManager(appContext, database, settingsRepository, attachmentFileStore) }
    val currencyConverter: CurrencyConverter by lazy { CurrencyConverter(database, settingsRepository) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database) }
    val notificationRepository: NotificationRepository by lazy { NotificationRepository(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val sessionInfo = SessionInfo()
    val updateChecker: UpdateChecker by lazy { UpdateChecker() }
    val apkDownloader: ApkDownloader by lazy { ApkDownloader(appContext) }
    val appVersion: AppVersion by lazy { AppVersion.read(appContext) }

    /** Revisa ya si Monki tiene algo que avisar (irregularidades o logros), sin esperar al chequeo diario. */
    fun checkMonkiNow() = com.yuzufin.app.reminders.MonkiNotifier.checkSoon(appContext)
}

class MoneaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as MoneaApplication).container
