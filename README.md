# Monea

> Antes se llamaba YuzuFin. El nombre visible cambió, pero por dentro se conservan el paquete `com.yuzufin.app`, la base `yuzufin.db` y el repositorio `Yatsufusax/YuzuFin`, para que la app instalada se actualice sola y no pierda datos. Las copias de seguridad hechas como YuzuFin se siguen pudiendo restaurar.

App de finanzas personales para Android (Kotlin, Jetpack Compose, Material 3, Room). Local-first, sin cuentas ni IA.
*Tu dinero. Tus decisiones.*

## Estado: Fase 6 + copias, cambio de moneda, borrado deslizando, fotos con comprobante PDF, gastos fijos y mascota (listo para probar)

**Hecho (escrito; pendiente de compilar en GitHub Actions)**
- Configuración inicial: moneda (Gs. por defecto) y saldo inicial, desde hoy o con fecha anterior.
- Base de datos Room: categorías, movimientos y saldo inicial.
- Registrar ingresos y gastos, historial con filtros (búsqueda, tipo, categoría, mes), editar y eliminar con confirmación.
- Inicio con datos reales: saldo, resumen del mes y últimos movimientos. Sin datos de demostración.
- Actualizador dentro de la app (Ajustes y aviso en Inicio): consulta las Releases de GitHub, descarga el APK e inicia la instalación.
- Nombre de usuario (opcional) en la configuración inicial y en Ajustes.
- Extracto en PDF (botón PDF en Movimientos): período a elección (este mes, mes pasado, todo el historial o fechas propias), encabezado con tu nombre, resumen, detalle con saldo corrido y pie. Se genera en el teléfono con la API `PdfDocument` y se comparte con el menú de Android (no pide permisos de almacenamiento). Solo se guarda el último extracto en la caché de la app.
- **Fase 3, ingresos fijos:** varias fuentes (sueldo, alquiler cobrado, etc.), con frecuencia mensual, quincenal o fechas propias. Día 31 significa "último día del mes". Cada cobro vencido queda *pendiente* hasta que tocás **Ya cobré** (podés ajustar el monto) o **Omitir**; opcionalmente se registra solo. Previstos, pendientes y omitidos no tocan el saldo: solo cuenta lo confirmado.
- **Recordatorios:** aviso local diario (9:00 por defecto, configurable en Ajustes) cuando hay cobros por confirmar. Una sola notificación por cobro; los montos no se muestran salvo que lo actives.
- **Fase 4, ahorros y metas:** metas con monto objetivo y fecha límite opcional; aportes y retiros manuales (con fecha y nota) y aporte automático opcional (un % de cada cobro que confirmás). Con fecha límite se muestra cuánto aportar por mes. Recordatorio opcional: un aviso al mes, solo si todavía no aportaste a esa meta.
- **Disponible vs. ahorrado:** Inicio muestra el **disponible** (saldo total − apartado) y debajo lo **ahorrado**. Apartar no es un gasto ni un ingreso: no aparece en Movimientos ni cambia el extracto PDF.
- **Fase 5, obligaciones:** facturas con vencimiento (únicas o mensuales) que **no descuentan el saldo hasta que las pagás**; al pagar se crea el gasto real (importe y fecha editables) y, si es mensual, queda lista la del mes siguiente. Préstamos que hiciste o que te hicieron, con devoluciones parciales. Inicio muestra los próximos vencimientos y el **libre estimado** (disponible − facturas pendientes). La pantalla Obligaciones suma además lo que te deben, lo que debés y el **patrimonio**.
- **Avisos de la Fase 5:** factura por vencer (desde el día anterior, o ya vencida; un aviso por vencimiento) y, los últimos dos días de cada mes, quién te debe y a quién le debés mientras quede saldo (una vez por mes y por préstamo). Todo se puede apagar por factura o préstamo.
- Tests: `ObligationMathTest` (vencimientos mensuales y meses cortos, avisos, devoluciones, totales, fin de mes), `SavingsMathTest` (disponible, avance, meses que quedan, aporte mensual, aporte automático), `StatementBuilderTest`, `AmountParserTest`, `BalanceCalculatorTest`, `UpdateCheckerTest`, `MoneyFormatterTest`, `IncomeSchedulerTest` (fechas, meses cortos, años bisiestos, quincenas), `ReminderSchedulerTest`, `HabitReminderTest`, `QuickLaunchTest`, `MovementFilteringTest`, `WeeklySummaryTest`.

