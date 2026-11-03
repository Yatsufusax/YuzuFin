# Monki AI 0.4: qué se hizo, cómo funciona y qué falta

Base de trabajo: `monea-proyecto-v60.zip` (Monki AI 0.3). Esta entrega es una **copia de pruebas**: el v60 original queda intacto.
Plan de origen: `README-MONKI-AI-04-PLANTEAMIENTO.md` (auditoría aprobada por el usuario).

## Estado honesto de verificación
- **No se compiló ni se corrieron los tests** (el entorno de trabajo no tiene Gradle, Kotlin ni red). Lo prueba el CI de GitHub.
- La lógica de lectura de frases (períodos e intenciones) se probó con un port a Python de las mismas reglas contra todas las frases de los tests. Eso reduce el riesgo, pero **no reemplaza** la compilación.
- Los textos esperados de los tests se calcularon a mano con los datos de prueba (ver `MonkiFinanceEngineTest`).

## Reglas que se respetan
- **Kotlin es la fuente de verdad.** Todas las cifras salen de `StatsCalculator`, `BalanceCalculator`, `SavingsMath` y `ObligationMath` (los mismos que usan Inicio y Estadísticas). Los chistes y el texto solo acompañan y **nunca llevan cifras** (hay un test que lo comprueba).
- **Solo lectura.** El chat recibe un `MonkiDataSource` con una única función (`load()`), no el `AppContainer` ni repositorios con funciones de escritura. `MonkiSnapshot` son listas inmutables. Tests: `MonkiReadOnlyTest`.
- **Sin inventar.** Sin datos, el motor lo dice; con una pregunta ambigua, pregunta; las estimaciones se rotulan como estimación.
- No se tocó la interfaz de Monea 0.6, salvo las sugerencias del chat (de 3 a 7 chips) para mostrar lo nuevo. Sin mascota nueva, sin puntos ni niveles, sin animaciones nuevas.
- Sin dependencias nuevas, sin migración de base de datos, sin cambios en el manifiesto.

## Archivos
**Nuevos** (`app/src/main/java/com/yuzufin/app/assistant/`):
- `MonkiText.kt`: normalización de texto (sin tildes ni signos).
- `MonkiPeriod.kt`: `MonkiPeriods.read` interpreta hoy, ayer, anteayer, esta semana, semana pasada, este mes, mes pasado, un mes por nombre, últimos N días, este año y año pasado. «El último mes» es ambiguo y se pregunta.
- `MonkiIntent.kt`: `MonkiIntentParser` (intención, períodos, tema, si es un "¿y…?"), `MonkiContext` (lo que Monki recuerda).
- `MonkiSnapshot.kt`: `MonkiSnapshot`, `MonkiDataSource` y `RepositoryMonkiDataSource` (único lugar que toca repositorios, y solo con `.first()` sobre flujos de lectura).
- `MonkiVoice.kt`: saludos, aclaraciones, ayuda y ~130 chistes por región y situación (Paraguay, Argentina, neutral).

**Modificados:**
- `assistant/MonkiFinanceEngine.kt`: reescrito. `reply(pregunta, snapshot, contexto, hoy, seed)` devuelve `MonkiReply(texto, contexto)`. Desaparece `MonkiLocale` (lo reemplaza `MonkiVoice`).
- `ui/assistant/MonkiViewModel.kt`: usa `MonkiDataSource`, guarda el contexto en el estado y pasa un `seed` (número de mensaje) para que los chistes no se repitan seguidos.
- `ui/assistant/MonkiChatScreen.kt`: solo la lista de sugerencias.
- `res/values/strings_monki.xml`: textos de las sugerencias.
- Tests: `MonkiFinanceEngineTest` (reescrito), más `MonkiPeriodTest`, `MonkiIntentParserTest`, `MonkiVoiceTest`, `MonkiReadOnlyTest`.

## Qué responde
| Pregunta | Fuente de las cifras |
|---|---|
| Gastos / ingresos de un período; en una categoría o cosa («comida», «chipa») | movimientos que cuentan igual que en Estadísticas (sin préstamos, sin antes del saldo inicial, sin fechas futuras) |
| En qué gasté más | gasto por categoría del período, con porcentajes |
| Saldo, apartado, disponible, libre estimado | `BalanceCalculator`, `SavingsMath`, `ObligationMath` (igual que Inicio) |
| ¿Gasto demasiado? | `StatsCalculator.recommendations` (reglas fijas, explicadas en la respuesta) |
| Comparación | mes en curso contra los mismos días del otro mes; semanas; períodos equivalentes |
| Tendencia y cierre del mes | `StatsCalculator.series` y `projectedExpense` (rotulada «estimación») |
| Presupuestos, ahorros, facturas, préstamos | `budgetProgress`, `SavingsMath`, `ObligationMath` |

Convenciones: «esta semana» son los últimos 7 días (igual que el resumen semanal de los domingos); un mes por nombre mira siempre hacia atrás («diciembre» en noviembre es diciembre del año pasado, y lo dice).

## Límites conocidos
- Chat sin memoria entre sesiones: el contexto vive en el ViewModel y se pierde al salir de la pantalla.
- Reconoce frases por palabras clave y sinónimos: no entiende cualquier redacción. Una pregunta rara cae en «No logré entender».
- Los temas («chipa», «nafta») se buscan en la descripción del movimiento y en los nombres de categoría; no hay datos de comercios.
- No hay patrones por día de la semana.
- Si la categoría tiene un nombre que el usuario cambió, las palabras genéricas («comida», «transporte», «servicios»…) funcionan por la clave del ícono.

## IA local (pendiente, no implementada)
Decisión del usuario: primero esta versión determinista; la IA local queda para después, como capa **opcional** que solo traduzca la pregunta a una intención (Kotlin sigue calculando todo, y si el modelo falla se usa este motor).
- Teléfono objetivo: Samsung S22. Hay que medir en el aparato real memoria, velocidad y batería antes de decidir.
- Candidatos: LiteRT-LM (Apache 2.0, API Kotlin) con un modelo chico Apache 2.0 (por ejemplo Qwen3-0.6B, unos 600 MB) o Gemma 4 E2B (unos 2,6 GB, más pesado). El modelo no puede ir dentro del APK: se descarga la primera vez.
- Pendiente de verificar: compatibilidad con Kotlin 2.0.21, peso del APK (hoy no hay filtro de arquitecturas), calidad del español rioplatense en un modelo de 0,6B y el cambio de mensaje de la app («sin IA»).

## Para probar en el teléfono
1. «¿Cuánto gasté este mes?» y después «¿Y el mes pasado?» y «¿Y en comida?».
2. «¿Cuánta plata se me fue?» sin contexto: debe preguntar el período.
3. «¿Cómo ando de dinero?» debe coincidir con el saldo disponible y el libre estimado de Inicio.
4. «¿Cómo vengo comparado con el mes pasado?», «¿Estoy gastando demasiado?», presupuestos, ahorros, facturas.
5. Que los chistes sean de tu región y cambien de un mensaje a otro.
