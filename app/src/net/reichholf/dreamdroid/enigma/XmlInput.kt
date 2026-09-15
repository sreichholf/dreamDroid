package net.reichholf.dreamdroid.enigma

/**
 * Control-character stripping so Enigma2 XML parsers survive messy box payloads.
 * Mild path: strip C0 controls (keep whitespace), `\u008A` → newline, `&nbsp;` → space.
 * Aggressive path: `\p{C}` then `&nbsp;` → space.
 */
internal object XmlInput {
    private val aggressiveControl = Regex("\\p{C}")

    fun sanitize(input: String, aggressive: Boolean): String = if (aggressive) {
        aggressiveControl.replace(input, "").replace("&nbsp;", " ")
    } else {
        stripControlCharacters(input).replace("\u008A", "\n").replace("&nbsp;", " ")
    }

    private fun stripControlCharacters(s: String): String {
        val length = s.length
        val oldChars = CharArray(length + 1)
        s.toCharArray(oldChars, 0, 0, length)
        oldChars[length] = '\u0000'

        var newLen = 0
        while (true) {
            newLen++
            val ch = oldChars[newLen]
            if (!(ch > ' ' || Character.isWhitespace(ch))) {
                break
            }
        }
        for (j in newLen until length) {
            val ch = oldChars[j]
            if (ch > ' ' || Character.isWhitespace(ch)) {
                oldChars[newLen] = ch
                newLen++
            }
        }
        return String(oldChars, 0, newLen)
    }
}
