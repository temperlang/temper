package lang.temper.be.csharp

import lang.temper.be.Backend
import lang.temper.common.MimeType
import lang.temper.common.isNotEmpty
import lang.temper.log.dirPath
import lang.temper.log.filePath
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml

data class CsProj(
    val authors: String? = null,
    val description: String? = null,
    val internalsVisibleTo: List<String> = listOf(),
    val outputType: String? = null,
    val packageLicenseExpression: String? = null,
    val packageProjectUrl: String? = null,
    val packageReferences: List<PackageReference> = listOf(),
    val projectReferences: List<String> = listOf(),
    val rootNamespace: String? = null,
    val startupObject: String? = null,
    val version: String? = null,
) {
    private fun toXml(): String {
        val xmlText = xml("Project") {
            // TODO PackageId, PackageIcon, PackageLicenseFile if expression not OSI/FSF?
            // See https://learn.microsoft.com/en-us/dotnet/standard/library-guidance/nuget
            attribute("Sdk", "Microsoft.NET.Sdk")
            "PropertyGroup" {
                // Currently, dotnet warns if you request net5.0 or earlier.
                "TargetFramework" { -"net6.0" }
                // Core project info.
                outputType?.let { "OutputType" { -it } }
                rootNamespace?.let { "RootNamespace" { -it } }
                startupObject?.let { "StartupObject" { -it } }
                // On Version properties: https://stackoverflow.com/a/42183301/2748187 and comment with link to more
                this@CsProj.version?.let { "Version" { -it } }
                // Additional metadata.
                authors?.let { "Authors" { -it } }
                description?.let { "Description" { -it } }
                "IsPackable" { -"true" }
                packageLicenseExpression?.let { "PackageLicenseExpression" { -it } }
                packageProjectUrl?.let { "PackageProjectUrl" { -it } }
                // Extra settings.
                "Nullable" { -"enable" }
            }
            // TODO Combine item groups?
            if (internalsVisibleTo.isNotEmpty()) {
                "ItemGroup" {
                    for (friend in internalsVisibleTo) {
                        "InternalsVisibleTo" {
                            attribute("Include", friend)
                        }
                    }
                }
            }
            if (projectReferences.isNotEmpty()) {
                "ItemGroup" {
                    for (reference in projectReferences) {
                        "ProjectReference" {
                            attribute("Include", reference)
                        }
                    }
                }
            }
            if (packageReferences.isNotEmpty()) {
                "ItemGroup" {
                    for (reference in packageReferences) {
                        "PackageReference" {
                            attribute("Include", reference.name)
                            attribute("Version", reference.version)
                        }
                    }
                }
            }
        }.toString(PrintOptions(pretty = true, indent = "  ", singleLineTextElements = true))
        return xmlText
    }

    fun toFileSpec(
        baseDir: String? = null,
        nameSuffix: String = "",
    ): Backend.MetadataFileSpecification {
        val name = filePath("${rootNamespace ?: DEFAULT_CSPROJ_BASENAME}${nameSuffix}.csproj")
        val path = when (baseDir) {
            null -> name
            else -> dirPath(baseDir).resolve(name)
        }
        val content = toXml()
        return Backend.MetadataFileSpecification(path = path, mimeType = MimeType("text", "xml"), content = content)
    }
}