**Sin verificar:** no se pudo compilar ni ejecutar donde se escribió (sin acceso al SDK de Android). Solo se revisaron recursos y XML con scripts. La compilación y los tests corren en GitHub Actions; si falla, pegá el error.

## Modelo de datos
- `categories(id, name, type, iconKey)`: se siembran 12 por defecto al terminar la configuración.
- `transactions(id, type, amountMinor, categoryId, description, epochDay, createdAt, updatedAt)`: el importe es siempre positivo y el tipo da el signo. La categoría no se puede borrar si tiene movimientos (FK RESTRICT).
- `initial_balance(id = 1, amountMinor, startEpochDay)`: un solo registro.
- **Saldo = saldo inicial + ingresos − gastos**, contando solo movimientos desde la fecha inicial (inclusive). Se recalcula siempre desde los movimientos, así que editar o borrar uno corrige el saldo. Puede ser negativo (el formulario avisa, no bloquea).
- `income_sources(id, name, amountMinor, categoryId, schedule, daysOfMonth, customDates, startEpochDay, requiresConfirmation, active, remind, createdAt)`: una fila por fuente de ingreso.
- `income_occurrences(id, sourceId, dueEpochDay, amountMinor, status, transactionId, notified, createdAt)`: un cobro concreto. Estado: `PENDING`, `CONFIRMED` o `SKIPPED`. **Índice único (sourceId, dueEpochDay)**: no puede haber dos cobros de la misma fuente el mismo día.
- **Conciliación (`reconcile`)**: corre al abrir la app y en el revisor diario. En una sola transacción crea los cobros vencidos que faltan (desde la fecha de inicio de la fuente hasta hoy) y registra los automáticos. Es idempotente: correrla dos veces no duplica nada.
- Confirmar un cobro crea el movimiento de ingreso (con la fecha del cobro) y marca el cobro como confirmado en la misma transacción. Si borrás ese movimiento, el cobro pasa a *omitido* para que no se vuelva a generar. Al reactivar una fuente pausada se parte de hoy, sin atrasos.
- La base pasó a la versión 2 con una migración (`MIGRATION_1_2`) que agrega las dos tablas y conserva tus datos.
- `savings_goals(id, name, targetMinor, deadlineEpochDay?, autoPercent, remind, lastRemindedMonth, createdAt)`: una fila por meta.
- `savings_entries(id, goalId, amountMinor, dateEpochDay, note, sourceTransactionId?, createdAt)`: aporte (+) o retiro (−). FK a la meta con borrado en cascada. **No son movimientos**: no tocan el saldo total.
- **Disponible = saldo total − suma de `savings_entries`**. Se recalcula siempre; puede quedar negativo si gastás lo que tenías apartado (Inicio lo avisa). Aportar no puede superar lo disponible y retirar no puede superar lo ahorrado en esa meta.
- Aporte automático: al confirmar un cobro, en la misma transacción, cada meta con porcentaje recibe esa parte (sin pasar de lo que le falta ni del cobro). El aporte guarda el id del ingreso: si borrás ese ingreso, el aporte se deshace. Si lo editás, el aporte no se recalcula. Entre todas las metas el porcentaje no puede pasar de 100.
- Borrar una meta borra sus aportes: ese dinero vuelve a estar disponible. Los movimientos no se tocan.
- La base pasó a la versión 3 (`MIGRATION_2_3`) y conserva tus datos.
- `bills(id, name, amountMinor, categoryId, dueEpochDay, repeatDay, remind, paid, paidTransactionId?, paidEpochDay?, notified, createdAt)`: una fila por vencimiento. Mensual = `repeatDay` 1 a 31 (31 = último día); al pagarla se crea la siguiente con el mismo día (en meses cortos, el último día).
- **Factura pendiente = no toca el saldo.** Pagarla crea el gasto y marca la factura en una sola transacción. Si después borrás ese gasto, la factura sigue pagada pero sin enlace (no se reabre para no duplicar la del mes siguiente).
- `loans(id, person, direction, principalMinor, startEpochDay, note, remind, lastRemindedMonth, principalTransactionId?, createdAt)` y `loan_payments(id, loanId, amountMinor, dateEpochDay, note, transactionId?, createdAt)` (FK con borrado en cascada).
- **Préstamos y saldo:** prestar o recibir y cada devolución crean un movimiento **interno** (`transactions.internalTransfer = 1`, categoría "Préstamos"). Mueven el saldo, aparecen en Movimientos y en el extracto PDF (tipo "Préstamo"; el resumen suma ingresos y gastos sin ellos y aclara "Préstamos (neto)"), pero **no cuentan como ingreso ni gasto del mes** y no se editan desde Movimientos (se gestionan en Obligaciones). Una devolución no puede superar lo que falta. Borrar un préstamo borra también sus movimientos y el saldo vuelve a como estaba.
- **Libre estimado = disponible − facturas pendientes. Patrimonio = disponible + ahorrado + te deben − debés** (el ahorro y los préstamos no se cuentan dos veces).
- La base pasó a la versión 4 (`MIGRATION_3_4`: columna `internalTransfer`, categoría "Préstamos" y las tablas nuevas) y conserva tus datos.
- Todo el dinero es `Long` en unidad mínima (Gs. sin decimales, USD en centavos).

