@file:Suppress("UNUSED_PARAMETER")

package lang.temper.be.py

import lang.temper.be.names.DescriptorChain
import lang.temper.be.names.LookupNameVisitor
import lang.temper.be.names.NameSelection
import lang.temper.be.names.idKind
import lang.temper.be.names.idReach
import lang.temper.be.py.PyIdentifierGrammar.safeIdent
import lang.temper.be.tmpl.SupportCode
import lang.temper.be.tmpl.TmpL
import lang.temper.common.sprintf
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ModularName
import lang.temper.name.ModuleName
import lang.temper.name.OutName
import lang.temper.name.QName
import lang.temper.name.ResolvedName
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.name.identifiers.IdentStyle
import lang.temper.type.WellKnownTypes

class PyNames(visit: LookupNameVisitor?, private val abbreviated: Boolean = false) {
    private val nameExclusion = pyReservedWordsAndNames.toMutableSet()
    private var nameCounter = 0
    private val supportCodeMap = mutableMapOf<SupportCode, OutName>()
    private val nameInfo = mutableMapOf<Pair<ModuleName, ResolvedName>, PyInfo>()
    private val pendingImports = mutableMapOf<Pair<ModuleName, ResolvedName>, (PyIdentifierName) -> Unit>()
    private val importedNames = mutableMapOf<Pair<ModuleName, ResolvedName>, OutName>()
    private var moduleName: ModuleName? = null
    var module: ModuleName
        get() = moduleName!!
        set(value) {
            moduleName = value
        }

    init {
        if (visit != null) {
            acceptNames(visit)
        }
    }

    private fun acceptNames(visitor: LookupNameVisitor) {
        assert(nameInfo.isEmpty())
        val lookup = visitor.toLookup()
        visitor.nameData { module, name, context, qName, cs ->
            fun findLocal(module: ModuleName, name: ResolvedName): Pair<ResolvedName, DescriptorChain?> {
                if (context == null) {
                    val local = lookup.lookupLocalName(module, name)
                    if (local != null) {
                        val decl = lookup.lookupDeclDescriptor(module, local)
                        if (decl != null) {
                            return local to decl
                        }
                    }
                }
                return name to context
            }
            val (localName, descriptor) = findLocal(module, name)
            val kind = descriptor?.idKind() ?: TmpL.IdKind.Value
            val reach = descriptor?.idReach(ignoreImport = true) ?: TmpL.IdReach.Internal
            val outName = pythonizeName(localName, kind, reach)
            nameInfo[module to name] = PyInfo(
                outName = outName,
                isDeclaredTopLevel = descriptor?.node is TmpL.TopLevelDeclaration,
                kind = kind,
                reach = reach,
                qName = qName,
                importedName = null,
            )
        }
        visitor.supportCodeData { name, descriptor, qName, _ ->
            val kind = descriptor?.idKind() ?: TmpL.IdKind.Value
            val reach = descriptor?.idReach(ignoreImport = true) ?: TmpL.IdReach.Internal
            val outName = pythonizeName(name, kind, reach)
            val info = PyInfo(
                outName = outName,
                isDeclaredTopLevel = descriptor?.node is TmpL.TopLevelDeclaration,
                kind = kind,
                reach = reach,
                qName = qName,
                importedName = null,
            )
            lookup.allModules.forEach { module ->
                nameInfo[module to name] = info
            }
        }
        visitor.importData { moduleName, externalName, localName ->
            val kind = lookup.lookupDeclDescriptor(moduleName, localName)?.idKind() ?: TmpL.IdKind.Value
            val localInfo = nameInfo[moduleName to localName]
            val externalKey = moduleName to externalName
            if (localInfo != null && externalKey !in nameInfo) {
                nameInfo[externalKey] = localInfo
            }
            importedNames[externalKey] = pythonizeName(externalName, kind, TmpL.IdReach.External)
        }

        // For each nameInfo where the key is an exported name, prefer the NameInfo
        // from the original source.
        val keysForModularNames = nameInfo.keys.mapNotNull { (mn, rn) ->
            if (rn is ModularName) {
                mn to rn
            } else {
                null
            }
        }
        for (key in keysForModularNames) {
            val (moduleName, modularName) = key
            val originModuleName = modularName.origin.loc as? ModuleName
                ?: continue
            if (originModuleName != moduleName) {
                val altKey = originModuleName to modularName
                val altInfo = nameInfo[altKey]
                if (altInfo != null) {
                    nameInfo[key] = altInfo.copy()
                }
            }
        }
    }

