package lang.temper.be.js

import lang.temper.be.MetadataKey
import lang.temper.log.FilePath
import lang.temper.name.BackendId

/** Namespace for metadata from [Dependencies\<JsBackend\>][lang.temper.be.Dependencies] */
abstract class JsMetadataKey<VALUE> : MetadataKey<JsBackend, VALUE>() {
    override val backendId: BackendId get() = JsBackend.Factory.backendId

    /**
     * The JavaScript library name as from the `"name"` property from the
     * translated library's `package.json`.
     */
    data object JsLibraryName : JsMetadataKey<String>()

    /** Path relative to the js/library-name output directory of the main index.js file. */
    data object MainPath : JsMetadataKey<FilePath>()
}
