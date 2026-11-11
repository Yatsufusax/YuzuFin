package com.yuzufin.app.assistant

/** Los momentos en que Monki escribe primero, como quien te conoce. */
enum class ProactiveKind { OVERSPEND, INCOME, MILESTONE, STREAK, LOW_FUNDS, SILENCE }

/** Un mensaje proactivo listo para guardar en la campanilla. */
data class ProactiveNote(
    val kind: ProactiveKind,
    /** Clave de deduplicación para `app_notifications`: con ella, el mismo aviso no se repite. */
    val dedupeKey: String,
    val text: String,
)

/**
 * Frases proactivas de Monki, por región. Regla de tono: celebrar fuerte, regañar suave.
 * [amount] llega ya formateado o `null`: cuando la persona apagó los montos en las notificaciones no se
 * dice el importe. Los avisos de ingreso y de libre estimado tienen frases aparte para ese caso, porque
 * con «un montón» quedaban contradictorios.
 * La rotación usa [seed], igual que los chistes de [MonkiVoice]: no se repiten seguidos.
 */
class MonkiProactive(private val region: MonkiRegion, private val seed: Int = 0) {

    fun overspend(category: String, amount: String?, period: String): String =
        pick(OVERSPEND.getValue(region), 0, category = category, amount = amount, period = period)

    fun income(amount: String?): String =
        if (amount == null) pick(INCOME_HIDDEN.getValue(region), 1) else pick(INCOME.getValue(region), 1, amount = amount)

    fun milestone(goal: String, percent: Int): String =
        pick(MILESTONE.getValue(region), 2, goal = goal, percent = percent)

    fun streak(days: Int): String =
        pick(STREAK.getValue(region), 3, days = days)

    fun lowFunds(amount: String?): String =
        if (amount == null) pick(LOW_FUNDS_HIDDEN.getValue(region), 4) else pick(LOW_FUNDS.getValue(region), 4, amount = amount)

    fun silence(days: Int): String =
        pick(SILENCE.getValue(region), 5, days = days)

    private fun pick(
        options: List<String>,
        extra: Int,
        category: String = "",
        amount: String? = null,
        period: String = "",
        goal: String = "",
        percent: Int = 0,
        days: Int = 0,
    ): String = options[Math.floorMod(seed + extra, options.size)]
        .replace("{cat}", category)
        .replace("{amount}", amount ?: "un montón")
        .replace("{period}", period)
        .replace("{goal}", goal)
        .replace("{percent}", "$percent")
        .replace("{days}", "$days")

