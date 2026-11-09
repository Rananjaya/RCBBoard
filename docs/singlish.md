# Sinhala (Singlish)

The **Sinhala (Singlish)** keyboard lets you type Sinhala phonetically with the normal English QWERTY keys. While you type a word, the Sinhala text appears as the underlined composing word, and the Sinhala dictionary suggests words for it. Space, punctuation or a number ends the word.

Everything runs on the phone. Nothing is sent anywhere, and the Latin letters you type are kept only in memory until the word is finished.

## Status

The keyboard follows the rules below exactly and gives one result (phase 1, with the conjunct rules of phase 2A). Some everyday spellings map to a different letter than the one you mean:

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
| `y` | ය | after a consonant: yansaya ්‍ය, `vidhyaa` විද්‍යා (see [Conjuncts](#conjuncts-and-zwj)) |
| `r` | ර | after a consonant: rakaransaya ්‍ර, `kra` ක්‍ර, or with `u`/`uu` the signs ෘ/ෲ, `kruthi` කෘති; before a consonant: repaya ර්‍, `karma` කර්‍ම |
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
| `ksh` | ක්ෂ | `akshara` අක්ෂර, `kshaNa` ක්ෂණ (`kSh` gives the same) |

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
| `Y` | ය | after a consonant: yansaya ්‍ය, `vidhYaa` විද්‍යා; otherwise ය (same as `y`) |
| `R` | ර | after a consonant: always rakaransaya ්‍ර, also before `u`: `kRu` ක්‍රු; after a vowel and before a consonant: repaya ර්‍, `kaRma` කර්‍ම; otherwise ර |

## Conjuncts and ZWJ

Rakaransaya, yansaya and repaya are written in Unicode with hal kirīma (්, U+0DCA) and the zero width joiner **ZWJ (U+200D)**. Android draws the joined form only when the ZWJ is there; without it the hal is shown on its own (for example ක්ර instead of ක්‍ර). The Singlish keyboard therefore always types the ZWJ:

| Form | Type | Unicode sequence | Result |
| --- | --- | --- | --- |
| rakaransaya | consonant + `r` (or `R`) | consonant, U+0DCA, **U+200D**, U+0DBB ර | `kra` ක්‍ර, `prashnaya` ප්‍රශ්නය, `shrii` ශ්‍රී, `indhra` ඉන්ද්‍ර |
| yansaya | consonant + `y` (or `Y`) | consonant, U+0DCA, **U+200D**, U+0DBA ය | `vidhyaa` විද්‍යා, `anya` අන්‍ය |
| repaya | `r` (or `R`) after a vowel, before a consonant | U+0DBB ර, U+0DCA, **U+200D**, consonant | `karma` කර්‍ම, `kaarya` කාර්‍ය, `dhharmaya` ධර්‍මය |
| vocalic r | consonant + `ru` / `ruu` | consonant, U+0DD8 ෘ / U+0DF2 ෲ | `kruthi` කෘති, `gruha` ගෘහ, `kruu` කෲ |

* `R` keeps rakaransaya where `r` would give ෘ: `kRu` ක්‍රු.
* `r` at the end of a word or before a vowel is ර: `gedhar` ගෙදර්, `kuru` කුරු, `ruwan` රුවන්.
* Words with several conjuncts work the same way: `shriilaxkaava` ශ්‍රීලංකාව, `vidhyaarthhiyaa` විද්‍යාර්‍ථියා.
* The letters are the ones in the key table: `th` is dental ත and `sh` is ශ, so අර්‍ථය is typed `arthhaya` (`arthaya` gives අර්‍තය) and දෘෂ්ටි is typed `dhruShti` (`dhrushti` gives දෘශ්ටි).

**Dictionary compatibility.** The bundled word list stores rakaransaya and yansaya with ZWJ, but most repaya words without it (about 2,980 words such as කර්ම, against about 30 with ZWJ). For the Singlish keyboard only, suggestions therefore also look up the spelling without the repaya ZWJ, and every suggestion is shown and committed with the ZWJ (කර්ම in the list becomes කර්‍ම). The Sinhala and Wijesekara keyboards and the dictionary file are not changed.

**Not supported:** ඍ, ඎ, ෟ, ඏ and ඐ have no keys (the word list has 20, 0, 2, 0 and 0 words with them), nor do touching letters or ZWNJ. A plain hal before ර or ය (ක්ර, as in a few loanwords) can not be typed.

## Typing behaviour

* **Backspace** while composing removes the last Latin letter and converts the word again. With nothing composed it deletes text as usual.
* **Space** commits the Sinhala word. **Punctuation, digits and other keys** commit the word first and are then typed normally.
* **Shift** types the capital letters used in the table (Sinhala has no capitals, so the Singlish keyboard upcases its keys like English).
* **Suggestions** come from the bundled Sinhala dictionary for the composing Sinhala word. Picking one commits it; typing the next word adds the space.

## Mapping convention and references

The table is RCBBoard's own phonetic mapping. Each Sinhala letter is typed with the Latin letters that sound closest to it: doubled vowels give the long vowels, an added `h` gives the aspirated consonants, and capital letters give the retroflex and other less common letters. Two open-source Singlish keyboards were used as references while writing this table:

* Sithakuru, GPL-3.0-only: <https://codeberg.org/sinhala/sithakuru>
* Remeinium Singlish, Apache-2.0: <https://github.com/remeinium/singlish>

No code or table was copied from them. The RCBBoard table and engine (`latin/singlish/SinglishTransliterator.kt`) were written for RCBBoard and are GPL-3.0-only like the rest of RCBBoard. Where the references differ, RCBBoard chose:

* `th`/`dh` dental (ත ද) with `thh`/`dhh` for the aspirated forms (ථ ධ);
* `T`/`D` for the aspirated retroflex letters (ඨ ඪ), and no separate `Th`/`Dh`;
* `r`/`y` (and `R`/`Y`) after a consonant for rakaransaya/yansaya, `r`/`R` before a consonant for repaya, and `ru`/`ruu` after a consonant for ෘ/ෲ;
* no capital vowel shortcuts except `A`/`Aa`/`AA`.

The m17n input methods (`si-singlish.mim`, `si-trans.mim`, `si-samanala.mim`, `si-sumihiri.mim`) use other, older conventions and were not used.
