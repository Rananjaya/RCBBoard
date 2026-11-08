# Sinhala (Singlish)

The **Sinhala (Singlish)** keyboard lets you type Sinhala phonetically with the normal English QWERTY keys. While you type a word, the Sinhala text appears as the underlined composing word, and the Sinhala dictionary suggests words for it. Space, punctuation or a number ends the word.

Everything runs on the phone. Nothing is sent anywhere, and the Latin letters you type are kept only in memory until the word is finished.

## Phase 1 status

This is phase 1: the keyboard follows the rules below exactly and gives one result. Some everyday spellings map to a different letter than the one you mean:

| Intended word | Everyday spelling gives | Type instead |
| --- | --- | --- |
| ගෙදර | `gedara` → ගෙඩර | `gedhara` |
| කොහොමද | `kohomada` → කොහොමඩ | `kohomadha` |
| ආයුබෝවන් | `ayubowan` → අයුබොවන් | `aayuboowan` |
| සිංහල | `sinhala` → සින්හල | `sixhala` |

Phase 2 is planned to offer the intended word as a suggestion for such spellings.

## How it works

* A consonant takes the vowel that follows it as a vowel sign: `ka` ක, `kaa` කා, `ki` කි.
* A consonant that is not followed by a vowel gets hal kirīma (්): `k` ක්, `amma` අම්ම, `aayuboowan` ආයුබෝවන්.
* A vowel at the start of a word, or after another vowel, is the independent letter: `aa` ආ, `oyaa` ඔයා.
* The longest key sequence wins: `zdh` ඳ before `zd` ඬ; `dhh` ධ before `dh` ද before `d` ඩ.
* Keys are case-sensitive where the table lists a capital. A capital without its own entry types like the lowercase letter (`M` is `m`).
* Letters without an entry (`c`, `q`, a lone `z`), digits and punctuation are typed unchanged.
* The result is Unicode NFC (for example ේ and ෝ are single code points).

## Key table

Vowels: the first Sinhala column is the independent letter, the second the vowel sign after a consonant.

| Key | Letter | Sign | Example |
| --- | --- | --- | --- |
| `a` | අ | (inherent) | `ka` ක |
| `aa` | ආ | ා | `kaa` කා |
| `A` | ඇ | ැ | `kA` කැ |
| `Aa` | ඈ | ෑ | `kAa` කෑ |
| `AA` | ඈ | ෑ | `kAA` කෑ |
| `i` | ඉ | ි | `ki` කි |
| `ii` | ඊ | ී | `kii` කී |
| `u` | උ | ු | `ku` කු |
| `uu` | ඌ | ූ | `kuu` කූ |
| `e` | එ | ෙ | `ke` කෙ |
| `ee` | ඒ | ේ | `kee` කේ |
| `o` | ඔ | ො | `ko` කො |
| `oo` | ඕ | ෝ | `koo` කෝ |
| `ai` | ඓ | ෛ | `kai` කෛ |
| `au` | ඖ | ෞ | `kau` කෞ |

Consonants:

| Key | Letter | Note |
| --- | --- | --- |
| `k` | ක | |
| `kh` | ඛ | aspirated |
| `g` | ග | |
| `gh` | ඝ | aspirated |
| `ch` | ච | |
| `chh` | ඡ | aspirated |
| `j` | ජ | |
| `jh` | ඣ | aspirated |
| `t` | ට | retroflex |
| `T` | ඨ | retroflex, aspirated |
| `d` | ඩ | retroflex |
| `D` | ඪ | retroflex, aspirated |
| `N` | ණ | retroflex |
| `th` | ත | dental |
| `thh` | ථ | dental, aspirated |
| `dh` | ද | dental |
| `dhh` | ධ | dental, aspirated |
| `n` | න | |
| `p` | ප | |
| `ph` | ඵ | aspirated |
| `b` | බ | |
| `bh` | භ | aspirated |
| `m` | ම | |
| `y` | ය | |
| `r` | ර | before a consonant: ර් (as in the bundled word list, `karma` කර්ම) |
| `l` | ල | |
| `L` | ළ | retroflex |
| `v` | ව | |
| `w` | ව | |
| `s` | ස | |
| `sh` | ශ | |
| `S` | ෂ | retroflex |
| `Sh` | ෂ | retroflex |
| `h` | හ | |
| `f` | ෆ | |

Prenasalised (sanyaka) and other nasals:

| Key | Letter | Note |
| --- | --- | --- |
| `zg` | ඟ | `azgala` අඟල |
| `zj` | ඦ | |
| `zd` | ඬ | |
| `zdh` | ඳ | `hazdha` හඳ |
| `zb` | ඹ | |
| `B` | ඹ | `aBa` අඹ |
| `zk` | ඤ | |
| `zh` | ඥ | |
| `X` | ඞ | |

Signs and conjuncts:

| Key | Letter | Use |
| --- | --- | --- |
| `x` | ං | anusvara; after a consonant it keeps the inherent vowel: `sixhala` සිංහල |
| `H` | ඃ | visarga: `dhuHkha` දුඃඛ |
| `Y` | ය | after a consonant: yansaya ්‍ය, `vidhYaa` විද්‍යා; otherwise ය |
| `R` | ර | after a consonant: rakaransaya ්‍ර, `shRii` ශ්‍රී; after a vowel and before a consonant: repaya ර්‍, `kaRma` කර්‍ම; otherwise ර |

## Typing behaviour

* **Backspace** while composing removes the last Latin letter and converts the word again. With nothing composed it deletes text as usual.
* **Space** commits the Sinhala word. **Punctuation, digits and other keys** commit the word first and are then typed normally.
* **Shift** types the capital letters used in the table (Sinhala has no capitals, so the Singlish keyboard upcases its keys like English).
* **Suggestions** come from the bundled Sinhala dictionary for the composing Sinhala word. Picking one commits it; typing the next word adds the space.

## Mapping convention and references

The table follows the phonetic convention that most Sinhala users know from the Helakuru keyboard. Two open-source keyboards that implement the same convention were used as references while writing this table:

* Sithakuru, GPL-3.0-only: <https://codeberg.org/sinhala/sithakuru>
* Remeinium Singlish, Apache-2.0: <https://github.com/remeinium/singlish>

No code or table was copied from them, and nothing comes from the proprietary Helakuru app. The RCBBoard table and engine (`latin/singlish/SinglishTransliterator.kt`) were written for RCBBoard and are GPL-3.0-only like the rest of RCBBoard. Where the references differ, RCBBoard chose:

* `th`/`dh` dental (ත ද) with `thh`/`dhh` for the aspirated forms (ථ ධ);
* `T`/`D` for the aspirated retroflex letters (ඨ ඪ), and no separate `Th`/`Dh`;
* `R`/`Y` for rakaransaya/yansaya, and `R` before a consonant for repaya;
* no capital vowel shortcuts except `A`/`Aa`/`AA`.

The m17n input methods (`si-singlish.mim`, `si-trans.mim`, `si-samanala.mim`, `si-sumihiri.mim`) use other, older conventions and were not used.
