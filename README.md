# YuzuFin

App de finanzas personales para Android (Kotlin, Jetpack Compose, Material 3). Local-first, sin cuentas ni IA.
*Tu dinero. Tus decisiones.*

## Estado: Fase 1 (base del proyecto)

**Hecho (escrito, pendiente de compilar en tu máquina)**
- Proyecto Gradle (Kotlin DSL, catálogo de versiones en `gradle/libs.versions.toml`), `minSdk 26`, `targetSdk 35`.
- Tema verde inspirado en tu banco: claro y oscuro, tarjetas blancas redondeadas, barra inferior flotante.
- Splash: fondo del sistema verde oscuro + splash animada (billetera que se abre, sale un billete, aparece “YuzuFin” y el lema). Se salta tocando.
- Navegación: Inicio · Movimientos · (+) · Ahorros · Menú → Cobros, Obligaciones, Estadísticas, Ajustes.
- Inicio con **datos de demostración** (banner visible; ver `data/demo/DemoData.kt`).
- Secciones futuras muestran qué incluirán y en qué fase llegan, sin datos inventados.
- Formateador de dinero con enteros (`core/money`) y su test.

**No hecho todavía:** base de datos, lógica financiera, notificaciones, anuncios (a propósito).

**Sin verificar:** no se pudo compilar ni ejecutar donde se escribió (sin acceso al SDK de Android). Revisados solo recursos y XML con scripts.

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
  core/money/      Moneda y formato de importes (Long en unidad mínima)
  data/demo/       Datos de demostración (se borran en la Fase 2)
  ui/theme/        Colores, tipografía, formas
  ui/splash/       Splash animada
  ui/components/   Tarjetas, insignias, pantalla "en construcción", registro rápido
  ui/navigation/   Destinos, barra flotante, NavHost
  ui/home|menu|sections/  Pantallas
```

## Siguiente paso
Revisar esta base. Después, Fase 2: configuración inicial, Room, saldo y movimientos.
