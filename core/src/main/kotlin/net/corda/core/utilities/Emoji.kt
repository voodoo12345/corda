package net.corda.core.utilities

import net.corda.core.codePointsString

/**
 * A simple wrapper class that contains icons and support for printing them only when we're connected to a terminal.
 */
object Emoji {
    // Unfortunately only Apple has a terminal that can do colour emoji AND an emoji font installed by default.
    val hasEmojiTerminal by lazy { listOf("Apple_Terminal", "iTerm.app").contains(System.getenv("TERM_PROGRAM")) }

    @JvmStatic val CODE_SANTA_CLAUS: String = codePointsString(0x1F385)
    @JvmStatic val CODE_DIAMOND: String = codePointsString(0x1F537)
    @JvmStatic val CODE_BAG_OF_CASH: String = codePointsString(0x1F4B0)
    @JvmStatic val CODE_NEWSPAPER: String = codePointsString(0x1F4F0)
    @JvmStatic val CODE_RIGHT_ARROW: String = codePointsString(0x27A1, 0xFE0F)
    @JvmStatic val CODE_LEFT_ARROW: String = codePointsString(0x2B05, 0xFE0F)
    @JvmStatic val CODE_GREEN_TICK: String = codePointsString(0x2705)
    @JvmStatic val CODE_PAPERCLIP: String = codePointsString(0x1F4CE)
    @JvmStatic val CODE_COOL_GUY: String = codePointsString(0x1F60E)
    @JvmStatic val CODE_NO_ENTRY: String = codePointsString(0x1F6AB)
    @JvmStatic val CODE_SKULL_AND_CROSSBONES: String = codePointsString(0x2620)
    @JvmStatic val CODE_BOOKS: String = codePointsString(0x1F4DA)

    /**
     * When non-null, toString() methods are allowed to use emoji in the output as we're going to render them to a
     * sufficiently capable text surface.
     */
    val emojiMode = ThreadLocal<Any>()

    val santaClaus: String get() = if (emojiMode.get() != null) "$CODE_SANTA_CLAUS  " else ""
    val diamond: String get() = if (emojiMode.get() != null) "$CODE_DIAMOND  " else ""
    val bagOfCash: String get() = if (emojiMode.get() != null) "$CODE_BAG_OF_CASH  " else ""
    val newspaper: String get() = if (emojiMode.get() != null) "$CODE_NEWSPAPER  " else ""
    val rightArrow: String get() = if (emojiMode.get() != null) "$CODE_RIGHT_ARROW  " else ""
    val leftArrow: String get() = if (emojiMode.get() != null) "$CODE_LEFT_ARROW  " else ""
    val paperclip: String get() = if (emojiMode.get() != null) "$CODE_PAPERCLIP  " else ""
    val coolGuy: String get() = if (emojiMode.get() != null) "$CODE_COOL_GUY  " else ""
    val books: String get() = if (emojiMode.get() != null) "$CODE_BOOKS  " else ""

    inline fun <T> renderIfSupported(body: () -> T): T {
        emojiMode.set(this)   // Could be any object.
        try {
            return body()
        } finally {
            emojiMode.set(null)
        }
    }

    fun renderIfSupported(obj: Any): String {
        if (!hasEmojiTerminal)
            return obj.toString()

        if (emojiMode.get() != null)
            return obj.toString()

        emojiMode.set(this)   // Could be any object.
        try {
            return obj.toString()
        } finally {
            emojiMode.set(null)
        }
    }

}
