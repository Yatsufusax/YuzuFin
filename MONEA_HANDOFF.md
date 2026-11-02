# Monea (antes YuzuFin): documento de traspaso para continuar el proyecto

Pegá este documento al empezar una conversación nueva (otra cuenta de Claude, por ejemplo) y adjuntá el último zip del proyecto (`monea-proyecto-v44.zip` o el más nuevo). Sin el zip no hay código: la otra cuenta no tiene acceso a mis archivos. Este mismo archivo viaja dentro del zip (raíz del proyecto) junto con `README.md`, `NOVEDADES.md` y `PLAN_DISENO.md`.

**Qué leer primero en la conversación nueva (y nada más, salvo lo necesario):** este documento, `MONITO_ANIMADO_README.md` si el tema es el monito animado, `PLAN_DISENO.md` (estado de cada punto del plan) y `NOVEDADES.md`. El `README.md` explica el modelo de datos y las reglas de negocio con detalle.

## 1. Qué es
Monea es una app Android de finanzas personales (control de gastos, ahorros, facturas, préstamos). Es **local primero**: todo queda en el teléfono, sin cuenta, sin servidor, **sin IA** y sin conexión a bancos.

- Interfaz en español rioplatense con voseo ("Tenés", "Anotá", "Pagué").
- Moneda por defecto: guaraní (Gs.). También USD, ARS, BRL y EUR.
- Lema: "Tu dinero. Tus decisiones." (`R.string.tagline`).
- Mascota: un monito marrón. Logo: el monito con celular, mates, alcancía y el texto MONEA con banderas de Paraguay y Argentina.
- Objetivo de diseño (de `PLAN_DISENO.md`): **anotar un gasto tiene que costar menos de 5 segundos y con una mano**. Todo lo demás va encima de eso.

### Cambio de nombre: qué cambió y qué NO
El proyecto se llamaba **YuzuFin** y se renombró a **Monea** en todo lo visible (nombre de la app, textos, PDFs, APK `Monea-<versión>.apk`, clases `MoneaApp`, `MoneaTheme`, `MoneaCard`, `MoneaNavHost`, `MoneaApplication`, tema `Theme.Monea`).

**Se dejó igual a propósito** (para que la app instalada se actualice sola y no pierda datos):
- Paquete y applicationId: `com.yuzufin.app` (no cambiarlo: Android lo trataría como otra app).
- Base de datos: `yuzufin.db`.
- Repo de GitHub: `Yatsufusax/YuzuFin`. El actualizador consulta ese repo (si el usuario lo renombra, GitHub redirige).
- `BackupManager`: las copias nuevas se marcan `app = "Monea"`, pero se aceptan las viejas con `"YuzuFin"` (`LEGACY_APP_ID`).

## 2. Stack
Kotlin 2.0.21 · Jetpack Compose (BOM 2024.12.01, Material 3 1.3.x) · Room · DataStore · WorkManager · Navigation Compose 2.8.5 · Activity Compose 1.9.3 · Android `PdfDocument` para PDFs. minSdk 26.

- El dinero se guarda como `Long` en unidades mínimas. Los guaraníes no tienen decimales.
- Los importes se muestran con `MoneyFormatter` y se leen con `AmountParser`.
- Versión: `versionCode` = número de ejecución del CI; `versionName` = `0.2.<n>`.

## 3. Cómo trabaja el usuario (muy importante)
- **Claude no puede compilar.** No hay Gradle en el entorno de trabajo. Todo lo que se entrega está sin compilar y el usuario lo prueba con GitHub Actions. Hay que decirlo con honestidad en cada entrega.
- **Flujo:** el usuario copia el contenido del zip a la carpeta del repo en GitHub Desktop (verifica que el nombre del repo diga YuzuFin), hace commit y push a `main`/`master`. El CI compila, corre los tests y publica un release `build-N`. La app se actualiza sola desde ese release (las actualizaciones son obligatorias).
- **El zip va sin `.git`, `.gradle` ni `build`.** Cada entrega actualiza `NOVEDADES.md` (secciones `## Nuevo` y `## Mejorado`; es el texto de la ventana de actualización **y ahora se guarda en la campanilla**), `README.md` y, si corresponde, `PLAN_DISENO.md`.
- **LECCIÓN CLAVE: un zip copiado encima del repo NO borra archivos.** Si se renombra o quita un archivo, el viejo queda al lado del nuevo y rompe el build (pasó con `.png` viejos junto a `.webp` nuevos: "Duplicate resources"). Reglas:
  - Recursos nuevos o cambiados de formato, con **nombres nuevos**.
  - El workflow `.github/workflows/build.yml` tiene un paso **"Quitar archivos viejos"** con `rm -f` de lo que se renombró o quitó (`mascot_*`, `YuzuFinApp.kt`, `YuzuNavHost.kt`, `ic_stat_yuzu.xml`, `ic_launcher_foreground.xml`). **Cada vez que se renombre o borre un archivo, agregar su línea ahí** y avisar al usuario qué borrar a mano en su carpeta local (opcional, para dejar el repo prolijo).