## Actualizaciones desde GitHub
Servicio externo (el único): `GET https://api.github.com/repos/Yatsufusax/YuzuFin/releases/latest`. No envía ningún dato tuyo.

- Cada push a `main` o `master` compila, corre los tests, publica el APK firmado como Release `build-<n>` (`n` = número de ejecución) y esa `n` es el `versionCode`.
- La app compara su `versionCode` con `n`. Las actualizaciones son **obligatorias**: mientras la app está abierta busca cada minuto (con espera creciente si no hay conexión), descarga sola la versión nueva y muestra una ventana que no se puede cerrar hasta instalarla. Android pide confirmar cada instalación y, la primera vez, el permiso "instalar apps desconocidas". No puede instalarse en silencio.
- La búsqueda de cada minuto lee la redirección de `github.com/<repo>/releases/latest`, que no cuenta contra el límite de 60 consultas por hora de la API. La API solo se consulta cuando hay versión nueva, para traer las novedades.
- **`NOVEDADES.md`**: es el texto de la ventana de actualización. Editalo antes de cada push con secciones `## Nuevo`, `## Mejorado`, `## Corregido` y puntos `- ...`; el workflow lo publica como notas de la Release.
- **El repositorio tiene que ser público**: la API de Releases responde 404 sin autenticación en repos privados.
- Para que la actualización se instale sobre la app vieja, el APK debe estar siempre firmado con la misma clave. Por eso la Release solo se publica si existen los secretos de firma.

### Configurar la firma (una sola vez)
En el repo: Settings → Secrets and variables → Actions → New repository secret:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Guardá la clave en un lugar seguro: si se pierde, las actualizaciones nuevas no se instalan sobre la app ya instalada. Nunca la subas al repo.

Nota: el APK de la Fase 1 estaba firmado con una clave de depuración aleatoria, así que la primera vez con la clave nueva hay que desinstalar la versión anterior. Todavía no hay datos reales que perder.

## Cómo ejecutarla
1. Abrí la carpeta en Android Studio (Ladybug o más nuevo, JDK 17+). Sincroniza Gradle.
2. Ejecutá `app` en un dispositivo o emulador.

Por terminal (con el SDK instalado y `local.properties` con `sdk.dir=...`):
```
./gradlew testDebugUnitTest      # tests
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/
```

