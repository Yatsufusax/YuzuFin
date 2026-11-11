# Plan de diseño de Monea (para continuar en otra cuenta)

Objetivo: **anotar un gasto tiene que costar menos de 5 segundos y con una mano**. Todo lo demás (colores, monito, animaciones) va encima de eso.

Reglas de trabajo (importantes):
- No se puede compilar acá: avisar siempre que lo entregado está sin compilar. El usuario prueba con GitHub Actions y pega el log si falla.
- No releer todo el código; usar búsquedas puntuales. Revisar imports a mano (hubo un CI roto por un `size` sin importar).
- Si se renombra o borra un archivo, agregarlo al paso `Quitar archivos viejos` de `.github/workflows/build.yml` (copiar un zip sobre el repo no borra nada). Recursos nuevos, con nombres nuevos.
- Cada entrega: actualizar `NOVEDADES.md` (secciones `## Nuevo` / `## Mejorado`) y `README.md`, y entregar el zip sin `.git`, `.gradle` ni `build`.
- Español rioplatense con voseo. Sin etiquetas (tags), sin bloqueo de la app, sin conexión a bancos.

## Estado

### Punto 1: Anotar al toque — HECHO (sin probar)
- [x] "+" central: un toque = gasto nuevo directo; mantener apretado = hoja con ingreso, pagar, préstamo, ahorro.
- [x] Teclado numérico propio (1-9, 0, "000" o coma según moneda, borrar; mantener borrar = vaciar).
- [x] Sumas rápidas (+10.000 / +50.000 / +100.000 en Gs.; +5 / +10 / +50 en otras monedas).
- [x] Categorías como grilla de botones con ícono, ordenadas por uso real.
- [x] Fecha "Hoy" por defecto, "Ayer" a un toque; nota, otra fecha y fotos en "Más detalles".
- [x] Gastos frecuentes: tocar uno completa importe, categoría y nota (después se toca Guardar; no registra solo para evitar errores).
- [x] Pedido del usuario (v35): barra inferior = Inicio, Movimientos, +, Menú (sin Ahorros; en v38 se sumó Ajustes antes de Menú, ver "Cambios pedidos por el usuario"); atajos de Inicio y hoja del "+" = solo Pagar, Préstamo, Ahorro (`QuickAction.shortcuts`). Ahorros sigue abierto desde esos atajos. El "+" está centrado exacto (mitades iguales a cada lado: Inicio y Movimientos a la izquierda, Menú a la derecha).
- Archivos: `ui/transaction/QuickEntry.kt` (nuevo), `TransactionFormViewModel.kt`, `TransactionFormScreen.kt`, `ui/navigation/FloatingNavBar.kt`, `ui/MainShell.kt`, `res/values/strings_quick.xml`.
- Pendiente de revisar con el usuario: cómo se ve en pantallas chicas (teclado + botón Guardar ocupan ~310 dp fijos y el resto scrollea), modo oscuro, y si prefiere que tocar un frecuente guarde directo.
- Idea para después: pruebas unitarias de `onKey` (hoy está dentro del ViewModel; se podría extraer a una función pura).

### Punto 2: Inicio que se entienda en 2 segundos — HECHO (sin probar)
- [x] Tarjeta de resumen arriba (`SummaryCard`, v54; antes la tarjeta bancaria MoneaCard).
- [x] Debajo, **una sola cosa que requiere atención** (`AttentionCard`): cobros por confirmar, luego facturas vencidas o que vencen en 3 días o menos, luego gastos fijos pendientes. Muestra el primero; "Ver N más" despliega el resto. Si no hay nada, se muestra el monito (`MascotCard`).
  - Cobros: "Ya cobré" / "Omitir" ahí mismo (mismo diálogo y mismo `inFlight`/`refresh` de antes, intactos).
  - Facturas y gastos fijos: botón "Ver" que abre Facturas o Gastos fijos (no se paga desde Inicio, a propósito, para no sumar más diálogos).
- [x] Resumen del mes: ahora vive dentro de la tarjeta de arriba (v54): barra y % gastado, ingresos y gastos, con selector de mes. Se quitó el bloque "Este mes" para no repetirlo.
- [x] Orden: saldo → para resolver → consejo → atajos → resumen → movimientos recientes → próximo cobro y próximos vencimientos (lo previsto, abajo).
- Archivos: `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt` (ahora recibe `RecurringRepository`; `HomeUiState.attention`, `AttentionItem`), `res/values/strings_attention.xml`.
- Pendiente de revisar con el usuario: cómo se ve con 3 o más ítems desplegados, el monito de 56 dp en la tarjeta, y si quiere poder pagar facturas y confirmar gastos fijos desde Inicio.
- Para otra vuelta: `RecurringViewModel.confirm/skip` y `ObligationsViewModel.payBill` no tienen el seguro contra toques repetidos ni el `refresh` que sí tienen Inicio e Ingresos. Pruebas unitarias para el armado de `attention` (hoy está dentro del `combine` del ViewModel; se podría extraer a una función pura).