- **No releer todo el código** entre pasos: el usuario se enoja si se gastan tokens recopilando de nuevo. Leer solo lo necesario, con `grep` y rangos de líneas. Si el usuario dice "solo leé tal archivo", hacerlo sin más.
- Hay un límite diario de uso. Conviene hacer entregas completas y pequeñas, y avisar en pocas palabras qué se está haciendo cuando algo tarda.
- Al terminar una entrega hay que **presentar el zip** (`present_files`): una vez se armó y se olvidó mostrarlo.
- No quiere etiquetas (tags) en los movimientos ni bloqueo de la app. Rechazó la sincronización con bancos.
- Tiene un reloj Samsung y usa el teléfono con Android 13 o superior (Samsung).
- Hay dos cuentas de Claude trabajando en el mismo proyecto (la versión v36 trajo cambios hechos en la otra: anotar rápido, tarjeta MoneaCard, arreglo de refresco de cobros). Siempre trabajar sobre **el zip más nuevo que suba el usuario**, no sobre una copia anterior propia.

## 4. Qué está hecho
| Área | Detalle |
|---|---|
| Fases 1-6 | Onboarding, movimientos, ingresos fijos con confirmación manual o automática, metas de ahorro, facturas y préstamos, categorías editables/archivables, presupuestos con avisos, estadísticas. Detalle en `README.md`. |
| Seguridad de datos | Copia de seguridad y restauración en zip (datos, ajustes y fotos). Cambio de moneda con conversión de todos los importes. |
| Movimientos | Deslizar a la izquierda para borrar (con confirmación). Clip si tiene fotos. |
| Fotos y PDF | Hasta 6 fotos por movimiento en `filesDir/attachments/`. Comprobante PDF por movimiento y extracto PDF general. |
| Gastos fijos | Mensuales. Pago pendiente cada mes; "Ya pagué" (importe editable) u omitir. Avisos el día y el día anterior. |
| Temas | Paletas de color con modo claro y oscuro. Se elige también al empezar (paso 1 de la configuración inicial, en vivo). |
| Campanilla | Historial de avisos (`app_notifications`). **Ahora también guarda las novedades de cada versión** (`NotificationKind.UPDATE`). |
| Renombre a Monea | Ver sección 1. |
| Splash (respaldo) | `ui/splash/AnimatedSplash.kt`: logo (`monea_logo.webp`) con rebote, brillo verde, flotación y lema; ~2,8 s, se salta tocando. |
| Monitos | 21 poses en `res/drawable-nodpi/monito_*.webp` (sección 6). |
| Ícono de la app | Adaptativo: fondo `#0A1E46` + `ic_launcher_monea.webp`. Sin capa monocromática. |
| Didáctica | `HintCard` por pantalla (se cierra con "Entendido") y pantalla **Ayuda** (Menú → Ayuda) con 12 preguntas. |
| Restaurar al empezar | `RestoreBackupCard` en los dos pasos de la configuración inicial. |
| Anotar rápido (Punto 1, otra cuenta) | "+" central: un toque = gasto nuevo directo en `QuickEntry` (teclado propio, sumas rápidas, categorías en grilla, Hoy/Ayer, frecuentes); mantener apretado = hoja con Pagar, Préstamo, Ahorro. |
| Tarjeta de resumen (v54, sin compilar) | `SummaryCard` en Inicio (plantilla del usuario): presupuesto disponible = dinero disponible hoy (`state.balance`, no el neto del mes: así el 1.º de cada mes no muestra 0), barra y % gastado del mes elegido, ingresos y gastos, selector de mes, ojo y atajo a Estadísticas, con el brillo animado. Reemplaza a `BankCard` y a `MonthSummaryCard`; se quitó también el botón "Ver movimientos". "Libre estimado" queda siempre visible debajo. `CardMonkey`: monito con la camiseta de la moneda; al tocarlo gira y reproduce su clip: festejo del Mundial con ARS (`monito_clip_mundial.webp`) o empanada y tereré con PYG (`monito_clip_paraguay.webp`). Detalle en `README.md`. |
| Refresco de cobros (otra cuenta) | `HomeViewModel` e `IncomesViewModel`: `refresh` + `flatMapLatest` e `inFlight` para que al confirmar un cobro Inicio se actualice y los toques repetidos no se procesen dos veces. |
| **Inicio en 2 segundos (Punto 2)** | Orden: saldo → **una sola cosa para resolver** (`AttentionCard`: cobro por confirmar, factura vencida o que vence en ≤ 3 días, gasto fijo pendiente; "Ver N más"; si no hay nada, el monito) → consejo → atajos → resumen del mes (barra gastado vs. ingresado) → movimientos recientes → próximo cobro y vencimientos. |
| **Aviso de novedades (v40)** | La campanilla muestra "Actualización v0.2.N" + "Tocá para ver las novedades"; al tocar abre `UpdateNotesWindow` con el texto completo. |
| **Punto 4 (v40, v43)** | `dropIn` para el movimiento recién guardado, `rememberFillAnimation` en las barras de progreso, monito `OVERSPENT` en Presupuestos y festejo del primer gasto (`FirstExpenseCelebration`). Falta solo la identidad visual coherente en el resto de pantallas. |
| **Menú y deshacer (Punto 3)** | Menú agrupado en Dinero / Planificar y analizar / Ayuda. Borrar un movimiento no pregunta: se oculta (`FinanceRepository.hideTransaction`) y `DeleteUndo` muestra "Deshacer" 5 s antes del borrado real. |
| **Intro en video (v48, video nuevo en v49)** | `ui/splash/VideoSplash.kt` reproduce `res/raw/monea_intro.mp4` (vertical 720x1280, entero, sobre `#112825`) y dibuja debajo del logo la frase "Tu dinero. Tus decisiones." con fundido; `AnimatedSplash` es el respaldo. Se salta tocando, no suena en silencio, no se muestra desde el widget. |
| **Anotar con la voz (v54, sin compilar)** | Mantener apretado el "+" abre `ui/voice/VoiceEntryScreen.kt` (ruta `VoiceRoute`, pantalla completa sin `Dialog`): `SpeechRecognizer` de Android con `EXTRA_PREFER_OFFLINE`, permiso `RECORD_AUDIO` (se pide al abrir) y `<queries>` de `RecognitionService`. `domain/VoiceParser.kt` (reglas, sin IA; test `VoiceParserTest`) saca importe (palabras, cifras, "mil", "millón y medio"), tipo (gasto/ingreso), categoría (nombre, palabra del nombre o palabras clave por `iconKey`) y descripción. La frase viaja en el argumento `say` de `TransactionRoute` y `TransactionFormViewModel.applySpoken` prellena el formulario (nunca guarda solo). El formulario muestra el cartelito `VoiceFilledNotice` (`TransactionFormState.fromVoice`) cuando se prellenó por voz. Un toque en el "+" = anotar a mano; se quitó la hoja `QuickActionsSheetContent` (los atajos Pagar/Préstamo/Ahorro siguen en Inicio). Reconocimiento con 3 niveles de tolerancia (idioma + preferir offline → solo idioma → idioma del teléfono) y plan B con `ACTION_RECOGNIZE_SPEECH` (dictado de Android); el error muestra el código. Corregido en v59 tras un video del usuario: en su Samsung fallaba al instante con el nivel 0. |
| **Aviso de la voz (v60, sin compilar)** | Globito flotante `ui/voice/VoiceTipBubble.kt` sobre el "+": "Anotá hablando / Mantené apretado el «+» y decí tu gasto". Aparece una sola vez en Inicio (id `voice_plus` en `dismissedHints`, 1,5 s de espera y 12 s visible); se cierra al tocarlo o al usar el "+". Se reactiva con "Volver a mostrar los consejos". |
| **Monito animado y mensajes (v50 ok, v51 sin compilar)** | Clip WebP animado del monito con monedas que se reproduce solo y al tocar (`MascotClip.kt`, `autoClip`) y globo que tipea letra por letra mensajes rotativos, sin botones (`MascotTalk.kt`). Estado, decisiones y pasos en `MONITO_ANIMADO_README.md`; prompts de Flow en `PROMPTS_FLOW_MONITOS.md`; script en `tools/clip_verde_a_webp.py`. |
| **Punto 5 (v41)** | Contraste AA en todas las paletas (con test), tamaño del texto Normal/Grande/Muy grande (`TextScale`, `ProvideTextScale`, `CapFontScale`), zonas táctiles de 48 dp y recorrido de 3 pasos del primer uso (`FirstUseTour`, `AppSettings.tourPending`; repetible desde Ayuda). |
| **Barra inferior (v38)** | Inicio, Movimientos, **+**, **Ajustes**, Menú. Ajustes salió de la pantalla Menú. |
| **Ajustes (v38)** | Se quitó el bloque "Lo que va a incluir". |
| **Notas en la campanilla (v38)** | Al detectar una versión nueva, `UpdateViewModel.saveNotes` guarda `NOVEDADES.md` (texto plano con viñetas) como aviso "Novedades de Monea 0.2.N", una sola vez por versión (`dedupeKey = update-<build>`). |