    fun selectedNames() = nameInfo.values.mapNotNull { info ->
        if (info.qName != null) {
            NameSelection(info.qName, info.outName.outputNameText)
        } else {
            null
        }
    }

    fun isDeclaredTopLevel(name: ResolvedName): Boolean = nameInfo[module to name]?.isDeclaredTopLevel != false

    private fun pythonizeName(name: ResolvedName, kind: TmpL.IdKind, reach: TmpL.IdReach) =
        when (name) {
            is Temporary -> chooseSourceName(name, name.nameHint, name.uid, kind, reach)
            is SourceName -> chooseSourceName(name, name.baseName.nameText, name.uid, kind, reach)
            is BuiltinName -> OutName(styleName(name.builtinKey, kind, reach), sourceName = name)
            is ExportedName -> {
                val styledName = styleName(toSafePrefix(name), kind, reach)
                val safeName = avoidReserved(styledName)
                OutName(safeName, sourceName = name)
            } // always external
        }

    private fun concatIfVerbose(prefix: String, suffix: String) =
        if (abbreviated) prefix else prefix + suffix

    /**
     * Note cached at top level because sometimes we're in different kind contexts, such as named parameter vs usage of
     * the renamed back to [SourceName] version of that parameter.
     */
    private fun chooseSourceName(
        name: ResolvedName,
        prefix: String,
        uid: Int,
        kind: TmpL.IdKind,
        reach: TmpL.IdReach,
    ): OutName {
        val styledName = styleName(safeIdent(prefix), kind, reach)
        val safeName = when (reach) {
            TmpL.IdReach.Internal -> concatIfVerbose(styledName, "_$uid") // numeric suffix, won't be a keyword
            TmpL.IdReach.External -> avoidReserved(styledName)
        }
        return OutName(safeName, sourceName = name)
    }

    private fun styleName(
        name: String,
        kind: TmpL.IdKind,
        reach: TmpL.IdReach,
    ): String = when (kind) {
        TmpL.IdKind.Value -> IdentStyle.Camel.convertTo(IdentStyle.Snake, name)
        TmpL.IdKind.Type -> name // Already matches Temper style for types.
    }

    /** A name that has not been returned by a previous call to this name generator. */
    fun unusedName(formatString: String): String {
        require("%d" in formatString)
        if (abbreviated) {
            return formatString.replace("%d", "")
        }
        val unused = sprintf(formatString, listOf(nameCounter++))
        require(unused !in nameExclusion)
        nameExclusion.add(unused)
        return unused
    }

    fun name(
        name: ResolvedName,
    ): OutName {
        val key = module to name
        val info = nameInfo[key]
        val pendingImport = pendingImports[key]
        // If we have a pending import, we know it hasn't been folded in because its PyInfo
        // record has no entry for it.
        // If so, pick a local name and fold the imported info into the record.
        // Either way, its outName is the one to use.
        if (info != null) {
            return if (info.importedName == null && pendingImport != null) {
                val localName = pythonizeName(name, info.kind, info.reach)
                importName(name, info.outName)
                pendingImport(toPin(localName))
                localName
            } else {
                info.outName
            }
        }
        // fallback
        return pythonizeName(name, TmpL.IdKind.Value, TmpL.IdReach.External)
    }

    fun name(
        id: TmpL.Id,
    ): OutName = name(id.name)

    fun importedName(
        name: ResolvedName,
    ): OutName = importedNames[module to name]
        // For as-needed imports, the imported name is stored with the modified PyInfo record
        ?: nameInfo[module to name]?.importedName
        ?: pythonizeName(name, TmpL.IdKind.Value, TmpL.IdReach.External)

    fun importName(
        externalName: ResolvedName,
        localName: OutName,
    ) {
        val key = module to externalName
        nameInfo[key]?.let { info ->
            if (info.importedName == null) {
                nameInfo[key] = info.copy(importedName = localName)
            }
        }
    }

    fun importNameAsNeeded(
        externalName: TmpL.Id,
        whenImported: (PyIdentifierName) -> Unit,
    ) {
        pendingImports[module to externalName.name] = whenImported
    }

