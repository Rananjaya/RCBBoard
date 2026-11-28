// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.tools

import helium314.keyboard.latin.makedict.DictIO
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Not a test: compiles `.combined` word lists into bundled `.dict` files with the app's own encoder, so the
 * English dictionaries can be rebuilt without external tools (see tools/english-dictionaries/README.md).
 * Skipped unless RCBBOARD_COMPILE_DICTS lists "input.combined=output.dict" pairs separated by ";".
 */
@RunWith(RobolectricTestRunner::class) // the encoder logs
class CompileDictionaryTool {
    @Test fun compile() {
        val jobs = System.getenv("RCBBOARD_COMPILE_DICTS")
        assumeTrue("RCBBOARD_COMPILE_DICTS is not set", !jobs.isNullOrBlank())
        jobs!!.split(";").filter { it.isNotBlank() }.forEach { job ->
            val (input, output) = job.split("=", limit = 2)
            val dictionary = DictIO.readCombinedDictionary(input)
            DictIO.writeBinaryDictionary(output, dictionary)
            assertTrue(File(output).length() > 0, "nothing written to $output")
            println("compiled $input -> $output (${File(output).length()} bytes)")
        }
    }
}