## 5. Reglas de negocio que no hay que romper
- El saldo solo cambia cuando existe un **movimiento** real. Los cobros y gastos fijos pendientes, y las facturas sin pagar, no tocan el saldo hasta confirmarse o pagarse.
- Los movimientos internos de préstamos (`internalTransfer`) **no cuentan** como ingreso ni gasto en las estadísticas.
- Los aportes a metas de ahorro **no son movimientos**: no entran al saldo total ni al extracto.
- Cada cobro o gasto fijo (fuente + fecha) existe una sola vez (índice único). La reconciliación puede correr muchas veces sin duplicar.
- No se generan pagos anteriores a la fecha inicial de las cuentas ni de la fuente. Al reactivar algo pausado se parte de hoy.
- **Cambio de moneda:** `CurrencyConverter.MONEY_COLUMNS` lista todas las columnas con dinero. Toda tabla nueva con importes debe agregarse ahí.
- **Base de datos:** nunca se borra. Versión **8**, migraciones 1→8 explícitas con SQL (`exportSchema = false`). Cada cambio necesita una migración nueva. Las columnas booleanas nuevas llevan `@ColumnInfo(defaultValue = "0")`.
- Términos de Inicio: "Saldo disponible" (saldo − apartado), "Comprometido" (facturas pendientes), "Libre estimado" (disponible − comprometido), "Ahorrado", "Patrimonio".
- La campanilla usa `dedupeKey` único: el mismo aviso no se repite. Guarda los últimos 200.