## Resumen semanal
`WeeklySummaryScheduler` (trabajo periódico de 7 días, primer disparo el próximo domingo 20:00) y `WeeklySummaryWorker`. Reglas puras en `domain/WeeklySummary.kt` (`WeeklySummaryCalculator`): últimos 7 días que terminan hoy, solo gastos propios (sin ingresos ni movimientos internos de préstamos), categoría mayor (empate: id más bajo). Solo avisa el domingo desde las 20:00 (`shouldSend`); si el trabajo se retrasa a otro día, no avisa. Respeta `showAmountsInNotifications`. Sin gastos, avisa que no se anotó nada. Un aviso por semana en la campanilla (`NotificationKind.SUMMARY`, `dedupeKey = weekly:<día>`, id de notificación 4100). Interruptor propio en Ajustes (`weekly_enabled`, también en la copia de seguridad). Test: `WeeklySummaryTest`.

## Filtros de Movimientos
`MovementsViewModel` + `MovementFiltering.kt` (reglas puras: `applyMovementFilters`, `hasActiveFilters`, `cleared`). Filtros: texto, mes (o todo el historial), tipo, categoría y rango de monto (`minAmountMinor`/`maxAmountMinor`, ambos opcionales e inclusivos; chip "Monto" abre un diálogo). Con algún filtro activo se muestra "N resultados" y "Quitar filtros" (mantiene el mes). Los totales de arriba excluyen los movimientos internos de préstamos. El estado vive en el ViewModel (sobrevive a la rotación). Test: `MovementFilteringTest`.

## Widget "+ Gasto"
`widget/ExpenseWidgetProvider` (`AppWidgetProvider` con `RemoteViews`, sin dependencias nuevas; recursos `widget_expense`, `widget_expense_bg`, `widget_expense_info`, `strings_widget.xml`). El toque lanza `MainActivity` (ahora `singleTop`) con el extra `open=expense` (`QuickLaunch`). `MainActivity` lo lee solo al crearse sin estado guardado o en `onNewIntent` (y lo borra, para que rotar no lo repita); `MoneaApp` lo descarta si falta la configuración inicial; `MainShell` abre el formulario de gasto cuando la navegación está lista y no hay otro formulario abierto. El widget no muestra ni guarda datos. Test: `QuickLaunchTest`.

## Avisos "¿Anotaste hoy?"
Un trabajo periódico diario de WorkManager por cada hora elegida (`HabitReminderScheduler`, `HabitReminderWorker`; hasta 7 horas ofrecidas en Ajustes, por defecto 13, 18 y 21). Avisa solo si hoy no hay ningún gasto ni ingreso propio (`HabitReminder.hasEntryOn`; los movimientos internos de préstamos no cuentan). Texto según el momento del día (`HabitSlot`), sin importes. Si Android demora el trabajo más de 2 horas no avisa. Un aviso por hora y por día en la campanilla (`NotificationKind.HABIT`, `dedupeKey = habit:<día>:<hora>`). Interruptor propio, independiente de los avisos de cobros. Ajustes: `habit_enabled` y `habit_hours` (también en la copia de seguridad). Test: `HabitReminderTest`.

## Recordatorios (Fase 3)
WorkManager con una tarea periódica diaria a la hora elegida (revisa cobros, vencimientos de facturas, saldos de préstamos a fin de mes y, una vez al mes por meta, el aporte a ahorros); todo local, sin servicios externos. Necesita el permiso de notificaciones (Android 13+), que se pide al activar los recordatorios. Con ahorro de batería agresivo Android puede retrasar la hora. Las notificaciones son de visibilidad privada y no incluyen montos por defecto.

## Estructura
```
app/src/main/java/com/yuzufin/app/
  AppContainer.kt  Dependencias a mano (sin librería de DI)
  core/money/      Moneda, formato y lectura de importes (Long)
  domain/          Modelos y reglas del saldo (funciones puras)
  data/            Room (db/), repositorios, ajustes (DataStore), actualizador (update/)
  ui/theme|splash|components|navigation   Base visual y navegación
  reminders/       Recordatorios (WorkManager + notificaciones)
  ui/onboarding|home|movements|transaction|incomes|settings|update   Pantallas y ViewModels
```

