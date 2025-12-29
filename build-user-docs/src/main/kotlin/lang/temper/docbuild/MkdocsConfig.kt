package lang.temper.docbuild

import lang.temper.fs.Url
import lang.temper.fs.read
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment

/** Programmatic access to build-user-docs/skeletal-docs/temper-docs/mkdocs.yml */
internal object MkdocsConfig {
    val siteUrl: Url

    /** If [siteUrl] is "https://temperlang.dev/tld/", this is "tld/" */
    val absPathToDocRoot: FilePath

    init {
        val config = UserDocFilesAndDirectories.skeletalDocRoot.resolve("temper-docs").resolve("mkdocs.yml")
        val yamlContent = config.read()
        // TODO(mvs): Might be worth using a YAML parser if we need more from here.
        siteUrl = Url(Regex("^site_url:\\s*(\\S+)", RegexOption.MULTILINE).find(yamlContent)!!.groupValues[1])

        absPathToDocRoot = FilePath(
            siteUrl.path!!.split("/").filter { it.isNotEmpty() }.map { FilePathSegment(it) },
            isDir = true,
        )
    }
}