## 6. Estructura (resumen)
Todo bajo `app/src/main/java/com/yuzufin/app/` (el paquete sigue siendo `yuzufin`):
- `data/db/`: `Entities.kt`, `Daos.kt`, `AppDatabase.kt`.
- `data/`: repositorios (`FinanceRepository`, `IncomeRepository`, `RecurringRepository`, `SavingsRepository`, `ObligationsRepository`, `BudgetRepository`, `AttachmentRepository`, `NotificationRepository`, `SettingsRepository`), `BackupManager`, `CurrencyConverter`, `AttachmentFileStore`, y `data/update/` (`UpdateChecker`, `ApkDownloader`, `ReleaseNotes`).
- `domain/`: modelos y reglas puras (`Models.kt` con `AppSettings`, `AppNotification.kt`, `Income.kt`, `Recurring.kt`, `Stats.kt`, `Budget.kt`…).
- `ui/`: una carpeta por pantalla (`home`, `movements`, `transaction`, `incomes`, `recurring`, `savings`, `obligations`, `stats`, `budgets`, `categories`, `settings`, `notifications`, `onboarding`, `help`, `menu`, `splash`, `update`), más `components/` (`Mascot.kt`, `HintCard.kt`, `Components.kt` con `MoneaCard`, `ScreenHeader`, `IconBadge`…), `common/` (`Effects.kt`: `entrance`, `rememberCountUp`, `ConfettiBurst`), `navigation/` (`Destination.kt`, `FloatingNavBar.kt`, `MoneaNavHost.kt`), `theme/`, `MoneaApp.kt` (splash → onboarding → `MainShell`).
- `export/`: `StatementPdfWriter` y `VoucherPdfWriter` / `VoucherBuilder`.
- `reminders/`: `ReminderWorker`, `NotificationHelper`.
- `AppContainer.kt`: inyección manual de dependencias. Incluye `SessionInfo` (`checked`, `daysAway`).
- Textos en `res/values/strings_*.xml` (`strings_help.xml`, `strings_attention.xml`, `strings_quick.xml`, `strings_mascot.xml`…).

