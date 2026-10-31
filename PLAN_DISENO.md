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
- Archivos: `ui/transaction/QuickEntry.kt` (nuevo), `TransactionFormViewModel.kt`, `TransactionFormScreen.kt`, `ui/navigation/FloatingNavBar.kt`, `ui/MainShell.kt`, `res/values/strings_quick.xml`.
- Pendiente de revisar con el usuario: cómo se ve en pantallas chicas (teclado + botón Guardar ocupan ~310 dp fijos y el resto scrollea), modo oscuro, y si prefiere que tocar un frecuente guarde directo.
- Idea para después: pruebas unitarias de `onKey` (hoy está dentro del ViewModel; se podría extraer a una función pura).

### Punto 2: Inicio que se entienda en 2 segundos — PENDIENTE
- MoneaCard arriba (ya existe).
- Debajo, **una sola cosa que requiere atención** (cobro por confirmar, factura por vencer, gasto fijo del día) en vez de varias tarjetas apiladas; si hay más, un "Ver N más".
- Resumen del mes con una barra simple gastado vs. ingresado en lugar de números sueltos.
- Movimientos recientes y lo demás más abajo. Menos cosas visibles a la vez.
- Archivos probables: `ui/home/HomeScreen.kt`, `HomeViewModel.kt` (ya tiene `refresh` e `inFlight`; no romperlos).

### Punto 3: Menos pantallas, más claras — PENDIENTE
- Barra con 4 destinos + "+" (ya está). Reagrupar "Menú" por tema: Dinero / Planificar / Ajustes.
- **Deshacer en vez de preguntar**: al borrar un movimiento (deslizar o desde el formulario) mostrar snackbar "Borrado · Deshacer" unos segundos en lugar del diálogo de confirmación. Hay que guardar el movimiento (con sus fotos, que no deben borrarse del disco hasta que pase el tiempo) y reinsertarlo si se toca Deshacer.
- Archivos probables: `ui/movements/MovementsScreen.kt`, `MovementsViewModel`, `TransactionFormScreen.kt` (diálogo `confirmDelete`), `ui/MainShell.kt` (ya tiene `SnackbarHost`), `ui/menu`.

### Punto 4: Que se sienta viva sin cansar — PENDIENTE
- Identidad coherente (tarjeta, monito, verde/amarillo) en todas las pantallas.
- Animaciones cortas con sentido: el movimiento recién guardado "cae" en la lista, la barra del presupuesto se llena, el saldo cuenta. Nada que obligue a esperar. Ya existen `Modifier.entrance`, `rememberCountUp`, `ConfettiBurst` en `ui/common/Effects.kt`.
- El monito aparece en momentos clave (primer gasto, meta cumplida, presupuesto pasado), no en todas partes.

### Punto 5: Facilidad para todos — PENDIENTE
- Textos grandes y buen contraste (revisar modo oscuro). Zonas táctiles de 48 dp mínimo. Acciones principales abajo.
- Recorrido de 3 pasos en el primer uso (anotar un gasto, ver el resumen) en vez de muchas pantallas de ayuda. Ya hay `HintCard` y la pantalla Ayuda.

## Ideas sueltas (no elegidas todavía)
Widget "+ Gasto", recordatorio nocturno inteligente ("¿Anotaste hoy?"), resumen semanal, detección de gastos desde notificaciones (necesita ejemplos reales y lista "Detectados para confirmar"), versión Google Play con Billing, exportar CSV, filtros de movimientos, monito en mayor resolución.
