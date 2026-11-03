package com.yuzufin.app.assistant

/** En qué momento de la conversación cae un chiste. */
enum class JokeSituation { GENERAL, LOW, HIGH, FOOD, SAVING, EMPTY }

/**
 * La voz de Monki: saludos, aclaraciones y chistes según la región de la moneda (Paraguay,
 * Argentina o neutral). Los chistes se eligen con [seed], que cambia en cada mensaje: así no se
 * repiten seguidos y las pruebas siempre dan el mismo resultado. Nunca llevan cifras.
 */
class MonkiVoice(private val region: MonkiRegion, private val seed: Int = 0) {

    /** Voseo en Paraguay y Argentina; tuteo en el resto. */
    fun vos(voseo: String, tuteo: String): String = if (region == MonkiRegion.NEUTRAL) tuteo else voseo

    fun greeting(): String = pick(GREETINGS.getValue(region))

    fun thanks(): String = pick(THANKS.getValue(region))

    fun joke(situation: JokeSituation): String =
        pick(JOKES.getValue(region).getValue(situation), situation.ordinal)

    fun askPeriod(): String = vos(
        "¿De qué período querés saber? Por ejemplo: hoy, esta semana, este mes o el mes pasado.",
        "¿De qué período quieres saber? Por ejemplo: hoy, esta semana, este mes o el mes pasado.",
    )

    fun askLastMonth(): String = vos(
        "Cuando decís «el último mes», ¿te referís al mes pasado completo o a los últimos 30 días?",
        "Cuando dices «el último mes», ¿te refieres al mes pasado completo o a los últimos 30 días?",
    )

    fun help(): String = vos(
        "Puedo contarte, con tus datos reales:\n" +
            "• Cuánto gastaste o ingresó (hoy, esta semana, este mes, el mes pasado, un mes puntual).\n" +
            "• En qué se va tu plata y cuánto en una categoría o cosa (comida, chipa, nafta…).\n" +
            "• Tu saldo, lo apartado y lo libre.\n" +
            "• Comparaciones con el mes pasado y la tendencia de tus gastos.\n" +
            "• Presupuestos, ahorros, facturas y préstamos.\n" +
            "• Cómo cerrarías el mes, gastos hormiga, dónde recortar y simulaciones («¿y si reduzco un 15%?»).\n" +
            "Probá con: «¿Cuánto gasté este mes?» y después «¿Y el mes pasado?».",
        "Puedo contarte, con tus datos reales:\n" +
            "• Cuánto gastaste o ingresó (hoy, esta semana, este mes, el mes pasado, un mes puntual).\n" +
            "• En qué se va tu dinero y cuánto en una categoría o cosa (comida, café, gasolina…).\n" +
            "• Tu saldo, lo apartado y lo libre.\n" +
            "• Comparaciones con el mes pasado y la tendencia de tus gastos.\n" +
            "• Presupuestos, ahorros, facturas y préstamos.\n" +
            "• Cómo cerrarías el mes, gastos hormiga, dónde recortar y simulaciones («¿y si reduzco un 15%?»).\n" +
            "Prueba con: «¿Cuánto gasté este mes?» y después «¿Y el mes pasado?».",
    )

    fun fallback(): String = vos(
        "No logré entender esa pregunta. Puedo ayudarte con gastos, ingresos, saldo, comparaciones, " +
            "presupuestos, ahorros, facturas y préstamos. Escribí «ayuda» y te muestro ejemplos.",
        "No logré entender esa pregunta. Puedo ayudarte con gastos, ingresos, saldo, comparaciones, " +
            "presupuestos, ahorros, facturas y préstamos. Escribe «ayuda» y te muestro ejemplos.",
    )

    private fun pick(options: List<String>, extra: Int = 0): String =
        options[Math.floorMod(seed + extra, options.size)]