### Poses del monito (`MascotPose`, en `ui/components/Mascot.kt`)
Archivo = `monito_<nombre>.webp`:

| Pose | Dónde se usa |
|---|---|
| `WELCOME` | Inicio, mes sin movimientos |
| `MISSED_YOU` | Inicio, 7+ días sin abrir la app (`SessionInfo.daysAway`, `SettingsRepository.markOpened`) |
| `BROKE` | Inicio, saldo negativo |
| `RICH` | Inicio, saldo > 0 y gastos ≤ 70 % de ingresos |
| `OVERSPENT` | Inicio, gastos > ingresos |
| `PROGRESS` | Inicio (resto de casos), consejo de Estadísticas |
| `NOTIFICATION` | Tarjeta "Para resolver ahora", Notificaciones vacías |
| `GOAL_DONE` | Ahorros, meta cumplida |
| `SAVING` | Consejo de Ahorros |
| `PIGGY` | Ahorros vacío |
| `COIN_HUG` | Movimientos vacío, consejo de Ingresos |
| `WONDER` | Movimientos sin resultados, consejo de Movimientos |
| `CONTROL` | Consejo de Categorías |
| `BUDGET` | Consejo de Presupuestos |
| `PAYING` | Consejo de Facturas y préstamos |
| `PLANNING` | Banner vacío de Inicio, paso 2 del onboarding, consejo de gastos fijos |
| `TIP` | Consejos por defecto |
| `QUESTION` | Pantalla Ayuda |
| `TERERE` / `MATE` / `COFFEE` | Onboarding según moneda (PYG→tereré, ARS→mate, resto→café) |

## 7. Didáctica y consejos
- **`HintCard(hintId, titleRes, bodyRes, modifier, pose)`**: guarda el id cerrado en DataStore (`dismissed_hints`). No lleva padding horizontal propio (el que la usa pasa `Modifier.padding(horizontal = 16.dp)`; en Inicio no hace falta). Para sumar uno: `hint_<clave>_title/body` en `strings_help.xml` y `HintCard(...)` en la pantalla. Ids: `home_numbers`, `movements`, `savings`, `incomes`, `recurring`, `obligations`, `budgets`, `stats`, `categories`, `backup`.
- **`HelpScreen`** (`Destination.Help`): preguntas `help_q_*` / `help_a_*` desplegables y botón "Volver a mostrar los consejos" (`resetHints()`).

## 8. Cómo verificar sin compilar
Revisar a mano (los scripts que se usaron fallaron alguna vez). Lo que se hizo y conviene repetir:
- Recursos `R.string.*`, `R.plurals.*`, `R.drawable.*` inexistentes y nombres de strings duplicados.
- Llaves y paréntesis balanceados. **Cuidado con los cortes por índice en scripts**: una vez un `s[:a] + nuevo + s[b:]` con `b < a` duplicó medio archivo y los conteos de llaves no lo delataron; usar `assert a < b` y contar apariciones de funciones.
- **Imports faltantes** en los archivos tocados (ya hubo un CI roto por `import ...layout.size`).
- Archivos viejos que quedan al copiar el zip (sección 3).
- No usar `%` suelto en strings sin argumentos (escribir "por ciento").
- `rememberSaveable` con `mutableStateOf(...)`, no con `mutableIntStateOf`.
- `when` sobre una clase sellada o enum debe ser exhaustivo: al agregar un valor nuevo (por ejemplo `NotificationKind.UPDATE`) revisar todos los `when`.
- Datos de las tablas nuevas en `CurrencyConverter.MONEY_COLUMNS`; el esquema de Room coincide con la migración.

