# Prompts de Google Flow para animar al monito

Para cada pose: subí la imagen `monito_<nombre>_verde.png` (viene en `monitos_fondo_verde_16x9.zip`) como **cuadro inicial** en Flow y, si tu versión lo permite, también como **cuadro final**. Pegá el prompt de abajo tal cual. Formato **16:9**, duración la mínima que te deje (4 s alcanza; después se recorta).

Después, para cada video que te guste:

```
python tools/clip_verde_a_webp.py video.mp4 app/src/main/res/drawable-nodpi/monito_clip_<nombre>.webp --desde 0.3 --hasta 3.3
```

y se enchufa en la app agregando el clip a la pose en `Mascot.kt` (ver `MONITO_ANIMADO_README.md`, sección "Cómo sumar un clip nuevo").

**Reglas que hacen que el recorte del verde salga limpio**
- Que el verde quede parejo todo el video. Si Flow le agrega sombras, piso o cambia el tono, regeneralo.
- Cámara fija y monito centrado y completo. Si se corta una parte, regeneralo.
- Una sola acción clara por video: en 4 segundos más de una se ve apurado.
- Mirá el primer y el último cuadro: tienen que parecerse para que no haya salto al volver a la imagen fija.

**Nombres de las poses, en orden de prioridad**

### Hecho

#### `RICH` — `monito_con_plata.webp`
- Dónde se ve: Inicio, cuando te sobra plata (YA HECHO: monito_clip_con_plata.webp).
- Cuadro inicial: `monito_con_plata_verde.png`
- Clip a generar: `monito_clip_con_plata.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the large gold coin and the stacks of gold coins exactly as they are. The monkey smiles happily, blinks, giggles and does one small joyful bounce, then lifts the large gold coin slightly while a soft sparkle glints on it. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

### Prioridad 1: las poses que salen en la tarjeta del monito de Inicio, más el festejo de meta cumplida

#### `MISSED_YOU` — `monito_te_extrana.webp`
- Dónde se ve: Inicio, cuando hace 7 o más días que no entrás.
- Cuadro inicial: `monito_te_extrana_verde.png`
- Clip a generar: `monito_clip_te_extrana.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the single tear and the sad expression exactly as they are. The monkey sniffles softly, the tear slowly rolls down, then it looks up with a small hopeful smile and gives a tiny wave before returning to the sad pose. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `OVERSPENT` — `monito_gasto_de_mas.webp`
- Dónde se ve: Inicio y Presupuestos, cuando gastaste más de lo que ingresó.
- Cuadro inicial: `monito_gasto_de_mas_verde.png`
- Clip a generar: `monito_clip_gasto_de_mas.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the crossed arms and the frowning look exactly as they are. The monkey puffs its cheeks, slowly shakes its head side to side, and gives a small grumpy sigh, cute and not scary. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `BROKE` — `monito_sin_plata.webp`
- Dónde se ve: Inicio, cuando el saldo está en negativo.
- Cuadro inicial: `monito_sin_plata_verde.png`
- Clip a generar: `monito_clip_sin_plata.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the navy backpack and the back view (no face visible) exactly as they are. The monkey shrugs its shoulders slowly, then takes a few slow steps in place with the tail swaying, head slightly down, then settles back into the first pose. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `NOTIFICATION` — `monito_notificaciones.webp`
- Dónde se ve: Inicio (cobro por confirmar) y pantalla Avisos.
- Cuadro inicial: `monito_notificaciones_verde.png`
- Clip a generar: `monito_clip_notificaciones.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the smartphone and the purple bell speech bubble exactly as they are. The monkey the phone buzzes, the bell bubble wobbles and pops bigger, the monkey looks at the screen surprised and then smiles. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `WELCOME` — `monito_bienvenida.webp`
- Dónde se ve: Inicio con el mes vacío y bienvenida.
- Cuadro inicial: `monito_bienvenida_verde.png`
- Clip a generar: `monito_clip_bienvenida.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the burst lines around the monkey exactly as they are. The monkey waves hello with both arms, laughs, does a small hop, and the burst lines around it pulse softly. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `PROGRESS` — `monito_progreso.webp`
- Dónde se ve: Inicio (vas bien) y Estadísticas.
- Cuadro inicial: `monito_progreso_verde.png`
- Clip a generar: `monito_clip_progreso.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the blue bar chart and the green arrow exactly as they are. The monkey the bars grow upward one after another, the green arrow rises, and the monkey points at it and cheers. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `GOAL_DONE` — `monito_objetivo.webp`
- Dónde se ve: Meta de ahorro cumplida y festejo del primer gasto.
- Cuadro inicial: `monito_objetivo_verde.png`
- Clip a generar: `monito_clip_objetivo.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the target and the dart in the bullseye exactly as they are. The monkey the dart vibrates in the bullseye, the monkey cheers with a joyful jump and tiny colorful sparkles pop around the target. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

### Prioridad 2: pantallas internas, consejos y estados vacíos

