package lang.temper.be.tmpl

import lang.temper.common.allMapToSameElseNull
import lang.temper.common.asciiTitleCase
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.log.unknownPos
import lang.temper.name.ModuleLocation
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Descriptor
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.withType
import lang.temper.value.CoverFunction
import lang.temper.value.MetadataMap
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.StayLeaf
import lang.temper.value.TFunction
import lang.temper.value.TType
import lang.temper.value.TypeInferences
import lang.temper.value.Value

internal class ConstantPool(
    val selfLoc: ModuleLocation,
    val sharedNameTables: SharedNameTables,
    val topLevels: MutableList<TmpL.TopLevel>,
) {
    internal class SharedNameTables(
        val declarationMetadataForName: Map<ResolvedName, Map<Symbol, Value<*>>>,
        val typeInferencesForName: Map<ResolvedName, TypeInferences>,

        /**
         * Help avoid multiply translating structures associated with a declaration and inlined
         * into values during partial evaluation.
         */
        val stayToName: MutableMap<StayLeaf, ResolvedName>,
    ) {
        val pooledToName = mutableMapOf<Poolable, ResolvedName>()
        val revConstantPool = mutableMapOf<ResolvedName, Poolable>()
    }

    @Suppress("LateinitUsage") // Yeah, but breaks a cycle and creation is in one place.
    internal lateinit var translator: TmpLTranslator
    private val pooledToName = sharedNameTables.pooledToName
    private val revConstantPool = sharedNameTables.revConstantPool
    private val stayToName = sharedNameTables.stayToName

    private fun unusedName(baseName: ParsedName) = translator.unusedName(baseName)

    fun refOrNull(pos: Position, poolable: Poolable, type: Type2): TmpL.Reference? {
        val poolableRefName = pooledToName[poolable] ?: return null
        return TmpL.Reference(TmpL.Id(pos, poolableRefName, null), type)
    }

    fun fnRefOrNull(pos: Position, poolable: Poolable, sig: Signature2): TmpL.FnReference? {
        val poolableRefName = pooledToName[poolable] ?: return null
        return TmpL.FnReference(TmpL.Id(pos, poolableRefName, null), sig)
    }

    fun valueNeedsPooling(pooledValue: PooledValue) =
        alternatePoolable(unknownPos, pooledValue) != null

    private fun pooled(supportCode: SupportCode): PooledSupportCode = PooledSupportCode(
        supportCode,
        translator.moduleIndex,
    )

    private fun alternatePoolable(pos: Position, poolable: Poolable) = when (poolable) {
        is PooledValue -> {
            val value = poolable.value
            val fn = TFunction.unpackOrNull(value)
            if (fn != null) {
                translator.supportNetwork.getSupportCode(pos, fn, Genre.Library)?.let { pooled(it) }
            } else {
                null
            }
        }
        is PooledSupportCode -> null
    }

    fun fillIfAbsent(
        pos: Position,
        supportCode: SupportCode,
        desc: Descriptor,
        metadata: MetadataMap,
        suggestedName: ResolvedName? = null,
    ): ResolvedName = fillIfAbsent(
        pos = pos,
        poolable = pooled(supportCode),
        desc = desc,
        metadata = metadata,
        suggestedName = suggestedName,
    )

    fun fillIfAbsent(
        pos: Position,
        poolable: Poolable,
        desc: Descriptor,
        metadata: MetadataMap,
        suggestedName: ResolvedName? = null,
    ): ResolvedName {
        val previouslyFilled = pooledToName[poolable]
        if (previouslyFilled != null) {
            return previouslyFilled
        }

        // If there is an equivalent, but simpler, poolable, use that instead and treat poolable
        // as an alias for that.
        val alternate: Poolable? = alternatePoolable(pos, poolable)
        if (alternate != null) {
            val nameInPool = fillIfAbsent(
                pos,
                alternate,
                desc = desc,
                metadata = metadata,
                suggestedName = suggestedName,
            )
            pooledToName[poolable] = nameInPool
            // revConstantPool[nameInPool] points to alternate
            return nameInPool
        }

        val nameInPool = suggestedName
            ?: run {
                val baseName = baseNameFor(poolable)
                unusedName(baseName)
            }

        // Associate poolable with its name early so that, e.g., a pooled function can recursively
        // reference itself.
        pooledToName[poolable] = nameInPool
        revConstantPool[nameInPool] = poolable

        // Actually generate a definition
        var definition = when (poolable) {
            is PooledSupportCode -> {
                val sig = when (desc) {
                    is Signature2 -> desc
                    is Type2 -> withType(
                        desc,
                        fn = { _, sig, _ -> sig },
                        fallback = { invalidSig },
                    )
                }
                fillSupportCode(
                    pos, poolable, nameInPool, desc = sig,
                    metadata = metadata,
                )
            }
            is PooledValue ->
                fillValue(pos, poolable, nameInPool, metadata = metadata)
        }
        if (definition is TmpL.Garbage) {
            val garbage = definition as TmpL.Garbage
            // We do need to declare the name so that the backend doesn't try to
            // import something that doesn't exist.
            definition = TmpL.ModuleLevelDeclaration(
                definition.pos,
                metadata = emptyList(),
                name = TmpL.Id(definition.pos.leftEdge, nameInPool, null),
                type = translator.translateType(definition.pos.leftEdge, WellKnownTypes.invalidType2).aType,
                init = TmpL.GarbageExpression(
                    garbage.pos,
                    garbage.diagnostic?.deepCopy(),
                ),
                assignOnce = true,
                descriptor = WellKnownTypes.invalidType2,
            )
        }
        topLevels.add(definition)

        return nameInPool
    }

    internal fun nameFor(poolable: Poolable): ResolvedName? = pooledToName[poolable]

    private fun fillValue(
        pos: Position,
        pooledValue: PooledValue,
        name: ResolvedName,
        metadata: MetadataMap,
    ): TmpL.TopLevel = translator.translateValueForPool(
        pos = pos,
        nameInPool = name,
        pooledValue = pooledValue,
        metadata = metadata,
    )

    private fun fillSupportCode(
        pos: Position,
        supportCode: PooledSupportCode,
        name: ResolvedName,
        desc: Signature2,
        metadata: MetadataMap,
    ): TmpL.SupportCodeDeclaration = TmpL.SupportCodeDeclaration(
        pos = pos,
        metadata = translator.translateDeclarationMetadata(metadata),
        name = TmpL.Id(pos, name),
        init = translator.wrapSupportCode(pos, supportCode.supportCode, desc),
        descriptor = desc,
    )

    internal fun poolRequirements(pos: Position, supportCode: SupportCode) {
        for (req in supportCode.requires) {
            when (req) {
                is OtherSupportCodeRequirement ->
                    fillIfAbsent(pos, pooled(req.required), req.type, req.metadataMap)
                is LibrarySupportCodeRequirement -> {}
            }
        }
    }

    internal fun associateSupportCode(d: TmpL.SupportCodeDeclaration) {
        val supportCode = pooled(d.init.supportCode)
        val nameInPool = d.name.name
        check(supportCode !in pooledToName) { supportCode }
        pooledToName[supportCode] = nameInPool
        revConstantPool[nameInPool] = supportCode
    }

    fun getSupportCodeReferenceForName(name: ResolvedName): SupportCode? =
        (revConstantPool[name] as? PooledSupportCode)?.supportCode

    fun associateWithStay(stay: StayLeaf, name: ResolvedName) {
        stayToName[stay] = name
    }
    fun nameForStay(stay: StayLeaf) = stayToName[stay]

    fun backendMightNeed(pos: Position, optionalSupportCodeKind: OptionalSupportCodeKind) {
        val supportCodeAndType =
            translator.supportNetwork.optionalSupportCode(optionalSupportCodeKind)
        if (supportCodeAndType != null) {
            val (supportCode, type) = supportCodeAndType
            fillIfAbsent(
                pos = pos,
                poolable = pooled(supportCode),
                metadata = emptyMap(),
                desc = type,
            )
        }
    }
}