## Siguiente paso
Ver `PLAN_DISENO.md` (estado de cada punto y lo que falta) y `MONEA_HANDOFF.md` (traspaso completo para continuar en otra conversación o cuenta).

## Temas y centro de notificaciones (entre Fase 5 y 6)
- **Paletas:** `ui/theme/Palettes.kt`. El verde conserva sus colores originales; las otras seis (violeta, azul marino, rojo, naranja, rosa, turquesa) se generan desde un tono para que fondos, superficies y contenedores combinen en claro y en oscuro. Ingresos (verde) y gastos (rojo) mantienen su significado en todas las paletas. Preferencias en DataStore: `theme_palette`, `theme_mode` (SYSTEM / LIGHT / DARK).
- **Centro de notificaciones:** tabla `app_notifications(id, kind, title, body, createdAt, read, dedupeKey)` con índice único en `dedupeKey` (el mismo aviso no se repite si el revisor corre dos veces). Base en versión 5 con `MIGRATION_4_5`. El revisor diario guarda cada aviso en la tabla y después lo muestra; se guardan los últimos 200. El texto guardado es el mismo de la notificación, así que no incluye importes salvo que lo actives.
- Sin verificar: no se compiló en el entorno donde se escribió; solo se revisaron recursos y balance de llaves con un script.

## Fase 6: categorías, presupuestos y estadísticas
- **Reglas de cálculo** (`domain/Stats.kt`, funciones puras con tests): ingreso/gasto del mes = movimientos confirmados, desde la fecha inicial, que no sean internos. Quedan fuera los préstamos (dados, recibidos, devueltos) y los aportes a ahorro (no son movimientos).
- **Presupuestos:** `budgets(id, categoryId único, limitMinor)`. Progreso = gasto del mes calendario ÷ tope. Nivel: normal (<80 %), aviso (≥80 %), superado (≥100 %). El revisor diario guarda un aviso por categoría, mes y nivel en la campanilla.
- **Categorías:** columna `categories.archived`. Archivadas no se ofrecen al registrar (la de un movimiento que se edita sí). Se eliminan solo si ningún movimiento, factura o ingreso fijo las usa. Las de préstamos son internas.
- **Recomendaciones:** reglas fijas (presupuesto superado o cerca, gasto mayor al ingreso, cierre estimado del mes, categoría que subió ≥20 %, ahorro bajo el 10 %, escenario de reducir 10 % la categoría más grande). Cada una muestra su cuenta; proyecciones y escenarios se marcan como aproximados. La proyección usa un promedio lineal y solo aparece desde el día 7 del mes.
- Base de datos en versión 6 (`MIGRATION_5_6`, conserva los datos).
- Sin verificar: no se compiló en el entorno donde se escribió.

## Copia de seguridad, cambio de moneda y borrado deslizando
- **Copia de seguridad** (`data/BackupManager.kt`): un `.zip` con `yuzufin.db` (después de un checkpoint del WAL), `settings.json` y `manifest.json`. Se guarda y se lee con el selector de archivos de Android (sin permisos de almacenamiento). **No está cifrado.** Al restaurar se valida: manifiesto de Monea, versión de la base no mayor a la actual, `integrity_check`, y las tablas principales. La base actual se renombra a `.pre-restore` hasta que la copia queda bien puesta; si algo falla se vuelve a poner. Una copia de una versión anterior se migra sola al abrir. Tras restaurar la app se reinicia.
- **Cambio de moneda** (`domain/CurrencyConversion.kt`, `data/CurrencyConverter.kt`): factor = tipo de cambio × 10^(decimales nuevos − decimales viejos), todo con BigDecimal. Se convierten las 10 columnas de dinero en una sola transacción y después se guarda la moneda. Redondeo al más cercano; un importe distinto de cero nunca queda en cero. Los totales pueden variar por centavos y volver atrás no devuelve los números exactos. El tipo de cambio se pregunta como "1 USD = X Gs." (si interviene el guaraní) o "1 [actual] = X [nueva]". "Mantener los números" solo cambia la moneda y sus decimales. Los avisos ya guardados en la campanilla conservan su texto original.
- **Borrado deslizando:** en Movimientos, deslizar a la izquierda abre la misma confirmación del formulario; los movimientos internos de préstamos no se deslizan (se gestionan desde Obligaciones).
- Sin verificar: no se compiló en el entorno donde se escribió.

