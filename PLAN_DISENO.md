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
- [x] MoneaCard arriba (ya existía).
- [x] Debajo, **una sola cosa que requiere atención** (`AttentionCard`): cobros por confirmar, luego facturas vencidas o que vencen en 3 días o menos, luego gastos fijos pendientes. Muestra el primero; "Ver N más" despliega el resto. Si no hay nada, se muestra el monito (`MascotCard`).
  - Cobros: "Ya cobré" / "Omitir" ahí mismo (mismo diálogo y mismo `inFlight`/`refresh` de antes, intactos).
  - Facturas y gastos fijos: botón "Ver" que abre Facturas o Gastos fijos (no se paga desde Inicio, a propósito, para no sumar más diálogos).
- [x] Resumen del mes: una barra gastado vs. ingresado, "Te quedan X" / "Te pasaste X" y "Gastaste X de Y que ingresaron". Sin ingresos: texto de siempre y lo gastado.
- [x] Orden: saldo → para resolver → consejo → atajos → resumen → movimientos recientes → próximo cobro y próximos vencimientos (lo previsto, abajo).
- Archivos: `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt` (ahora recibe `RecurringRepository`; `HomeUiState.attention`, `AttentionItem`), `res/values/strings_attention.xml`.
- Pendiente de revisar con el usuario: cómo se ve con 3 o más ítems desplegados, el monito de 56 dp en la tarjeta, y si quiere poder pagar facturas y confirmar gastos fijos desde Inicio.
- Para otra vuelta: `RecurringViewModel.confirm/skip` y `ObligationsViewModel.payBill` no tienen el seguro contra toques repetidos ni el `refresh` que sí tienen Inicio e Ingresos. Pruebas unitarias para el armado de `attention` (hoy está dentro del `combine` del ViewModel; se podría extraer a una función pura).

### Punto 3: Menos pantallas, más claras — HECHO (sin probar)
- [x] Menú agrupado por tema: Dinero (Cobros e ingresos, Gastos fijos, Obligaciones) / Planificar y analizar (Presupuestos, Estadísticas, Categorías) / Ayuda. (`ui/menu/MenuScreen.kt`, `strings_menu.xml`; Ajustes ya tiene su lugar en la barra).
- [x] **Deshacer en vez de preguntar** al borrar un movimiento (deslizando en Movimientos o con el tachito del formulario). Diseño: no se borra enseguida. `FinanceRepository.hideTransaction/showTransaction` lo ocultan de `transactions` (saldo, listas y estadísticas ya no lo cuentan); `ui/common/DeleteUndo.kt` muestra el snackbar durante 5 s; si se toca "Deshacer" se vuelve a mostrar, y si pasa el tiempo (o llega otro borrado) recién ahí se llama a `deleteTransaction` (efectos laterales: cobro vuelve a "omitido", aportes de ahorro asociados, factura desvinculada, fotos). Así no hay que reconstruir nada al deshacer. Si la app se cierra durante los 5 s, el movimiento queda sin borrar (falla seguro).
- Se entrega con `LocalDeleteUndo` (CompositionLocal) desde `MainShell`; si es `null` (vista previa) las pantallas usan el borrado directo / diálogo de antes.
- Pendiente a mirar con el usuario: que el snackbar se vea bien sobre la barra flotante, y si quiere el mismo "Deshacer" para borrar metas, facturas y fuentes de ingreso (hoy siguen con diálogo de confirmación).

### Punto 4: Que se sienta viva sin cansar — PENDIENTE
- Identidad coherente (tarjeta, monito, verde/amarillo) en todas las pantallas.
- Animaciones cortas con sentido: el movimiento recién guardado "cae" en la lista, la barra del presupuesto se llena, el saldo cuenta. Nada que obligue a esperar. Ya existen `Modifier.entrance`, `rememberCountUp`, `ConfettiBurst` en `ui/common/Effects.kt`.
- El monito aparece en momentos clave (primer gasto, meta cumplida, presupuesto pasado), no en todas partes.

### Punto 5: Facilidad para todos — PENDIENTE
- Textos grandes y buen contraste (revisar modo oscuro). Zonas táctiles de 48 dp mínimo. Acciones principales abajo.
- Recorrido de 3 pasos en el primer uso (anotar un gasto, ver el resumen) en vez de muchas pantallas de ayuda. Ya hay `HintCard` y la pantalla Ayuda.

## Cambios pedidos por el usuario (fuera de los 5 puntos) — HECHOS (sin probar)
- [x] **Ajustes en la barra inferior** (v38): quedó libre un lugar a la derecha del "+"; ahora la barra es Inicio, Movimientos, +, Ajustes, Menú. Se quitó Ajustes de la pantalla Menú. (`FloatingNavBar.kt`, `Destination.kt`, `MenuScreen.kt`)
- [x] **Quitar "Lo que va a incluir"** al final de Ajustes (`SettingsScreen.kt`, `strings.xml`).
- [x] **Notas de actualización en la campanilla**: se guardan al detectar la versión nueva, para poder leerlas después (`UpdateViewModel.saveNotes`, `ReleaseNotes.toPlainText`, `NotificationKind.UPDATE`). Pendiente de revisar con el usuario: que el aviso aparezca con el contador de no leídos y que el texto se lea bien.
- Cosas que el usuario reportó antes: confirmar un cobro dejaba Inicio con el saldo viejo (arreglado en v36 con `refresh` + `inFlight`; falta confirmar en el teléfono).

## Ideas sueltas (no elegidas todavía)
Widget "+ Gasto", recordatorio nocturno inteligente ("¿Anotaste hoy?"), resumen semanal, detección de gastos desde notificaciones (necesita ejemplos reales y lista "Detectados para confirmar"), versión Google Play con Billing, exportar CSV, filtros de movimientos, monito en mayor resolución.
