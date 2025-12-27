package lang.temper.be.java

import xmlparser.XmlParser

internal actual fun loadTemperCoreDependency(): Dependency {
    val text = JavaBackend::class.java.classLoader.getResourceAsStream("lang/temper/be/java/temper-core/pom.xml")
    val element = XmlParser().fromXml(text)
    val groupId = element.findChildForName("groupId", null).text
    val artifactId = element.findChildForName("artifactId", null).text
    val version = element.findChildForName("version", null).text
    return Dependency(
        Java.SourceDirectory.MainJava,
        Artifact(groupId, artifactId, version),
    )
}
