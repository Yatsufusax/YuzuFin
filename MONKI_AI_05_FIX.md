# Monki AI 0.5 — correcciones puntuales (fix)

Copia independiente de `monea-monki-ai-05.zip`. No se tocó la versión original, la interfaz del chat,
las dependencias, el resumen semanal ni la arquitectura de solo lectura.

## Modificaciones
1. **Seguimiento tras simulaciones.** `MonkiContext` guarda ahora el porcentaje (`percent`) y SIMULATION se
   recuerda como contexto. «¿Y el mes pasado?», «¿Y el anterior?», «¿Y en septiembre?», «¿Y hace dos meses?»
   repiten el mismo escenario sobre el nuevo período; sin datos, se explica. «¿Y cuánto ahorraría el mes
   pasado?» sigue la simulación (si habla de metas/ahorrado/apartado, sigue siendo consulta de metas).
   Un porcentaje nuevo reemplaza al recordado. Un porcentaje inválido no deja escenario que seguir.
2. **Categorías.** Nueva intención `CATEGORY_EXCESS` («¿En qué categoría estoy gastando demasiado?»).
   Con presupuestos del mes: indica cuáles se superaron/están cerca (usa `budgetProgress`). Sin presupuestos
   (o en períodos que no son un mes): muestra las categorías con más gasto, importes y %, sin decir que
   alguna sea excesiva. No pasa por el chequeo general ni por hábitos.
3. **Gastos hormiga.** Muestra el total identificado y su % del período (gasto histórico) y, aparte, escenarios
   hipotéticos de 10/25/50 % (o el % pedido) con `StatsCalculator.reduction`. Sin patrones o con pocos
   movimientos, no calcula nada.
4. **Umbral.** El texto dice «mediana de tus gastos del período» y muestra el valor calculado (no es un importe fijo).

## Archivos
- `app/src/main/java/com/yuzufin/app/assistant/MonkiIntent.kt` (modificado)
- `app/src/main/java/com/yuzufin/app/assistant/MonkiFinanceEngine.kt` (modificado)
- `app/src/test/java/com/yuzufin/app/assistant/MonkiCorrectionsTest.kt` (nuevo, 30 pruebas)

## Verificación
- **No se ejecutaron pruebas ni se compiló.** El entorno de trabajo no tiene Gradle, Kotlin ni red.
  Solo se revisó el balance de llaves/paréntesis y se trazaron a mano las pruebas contra el código.
- Pendiente en tu máquina: `./gradlew testDebugUnitTest` y luego `./gradlew assembleDebug`.