internal fun baseNameFor(poolable: Poolable): ParsedName = when (poolable) {
    is PooledValue ->
        when (poolable.value.typeTag) {
            TFunction -> when (val funOrNull = TFunction.unpack(poolable.value)) {
                // It helps to be able to distinguish these from other functions
                is NamedBuiltinFun -> ParsedName("f${funOrNull.name.asciiTitleCase()}")
                is CoverFunction -> {
                    // If it's a cover of builtins with the same name, use that.
                    val commonName = funOrNull.covered.allMapToSameElseNull {
                        (it as? NamedBuiltinFun)?.name
                    }
                    commonName?.let { ParsedName("f${it.asciiTitleCase()}") }
                }

                else -> null // Fallback to generic value of type indicator below
            }
            TType -> ParsedName(
                "vReifiedType${
                    TType.unpack(poolable.value).builtinTypeName?.builtinKey?.asciiTitleCase()
                        ?: ""
                }",
            )
            else -> null // Fallback to generic value of type indicator below
        }
            ?: ParsedName("v${poolable.value.typeTag.name.toString().asciiTitleCase()}")
    is PooledSupportCode -> when (val supportCode = poolable.supportCode) {
        is NamedSupportCode -> supportCode.baseName
        is SeparatelyCompiledSupportCode -> supportCode.stableKey
        else -> null
    } ?: ParsedName("supportCode")
}
