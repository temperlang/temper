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
    val results = xmlTolerant.decodeFromString<TestSuites>(input)
    return JUnitResults(suites = results.suites)
}

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
    val message: String? = null,
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
data class TestCase(
    val name: String,
    // In seconds
    val time: String,
    @XmlSerialName("classname", namespace = "", prefix = "")
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
    val name: String,
    @XmlSerialName("timestamp", namespace = "", prefix = "")
    val timeStamp: String = "",
    val tests: Int,
    val failures: Int,
    val time: String,
    @XmlElement(true)
    @XmlSerialName(value = "testcase", namespace = "", prefix = "")
    val testCases: List<TestCase>,
)

@Serializable
@SerialName("testsuites")
private data class TestSuites(
    @XmlElement(true)
    @XmlSerialName(value = "testsuite", namespace = "", prefix = "")
    val suites: List<TestSuite>,
)