    /**
     * Rename optional arguments as `_argument_name` so they can be reassigned. This
     * is safe in a function declaration as arguments aren't seen outside it.
     */
    fun privatizeName(name: ResolvedName): Pair<OutName, OutName> {
        val info = nameInfo[module to name]!!
        val publicName = info.outName
        val privateName = OutName("_" + publicName.outputNameText, publicName.sourceName)
        nameInfo[module to name] = info.copy(outName = privateName)
        return publicName to privateName
    }

    fun testName(name: TmpL.Id) = pyIdent(name.pos, "${PyBackend.TEST_FUNCTION_PREFIX}${name.name.rawDiagnostic}")

    fun supportCodeName(code: SupportCode): OutName = supportCodeMap.getOrPut(code) {
        if (code is PySupportCode) {
            OutName(unusedName("${code.baseName.nameText}%d"), sourceName = code.baseName)
        } else {
            throw IllegalArgumentException("Unrecognized $code")
        }
    }

    /**
     * An sprintf format string suitable for [unusedName] that is like the input name but, if
     * later suffixed with ASCII digits, matches [PyIdentifierGrammar].
     */
    private fun toSafePrefix(name: ResolvedName): String = safeIdent(name.prefix().trim('_'))
}

fun ResolvedName.prefix(): String = when (this) {
    is BuiltinName -> this.builtinKey
    is Temporary -> this.nameHint
    is SourceName -> this.baseName.nameText
    is ExportedName -> this.baseName.nameText
}

fun OutName.asRName(pos: Position): Py.Expr = this.asPyName(pos)
fun OutName.asPyName(pos: Position): Py.Name =
    Py.Name(pos, id = toPin(this), this.sourceName as? TemperName)
fun OutName.asPyId(pos: Position): Py.Identifier =
    Py.Identifier(pos, id = toPin(this), this.sourceName as? TemperName)

/** Render a stringified name for use in annotations. */
fun OutName.asStr(pos: Position): Py.Expr = Py.Str(pos, this.outputNameText)

fun Py.Identifier.asName() = Py.Name(pos, id, sourceIdentifier)

private val specials = Regex("[#.]+")
private fun toPin(outName: OutName) = PyIdentifierName(avoidReserved(specials.replace(outName.outputNameText, "_")))

internal fun avoidReserved(name: String) =
    if (name in pyReservedWords) {
        "${name}_" // pep8-recommended for avoiding keywords
    } else {
        name
    }

private data class PyInfo(
    val outName: OutName,
    val isDeclaredTopLevel: Boolean,
    val kind: TmpL.IdKind,
    val reach: TmpL.IdReach,
    val qName: QName?,
    val importedName: OutName?,
)

val typeDefsToPyExpr: Map<Symbol, PyTranslator.(Position) -> Py.Expr> = mapOf(
    WellKnownTypes.voidTypeDefinition.word!! to { PyConstant.None.at(it) },
    WellKnownTypes.nullTypeDefinition.word!! to { PyConstant.None.at(it) },
)

val typeDefsToSepCode: Map<Symbol, PySeparateCode> = mapOf(
    WellKnownTypes.intTypeDefinition.word!! to IntType,
    WellKnownTypes.float64TypeDefinition.word!! to FloatType,
    WellKnownTypes.stringTypeDefinition.word!! to StrType,
    WellKnownTypes.stringIndexTypeDefinition.word!! to IntType,
    WellKnownTypes.stringIndexOptionTypeDefinition.word!! to IntType,
    WellKnownTypes.noStringIndexTypeDefinition.word!! to IntType,
    WellKnownTypes.booleanTypeDefinition.word!! to BoolType,
    WellKnownTypes.mapBuilderTypeDefinition.word!! to TypingDict,
    WellKnownTypes.mapTypeDefinition.word!! to TypingDict,
    WellKnownTypes.mappedTypeDefinition.word!! to MappedType,
    WellKnownTypes.pairTypeDefinition.word!! to PairType,
    WellKnownTypes.anyValueTypeDefinition.word!! to AnyType,
    WellKnownTypes.typeTypeDefinition.word!! to TypeType,
    WellKnownTypes.listBuilderTypeDefinition.word!! to MutableSequenceType,
    WellKnownTypes.listedTypeDefinition.word!! to SequenceType,
    WellKnownTypes.listTypeDefinition.word!! to SequenceType,
    Symbol("Console") to LoggingConsole,
    Symbol("DenseBitVector") to DenseBitVector,
    Symbol("Deque") to Deque,
)
