# Monito animado y mensajes rotativos: estado y cómo seguir

Este archivo es para retomar el trabajo en una conversación nueva (por el límite de uso). Se lee junto con `MONEA_HANDOFF.md` (reglas del proyecto, flujo con GitHub, lecciones) y `PROMPTS_FLOW_MONITOS.md` (prompts de cada pose). Adjuntá el último zip del proyecto: sin el zip no hay código.

## 1. Qué estamos haciendo
Darle más vida al monito de Monea de dos maneras:
1. **Animaciones**: pequeños clips del monito (generados con Google Flow a partir de las poses que ya existen) que se reproducen cuando se toca al monito.
2. **Mensajes que hablan**: el globo de texto del monito en Inicio ya no es fijo. Se tipea solo letra por letra, rota solo, cambia al tocarlo y no usa audio (sin botones desde v51).

## 2. Estado (v51, sobre v50 que compiló y el usuario aprobó)
Cambios de v51 (sin compilar): (a) el clip se reproduce **solo** (`Mascot(autoClip = true)`: a los 0,9 s de aparecer y luego cada 4 s tras terminar, `AUTO_CLIP_*` en `Mascot.kt`; solo si la pose tiene clip, Android 9+ y animaciones activas); (b) el globo **tipea** cada mensaje letra por letra (`MascotTalk.TYPE_MS` = 32 ms), lo deja `HOLD_MS` = 5 s y pasa al siguiente; el texto entero invisible reserva el alto; tocar el globo mientras tipea lo completa, y completo (o tocar al monito) pasa al próximo; (c) **se quitaron los botones** (`MascotAction`, strings `mascot_act_*`, `onAction`). Nada se renombró ni borró como archivo.

**Ajuste de tamaño (v51):** el clip se recorta con aire y la imagen fija justo al monito, así que al pasar de una a otro el monito cambiaba de tamaño. `MascotPose.clipFit` (`ClipFit`) corrige el clip con `graphicsLayer` (escala y desplazamiento). Para un clip nuevo: en el primer cuadro medir el recuadro del monito (alpha > 10); `scale` = alto del lienzo / alto del recuadro; `dx`, `dy` = (centro del recuadro − centro del lienzo) / alto del lienzo. Para `RICH`: lienzo 387x440, recuadro (43,27)-(379,438). Más fácil: recortar el clip al recuadro del primer cuadro (sin perder lo que se mueve fuera) y dejar `ClipFit.None`.

**Clips de bebidas (v53):** `monito_clip_mate_hd.webp` (reemplaza a `monito_clip_mate.webp`, que se borra en el workflow), `monito_clip_terere.webp` y `monito_clip_cafe.webp`, de videos HD de 4 s (1280x720, 12 fps, alto 440, ~1 MB cada uno), cada uno con su `ClipFit`. En Inicio, cuando el ánimo es `GOOD` (todo normal), el monito usa la pose de la moneda (`mascotForCurrency`: PYG tereré, ARS mate, resto café) y se anima solo; los otros ánimos conservan su pose. Los símbolos que flotan (corazones, copos) hacen el lienzo más ancho: el `ClipFit` compensa.

**Clip del mate (v52, reemplazado en v53):** `monito_clip_mate.webp` (267x320, 80 cuadros a 10 fps, ~1 MB, sale del video de 8 s del usuario, 640x360) en la pose `MATE` con su `ClipFit`; solo se ve en la configuración inicial con pesos argentinos (ARS), donde el monito se anima solo (`autoClip`). `tools/clip_verde_a_webp.py` ahora usa `method=4` (con 6 tardaba más de 2 minutos). El video de origen es chico: no conviene pasar de `--alto 320`.

**Saldo oculto (v52):** el ojo de la tarjeta de saldo ahora se guarda en los ajustes (`AppSettings.balanceHidden`, `SettingsRepository.setBalanceHidden`) y Inicio abre con el saldo tapado si así se dejó.

### Estado de v50
| Cosa | Estado |
|---|---|
| Clip de `RICH` (monito con monedas, 4 s) | Hecho: `res/drawable-nodpi/monito_clip_con_plata.webp` |
| Reproductor de clips (`MascotClip.kt`) | Escrito, **sin compilar** |
| `Mascot` con parámetro `clip` en la pose y `onClick` | Escrito, **sin compilar** |
| Mensajes rotativos y con botón (`MascotTalk.kt`, `strings_mascot_talk.xml`) | Escrito, **sin compilar** |
| Tarjeta del monito en Inicio usa `TalkingMascot` | Escrito, **sin compilar** |
| Tests (`MascotTalkTest.kt`) | Escritos, **sin correr** |
| Clips de las otras 20 poses | Pendiente (prompts listos en `PROMPTS_FLOW_MONITOS.md`) |

**Claude no puede compilar este proyecto.** Lo probás vos con GitHub Actions. Si el build falla, pegá el error en la conversación y se corrige.