    private companion object {

        val GREETINGS: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "¡Mba'éichapa! Soy Monki. ¿Qué revisamos de tu plata hoy? 🧉",
                "¡Iporã verte por acá! ¿Qué querés saber de tus finanzas?",
                "¡Hola! Acá Monki, con el tereré cebado y los números listos. 🧉",
                "¡Buenas! ¿Cómo andamos con los guaraníes?",
                "¡Hola, hola! Preguntame lo que quieras de tus gastos.",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "¡Buenas! Soy Monki. ¿Qué revisamos de tu guita hoy? 🧉",
                "¡Hola, che! ¿Vemos cómo viene la plata?",
                "¡Buen día! Mate en mano y números listos. ¿Qué querés saber?",
                "¡Hola! ¿Charlamos de tus gastos?",
                "¡Buenas! Preguntame lo que quieras sobre tus pesos. 😄",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "¡Hola! Soy Monki. ¿Qué te gustaría revisar de tus finanzas?",
                "¡Hola! Estoy listo para ayudarte con tus números. 🙂",
                "¡Buenas! Pregúntame lo que quieras sobre tus gastos.",
                "¡Hola! ¿Revisamos cómo van tus finanzas?",
                "¡Qué bueno verte! ¿Qué quieres saber hoy?",
            ),
        )

        val THANKS: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "¡Aguyje a vos! Cuando quieras, acá estoy. 🧉",
                "¡Con gusto! Cualquier otra duda, me avisás.",
                "¡Iporã! Para eso estoy.",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "¡De nada, che! Acá estoy cuando quieras. 🙌",
                "¡Un placer! Cualquier cosa, avisame.",
                "¡Joya! Para eso estoy.",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "¡De nada! Aquí estoy cuando me necesites. 🙂",
                "¡Con gusto! Cualquier otra duda, me avisas.",
                "¡Para eso estoy!",
            ),
        )

        val JOKES: Map<MonkiRegion, Map<JokeSituation, List<String>>> = mapOf(
            MonkiRegion.PARAGUAY to mapOf(
                JokeSituation.GENERAL to listOf(
                    "Ojo, que la billetera no se llena solo con tereré. 🧉",
                    "Anotar cada gasto es como cebar el tereré: sin yuyos no tiene gracia. 🌿",
                    "¡Iporã! Llevar la cuenta ya es medio camino ganado.",
                    "Si la plata fuera chipa, ¿cuántas docenas serían? 😄",
                    "Que no te pase como al tereré en verano: ¡se acaba antes de lo que pensás! ☀️",
                    "Cuidar los guaraníes también es una forma de cuidarte. 😉",
                ),
                JokeSituation.LOW to listOf(
                    "¡Iporã! Un bolsillo tranquilo es un bolsillo feliz. 😌",
                    "Con ese ritmo, el tereré de la tarde está más que asegurado. 🧉",
                    "¡Qué orden! Hasta la abuela aprobaría estas cuentas. 👵",
                    "Así se hace: gasto chico, cabeza tranquila.",
                    "¡Aguyje por cuidar la plata! El ahorro te saluda. 👋",
                    "El que cuida los guaraníes se toma el tereré sin culpa. 😄",
                ),
                JokeSituation.HIGH to listOf(
                    "Uy, esto se calentó más que el asfalto en enero. 🔥 Revisemos de a poco.",
                    "Cuando los gastos pasan a los ingresos, hasta el tereré se pone tibio. 😅",
                    "Tranquilo, con un poquito de orden esto se acomoda. ¡Vos podés!",
                    "Ojo con la guampa: se vacía más rápido de lo que se llena. 🧉",
                    "Toca apretar un poquito el cinturón... pero sin dejar el tereré, ¡eso es sagrado!",
                    "Hasta el lomito más rico merece su presupuesto. 😄",
                ),
                JokeSituation.FOOD to listOf(
                    "Capaz mañana le damos un descanso al lomito. 😂",
                    "La chipa es sagrada, pero la billetera también pide su ración. 🥖",
                    "Sopa paraguaya, mbeju, asado... ¡qué rico se gasta en comida! 😋",
                    "Entre chipa y lomito, la comida siempre gana. ¡Pero con medida!",
                    "Si el delivery tuviera programa de puntos, ya serías VIP. 🛵",
                    "Un asadito de vez en cuando no le hace mal a nadie... ni a este monito. 🔥",
                ),
                JokeSituation.SAVING to listOf(
                    "¡Aguyje! Ahorrar así da gusto.",
                    "Cada guaraní apartado es un tereré más para el futuro. 🧉",
                    "¡Iporã! Sigue así y la meta llega sola.",
                    "Ahorrar es como cebar tereré: de a poquito y con paciencia. 🌿",
                    "¡Eso es tener cabeza! El futuro te lo va a agradecer.",
                ),
                JokeSituation.EMPTY to listOf(
                    "Todavía no hay nada que contar: ¡buen momento para anotar el primer gasto! ✍️",
                    "Libreta en blanco, como el tereré recién cebado. ¡Empecemos!",
                    "Sin movimientos por acá... ¿se escondió la plata? 🔍",
                    "Anotá el primero y yo me encargo de las cuentas. 😉",
                ),
            ),
            MonkiRegion.ARGENTINA to mapOf(
                JokeSituation.GENERAL to listOf(
                    "Ojo, que la billetera no tiene botón de recarga infinita. 😂",
                    "Anotar todo es lo mejor que podés hacer... después del asado del domingo. 🥩",
                    "La guita no se cuida sola, che. ¡Pero para eso estoy yo!",
                    "Los números no mienten, aunque a veces duelen. 📊",
                    "Con mates y números claros se ordena cualquier mes. 🧉",
                    "Tranqui, que ordenando de a poco esto sale al pelo.",
                ),
                JokeSituation.LOW to listOf(
                    "¡Qué bárbaro! Un período tranqui para el bolsillo. 👏",
                    "Al pelo: gasto bajo, mente en paz.",
                    "Con este ritmo, hasta te sobra para unas medialunas. 🥐",
                    "Ahorrando como un campeón: ¡hasta Messi estaría orgulloso! ⚽",
                    "¡Joya! Así da gusto llevar las cuentas.",
                    "¡Eso es administrar! Capaz hasta alcanza para un asadito. 🥩",
                ),
                JokeSituation.HIGH to listOf(
                    "Uh, esto se pasó de rosca. Revisemos qué se puede recortar. 😅",
                    "La billetera quedó como un mate lavado: ¡se vació rapidísimo!",
                    "Cuando el gasto le gana al sueldo, hasta el fernet se pone a pensar. 🥃",
                    "Posta que hay que cuidar un poquito el mango, ¿eh?",
                    "Tranqui, no es el fin del mundo: ajustando un poco, vuelve al eje. 💪",
                    "Ojo con el bondi del gasto: ¡viene a mil! 🚌",
                ),
                JokeSituation.FOOD to listOf(
                    "La próxima hamburguesa o pizza que espere un ratito, ¿no? 😂",
                    "Entre asado, empanadas y pizza, el morfi siempre gana. 🥟",
                    "El morfi es sagrado, pero la billetera también tiene derechos. 😄",
                    "Una birra o un fernet de vez en cuando, ¡pero con medida!",
                    "Si el delivery diera puntos, ya serías cliente VIP. 🛵",
                    "Medialunas, facturas, mates... ¡qué lindo se gasta en esto! 🥐",
                ),
                JokeSituation.SAVING to listOf(
                    "¡Bien ahí! Cada peso apartado es futuro asegurado.",
                    "Ahorrar es como cebar mate: con paciencia sale rico. 🧉",
                    "¡Qué crack! La meta se acerca cada vez más.",
                    "Así se hace, che: de a poquito se llega lejos.",
                    "Con esa constancia, la meta llega al pelo.",
                ),
                JokeSituation.EMPTY to listOf(
                    "Todavía no hay nada para contar: ¡anotá el primer gasto! ✍️",
                    "Cuaderno en blanco, como un mate recién cebado. ¡A empezar!",
                    "¿Dónde se metió la guita? Anotá y la encontramos. 🔍",
                    "Anotá el primero y yo me encargo de la cuenta. 😉",
                ),
            ),
            MonkiRegion.NEUTRAL to mapOf(
                JokeSituation.GENERAL to listOf(
                    "Un buen registro ayuda a que el dinero no desaparezca sin explicación. 🙂",
                    "Llevar las cuentas al día es la mitad del trabajo. ✅",
                    "Los números claros dan tranquilidad. 📊",
                    "Un pequeño hábito diario hace la diferencia. 🌱",
                    "Anotar poco a poco funciona mejor que intentar recordarlo todo.",
                    "Con datos claros, las decisiones son más fáciles.",
                ),
                JokeSituation.LOW to listOf(
                    "¡Muy bien! Un período tranquilo para el bolsillo. 👏",
                    "Buen ritmo: gastos bajo control. ✅",
                    "Así se construyen hábitos sólidos. 🌟",
                    "Menos gasto, más tranquilidad. 😌",
                    "¡Excelente orden con las cuentas!",
                    "Con este ritmo, tus metas se acercan.",
                ),
                JokeSituation.HIGH to listOf(
                    "Cuando los gastos pasan a los ingresos, conviene mirar con calma qué se puede ajustar. 🔍",
                    "Nada grave: con pequeños ajustes se acomoda. 💪",
                    "Revisar las categorías grandes suele dar las mejores pistas.",
                    "Un ajuste chico hoy se nota mucho a fin de mes.",
                    "Respira hondo: los números están para ayudarte, no para asustarte. 🙂",
                    "Un presupuesto por categoría puede ayudar a frenar esto.",
                ),
                JokeSituation.FOOD to listOf(
                    "Registrar estos consumos ayuda a detectar patrones sin juzgarte. 🙂",
                    "Comer rico es parte de la vida; solo conviene medirlo. 🍽️",
                    "Cocinar en casa de vez en cuando también ayuda al bolsillo. 🍳",
                    "Un café aquí, un delivery allá: sumados cuentan. ☕",
                    "Mirar el gasto en comida suele revelar lo más fácil de ajustar.",
                    "Sin culpa: solo datos para decidir mejor.",
                ),
                JokeSituation.SAVING to listOf(
                    "¡Ahorrar es una gran decisión! 🌱",
                    "Cada aporte cuenta, por pequeño que sea.",
                    "Constancia: la clave de cualquier meta. 🔑",
                    "Tu yo del futuro te lo va a agradecer.",
                    "¡Vas por buen camino!",
                    "De a poco se llega lejos.",
                ),
                JokeSituation.EMPTY to listOf(
                    "Todavía no hay datos para mostrar: anota tu primer movimiento y empezamos. ✍️",
                    "Una libreta en blanco es una buena oportunidad para empezar. 📒",
                    "Sin movimientos por ahora. Cuando anotes, te ayudo a verlos. 🙂",
                    "Empezar es lo más difícil... y ya estás aquí.",
                    "Anota un gasto o un ingreso y te muestro el panorama.",
                    "Cuanto antes anotes, más útil me vuelvo. 😉",
                ),
            ),
        )
    }
}