Errores que ya encontró el CI: un import faltante, un bug al importar fotos (`BitmapFactory.decodeStream` con `inJustDecodeBounds` siempre devuelve `null`) y "Duplicate resources" por archivos viejos.

## 9. Estado de pruebas
**El usuario confirmó (v44) que todo lo hecho hasta v44 funciona en el teléfono y compila**: anotar rápido, Inicio, menú y deshacer, animaciones, tamaño de texto, contraste, recorrido de 3 pasos, monito del primer gasto, toques repetidos en Gastos fijos y Facturas, fotos y comprobante PDF. No hay nada pendiente de probar de las versiones anteriores.

**Pendiente de probar (v47, sin compilar):** resumen semanal (`domain/WeeklySummary.kt`, `reminders/WeeklySummaryScheduler.kt`, `WeeklySummaryWorker.kt`, tarjeta `WeeklySummaryCard` en Ajustes, `strings_weekly.xml`, `NotificationKind.SUMMARY`, test `WeeklySummaryTest`; ajuste `weeklySummaryEnabled`) y, de v46: filtros de Movimientos (`ui/movements/MovementFiltering.kt`, chip y diálogo "Monto", contador y "Quitar filtros", `strings_filters.xml`, test `MovementFilteringTest`) y, de v45: widget "+ Gasto" (`widget/ExpenseWidgetProvider.kt`, `ui/navigation/QuickLaunch.kt`, `MainActivity` ahora `singleTop` y con `openExpense`, recursos `widget_expense*`, `strings_widget.xml`, test `QuickLaunchTest`) y avisos "¿Anotaste hoy?" (`reminders/HabitReminderScheduler.kt`, `HabitReminderWorker.kt`, `domain/HabitReminder.kt`, tarjeta `HabitRemindersCard` en Ajustes, `strings_habit.xml`, `NotificationKind.HABIT`, test `HabitReminderTest`). Ajustes nuevos: `habitRemindersEnabled` y `habitHours`.

Historial de entregas recientes (todas ya probadas):
- **v51:** el monito se anima solo y los mensajes se tipean; se quitaron los botones (sin probar). v50 compiló y el usuario la aprobó.
- **v49:** video de intro nuevo (vertical) mostrado entero y frase "Tu dinero. Tus decisiones." debajo del logo (sin probar).
- **v48:** intro en video (el usuario la vio en el teléfono: la app compiló; dijo que el video "no se veía tan bien" y mandó otro).
- **v44:** el recorrido (`FirstUseTour`) y `UpdateNotesWindow` dejaron de ser `Dialog` y se dibujan dentro de la app con `statusBarsPadding`/`navigationBarsPadding` (en el teléfono del usuario el `Dialog` no recibía el margen de la barra de gestos y los botones quedaban tapados; las notas reciben `bottomPadding` para no quedar bajo la barra flotante). **Regla: para pantallas completas, no usar `Dialog`.**
- **v43:** monito del primer gasto (`FirstExpenseCelebration`, `AppSettings.firstExpensePending`), `inFlight` + `refresh` en `RecurringViewModel` y `ObligationsViewModel` (payBill y repay).
- **v42:** títulos del onboarding en oscuro (`LocalContentColor`).
- **v41:** tamaño del texto, recorrido de 3 pasos, colores con más contraste, botones de 48 dp.
- **v40:** aviso corto de actualización + ventana de novedades, animaciones del punto 4.
- **v39:** Menú agrupado y borrar con Deshacer.
- **v38:** Ajustes en la barra, Menú sin Ajustes, notas de versión en la campanilla.

Si un build falla, el usuario pega el log y se corrige el error puntual.

### Detalles menores conocidos
- Los garabatos negros de los monitos de tereré y café casi no se ven en tema oscuro (es del arte).
- Cuando se cierra un consejo dentro de una lista (LazyColumn) queda un hueco de 12 dp (cosmético).
- TalkBack: falta auditar los íconos clickeables sin `contentDescription`.

