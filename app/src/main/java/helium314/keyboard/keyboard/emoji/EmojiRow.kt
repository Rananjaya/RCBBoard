// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import helium314.keyboard.latin.utils.InputTypeUtils

/**
 * RCBBoard emoji row: a row of emoji above the letters and symbols (Preferences > Emoji row).
 * It shows the most recently used emoji, filled up with popular ones, and is never shown in password fields.
 */
object EmojiRow {
    const val KEY_COUNT = 10

    /** Shown when fewer than [KEY_COUNT] emoji were used recently, in this order. */
    val DEFAULT_EMOJIS = listOf("😂", "❤️", "👍", "😊", "🙏", "😍", "😭", "🔥", "🎉", "😘")

    /** Recent emoji first (most recent first), then the defaults, without duplicates. */
    fun keys(recents: List<String>, count: Int = KEY_COUNT): List<String> =
        (recents.filter { it.isNotBlank() } + DEFAULT_EMOJIS).distinct().take(count)

    /** The emoji of the row, or null if there is no row for this setting and input field. */
    fun keysFor(enabled: Boolean, inputType: Int, recents: () -> List<String>): List<String>? =
        if (!enabled || InputTypeUtils.isAnyPasswordInputType(inputType)) null
        else keys(runCatching(recents).getOrDefault(emptyList()))

    /**
     * Emoji typed with keys (from the emoji row, or emoji keys in a layout) count as recently used, so the row
     * shows the user's own emoji. Not in incognito mode, and only for a single emoji, never for other text.
     * The row shows the change the next time the keyboard is shown, so keys don't move while typing.
     */
    fun recordTyped(text: String?, incognito: Boolean, isEmoji: (String) -> Boolean, add: (String) -> Unit) {
        if (incognito || text.isNullOrEmpty() || !isEmoji(text)) return
        add(text)
    }
}
