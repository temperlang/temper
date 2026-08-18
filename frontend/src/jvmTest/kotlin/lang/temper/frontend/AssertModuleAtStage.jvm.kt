package lang.temper.frontend

import lang.temper.fs.Url
import lang.temper.fs.temperRoot
import java.nio.file.Path

private object FrontendResourcePlaceholder

private const val PACKAGE_PATH = "lang/temper/frontend"
private const val README_RELPATH = "stage-tests/README-stage-tests.md"

actual val stageTestDirFileRoot: Url by lazy {
    val readmeUri = FrontendResourcePlaceholder.javaClass
        .getResource("/$PACKAGE_PATH/$README_RELPATH")!!
        .toURI()!!
    readmeUri.resolve(".")
}

actual val stageTestDirFileSourceRoot: Url by lazy {
    val path = "$temperRoot/frontend/src/commonTest/resources/$PACKAGE_PATH/$README_RELPATH"
    Path.of(path).toUri().resolve(".")
}