## Fotos adjuntas y comprobante PDF
- Cada movimiento admite hasta 6 fotos (cámara o galería). Se guardan en `filesDir/attachments/` (privado), corregidas de rotación, a 1600 px máx. y JPEG. Tabla `attachments` (base de datos versión 7, migración 6→7 conserva los datos), borrado en cascada con el movimiento.
- Las fotos se agregan o quitan al tocar "Guardar". El botón "Generar comprobante en PDF" (al editar un movimiento) arma un PDF A4 con los datos y las fotos (`export/VoucherPdfWriter`), independiente del extracto; se comparte desde el menú de Android.
- La copia de seguridad incluye las fotos (`attachments/` dentro del zip). No es una factura ni tiene validez fiscal.

## Gastos fijos mensuales
- Menú → Gastos fijos. Cada gasto tiene nombre, importe, categoría, día del mes (si el mes es corto cae en su último día) y aviso opcional.
- Cada mes se crea un pago pendiente; el saldo solo cambia cuando se confirma ("Ya pagué", con el importe editable), que crea el movimiento de gasto. También se puede omitir. Pausar un gasto no genera los pagos de la pausa.
- Tablas `recurring_expenses` y `recurring_occurrences` (base de datos versión 8, migración 7→8; el cambio de moneda las convierte; entran en la copia de seguridad). Avisos diarios y de "mañana" desde `ReminderWorker`.

## Mascota y animaciones
- Imágenes en `res/drawable-nodpi/monito_*.webp` (fondo transparente, recortadas a su contorno, máx. 640 px). `ui/components/Mascot.kt`: `MascotPose` (21 poses; el comentario de cada una dice cuándo aparece) y `Mascot(...)` (respira, cambia de pose con efecto y salta con vibración al tocarlo).
- Inicio elige la pose según el estado: `MISSED_YOU` (7 días o más sin abrir la app; se calcula una vez por proceso con `SessionInfo` y `SettingsRepository.markOpened`), `BROKE` (saldo negativo), `NOTIFICATION` (cobro pendiente), `WELCOME` (mes vacío), `OVERSPENT` (gastos > ingresos), `RICH` (gastos ≤ 70 % de los ingresos y saldo positivo) y `PROGRESS`.
- Transiciones de navegación en `MoneaNavHost`.

## Didáctica
- `ui/components/HintCard.kt`: consejo del monito por pantalla. Se cierra con «Entendido» y se guarda en DataStore (`dismissedHints`). Para agregar uno nuevo: textos `hint_<clave>_title/body` en `strings_help.xml` y `HintCard(hintId = ...)` en la pantalla.
- `ui/help/HelpScreen.kt` (Menú → Ayuda): preguntas frecuentes desplegables y botón «Volver a mostrar los consejos». Textos `help_q_*` / `help_a_*` en `strings_help.xml`.
- Onboarding paso 1: `ThemeStep` reusa `AppearanceCard` de Ajustes; guarda paleta y modo al tocar (`SettingsRepository`), por lo que toda la pantalla se recolorea en vivo.
- `ui/onboarding/RestoreBackupCard.kt`: restaurar una copia desde la configuración inicial (pasos 1 y 2). Usa el mismo `BackupManager` que Ajustes.

## Ícono de la app
- Icono adaptativo: fondo azul marino (`ic_launcher_background`) y logo en `res/drawable-nodpi/ic_launcher_monea.webp`. No tiene versión monocromática (los íconos temáticos de Android 13 muestran el normal).

