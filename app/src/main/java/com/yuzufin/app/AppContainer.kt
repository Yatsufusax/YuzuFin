package com.yuzufin.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.update.ApkDownloader
import com.yuzufin.app.data.update.AppVersion
import com.yuzufin.app.data.update.UpdateChecker

/** Crea y comparte las piezas de la app (base de datos, repositorios). Sin librerías de inyección. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val financeRepository: FinanceRepository by lazy { FinanceRepository(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val updateChecker: UpdateChecker by lazy { UpdateChecker() }
    val apkDownloader: ApkDownloader by lazy { ApkDownloader(appContext) }
    val appVersion: AppVersion by lazy { AppVersion.read(appContext) }
}

class YuzuFinApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as YuzuFinApplication).container
