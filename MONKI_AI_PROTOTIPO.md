# Monki AI — prototipo técnico 0.1

## Qué se agregó

- `app/src/main/java/com/yuzufin/app/assistant/MonkiFinanceEngine.kt`
  - Motor determinista local, sin llamadas de red.
  - Recibe movimientos y categorías ya cargados por la app; no consulta Room directamente.
  - Responde consultas iniciales sobre gastos/ingresos de los últimos 7 días, categorías y menciones de chipa/delivery/comida.
  - Excluye transferencias internas y movimientos futuros.
  - Adapta saludo y humor moderado según moneda ARS o el resto de monedas (perfil paraguayo por defecto).
  - No escribe ni modifica datos.
- `app/src/test/java/com/yuzufin/app/assistant/MonkiFinanceEngineTest.kt`
  - Pruebas unitarias iniciales para transferencias/futuro y voz argentina.

## Estado real

Esto es el primer motor de respuestas deterministas; todavía NO es un modelo generativo ni una pantalla de chat integrada. No se añadió ninguna dependencia ni se modificó la navegación, la base de datos, el manifiesto o las funciones actuales.

## Próximo paso de integración

1. Crear un ViewModel que combine `FinanceRepository.transactions`, `FinanceRepository.categories` y `SettingsRepository.settings`.
2. Pasar snapshots de solo lectura al `MonkiFinanceEngine`.
3. Crear la pantalla Compose del chat y una ruta de navegación separada.
4. Probar manualmente en teléfono y tablet.
5. Evaluar un runtime/modelo pequeño local (LiteRT-LM o llama.cpp) como capa conversacional opcional; mantener todos los cálculos monetarios en Kotlin.

## Verificación

Se intentó ejecutar `./gradlew testDebugUnitTest`, pero el entorno no pudo descargar Gradle 8.10.2 por falta de acceso a `services.gradle.org`. Por tanto, el código agregado aún necesita compilación y ejecución de pruebas en Android Studio o CI con Gradle disponible.