## Tarjeta MoneaCard y refresco de cobros
- `BankCard` en `ui/home/HomeScreen.kt`: el saldo disponible se dibuja como tarjeta bancaria (gradiente del tema, chip, pago sin contacto, nombre en mayúsculas, símbolo de dos círculos propio y brillo animado). El ojo oculta el saldo.
- Orden de Inicio: tarjeta de saldo → **una sola cosa para resolver** (`AttentionCard`; si no hay nada, el monito) → consejo → atajos → resumen del mes (barra gastado vs. ingresado) → movimientos recientes → próximo cobro y próximos vencimientos. `HomeUiState.attention` (`AttentionItem`: cobro por confirmar, factura vencida o que vence en ≤ 3 días, gasto fijo pendiente) se arma en `HomeViewModel` en ese orden de prioridad; los cobros se confirman en la misma tarjeta (con el diálogo de siempre), las facturas y los gastos fijos abren su pantalla. "Ver N más" despliega el resto.
- `HomeViewModel` e `IncomesViewModel` re-consultan los datos (`refresh` + `flatMapLatest`) al terminar de confirmar u omitir un cobro, y ignoran toques repetidos sobre el mismo cobro (`inFlight`).

## Anotar rápido (punto 1 del plan de diseño)
- Botón "+" central: un toque abre directo un gasto nuevo; mantenerlo apretado abre la hoja con las otras acciones (`FloatingNavBar`, `MainShell`).
- Movimiento nuevo → `ui/transaction/QuickEntry.kt` (importe grande, teclado propio, sumas rápidas, frecuentes, grilla de categorías, Hoy/Ayer y "Más detalles"). Editar un movimiento sigue usando el formulario clásico.
- `TransactionFormViewModel`: `onKey`, `onQuickAdd`, `onFrequentPick`; categorías ordenadas por uso y frecuentes calculados del historial (últimos 120 días, 2 o más repeticiones).
- Plan completo y estado de cada punto: ver `PLAN_DISENO.md`.

## Barra inferior, Ajustes y notas de versión
- Barra inferior (`ui/navigation/FloatingNavBar.kt`): a la izquierda **Inicio** y **Movimientos**, al centro el **+** y a la derecha **Ajustes** y **Menú** (mitades iguales: el + queda justo en el centro). `Destination.Settings` es de primer nivel (`isTopLevel = true`, vuelve a Inicio con atrás) y salió de `isMenuGroupRoute`, así que no marca "Menú" como activo. Ajustes ya no aparece dentro de la pantalla Menú.
- Ajustes ya no muestra el bloque "Lo que va a incluir" (se quitó el array `settings_features`; el texto `coming_soon_features_title` sigue porque lo usa `ComingSoonScreen`, que hoy no se llama desde ningún lado).
- **Notas de actualización en la campanilla:** como la actualización es obligatoria y se instala enseguida, la ventana pasa demasiado rápido para leer las novedades. Ahora `UpdateViewModel.saveNotes` guarda las notas de la Release (el contenido de `NOVEDADES.md`, convertido a texto plano con viñetas por `ReleaseNotes.toPlainText`) en `app_notifications` apenas se detecta la versión nueva. Tipo `NotificationKind.UPDATE` (ícono `NewReleases`), título "Novedades de Monea 0.2.N" y clave de deduplicación `update-<buildNumber>` (una sola vez por versión aunque se vuelva a detectar). No hizo falta migración de base de datos (`kind` se guarda como texto). Se leen abriendo la campanilla; el cuerpo se muestra completo.
- Convención: `NOVEDADES.md` va a la campanilla tal cual, así que conviene que contenga solo lo nuevo desde la última Release publicada (cuando se confirme que una versión salió, vaciar lo anterior y empezar de nuevo).
- Test nuevo: `ReleaseNotesTest` (texto plano con viñetas y vacío).

## Menú agrupado y borrar con deshacer (punto 3)
- `MenuScreen`: tres grupos con título (Dinero / Planificar y analizar / Ayuda).
- Borrar un movimiento: `DeleteUndo` (en `ui/common`) oculta el movimiento con `FinanceRepository.hideTransaction`, muestra el aviso con "Deshacer" 5 segundos y recién después llama a `deleteTransaction`. Se ofrece a las pantallas con `LocalDeleteUndo`.

