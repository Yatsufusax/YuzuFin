# Monea + Monki AI — integración inicial 0.2

## Qué se implementó en esta copia de prueba

- Se agregó `MonkiViewModel`, que combina los flujos locales de movimientos, categorías y configuración desde los repositorios existentes.
- Monki toma una fotografía de solo lectura de esos datos al responder. No accede directamente a Room y no tiene funciones para editar/eliminar movimientos.
- Se agregó una pantalla Compose de chat (`MonkiChatScreen`) con historial durante la sesión, campo de consulta y envío.
- Se agregó la ruta `monki` a la navegación.
- El acceso al chat está en el espacio del nombre de usuario en el encabezado de Inicio, tal como se había planteado; el nombre ya aparece en otras partes de Inicio.
- Se ajustó el perfil regional: PYG usa expresiones paraguayas, ARS usa expresiones argentinas y USD/EUR/BRL usan un español neutral sin modismos regionales forzados.

## Consultas que entiende actualmente el motor

- Saludo.
- Gastos de los últimos 7 días.
- Ingresos de los últimos 7 días.
- Principales categorías de gastos de los últimos 7 días.
- Gastos identificados por descripción/categoría con palabras como chipa, delivery o comida.

Las transferencias internas y movimientos futuros quedan excluidos del resumen semanal. Los importes se formatean con el sistema monetario de Monea; no se usa coma flotante para cálculos.

## Límites conocidos

- Esto todavía NO es un LLM generativo: es un motor determinista con lenguaje regional.
- El historial del chat vive en memoria mientras la pantalla está abierta; todavía no se persiste.
- El repertorio de preguntas es acotado. Preguntas no reconocidas reciben una explicación de las consultas disponibles.
- No se integraron todavía presupuestos, metas, préstamos, obligaciones ni análisis de tendencias.
- No se modificó el manifiesto ni se añadieron dependencias o permisos de red.

## Verificación pendiente

Se intentó `./gradlew testDebugUnitTest --no-daemon`, pero Gradle 8.10.2 no pudo descargarse porque el entorno no resuelve `services.gradle.org` (`UnknownHostException`). Por lo tanto, esta entrega NO está compilada ni validada en dispositivo. Ejecutar en Android Studio o CI con Gradle disponible:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Después probar al menos: moneda PYG, ARS y USD; sin movimientos; transferencias internas; movimientos futuros; categorías archivadas; y consultas desde teléfono/tablet.

## Instrucciones para Claude

1. Revisar el diff completo contra el proyecto base y verificar que no se alteraron funciones ajenas a Monki.
2. Compilar y ejecutar `testDebugUnitTest`; corregir cualquier error de Kotlin/Compose.
3. Revisar accesibilidad, teclado/IME y scroll del chat en pantallas pequeñas y tablets.
4. Ampliar el motor con consultas financieras verificables, reutilizando los modelos y calculadores existentes. No permitir que un LLM calcule importes ni escriba datos.
5. Mantener todo local/offline. No añadir API keys ni enviar movimientos a servicios externos.
6. Devolver observaciones y un APK debug para pruebas, sin fusionar a la rama original todavía.

---

## Revisión 03 (Claude): errores corregidos y diseño del chat

**Errores corregidos (sin compilar; revisados a mano)**
- `MonkiChatScreen.kt` importaba `androidx.compose.foundation.layout.weight`, que no existe (`weight` es miembro de `ColumnScope`/`RowScope`): habría roto la compilación.
- El encabezado quedaba debajo de la barra de estado y el teclado tapaba el campo de texto: ahora la pantalla es de pantalla completa (`isFullScreenRoute` incluye `monki`, sin barra inferior) con `statusBarsPadding`, `navigationBarsPadding` e `imePadding`.
- El historial vivía en `remember` (se perdía al rotar): pasó a `MonkiViewModel.state` (`MonkiChatState`, `MonkiMessage`). El borrador usa `rememberSaveable`.
- La lista no bajaba al último mensaje: `LazyListState` + `animateScrollToItem`.
- `any(q::startsWith)` (referencia a función sobrecargada) reemplazado por una lambda explícita.
- Textos del chat movidos a `res/values/strings_monki.xml`.

**Diseño nuevo**
- Entrada a Monki: el pill del nombre en Inicio (`HomeHeader`) ahora muestra la carita de Monki (`MonkiAvatar`) en lugar de la billetera. Descripción para TalkBack: "Abrir Monki AI".
- Chat: respuestas a la izquierda con avatar, mensajes de la persona a la derecha con el color principal del tema, indicador "Monki está revisando…", atajos (`AssistChip`) con 3 preguntas frecuentes y campo redondeado con botón de envío (48 dp, acción Enviar del teclado).
- Avatares nuevos (recortes de los monitos existentes): `monki_avatar_pyg.webp` (tereré, camiseta de Paraguay), `monki_avatar_ars.webp` (mate, camiseta de Argentina) y `monki_avatar_neutral.webp` (café, sin camiseta). La región sale de `MonkiRegion.fromCurrency` (PYG, ARS, resto).
- Motor: el orden de consultas ahora es categorías → comidas → gastos → ingresos, y "¿cuánto gasté?" sin período usa los últimos 7 días. 4 pruebas nuevas en `MonkiFinanceEngineTest`.

**Sigue pendiente**
- Compilar y correr `./gradlew testDebugUnitTest` y probar en el teléfono (chat con teclado, rotación, tema oscuro y las 7 paletas).
- Ampliar el motor: presupuestos, ahorros, obligaciones, tendencias y comparación entre períodos (todo calculado en Kotlin con `StatsCalculator`, `ObligationMath`, `SavingsMath`, etc.).
- Animar al personaje en el chat (hoy es un avatar estático).

**Ajuste posterior (pedido del usuario)**
- El botón de Inicio dice **"Monki"** (con la carita y una chispita), no el nombre de la persona: el nombre ya aparece en la tarjeta del saldo.
- Nueva `MonkiIntroCard` (`ui/assistant/`): tarjeta de presentación que se muestra una sola vez en Inicio, tanto a quien empieza como a quien se actualiza. Botones "Entendido" y "Hablar con Monki" (abre el chat). Reutiliza `dismissedHints` con el id `monki_intro`, así que "Volver a mostrar los consejos" en Ayuda la vuelve a mostrar.

