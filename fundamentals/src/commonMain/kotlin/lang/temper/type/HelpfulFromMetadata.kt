package lang.temper.type

import lang.temper.value.Helpful
import lang.temper.value.MetadataMultimap
import lang.temper.value.MetadataValueMultimap
import lang.temper.value.OccasionallyHelpful
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.docStringSymbol
import lang.temper.value.valueContained

fun helpfulFromMetadata(metadata: MetadataValueMultimap): OccasionallyHelpful? =
    helpfulFromMetadataValue(metadata[docStringSymbol]?.firstOrNull())

fun helpfulFromMetadata(metadata: MetadataMultimap): OccasionallyHelpful? =
    helpfulFromMetadataValue(metadata[docStringSymbol]?.firstOrNull()?.target?.valueContained)

fun helpfulFromMetadataValue(v: Value<*>?): OccasionallyHelpful? {
    val stringList = TList.unpackOrNull(v)
    if (stringList != null && stringList.size >= HELP_LIST_SIZE) {
        val (briefHelpValue, longHelpValue, contextValue) = stringList
        val briefHelpText = TString.unpackOrNull(briefHelpValue)
        val longHelpText = TString.unpackOrNull(longHelpValue)
        val contextText = TString.unpackOrNull(contextValue)
        if (briefHelpText != null && longHelpText != null && contextText != null) {
            return HelpfulFromMetadata(
                briefHelp = briefHelpText,
                longHelp = longHelpText,
                context = contextText,
            )
        }
    }
    return null
}

data class HelpfulFromMetadata(
    val briefHelp: String,
    val longHelp: String,
    val context: String,
) : Helpful {
    override fun briefHelp(): String = briefHelp

    override fun longHelp(): String = longHelp
}

private const val HELP_LIST_SIZE = 3