### Punto 3: Menos pantallas, más claras — HECHO (sin probar)
- [x] Menú agrupado por tema: Dinero (Cobros e ingresos, Gastos fijos, Obligaciones) / Planificar y analizar (Presupuestos, Estadísticas, Categorías) / Ayuda. (`ui/menu/MenuScreen.kt`, `strings_menu.xml`; Ajustes ya tiene su lugar en la barra).
- [x] **Deshacer en vez de preguntar** al borrar un movimiento (deslizando en Movimientos o con el tachito del formulario). Diseño: no se borra enseguida. `FinanceRepository.hideTransaction/showTransaction` lo ocultan de `transactions` (saldo, listas y estadísticas ya no lo cuentan); `ui/common/DeleteUndo.kt` muestra el snackbar durante 5 s; si se toca "Deshacer" se vuelve a mostrar, y si pasa el tiempo (o llega otro borrado) recién ahí se llama a `deleteTransaction` (efectos laterales: cobro vuelve a "omitido", aportes de ahorro asociados, factura desvinculada, fotos). Así no hay que reconstruir nada al deshacer. Si la app se cierra durante los 5 s, el movimiento queda sin borrar (falla seguro).
- Se entrega con `LocalDeleteUndo` (CompositionLocal) desde `MainShell`; si es `null` (vista previa) las pantallas usan el borrado directo / diálogo de antes.
- Pendiente a mirar con el usuario: que el snackbar se vea bien sobre la barra flotante, y si quiere el mismo "Deshacer" para borrar metas, facturas y fuentes de ingreso (hoy siguen con diálogo de confirmación).

### Punto 4: Que se sienta viva sin cansar — EN CURSO (v40, sin probar)
- [x] El movimiento recién guardado "cae" en la lista de Movimientos: `Modifier.dropIn` (`ui/common/Effects.kt`) + `MovementsViewModel.fresh` (ids que aparecen mientras la pantalla vive; se limpian a los 1,8 s). Solo se ve si el usuario vuelve a Movimientos desde el formulario.
- [x] Barras de progreso que se llenan al aparecer: `rememberFillAnimation` en `ProgressBar` (Estadísticas y Presupuestos) y en la barra de metas de Ahorros.
- [x] Monito en un momento clave: Presupuestos muestra `OVERSPENT` con las categorías pasadas del tope. (Meta cumplida ya tenía `GOAL_DONE` + confeti.)
- [x] Monito al guardar el **primer gasto** (v43): `TransactionFormViewModel.save` detecta que no hay gastos previos y llama a `SettingsRepository.markFirstExpense` (marca `first_expense_done`, una sola vez); `MainShell` muestra `FirstExpenseCelebration`.
- [ ] Falta: identidad visual coherente (tarjeta/monito/verde-amarillo) en el resto de pantallas, y revisar que ninguna animación obligue a esperar.
- Archivos: `ui/common/Effects.kt`, `ui/movements/*`, `ui/stats/StatsScreen.kt`, `ui/savings/SavingsScreen.kt`, `ui/budgets/BudgetsScreen.kt`, `strings_notes.xml`.

### Aviso de novedades en la campanilla (pedido del usuario, v40)
- `UpdateViewModel.saveNotes` guarda el aviso como "Actualización v0.2.N" (el cuerpo sigue siendo el texto completo). En la lista (`NotificationsScreen`) un aviso `NotificationKind.UPDATE` muestra solo el título y "Tocá para ver las novedades"; al tocarlo abre `UpdateNotesWindow` (ventana a pantalla completa con el monito, secciones y viñetas). Los avisos viejos "Novedades de Monea X" se muestran con el título nuevo (`displayTitle`).

### Punto 5: Facilidad para todos — HECHO (v41, sin probar)
- [x] **Contraste** (WCAG AA) en las 7 paletas, claro y oscuro: ajustes en `Color.kt` y `Palettes.kt` (verde/rojo/naranja de texto, bordes, texto sobre el banner). Vigilado por `PalettesTest.textHasEnoughContrastInEveryPalette`.
- [x] **Tamaño del texto** Normal / Grande / Muy grande (se suma al del teléfono), en Ajustes y en la configuración inicial. Barra inferior y tarjeta del saldo con tope de escala (`CapFontScale`).
- [x] **Zonas táctiles de 48 dp** donde faltaban (campanilla, quitar foto, ojo de la tarjeta, "Ahorrado", íconos de categoría).
- [x] **Recorrido de 3 pasos** al terminar la configuración inicial (`ui/tour/FirstUseTour.kt`); se repite desde Menú → Ayuda. Acciones principales abajo.
- Pendiente de revisar con el usuario en el teléfono: Grande y Muy grande en Inicio, Movimientos y el teclado de anotar rápido (alturas fijas), el recorrido con letra grande, y TalkBack en la barra inferior y el recorrido.
- Para otra vuelta: botones de 54 dp del teclado numérico (`KeyButton`) con letra enorme; auditar `contentDescription` de íconos clickeables con TalkBack.

