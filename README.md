# YuzuFin

App de finanzas personales para Android (Kotlin, Jetpack Compose, Material 3, Room). Local-first, sin cuentas ni IA.
*Tu dinero. Tus decisiones.*

## Estado: Fase 6, presupuestos y estadísticas (listo para probar)

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
- Tests: `ObligationMathTest` (vencimientos mensuales y meses cortos, avisos, devoluciones, totales, fin de mes), `SavingsMathTest` (disponible, avance, meses que quedan, aporte mensual, aporte automático), `StatementBuilderTest`, `AmountParserTest`, `BalanceCalculatorTest`, `UpdateCheckerTest`, `MoneyFormatterTest`, `IncomeSchedulerTest` (fechas, meses cortos, años bisiestos, quincenas), `ReminderSchedulerTest`.

**No hecho todavía:** presupuestos y estadísticas (Fase 6).

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

- Cada push a `main` compila, corre los tests, publica el APK firmado como Release `build-<n>` (`n` = número de ejecución) y esa `n` es el `versionCode`.
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
Revisar la Fase 5. Después, Fase 6: presupuestos y estadísticas.

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