#### `COIN_HUG` — `monito_moneda.webp`
- Dónde se ve: Primer ingreso, pantallas vacías y paso 1 del recorrido.
- Cuadro inicial: `monito_moneda_verde.png`
- Clip a generar: `monito_clip_moneda.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the big gold coin and the two hearts exactly as they are. The monkey squeezes the big coin in a warm hug, giggles, and the two hearts float up and fade. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `SAVING` — `monito_ahorrando.webp`
- Dónde se ve: Ahorros.
- Cuadro inicial: `monito_ahorrando_verde.png`
- Clip a generar: `monito_clip_ahorrando.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the glass jar with coins and the heart exactly as they are. The monkey drops one gold coin into the jar with a happy clink, the coins settle, and the heart floats up while the monkey smiles. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `PIGGY` — `monito_alcancia.webp`
- Dónde se ve: Ahorros sin metas.
- Cuadro inicial: `monito_alcancia_verde.png`
- Clip a generar: `monito_clip_alcancia.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the pink piggy bank and the coin exactly as they are. The monkey slides the coin into the piggy bank slot, the piggy bank wiggles happily and the monkey giggles. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `CONTROL` — `monito_control.webp`
- Dónde se ve: Categorías.
- Cuadro inicial: `monito_control_verde.png`
- Clip a generar: `monito_clip_control.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the clipboard with the checklist exactly as they are. The monkey winks, taps the clipboard and a green check mark pops on it, then nods satisfied. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `WONDER` — `monito_en_que_gasto.webp`
- Dónde se ve: Movimientos sin resultados.
- Cuadro inicial: `monito_en_que_gasto_verde.png`
- Clip a generar: `monito_clip_en_que_gasto.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the thought bubble with the coin and the question marks exactly as they are. The monkey scratches its chin and tilts its head, the thought bubble wobbles and the question marks pop one by one. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `PAYING` — `monito_pagando.webp`
- Dónde se ve: Facturas y préstamos.
- Cuadro inicial: `monito_pagando_verde.png`
- Clip a generar: `monito_clip_pagando.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the card terminal and the card speech bubble exactly as they are. The monkey taps the card against the terminal, the bubble pops with a small flash, and the monkey nods satisfied. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `PLANNING` — `monito_planificando.webp`
- Dónde se ve: Gastos fijos y primer vistazo del mes.
- Cuadro inicial: `monito_planificando_verde.png`
- Clip a generar: `monito_clip_planificando.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the laptop and the potted plant exactly as they are. The monkey types on the laptop for a moment, the plant leaves sway gently, then it glances up and smiles. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `BUDGET` — `monito_presupuesto.webp`
- Dónde se ve: Presupuestos.
- Cuadro inicial: `monito_presupuesto_verde.png`
- Clip a generar: `monito_clip_presupuesto.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the black wallet with the cash and cards exactly as they are. The monkey opens the wallet a little so the bills peek out, then closes it and hugs it with a content smile. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `QUESTION` — `monito_preguntas.webp`
- Dónde se ve: Ayuda y paso 3 del recorrido.
- Cuadro inicial: `monito_preguntas_verde.png`
- Clip a generar: `monito_clip_preguntas.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the purple question mark exactly as they are. The monkey tilts its head, taps a finger on its chin, and the question mark bobs up and down. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `TIP` — `monito_sugerencias.webp`
- Dónde se ve: Consejos (HintCard) en casi todas las pantallas.
- Cuadro inicial: `monito_sugerencias_verde.png`
- Clip a generar: `monito_clip_sugerencias.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the glowing light bulb exactly as they are. The monkey the light bulb flickers on and glows, the monkey raises a finger and does a tiny "eureka" hop. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

### Prioridad 3: solo se ven una vez, en la configuración inicial

#### `COFFEE` — `monito_cafe.webp`
- Dónde se ve: Configuración inicial con otras monedas.
- Cuadro inicial: `monito_cafe_verde.png`
- Clip a generar: `monito_clip_cafe.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the coffee cup with steam and the heart exactly as they are. The monkey takes a small sip, sighs happily with eyes closed, steam rises and the heart floats up. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `MATE` — `monito_mate.webp`
- Dónde se ve: Configuración inicial con pesos argentinos.
- Cuadro inicial: `monito_mate_verde.png`
- Clip a generar: `monito_clip_mate.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the Argentina football shirt and the mate gourd with the straw exactly as they are. The monkey sips through the straw, closes its eyes with a content sigh, and the steam rises softly. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```

#### `TERERE` — `monito_terere.webp`
- Dónde se ve: Configuración inicial con guaraníes.
- Cuadro inicial: `monito_terere_verde.png`
- Clip a generar: `monito_clip_terere.webp`

```
Animate this exact character, keeping its design, fur texture, proportions and colors unchanged. It keeps the Paraguay football shirt and the tereré cup with the straw exactly as they are. The monkey sips through the straw, sighs happily, and the snowflake sparkle twinkles. Ends in exactly the same pose as the first frame, so it loops cleanly.
Keep the background perfectly flat pure green (#00FF00) for the whole video: no floor, no shadows, no reflections, no vignette, no lighting changes on the background. Static camera, no zoom, no pan, the character stays centered and fully in frame. No text, no audio.
```