## 3. Decisiones y por qué
- **WebP animado con transparencia, no video**, para el monito chico: el video de Android no soporta transparencia y el monito aparece sobre fondos de muchas paletas, en modo claro y oscuro. El video queda para pantalla completa (la intro ya es así).
- **Sin librerías nuevas** (no Coil): se usa `ImageDecoder` y `AnimatedImageDrawable` de Android. Requiere Android 9 (API 28). En Android 8 (`minSdk` 26) el toque sigue haciendo el saltito de siempre, sin clip.
- **Fondo verde puro (#00FF00) en Flow**, y el verde se quita después con `tools/clip_verde_a_webp.py`. Así sirve para cualquier paleta. El script quita el verde por dominancia de color (no por tono), porque el método común de ffmpeg (`chromakey` + `despill`) volvía anaranjadas las monedas doradas.
- **El clip se recorta justo al monito**, igual que las poses estáticas, para que al pasar de la imagen fija al clip no cambie el tamaño.
- **Duración**: 2 a 4 s, 12 cuadros por segundo, alto 440 px. Pesa unos 900 KB por clip; vigilar el peso del APK si se suman muchos (probar `--alto 380`, `--fps 10`, `--calidad 40`).
- **Los clips se reproducen una sola vez** y vuelven a la imagen fija. Por eso cada video debe terminar en la misma pose en la que empieza.
- **Mensajes**: cada ánimo (`MascotMood`) tiene 3 o 4 mensajes (`MascotTalk.lines`). Se tipean y quedan 5 s (`TYPE_MS`, `HOLD_MS`), o cambian al tocar al monito o al globo, y nunca repiten el mismo seguido. No rotan solos con TalkBack activado ni con las animaciones del teléfono apagadas. Algunos mensajes usan el número de días sin entrar o el porcentaje gastado del ingreso.
- Los textos están en voseo, como el resto de la app.

## 4. Archivos tocados en v50
Nuevos:
- `app/src/main/res/drawable-nodpi/monito_clip_con_plata.webp`
- `app/src/main/java/com/yuzufin/app/ui/components/MascotClip.kt`
- `app/src/main/java/com/yuzufin/app/ui/components/MascotTalk.kt`
- `app/src/main/res/values/strings_mascot_talk.xml`
- `app/src/test/java/com/yuzufin/app/ui/MascotTalkTest.kt`
- `tools/clip_verde_a_webp.py`, `PROMPTS_FLOW_MONITOS.md`, este archivo

Modificados:
- `ui/components/Mascot.kt`: `MascotPose` tiene un segundo valor opcional `clip`; `Mascot` recibe `onClick` y reproduce el clip al tocar.
- `ui/home/HomeScreen.kt`: `MascotCard` ahora recibe `onNavigate` y `onQuickAction` y usa `TalkingMascot`.
- `NOVEDADES.md`, `MONEA_HANDOFF.md`.

No se renombró ni borró ningún archivo, así que `build.yml` no necesita cambios.

## 5. Cómo sumar un clip nuevo
1. Sacá de `monitos_fondo_verde_16x9.zip` la imagen `monito_<pose>_verde.png`, subila a Flow como cuadro inicial (y final) y pegá el prompt de esa pose de `PROMPTS_FLOW_MONITOS.md`.
2. Convertí el video: `python tools/clip_verde_a_webp.py video.mp4 app/src/main/res/drawable-nodpi/monito_clip_<pose>.webp --desde 0.3 --hasta 3.3`. Necesita ffmpeg y `pip install pillow numpy`. Mirá el resultado sobre un fondo claro y uno oscuro antes de aceptarlo.
3. En `Mascot.kt`, agregá el clip a la pose: `MISSED_YOU(R.drawable.monito_te_extrana, R.drawable.monito_clip_te_extrana),`.
4. Listo: al tocar al monito con esa pose, se reproduce.
5. Nombres de recursos nuevos, nunca reutilizar uno viejo con otro formato (ver la lección del zip en `MONEA_HANDOFF.md`).

## 6. Cómo probar en el teléfono (Android 9 o superior)
1. En Inicio, el monito con monedas (`RICH`) aparece cuando tenés saldo positivo y gastaste el 70 % o menos de lo que ingresó este mes. Con otros datos sale otra pose: es normal, y esas todavía no tienen clip.
2. Tocá al monito: salta, vibra, reproduce el clip y cambia el mensaje.
3. Sin tocar nada: el monito con monedas hace su animación solo a los ~1 s y la repite cada pocos segundos; el mensaje se escribe letra por letra y pasa al siguiente solo.
4. Tocá el globo mientras escribe: se completa. Tocalo de nuevo: pasa al otro mensaje. La tarjeta no tiene que saltar de alto al escribir.
5. Probalo en modo claro y oscuro y con el tamaño de texto "Muy grande".
6. En Android 8: el toque hace el saltito y cambia el mensaje, sin clip.

## 7. Riesgos conocidos
- Nada se compiló. Los puntos más probables de error: el parámetro `onRelease` de `AndroidView` (depende de la versión de Compose), la inferencia de tipos en `slideInVertically` dentro de `AnimatedContent`, y imports no usados que solo dan avisos.
- Si el WebP animado no se reproduce, el código cae a la imagen fija sin romper nada.
- `monito_clip_con_plata.webp` se generó una sola vez con el video de Flow; si se regenera, que mantenga el mismo recorte (usar el script).

## 8. Próximos pasos sugeridos
1. Compilar y probar v50 (sección 6).
2. Generar los clips de prioridad 1 (`MISSED_YOU`, `OVERSPENT`, `BROKE`, `NOTIFICATION`, `WELCOME`, `PROGRESS`, `GOAL_DONE`).
3. Reproducir el clip de `GOAL_DONE` solo en el festejo de meta cumplida y en `FirstExpenseCelebration`.
4. Usar `TalkingMascot` en otras pantallas (presupuestos, ahorros) con sus propios ánimos y mensajes.
5. Opcional: reproducir el clip una vez cuando aparece la tarjeta, no solo al tocar.

## 9. Para pegar al empezar la conversación nueva
> Seguimos con Monea. Leé `MONEA_HANDOFF.md` y `MONITO_ANIMADO_README.md` del zip adjunto y no releas todo el código. Estoy en el punto 8.1: te paso el resultado del build de v50. Respondé en voseo y avisá si algo quedó sin compilar.
