// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.text.InputType
import helium314.keyboard.keyboard.emoji.EmojiRow
import helium314.keyboard.latin.settings.Defaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmojiRowTest {
    @Test fun `emoji row is off by default`() {
        assertEquals(false, Defaults.PREF_SHOW_EMOJI_ROW)
    }

    @Test fun `without recent emoji the row shows the defaults`() {
        assertEquals(EmojiRow.DEFAULT_EMOJIS, EmojiRow.keys(emptyList()))
        assertEquals(EmojiRow.KEY_COUNT, EmojiRow.DEFAULT_EMOJIS.size)
    }

    @Test fun `recent emoji come first, without duplicates`() {
        val keys = EmojiRow.keys(listOf("🍕", "👍", "🍕", " "))
        assertEquals(listOf("🍕", "👍", "😂", "❤️"), keys.take(4))
        assertEquals(EmojiRow.KEY_COUNT, keys.size)
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test fun `many recent emoji fill the whole row`() {
        val recents = listOf("🍕", "🚗", "⚽", "🎵", "🌧️", "📚", "☕", "🐶", "🌸", "✈️", "🍎")
        assertEquals(recents.take(EmojiRow.KEY_COUNT), EmojiRow.keys(recents))
    }

    @Test fun `no row when turned off or in password fields`() {
        val text = InputType.TYPE_CLASS_TEXT
        val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertNull(EmojiRow.keysFor(false, text) { emptyList() })
        assertNull(EmojiRow.keysFor(true, password) { emptyList() })
        assertEquals(EmojiRow.DEFAULT_EMOJIS, EmojiRow.keysFor(true, text) { emptyList() })
    }

    @Test fun `unreadable recent emoji fall back to the defaults`() {
        assertEquals(EmojiRow.DEFAULT_EMOJIS, EmojiRow.keysFor(true, InputType.TYPE_CLASS_TEXT) { error("broken prefs") })
    }

    @Test fun `typed emoji become recent emoji, but not in incognito mode or for other text`() {
        val added = mutableListOf<String>()
        val isEmoji = { s: String -> s in setOf("🍕", "❤️") }
        EmojiRow.recordTyped("🍕", incognito = false, isEmoji = isEmoji) { added.add(it) }
        EmojiRow.recordTyped("❤️", incognito = true, isEmoji = isEmoji) { added.add(it) }
        EmojiRow.recordTyped("a", incognito = false, isEmoji = isEmoji) { added.add(it) }
        EmojiRow.recordTyped(null, incognito = false, isEmoji = isEmoji) { added.add(it) }
        assertEquals(listOf("🍕"), added)
    }
}
