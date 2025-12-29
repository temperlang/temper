package lang.temper.docgen

import java.io.File
import java.util.Scanner

interface DocumentParser<TDoc, TFragment : Fragment, TCodeFragment : CodeFragment<TCodeFragment>> {
    fun parse(input: String): Document<TDoc, TFragment, TCodeFragment>

    fun parse(readable: Readable): Document<TDoc, TFragment, TCodeFragment> {
        val scanner = Scanner(readable)

        return parse(scanner.useDelimiter("\\Z").next())
    }

    fun canParse(file: File): Boolean
}
