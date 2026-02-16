package lang.temper.result.junit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlCData
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

fun parseJunitResults(input: String?): JUnitResults {
    if (input.isNullOrBlank()) {
        return JUnitResults(emptyList())
    }
    // Sanitize risky chars already escaped in xml source.
    val inputSansEscapedRisks = input.replace(Regex("&#(x?)([0-9A-Fa-f]+);")) { match ->
        val isHex = match.groupValues[1] == "x"
        val code = match.groupValues[2].toInt(if (isHex) 16 else 10)
        when {
            isRiskyChar(code) -> sanitizeRiskyChar(code)
            else -> match.value
        }
    }
    // Sanitize raw risky chars that might even just be illegal. We don't control full test framework path.
    // TODO Also sanitize up front in our generated failure messaging? I'm mixed on that.
    val inputSansRawRisks = buildString(inputSansEscapedRisks.length) {
        for (char in inputSansEscapedRisks) {
            val code = char.code
            when {
                isRiskyChar(code) -> append(sanitizeRiskyChar(code))
                else -> append(char)
            }
        }
    }
    val results = xmlTolerant.decodeFromString<TestSuites>(inputSansRawRisks)
    return JUnitResults(suites = results.suites)
}

private fun isRiskyChar(code: Int): Boolean {
    // Apparently some of these are legal in XML 1.1, but might as well be aggressive.
    return (code < 0x20 && code != 0x09 && code != 0x0A && code != 0x0D) ||
        (code in 0x7F..0x84) || (code in 0x86..0x9F) || (code == 0xFFFF) || (code == 0xFFFE)
}

private fun sanitizeRiskyChar(code: Int) = "[0x${code.toString(16).uppercase()}]"

fun combineSurefireResults(input: Iterable<String>): String {
    val results = input.map { xmlTolerant.decodeFromString<TestSuite>(it) }
    return xmlTolerant.encodeToString(
        xmlTolerant.serializersModule.serializer(),
        TestSuites(results),
    )
}

@OptIn(ExperimentalXmlUtilApi::class)
private val xmlTolerant get() = XML {
    xmlDeclMode = XmlDeclMode.Auto
    policy = DefaultXmlSerializationPolicy {
        pedantic = false
        autoPolymorphic = true
        unknownChildHandler =
            UnknownChildHandler { _, _, _, _, _ -> emptyList() }
    }
}

data class JUnitResults(
    val suites: List<TestSuite>,
) {
    val testsRun: Int
        get() = suites.sumOf { it.tests }
    val failures: Set<FailureReport>
        get() = buildSet {
            // For errors get cdata because we don't have detail otherwise, but just get message for failures.
            for (suite in suites) {
                for (case in suite.testCases) {
                    val error = case.error
                    val failure = case.failure
                    if (error != null) {
                        add(FailureReport(case.name, error.cdata))
                    } else if (failure != null) {
                        add(FailureReport(case.name, failure.cause))
                    }
                }
            }
        }
}

/**
 * @param name combines both the class name and the test name.
 */
data class FailureReport(val name: String, val cause: String)

// These models are based on https://github.com/windyroad/JUnit-Schema/blob/master/JUnit.xsd but have been tweaked
// since 'JUnit' isn't a real spec just a set of conventions

// Notes on modeling the xml doc
// 1) you can ignore attributes in the XML, but need to set the unknownChildHandler in XML to do so
// 2) It can do some basic type conversions. At least string -> int
// 3) When you use @XmlSerialName (and it seems all the other annotations) it seems that the 'optional' namespace and
// prefix fields are required otherwise you get a horrendous kotlin error about failure to generate code
// 4) for the CDATA part refer to https://github.com/pdvrieze/xmlutil/commit/f81abcca5af414bf84a31af80381227ea7256494

@Serializable
@SerialName("failure")
data class FailureInfo(
    @XmlElement(false)
    val message: String? = null,
    @XmlElement(false)
    val type: String? = null,
    @XmlCData(true)
    @XmlValue(true)
    val cdata: String,
) {
    // Where luaunit uses type instead of message, so use that as a fallback,
    // and we do some conversion there but not full.
    val cause: String get() = message ?: type!!
}

@Serializable
@SerialName("testcase")
data class TestCase(
    @XmlElement(false)
    val name: String,
    // In seconds
    @XmlElement(false)
    val time: String,
    @XmlSerialName("classname", namespace = "", prefix = "")
    @XmlElement(false)
    val className: String,
    @XmlElement(true)
    @XmlSerialName(value = "error", namespace = "", prefix = "")
    val error: FailureInfo? = null,
    @XmlElement(true)
    @XmlSerialName(value = "failure", namespace = "", prefix = "")
    val failure: FailureInfo? = null,
)

@Serializable
@SerialName("testsuite")
data class TestSuite(
    @XmlElement(false)
    val name: String,
    @XmlElement(false)
    @XmlSerialName("timestamp", namespace = "", prefix = "")
    val timeStamp: String = "",
    @XmlElement(false)
    val tests: Int,
    @XmlElement(false)
    val failures: Int,
    @XmlElement(false)
    val time: String,
    val testCases: List<TestCase>,
)

@Serializable
@SerialName("testsuites")
data class TestSuites(
    val suites: List<TestSuite>,
    @XmlElement(false)
    val name: String = "",
    @XmlElement(false)
    val tests: Int = -1,
    @XmlElement(false)
    val failures: Int = -1,
    @XmlElement(false)
    val time: String = "",
) {
    fun toXml(): String = xmlTolerant.encodeToString(serializer(), this)
}