## Aviso de novedades y animaciones (v40)
- Campanilla: los avisos de versión (`NotificationKind.UPDATE`) son cortos y abren `UpdateNotesWindow` con el texto completo.
- `ui/common/Effects.kt`: `dropIn` (movimiento recién guardado) y `rememberFillAnimation` (barras de progreso).

## Accesibilidad y primer uso (punto 5)
- **Contraste:** los colores de `Color.kt` y `Palettes.kt` se ajustaron para cumplir WCAG AA en todas las paletas, claro y oscuro (texto 4,5 a 1; bordes 3 a 1). `PalettesTest.textHasEnoughContrastInEveryPalette` lo vigila: si se cambia un color y baja del mínimo, el CI falla y dice qué par.
- **Tamaño del texto:** `TextScale` (`NORMAL` 1,0 / `LARGE` 1,15 / `XLARGE` 1,3) en `AppSettings.textScale`, guardado en DataStore (`text_scale`) y en la copia de seguridad (`textScale`). `ProvideTextScale` (`ui/common/Accessibility.kt`, usado en `MoneaApp`) multiplica el `fontScale` del teléfono por ese factor, con tope 2,0. `CapFontScale` limita la letra en zonas de tamaño fijo: barra inferior (1,3) y tarjeta del saldo (1,15). El selector está en `AppearanceCard` (Ajustes y configuración inicial).
- **Zonas táctiles:** mínimo 48 dp en el botón de borrar de la campanilla, quitar foto, ojo de la tarjeta, "Ahorrado" de Inicio e íconos de categoría. Los botones fijos de 52 dp de las ventanas pasaron a `heightIn(min = 52.dp)` para crecer con la letra.
- **Recorrido del primer uso:** `ui/tour/FirstUseTour.kt`, diálogo a pantalla completa de 3 pasos (anotar un gasto, resumen de Inicio, ayuda) con "Saltar", "Atrás" y "Siguiente" abajo. Se muestra desde `MainShell` mientras `AppSettings.tourPending` sea verdadero. `SettingsRepository.completeOnboarding` lo activa; `finishTour` lo apaga; `restartTour` (botón en Ayuda) lo vuelve a activar. Quien restaura una copia o ya tenía la app no lo ve solo.
- Tests nuevos: contraste en `PalettesTest` y `TextScaleTest`.

## Toques repetidos y primer gasto (v43)
- `RecurringViewModel.confirm/skip`, `ObligationsViewModel.payBill` y `repay` ignoran toques repetidos (`inFlight`) y re-consultan los datos al terminar (`refresh` + `flatMapLatest`), igual que Inicio e Ingresos. Los repositorios ya rechazaban pagos repetidos dentro de la transacción.
- Primer gasto: `FirstExpenseCelebration` (diálogo con el monito `GOAL_DONE`) se muestra una sola vez cuando se guarda el primer gasto nuevo de la app.

## Pantallas completas sin Dialog (v44)
- `FirstUseTour` y `UpdateNotesWindow` ya no usan `Dialog`: en Samsung con gestos el diálogo no recibía el margen de la barra de navegación y el botón quedaba tapado. Ahora son un `Surface` a pantalla completa dentro de la composición (con `BackHandler` y bloqueo de toques), igual que la configuración inicial. `UpdateNotesWindow` recibe `bottomPadding` (el de la barra flotante).

## Intro en video (v48)
- `res/raw/monea_intro.mp4` (4 s, 1280x720, H.264 + AAC, ~0,7 MB): el video que subió el usuario, con el fondo de cuadritos "transparentes" eliminado y reemplazado por el verde oscuro de la splash del sistema (`#0F2A24`) con un brillo suave. Script: `tools/key_checkerboard_video.py` (Python + ffmpeg + scipy; flood-fill de los píxeles neutros y claros que tocan el borde).
- `ui/splash/VideoSplash.kt`: `VideoView` dentro de `MoneaApp` (etapa Splash). Se salta tocando; sin sonido si el teléfono está en silencio o vibración; no pide foco de audio; si falla, cae a `AnimatedSplash`. No se muestra si la app se abre desde el widget "+ Gasto".