## Cambios pedidos por el usuario (fuera de los 5 puntos) — HECHOS (sin probar)
- [x] **Ajustes en la barra inferior** (v38): quedó libre un lugar a la derecha del "+"; ahora la barra es Inicio, Movimientos, +, Ajustes, Menú. Se quitó Ajustes de la pantalla Menú. (`FloatingNavBar.kt`, `Destination.kt`, `MenuScreen.kt`)
- [x] **Quitar "Lo que va a incluir"** al final de Ajustes (`SettingsScreen.kt`, `strings.xml`).
- [x] **Notas de actualización en la campanilla**: se guardan al detectar la versión nueva, para poder leerlas después (`UpdateViewModel.saveNotes`, `ReleaseNotes.toPlainText`, `NotificationKind.UPDATE`). Pendiente de revisar con el usuario: que el aviso aparezca con el contador de no leídos y que el texto se lea bien.
- Cosas que el usuario reportó antes: confirmar un cobro dejaba Inicio con el saldo viejo (arreglado en v36 con `refresh` + `inFlight`; falta confirmar en el teléfono).

## Avisos "¿Anotaste hoy?" (pedido del usuario, v45) — HECHO (sin compilar ni probar)
- [x] Varias horas al día (13, 18 y 21 por defecto; elegibles en Ajustes), solo si hoy no se anotó nada. Pendiente de probar en el teléfono: que lleguen, que no lleguen si ya anotó, que los chips guarden.

## Widget "+ Gasto" (idea 2, v45) — HECHO (sin compilar ni probar)
- [x] Widget de pantalla de inicio que abre directo el formulario de gasto. Pendiente de probar en el teléfono: que aparezca en la lista de widgets, que abra el formulario con la app cerrada y abierta, que no se abra dos veces, que con la configuración inicial sin terminar abra la app normal.

## Filtros en Movimientos (idea 4, v46) — HECHO (sin compilar ni probar)
- [x] Filtro por rango de monto, contador de resultados y "Quitar filtros". Pendiente de probar en el teléfono: el diálogo de monto con teclado, que el chip muestre el rango, que "Quitar filtros" no cambie el mes y que los totales no cuenten préstamos.

## Resumen semanal (idea 5, v47) — HECHO (sin compilar ni probar)
- [x] Aviso del domingo a las 20:00 con el total de 7 días y la categoría que más pesó. Pendiente de probar en el teléfono: que llegue el domingo, que respete el ajuste de importes y que el interruptor lo apague.

## Ideas sueltas (no elegidas todavía)
Detección de gastos desde notificaciones (necesita ejemplos reales y lista "Detectados para confirmar"), versión Google Play con Billing, exportar CSV (descartado por ahora), monito en mayor resolución.

## Intro en video (pedido del usuario, v48; video nuevo y frase en v49)
- Hecho: `VideoSplash` reproduce `res/raw/monea_intro.mp4` como intro; `AnimatedSplash` queda de respaldo. En v49 el usuario cambió el video (vertical, 720x1280, con su propio fondo verde) porque el anterior "no se veía tan bien", y pidió **mantener la frase** de la intro anterior: ahora "Tu dinero. Tus decisiones." aparece debajo del logo, con fundido, dibujada con Compose. Sin probar.
- A mirar en el teléfono: que el video se vea entero sin franjas visibles arriba y abajo (el fondo es `#112825`, el color de los bordes del video), que la frase quede justo debajo de las banderas y no tape nada, que el destello previo al primer cuadro no se note, que se salte con un toque y que no suene con el teléfono en silencio.
- Si cambia otra vez el video: revisar `VIDEO_ASPECT`, `VideoBackground` (color de los bordes del video), `TAGLINE_TOP_FRACTION` y `TAGLINE_DELAY_MS` en `VideoSplash.kt`. Si llega uno horizontal con cuadritos, usar `tools/key_checkerboard_video.py`.

