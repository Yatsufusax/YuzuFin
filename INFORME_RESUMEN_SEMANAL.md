# Monea — Corrección: la notificación del resumen semanal no abre el resumen

Base: proyecto `monea-monki-ai-051-fix` (incluye las correcciones de Monki 0.5.1). Todo va en un solo ZIP.

## ESTADO REAL DE LA VERIFICACIÓN (leer primero)

| Ítem | Estado |
|---|---|
| Compilación Android | **NO verificada.** El entorno donde trabajé no tiene Gradle, Kotlin ni red. Revisé a mano llaves, paréntesis, imports y tipos. |
| Tests unitarios | **NO ejecutados.** Hay 24 pruebas unitarias nuevas de este arreglo (14 de navegación y 10 de persistencia), además de las 19 de Monki 0.5.1; corren con `./gradlew testDebugUnitTest`. |
| Prueba en dispositivo (tocar la notificación) | **NO realizada.** Agregué una prueba instrumentada del `Intent`, pero tampoco se ejecutó. |
| APK | **NO generado.** Lo genera tu GitHub Actions al compilar. |

Si algo falla en CI es un resultado real: pasame el log.

## Causas exactas

1. **El `PendingIntent` era el intent del lanzador, sin nada adentro.** `NotificationHelper.show` armaba la notificación con `getLaunchIntentForPackage(...)` y ningún extra: tocarla solo abre la app. El worker del resumen semanal no pasaba ningún dato que dijera «abrí el resumen».
2. **La actividad no tenía qué reconocer.** `MainActivity` solo entendía un extra (`open=expense`, del widget). Con la app abierta o en segundo plano, `onNewIntent` no hacía nada con la notificación; con la app cerrada, la splash y la navegación tampoco sabían de ella.
3. **No existía una pantalla de resumen.** El resumen solo se guardaba como texto (título y cuerpo) en la campanilla. Las filas de tipo «resumen» no eran tocables ni abrían nada más que el texto de la lista.
4. **No se podía ubicar el resumen de una semana concreta.** El registro guarda la clave `weekly:<día>`, pero ni el dominio (`AppNotification`) ni el repositorio la exponían: no había forma de encontrar «el resumen de esa semana».
5. **Riesgo adicional si solo se agregaban extras:** Android compara los `PendingIntent` sin mirar los extras. Con el mismo código de pedido (4100) y `FLAG_UPDATE_CURRENT`, la notificación de la semana anterior habría terminado abriendo la semana nueva.

## Qué se cambió

- **Notificación** (`NotificationHelper.show` + `WeeklySummaryIntents`): intent explícito a `MainActivity`, con los extras `open=weekly_summary` y `summary_key=weekly:<día>`, banderas `NEW_TASK | CLEAR_TOP | SINGLE_TOP` y una dirección propia por semana (`monea://weekly-summary/<día>`) para que cada semana tenga su `PendingIntent`. El resto de los avisos siguen abriendo la app como antes.
- **Reconocimiento y navegación** (`SummaryLaunch`, lógica pura): valida el formato de la clave, arma y lee los extras con el mismo código, y decide qué hacer según el estado: sin ajustes cargados → esperar; sin configuración inicial → descartar; navegación sin lista → esperar; en cualquier pantalla → ir a la campanilla; ya en la campanilla → abrir el resumen ahí.
- **App cerrada / en segundo plano / abierta** (`MainActivity`, `MoneaApp`, `MainShell`, `MoneaNavHost`): `onCreate` y `onNewIntent` leen el pedido (y borran los extras para que no se repita al rotar); si viene del resumen se omite la splash; `MainShell` navega a la campanilla cuando la navegación está lista, desde donde esté la persona.
- **Dónde se muestra** (`NotificationsScreen` + `WeeklySummaryWindow`): al llegar el pedido, la campanilla busca el resumen de esa semana por su clave y lo abre a pantalla completa. Las filas de resumen ahora son tocables siempre («Tocá para abrir el resumen»), así que se puede consultar después. «Atrás» vuelve a la pantalla anterior.
- **Persistencia** (`AppNotification.dedupeKey`, `NotificationDao.findByKey`, `NotificationRepository.findByKey`): el repositorio recibe el DAO (constructor secundario con `AppDatabase`, por lo que `AppContainer` no cambió). La ventana muestra el título y el texto guardados al generarlo: **nada se recalcula**. No hay cambio de esquema de Room (no hace falta migración).
- **Duplicados:** se mantiene la clave única por semana (índice único + `IGNORE`); la prueba confirma que un segundo intento no duplica ni pisa el texto original.

## Archivos

Modificados: `MainActivity.kt`, `ui/MoneaApp.kt`, `ui/MainShell.kt`, `ui/navigation/MoneaNavHost.kt`, `ui/notifications/NotificationsScreen.kt`, `reminders/NotificationHelper.kt`, `reminders/WeeklySummaryWorker.kt`, `data/NotificationRepository.kt`, `data/db/Daos.kt`, `domain/AppNotification.kt`, `res/values/strings_weekly.xml`.
Nuevos: `ui/navigation/SummaryLaunch.kt`, `reminders/WeeklySummaryIntents.kt`, `ui/notifications/WeeklySummaryWindow.kt`.
Pruebas nuevas: `ui/navigation/SummaryLaunchTest.kt`, `data/NotificationRepositoryTest.kt` (unitarias) y `reminders/WeeklySummaryLaunchInstrumentedTest.kt` (en dispositivo).

## Qué cubren las pruebas

- **Navegación** (`SummaryLaunchTest`): extras que viaja la notificación y su lectura; rechazo de claves malformadas y del pedido del widget; dirección distinta por semana; semana derivada de la clave; decisión para app cerrada (espera ajustes/navegación), en segundo plano (desde Inicio, Movimientos, Ajustes, Estadísticas, formulario), ya en la campanilla, sin configuración inicial; el destino se apila y vuelve con «atrás».
- **Persistencia** (`NotificationRepositoryTest`, con un DAO en memoria que respeta la clave única, el orden y la poda): mismo título y texto al reabrir, sin duplicados y conservando el primer texto, una fila por semana, clave inexistente, marcar leído sin alterar el contenido, borrar otro aviso no toca el resumen, orden por reciente, poda a 200, y recorrido completo clave → pedido → registro.

## Limitaciones que conviene saber

- **No se probó en un dispositivo.** Las pruebas unitarias cubren la lógica; que Android entregue el intent como se espera (en especial desde la app cerrada) hay que confirmarlo tocando la notificación en el teléfono. Para forzar el aviso sin esperar al domingo, las pruebas instrumentadas existentes ya simulan el reloj del trabajo.
- **El resumen es el texto guardado**, no una pantalla de gráficos: si el ajuste «mostrar importes en los avisos» está apagado, el resumen guardado tampoco lleva importes (es lo que se generó).
- **La campanilla conserva los últimos 200 avisos** (límite que ya existía): un resumen muy antiguo puede haberse podado.
- Si el resumen de esa semana fue borrado de la campanilla, la notificación abre la lista y no el resumen.
