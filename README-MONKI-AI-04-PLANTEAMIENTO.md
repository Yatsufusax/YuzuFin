# Monea --- Monki AI 0.4

## Estado actual, planteamiento y próximos pasos

**Documento de continuidad para Claude**\
**Base de trabajo:** `monea-monki-ai-integracion-06.zip`\
**Estado:** planificación y auditoría previa. Todavía no se ha
autorizado implementar cambios.

------------------------------------------------------------------------

## 1. Objetivo

Queremos evolucionar **Monki AI**, el asistente financiero integrado en
Monea, para que entienda mejor el lenguaje natural y mantenga
conversaciones más útiles sobre las finanzas del usuario.

La prioridad es mejorar el chat, no reconstruir Monea ni rediseñar la
aplicación.

La versión 0.6 es la nueva base porque contiene cambios de interfaz que
el usuario quiere conservar. La integración de Monki AI 0.3 debe
mantenerse como punto de partida. Los archivos y dependencias deben
volver a comprobarse durante la auditoría.

## 2. Base que debe preservarse

-   Proyecto Android desarrollado en Kotlin y Jetpack Compose.
-   Motor existente: `MonkiFinanceEngine`.
-   Integración del chat: `MonkiChatScreen` y `MonkiViewModel`.
-   Repositorios y calculadores financieros existentes de Monea.
-   Interfaz actual de Monea 0.6, incluidos los cambios recientes de
    Inicio, tarjetas, navegación y recursos gráficos.
-   Funcionamiento actual del chat Monki AI 0.3.

**No reconstruir la aplicación ni reemplazar el chat actual.**
Evolucionar sobre el código existente.

## 3. Qué queremos mejorar en Monki 0.4

### A. Comprensión del lenguaje natural

Monki no debería depender de que el usuario escriba una frase exacta.
Debe reconocer distintas formas de expresar una misma intención.

  -----------------------------------------------------------------------
  Pregunta                            Intención
  ----------------------------------- -----------------------------------
  ¿Cuánto gasté este mes?             Consultar gastos del mes

  ¿Cuánta plata se me fue?            Consultar gastos del período
                                      contextual

  ¿En qué estoy gastando más?         Identificar categoría con mayor
                                      gasto

  ¿Cómo ando de dinero?               Mostrar resumen financiero

  ¿Estoy gastando demasiado?          Analizar gastos con criterios
                                      explicables

  ¿Cómo vengo comparado con el mes    Comparar períodos
  pasado?                             
  -----------------------------------------------------------------------

Debe resolver fechas y períodos con cuidado. Si la pregunta es ambigua,
pedir una aclaración en lugar de asumir.

### B. Conversación más natural

Queremos respuestas claras, cálidas y conversacionales, no mensajes
rígidos o excesivamente predefinidos.

También queremos que pueda mantener el contexto. Ejemplo:

1.  Usuario: «¿Cuánto gasté este mes?»
2.  Monki: presenta el total real.
3.  Usuario: «¿Y el mes pasado?»
4.  Monki: entiende que sigue hablando de gastos y compara el período
    anterior.

La personalidad debe ser amable y moderada, sin exagerar modismos ni
convertir el chat en un personaje infantil.

### C. Consultas financieras

Auditar primero qué datos y operaciones están realmente disponibles.
Después, ampliar progresivamente las consultas que puedan responderse
con información real de Monea:

-   Saldo y resumen financiero.
-   Ingresos y gastos.
-   Categorías y principales consumos.
-   Presupuestos.
-   Ahorros.
-   Obligaciones y pagos.
-   Estadísticas semanales y mensuales.
-   Comparaciones entre períodos.
-   Tendencias y variaciones de gastos.

No prometer una consulta si los modelos, repositorios o reglas
existentes no permiten calcularla correctamente.

## 4. Arquitectura y seguridad: reglas obligatorias

### Kotlin es la fuente de verdad financiera

Todos los importes, totales, porcentajes, comparaciones y resultados
deben calcularse mediante lógica determinista en Kotlin, usando los
datos reales de Monea.

Un modelo de lenguaje puede ayudar a: - Interpretar la pregunta. -
Identificar la intención y los parámetros. - Formular una respuesta
natural a partir de resultados verificados. - Mantener el contexto
conversacional.

Un modelo de lenguaje **no puede inventar ni calcular por su cuenta
cifras financieras**. Los resultados numéricos deben provenir del motor
y los calculadores de Monea.

### Monki es exclusivamente de lectura

Monki no debe: - Crear movimientos. - Modificar ingresos o gastos. -
Eliminar registros. - Modificar presupuestos. - Alterar ahorros u
obligaciones. - Ejecutar operaciones que cambien la base de datos.

Debe consultar únicamente las interfaces de lectura necesarias. Las
pruebas deben verificar que una conversación no modifica datos.

### Respuestas seguras