## Monki AI: inicio e historias
- Inicio (fondo del color del tema, campo y pastillas sobre superficie): saludo, Monki con su bebida según la moneda (animado) centrado, pastillas con preguntas que el motor ya entiende, campo redondeado con micrófono/enviar y pie «Monki solo lee tus datos y puede equivocarse». No dice «generado con IA» porque Monki es determinista.
- Historias: 5 láminas con degradé del tema (primario, secundario y terciario), monito grande y tarjeta del color del tema abajo; láminas 1 y 5 con la bebida según la moneda (etiqueta, título, texto, flecha). Sin puntos, niveles ni premios.


### Rediseño de anotar rápido (maquetas aprobadas) — HECHO (sin compilar ni probar)
- Tarjeta de importe con el monito, fila de 5 categorías + «Todas» (hoja con la grilla), chip único de fecha, «Guardar <importe>» y confirmación «¡Anotado!» (0,9 s). Archivos: `ui/transaction/QuickEntry.kt`, `TransactionFormScreen.kt`, `res/values/strings_quick.xml`. La pantalla de voz (VoiceEntryScreen) no se tocó: sigue igual y al terminar abre esta pantalla con el aviso de voz.

### Ícono nuevo (monito con billetera) — HECHO (sin compilar ni probar)
- Adaptativo con fondo crema `#FFE9B8`, primer plano `ic_launcher_billetera.webp` y capa monocromática `ic_launcher_billetera_mono.webp` (Android 13+). El monito es el original (`monito_tarjeta_neutral`) sin cambios; la billetera está dibujada encima. Archivos viejos (`ic_launcher_monea.webp`, color `ic_launcher_background`) quedan sin usar. Ícono de Google Play 512×512 en `PLAY_STORE/icono-play-512.png`.

### Multidivisa manual — HECHO (sin compilar ni probar)
- Base de datos 8 → 9: `MIGRATION_8_9` solo agrega 3 columnas opcionales a `transactions` (`originalCurrency`, `originalAmountMinor`, `exchangeRate`). Los movimientos viejos quedan con null. El importe de siempre (`amountMinor`) sigue siendo el de la moneda principal.
- Anotar rápido: chip de moneda, chip del tipo de cambio, línea «≈ Gs. X» en la tarjeta y hoja «Moneda del gasto». El cambio se escribe a mano (punto = miles, coma = decimal) y se propone el último usado con esa moneda.
- Movimientos: segunda línea «US$ 50 · cambio 7.600». Al editar un movimiento, los datos de la otra moneda se conservan solo si el importe no cambia.
- La copia de seguridad no necesitó cambios (copia el archivo de la base; una copia de la versión 8 se migra sola al abrir).
- Pendiente a propósito: botón «Usar cambio de hoy» desde internet, voz en otra moneda, vista ⇄.

### Anotar rápido, versión final — HECHO (sin compilar ni probar)
- Sin sumas rápidas, sin frecuentes y sin «Más detalles». Fila de 4 categorías + «Todas» (quinto lugar, siempre visible); campo «Descripción (opcional)» con botón «+» (menú Sacar foto / Elegir de la galería, contador de fotos y miniaturas con ✕, `PhotoThumbsRow`); chips de fecha («Hoy ⌄», con «Otra fecha…» que abre el calendario) y de moneda («Gs. ⌄»).
- Las fotos nuevas no se pueden abrir en grande antes de guardar (se ven al editar el movimiento). El comprobante PDF sigue solo al editar.
- `onQuickAdd`, `onFrequentPick` y `frequent` del ViewModel quedaron sin usar a propósito (no se tocó la lógica).

### Monki proactivo (paquete de Kimi AI, revisado e integrado) — HECHO (sin compilar ni probar)
- Archivos nuevos en `assistant/`: `MonkiProactive.kt` (frases), `ProactiveMonki.kt` (motor puro), `MonkiRegionResolver.kt`; pruebas en `ProactiveMonkiTest.kt`.
- Integración: `ReminderWorker.remindMonki` guarda los mensajes en la campanilla (`NotificationKind.MONKI`, sin notificación en pantalla), hasta 2 nuevos por día; ajuste «Monki te escribe» (Todo / Solo lo importante / Nunca, por defecto Solo lo importante) en `MonkiProactiveCard`, guardado también en la copia de seguridad; `SettingsRepository.lastOpenDay()` (solo lectura).
- Arreglos sobre el paquete: aviso de silencio sin dato de apertura (0); el tope de 2 avisos ahora cuenta solo los nuevos (antes uno ya publicado ocupaba lugar); sin presupuesto-duplicado (las categorías con presupuesto las cubren los avisos de presupuesto); libre estimado solo con cuentas del mes; frases propias cuando los montos están apagados; silencio dice «no abrís la app»; guaraní solo con palabras confirmadas (Aguyje, iporã, Mba'eichapa).
