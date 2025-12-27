package lang.temper.be

import lang.temper.be.tmpl.TmpL

class BackendInfo<BACKEND : Backend<out BACKEND>>(val backend: BACKEND) {
    val libraryRoot
        get() = backend.libraryConfigurations.currentLibraryConfiguration.libraryRoot
    private var _tmpL: TmpL.ModuleSet? = null
    private var _outputFiles: List<Backend.OutputFileSpecification>? = null
    private var _keepFiles: List<Backend.MetadataFileSpecification>? = null

    var tmpL: TmpL.ModuleSet
        get() = _tmpL!!
        set(newValue) { this._tmpL = newValue }

    var outputFiles: List<Backend.OutputFileSpecification>
        get() = _outputFiles!!
        set(newValue) { this._outputFiles = newValue }

    var keepFiles: List<Backend.MetadataFileSpecification>
        get() = _keepFiles!!
        set(newValue) { this._keepFiles = newValue }
}