-   Si no hay registros, explicarlo claramente.
-   Si falta información, pedir aclaración.
-   Si una consulta no está soportada, reconocerlo.
-   Diferenciar saldo real, dinero reservado y dinero disponible solo si
    esos conceptos existen y están definidos en el proyecto.
-   No presentar estimaciones como datos reales.

## 5. Posible IA local y gratuita: todavía es una propuesta

El usuario quiere explorar si Monki puede ganar naturalidad sin pagar
servicios de API y, preferentemente, funcionar sin conexión.

Se planteó investigar modelos abiertos pequeños, como **Gemma** o
**Qwen**, y un runtime para Android como **LiteRT-LM**.

Esto todavía **no es una decisión técnica ni una autorización para
integrar una dependencia**.

Antes de recomendar o incorporar un modelo, investigar y comparar: -
Compatibilidad real con la versión de Android y Kotlin del proyecto. -
Compatibilidad con los dispositivos objetivo y sus recursos de RAM. -
Tamaño de descarga y almacenamiento. - Rendimiento, latencia y consumo
de batería. - Licencia y condiciones de uso. - Soporte real para
ejecución completamente local. - Mantenimiento, estabilidad y facilidad
de integración. - Posibilidad de mantener el motor financiero
determinista y aislado del modelo.

No integrar un modelo solo porque sea gratuito o porque exista un
ejemplo. Presentar ventajas, limitaciones y requisitos concretos antes
de decidir.

Si el modelo local no resulta viable para los dispositivos objetivo,
proponer alternativas sin modificar el proyecto hasta recibir
autorización.

## 6. Restricciones de alcance

No implementar: - Mascota permanente en la pantalla de Inicio. -
Mecánicas tipo Duolingo. - Puntos, niveles, recompensas o
celebraciones. - Animaciones innecesarias. - Rediseños de Monea. -
Reconstrucción del chat desde cero. - Funciones de escritura o edición
de registros financieros.

Conservar la interfaz actual de la versión 0.6. Los cambios visuales
deben limitarse a lo estrictamente necesario para mejorar el chat y
requerir justificación.

## 7. Primera etapa: auditoría, sin modificar archivos

Antes de programar, revisar el ZIP de la versión 0.6 y entregar un
diagnóstico que incluya:

1.  Cómo funciona realmente `MonkiFinanceEngine`.
2.  Qué hace `MonkiViewModel` y cómo se conecta con la interfaz.
3.  Cómo funciona `MonkiChatScreen`.
4.  Qué preguntas reconoce hoy y cuáles no.
5.  Qué repositorios, modelos y calculadores están disponibles.
6.  Qué consultas financieras pueden incorporarse con seguridad.
7.  Qué pruebas existen y qué cobertura falta.
8.  Si compila y qué verificaciones se ejecutaron realmente.
9.  Riesgos técnicos y de seguridad.
10. Viabilidad de una capa de lenguaje natural local.
11. Archivos concretos que sería necesario modificar.
12. Plan de implementación ordenado y estimación de complejidad.

**En esta etapa no modificar, eliminar, reescribir ni crear archivos del
proyecto.** No afirmar que se ejecutó una compilación o prueba si no se
realizó.

## 8. Segunda etapa: implementación, solo después de aprobar el diagnóstico

Una vez revisado y aprobado el informe:

1.  Crear una copia de pruebas basada en la versión 0.6.
2.  Mantener intacto el proyecto original.
3.  Ampliar las capacidades del motor por etapas.
4.  Añadir interpretación flexible de preguntas.
5.  Mejorar el contexto conversacional.
6.  Evaluar e integrar una IA local únicamente si se aprueba su
    viabilidad.
7.  Mantener los cálculos y las reglas financieras en Kotlin.
8.  Añadir pruebas unitarias para intenciones, fechas, cálculos, datos
    vacíos, comparaciones y casos ambiguos.
9.  Añadir pruebas que demuestren que Monki no modifica datos.
10. Actualizar README y NOVEDADES con lo que realmente se implementó.

No realizar cambios fuera del alcance aprobado.

## 9. Criterios para considerar exitosa la versión 0.4

-   Comprende distintas formas naturales de preguntar.
-   Puede continuar una conversación usando contexto anterior.
-   Responde con datos reales de Monea.
-   Los cálculos son deterministas y verificables.
-   Reconoce ambigüedades y falta de datos.
-   No inventa información.
-   No modifica registros financieros.
-   Conserva la interfaz y las funciones existentes de Monea 0.6.
-   Compila y supera las pruebas correspondientes.

------------------------------------------------------------------------

## Instrucción para Claude

Usa este documento como contexto del objetivo de Monki AI 0.4 y trabaja
sobre `monea-monki-ai-integracion-06.zip`.

**Por ahora, realiza únicamente la auditoría de la primera etapa. No
escribas código ni modifiques el proyecto.**

Devuelve el informe técnico, señala qué es viable y qué no, y espera la
aprobación antes de implementar cualquier cambio.
