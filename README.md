# YuzuFin

App de finanzas personales para Android (Kotlin, Jetpack Compose, Material 3, Room). Local-first, sin cuentas ni IA.
*Tu dinero. Tus decisiones.*

## Estado: Fase 2 (movimientos reales + actualizaciones desde GitHub)

**Hecho (escrito; pendiente de compilar en GitHub Actions)**
- Configuración inicial: moneda (Gs. por defecto) y saldo inicial, desde hoy o con fecha anterior.
- Base de datos Room: categorías, movimientos y saldo inicial.
- Registrar ingresos y gastos, historial con filtros (búsqueda, tipo, categoría, mes), editar y eliminar con confirmación.
- Inicio con datos reales: saldo, resumen del mes y últimos movimientos. Sin datos de demostración.
- Actualizador dentro de la app (Ajustes y aviso en Inicio): consulta las Releases de GitHub, descarga el APK e inicia la instalación.
- Tests: `AmountParserTest`, `BalanceCalculatorTest`, `UpdateCheckerTest`, `MoneyFormatterTest`.

**No hecho todavía:** cobros pendientes, obligaciones, ahorros, estadísticas, notificaciones (Fases 3 a 6).

**Sin verificar:** no se pudo compilar ni ejecutar donde se escribió (sin acceso al SDK de Android). Solo se revisaron recursos y XML con scripts. La compilación y los tests corren en GitHub Actions; si falla, pegá el error.

## Modelo de datos
- `categories(id, name, type, iconKey)`: se siembran 12 por defecto al terminar la configuración.
- `transactions(id, type, amountMinor, categoryId, description, epochDay, createdAt, updatedAt)`: el importe es siempre positivo y el tipo da el signo. La categoría no se puede borrar si tiene movimientos (FK RESTRICT).
- `initial_balance(id = 1, amountMinor, startEpochDay)`: un solo registro.
- **Saldo = saldo inicial + ingresos − gastos**, contando solo movimientos desde la fecha inicial (inclusive). Se recalcula siempre desde los movimientos, así que editar o borrar uno corrige el saldo. Puede ser negativo (el formulario avisa, no bloquea).
- Todo el dinero es `Long` en unidad mínima (Gs. sin decimales, USD en centavos).

## Actualizaciones desde GitHub
Servicio externo (el único): `GET https://api.github.com/repos/Yatsufusax/YuzuFin/releases/latest`. No envía ningún dato tuyo.

- Cada push a `main` compila, corre los tests, publica el APK firmado como Release `build-<n>` (`n` = número de ejecución) y esa `n` es el `versionCode`.
- La app compara su `versionCode` con `n`. Si hay uno mayor, avisa, descarga e instala. Android pide una vez el permiso "instalar apps desconocidas".
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

## Estructura
```
app/src/main/java/com/yuzufin/app/
  AppContainer.kt  Dependencias a mano (sin librería de DI)
  core/money/      Moneda, formato y lectura de importes (Long)
  domain/          Modelos y reglas del saldo (funciones puras)
  data/            Room (db/), repositorios, ajustes (DataStore), actualizador (update/)
  ui/theme|splash|components|navigation   Base visual y navegación
  ui/onboarding|home|movements|transaction|settings|update   Pantallas y ViewModels
```

## Siguiente paso
Revisar la Fase 2. Después, Fase 3: cobros pendientes.