    private companion object {
        val OVERSPEND: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "Che, ¿viste que ya llevás {amount} en {cat} {period}? 🙈 ¿Le damos un vistazo?",
                "Ojo que {cat} se está yendo de las manos: {amount} {period}. ¿Miramos qué onda?",
                "{amount} en {cat} {period}… hasta el tereré me supo a culpa. 😅 ¿Le bajamos un cambio?",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "Che, {cat} viene pasado de rosca: {amount} {period}. ¿Le metemos freno o seguimos? 😅",
                "Uh, {amount} en {cat} {period}… el bolsillo está pidiendo pausa. 🙈",
                "Mirá que {cat} no perdona: {amount} {period}. ¿Vemos en qué se fue?",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "Ojo: llevas {amount} en {cat} {period}. ¿Quieres ver en qué se fue?",
                "{cat} está subiendo más de lo usual: {amount} {period}.",
                "Un aviso de {cat}: {amount} {period}. Tal vez sea buen momento para revisarlo. 🙂",
            ),
        )
        val INCOME: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "¡Llegó plata! 💸 {amount} de ingreso. ¿Apartamos algo para la meta?",
                "¡Iporã! Entró {amount}. ¿Te animás a guardar un poquito?",
                "¡Eso! {amount} entraron. Tu yo del futuro ya está sonriendo. 😄",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "¡Llegó la guita! 💸 {amount}. ¿Apartamos algo para las metas, campeón?",
                "¡Qué joya! Entraron {amount}. ¿Hacemos que rindan? 😄",
                "¡Eso es! {amount} de ingreso. Ya estoy moviendo las fichas.",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "¡Buenas noticias! Entraron {amount}. ¿Quieres apartar algo para tus metas? 🙂",
                "Un ingreso de {amount} acaba de registrarse. ¡Buen momento para ahorrar!",
            ),
        )
        /** Sin importe (montos apagados en los avisos). */
        val INCOME_HIDDEN: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "¡Llegó plata! 💸 Entró un buen ingreso. ¿Apartamos algo para la meta?",
                "¡Iporã! Entró un ingreso importante. ¿Te animás a guardar un poquito?",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "¡Llegó la guita! 💸 Entró un buen ingreso. ¿Apartamos algo para las metas, campeón?",
                "¡Qué joya! Entró un ingreso importante. ¿Hacemos que rinda? 😄",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "¡Buenas noticias! Entró un buen ingreso. ¿Quieres apartar algo para tus metas? 🙂",
                "Se registró un ingreso importante. ¡Buen momento para ahorrar!",
            ),
        )
        val MILESTONE: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "¡{goal} llegó al {percent}%! 🎉 Aguyje por la constancia, che.",
                "¡{percent}% de {goal}! Ya se siente el tereré de la victoria. 🏆",
                "¡Meta {goal} al {percent}%! ¿Viste que sí se podía? 😄",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "¡{goal} llegó al {percent}%! 🎉 Sos un crack, che.",
                "¡{percent}% de {goal}! Esto ya casi está. ⚽",
                "¡{goal} al {percent}%! Posta que no hay nada que no puedas. 💪",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "¡{goal} llegó al {percent}%! 🎉 Tu constancia está dando frutos.",
                "¡{percent}% de {goal}! Vas por muy buen camino. 🙂",
            ),
        )
        val STREAK: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "¡{days} días anotando todo! Qué organizado, iporã. 📈",
                "{days} días seguidos sin perderte un guaraní. ¡Aguyje! 🌿",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "¡{days} días anotando todo! Un crack de la administración. 📈",
                "{days} días seguidos registrando. Ni el banco te lleva tan bien la cuenta. 😄",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "¡{days} días registrando todos tus movimientos! Excelente constancia. 📈",
            ),
        )
        val LOW_FUNDS: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "Ojo que venís justo con la plata: quedan {amount} libres estimados. ¿Vemos en qué se fue?",
                "El libre estimado está en {amount}… ¿te ayudo a revisar los gastos?",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "Che, el libre estimado quedó en {amount}. ¿Miramos qué podemos ajustar?",
                "Uh, venís justo: {amount} libres estimados. ¿Vemos los gastos del mes?",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "Atención: el libre estimado quedó en {amount}. ¿Quieres revisar los gastos del mes?",
            ),
        )
        /** Sin importe (montos apagados en los avisos). */
        val LOW_FUNDS_HIDDEN: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "Ojo que venís justo con la plata este mes. ¿Vemos en qué se fue?",
                "Se achicó el libre estimado del mes… ¿te ayudo a revisar los gastos?",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "Che, venís justo este mes. ¿Miramos qué podemos ajustar?",
                "Uh, se achicó lo libre del mes. ¿Vemos los gastos?",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "Atención: el libre estimado de este mes está bajo. ¿Quieres revisar los gastos?",
            ),
        )
        val SILENCE: Map<MonkiRegion, List<String>> = mapOf(
            MonkiRegion.PARAGUAY to listOf(
                "¿Mba'eichapa? Hace {days} días que no pasás por Monea… ¿todo bien? 🐵",
                "Te extrañé, che. Hace {days} días que no abrís la app. ¿Retomamos?",
            ),
            MonkiRegion.ARGENTINA to listOf(
                "¿Todo bien, che? Hace {days} días que no pasás por Monea… 🐵",
                "Te extrañé un montón. Hace {days} días que no abrís la app. ¿Retomamos?",
            ),
            MonkiRegion.NEUTRAL to listOf(
                "¿Todo bien? Hace {days} días que no abres Monea. ¿Quieres retomar? 🙂",
            ),
        )
    }
}