## 10. Arte y cómo se procesó (por si llega arte nuevo)
- Todo con Python/PIL en el contenedor. Los monitos vienen como PNG RGBA con transparencia real: se recortan al bounding box (`alpha > ~10`), se reducen a 640 px de lado mayor y se guardan como WebP (calidad ~90, `method=6`) con nombre `monito_<algo>.webp`.
- **El logo `MONEA` venía con el fondo de cuadritos pegado en los píxeles (PNG RGB sin alpha).** Se recortó con flood-fill desde los bordes sobre píxeles neutros y claros (`max-min ≤ 6`, `min ≥ 228`), dilatando 1 px para comerse el halo y suavizando el borde. Hay que repetirlo si llega otra imagen así.
- Ícono: lienzo de 576 px, logo al 68 %, fondo azul marino `#0A1E46`.
- Video de la intro: el usuario lo genera con IA. El primero traía el fondo de cuadritos "transparente" pegado en los píxeles y se limpió con `tools/key_checkerboard_video.py` (ver README). El de v49 ya viene con fondo verde propio y se usa sin procesar; para que no haya franjas en pantallas altas, el fondo de la intro es el color de los bordes del video (`VideoBackground`).
- Siempre **nombres nuevos** para recursos nuevos.

## 11. Lo que sigue (en orden de prioridad)
1. ~~Probar y arreglar~~ (todo confirmado hasta v44). Lo que sigue está en **`MONEA_IDEAS_PROXIMOS_PASOS.md`** (recordatorio nocturno, widget, CSV, filtros, resumen semanal, deshacer en más pantallas). Preguntar al usuario con cuál empezar; se recomendó la 1 y la 2.
2. ~~Toques repetidos y refresco en Gastos fijos y Facturas~~ (hecho en v43, sin probar; los repositorios ya validaban el estado dentro de la transacción, así que no había duplicados reales, solo pantalla sin refrescar).
3. ~~Punto 3 del plan~~ (hecho en v39, sin probar). Falta, si el usuario quiere, el mismo "Deshacer" para metas, facturas y fuentes de ingreso.
4. **Punto 4** (en curso, ver `PLAN_DISENO.md`): el monito del primer gasto ya está (v43); falta la identidad visual coherente.
5. ~~Punto 5~~ (hecho en v41, sin probar). Falta mirar en el teléfono con letra grande y TalkBack (ver `PLAN_DISENO.md`).
6. Más didáctica: textos de ayuda dentro de los formularios, y el monito/logo en los encabezados de los PDFs (hoy tienen las hojas del diseño viejo).
7. Ideas sueltas: widget "+ Gasto", recordatorio nocturno inteligente ("¿Anotaste hoy?"), resumen semanal, detección de gastos desde notificaciones de pago (`NotificationListenerService`, lista "Detectados para confirmar", nunca guardar el texto completo; hacen falta 2 o 3 ejemplos reales con datos tapados), versión Google Play con Billing (variante sin el actualizador de GitHub; el usuario todavía no decidió qué sería premium), exportar CSV, filtros de movimientos, monito en mayor resolución.
8. Pruebas unitarias para lo nuevo (hay tests de paletas, estadísticas, conversión de moneda, planificador de gastos fijos, mascota, actualizador y notas de versión). Candidatos: `onKey` del teclado de `QuickEntry` y el armado de `attention` de Inicio (hoy dentro del ViewModel; extraer a funciones puras).

## 12. Consejos para la nueva conversación
- Pedir primero que el usuario adjunte el zip más nuevo. Leer solo este documento, `PLAN_DISENO.md` y `NOVEDADES.md`; el resto, con `grep` puntual.
- Antes de un cambio grande, buscar cómo funciona el equivalente existente (`IncomeRepository` sirvió de molde para `RecurringRepository`; `BackupCard` para `RestoreBackupCard`; `AppearanceCard` se reusó en el onboarding).
- Entregar siempre el zip con `NOVEDADES.md` y `README.md` actualizados, **presentarlo con `present_files`** y decir con honestidad que no se compiló.
- Si se renombra o borra un archivo: actualizar el paso "Quitar archivos viejos" del workflow y avisar qué borrar a mano.
- `NOVEDADES.md` se ve en la ventana de actualización **y en la campanilla**: pedido del usuario, que lleve **solo lo nuevo de esa entrega** (no el historial) y sea corto. Desde v41 se reinició: cada entrega reemplaza el archivo entero.
- Hablar en español rioplatense (voseo), con respuestas cortas: el usuario usa el celular.
