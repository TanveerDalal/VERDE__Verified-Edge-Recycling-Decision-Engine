package com.verde.app.core

/**
 * Some messages are not about recycling at all: someone may be in danger.
 * Verde must never answer those with recycling advice, so this check runs
 * BEFORE Llama and before the built-in answers (Ask tab and WhatsApp).
 */
object Safety {

    private val phrases = listOf(
        "want to die", "wanna die", "kill myself", "killing myself", "suicide", "suicidal",
        "end my life", "end it all", "self harm", "self-harm", "harm myself", "hurt myself",
        "don't want to live", "dont want to live", "no reason to live", "better off dead"
    )

    const val REPLY =
        "I'm really sorry you're feeling like this. I'm only a recycling assistant, but you " +
        "deserve support from a real person right now. In the UK you can call Samaritans free " +
        "on 116 123 (any time, day or night), text SHOUT to 85258, or call NHS 111 and choose " +
        "the mental health option. If you are in immediate danger, call 999."

    /** The safety reply if [text] sounds like someone may be in danger, otherwise null. */
    fun reply(text: String): String? {
        val t = text.lowercase().replace('’', '\'')
        return if (phrases.any { it in t }) REPLY else null
    }
}
