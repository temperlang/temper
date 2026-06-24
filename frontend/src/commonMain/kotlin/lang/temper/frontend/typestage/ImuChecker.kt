package lang.temper.frontend.typestage

import lang.temper.common.Log
import lang.temper.log.LeveledMessageTemplate
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.Abstractness
import lang.temper.type.MemberShape
import lang.temper.type.PropertyShape
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.SuperTypeTree2
import lang.temper.type2.Type2
import lang.temper.type2.TypeParamRef
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.DeclTree
import lang.temper.value.Tree
import lang.temper.value.imuSymbol
import lang.temper.value.partialImuSymbol
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.typeSymbol
import lang.temper.value.varSymbol

enum class ImuMessage(
    override val formatString: String,
) : LeveledMessageTemplate {
    ImuTypeHasVar("Class %s claims %s but has a `var` property, %s"),
    ImuClassPropertyIsNotImu(
        "Class %s claims %s but property %s has type %s which is not imu",
    ),
    ImuClassPropertyUsesContravariantType(
        "Class %s claims %s but property %s: %s uses contravariant type %s",
    ),
    PartialImuTypeHasNoTypeParameters(
        "Type %s claims partialImu but has no type parameters",
    ),
    ExpectedImuType("Expected imu type but got %s"),
    DeepPartialImuTypeDoesNotImplySuperTypeImu(
        "PartialImu interface %s's type parameter <%s> would not be imu" +
            " when cast to its effectively Imu super-type %s because %s is not imu",
    ),
    DeepPartialImuTypeCanNeverBeImu(
        "PartialImu interface %s could have imu parameters but it extends" +
            " %s which cannot be imu because %s is not",
    ),
    ;

    override val suggestedLevel: Log.Level = Log.Error
}

class ImuChecker(
    private val logSink: LogSink,
) {
    private val superTypesCache = mutableMapOf<Type2, SuperTypeTree2<Type2>>()

    fun check(tree: Tree) {
        for (child in tree.children) {
            if (child is DeclTree) {
                child.parts!!.metadataSymbolMap[typeDeclSymbol]?.target
                    ?.typeShapeAtLeafOrNull?.let { typeShape ->
                        check(typeShape)
                    }
            }
        }
    }

    fun check(typeShapesToCheck: Iterable<TypeShape>): Pair<Set<TypeShape>, Set<TypeShape>> {
        val passing = mutableSetOf<TypeShape>()
        val failing = mutableSetOf<TypeShape>()
        for (typeShape in typeShapesToCheck) {
            val passed = check(typeShape)
            if (passed) {
                passing.add(typeShape)
            } else {
                failing.add(typeShape)
            }
        }
        return passing.toSet() to failing.toSet()
    }

    fun check(typeShape: TypeShape): Boolean {
        val type = MkType2(typeShape).actuals(typeShape.formals.map { MkType2(it).get() }).get()
        val supers = getSuperTypeTree(type)
        return when (typeShape) {
            // Special-case some core types.
            WellKnownTypes.listTypeDefinition,
            WellKnownTypes.mapTypeDefinition,
            -> true

            // Check others that claim something.
            else if supers.hasSymbol(imuSymbol) ->
                checkImu(typeShape, typeShape.diagnosticTypeName)
            else if supers.hasSymbol(partialImuSymbol) ->
                checkPartialImu(typeShape, typeShape.diagnosticTypeName)

            // No claim to check.
            else -> true
        }
    }

    private fun checkImu(typeShape: TypeShape, typeName: TemperName): Boolean {
        return checkBackedProperties(typeShape, typeName, imuSymbol)
    }

    private fun checkPartialImu(typeShape: TypeShape, typeName: TemperName): Boolean {
        // Find the shallowest super-types that are partial imu.
        val partialImuSupers = typeShape.superTypes.map { superType ->
            hackMapOldStyleToNew(superType)
        }.filter { superType ->
            getSuperTypeTree(superType).hasSymbol(partialImuSymbol)
        }

        val presumedImu = buildSet {
            typeShape.formals.mapNotNullTo(this) { formal ->
                when {
                    // All partialImu supers need to reference this formal to be able to presume imu.
                    // Otherwise, even for classes, we could upcast and hide mutability.
                    partialImuSupers.all { it.references(formal) } -> MkType2(formal).get()
                    else -> null
                }
            }
        }
        var passes = true
        if (typeShape.formals.isEmpty() && typeShape.metadata.containsKey(partialImuSymbol)) {
            // Types with no type parameters have no reason to be directly tagged partialImu.
            passes = false
            logSink.log(
                ImuMessage.PartialImuTypeHasNoTypeParameters,
                typeShape.pos,
                listOf(typeName),
            )
        }

        if (
            !checkBackedProperties(
                typeShape,
                typeName,
                partialImuSymbol,
                presumedImu = presumedImu,
            )
        ) {
            passes = false
        }

        when (typeShape.abstractness) {
            // If we require the same for classes as for interfaces, we can't distinguish
            // backed properties vs computed-only things.
            Abstractness.Concrete -> {}
            Abstractness.Abstract -> {
                // For an interface type, look at its super-types that are partial imu and make sure that
                // its parameterizations of them imply consistently
                // They all need to have effectively imu types when type bindings are imu.

                // For each of partialImu super, the type projection has to imply consistency when up-cast.
                for (partialImuSuper in partialImuSupers) {
                    // Check consistency of down-casting.
                    //
                    //    interface Super<T> { ... }
                    //    interface Sub<T> extends Super<ListBuilder<T>> { ... }
                    //
                    // Here, even if we assume that Sub's <T> is valid, then
                    // Sub<ListBuilder<T>> is not Imu.
                    //
                    //    let sub: Sub<String> =  // Looks Imu
                    //        f();
                    //    let sup: Super<ListBuilder<String>> = sub;  // Definitely not
                    //
                    // If the super type won't be Imu even when the subtype's
                    // type arguments are, then we could invalidly conclude
                    // that the subtype is Imu.
                    val superTypeProblem = findNonImuPart(
                        partialImuSuper,
                        buildSet {
                            typeShape.formals.mapTo(this) { MkType2(it).get() }
                        },
                    )
                    if (superTypeProblem != null) {
                        passes = false
                        logSink.log(
                            ImuMessage.DeepPartialImuTypeCanNeverBeImu,
                            typeShape.pos,
                            listOf(typeName, partialImuSuper, superTypeProblem),
                        )
                        // Don't bother to check up-casting because the invalidity of the
                        // super-type might cause spurious errors about missing implications.
                        continue
                    }

                    val superTypeArgs = partialImuSuper.bindings
                    // Check consistency of up-casting.
                    //
                    // Consider an example:
                    //
                    //     interface Sub<T> extends SuperPartialImu<List<T>, X>
                    //
                    // superTypeArgs is <List<T>, X>
                    // sub's formal list is <T>
                    //
                    // If that type passes the type checker then, assuming X and String are Imu,
                    // it's safe to do:
                    //
                    //     class SubImpl<T> extends Sub<T> { .. }             // PartialImu type
                    //     let sub: Sub<String> = new SubImpl<String>(...);   // Imu type
                    //     let sup: SuperPartialImu<List<String>, X> = sub;   // Imu type
                    //
                    // We want to check is that:
                    //
                    //    isImu(List<T>) && isImu(X) -> isImu(T)
                    //
                    // In this case, we consider the superTypeArgs separately:
                    // - List is a PartialImu type, so for it to be Imu, T must be.
                    // - X is an externally defined type, so has no implications for T.
                    //
                    // We get a set of presumptions: [T].
                    //
                    // Then we look through our list of sub formals and find that <T> is
                    // Imu by presumption.
                    val presumedImuForDeepCheck = mutableSetOf<Type2>()
                    for (superTypeArg in superTypeArgs) {
                        fun implied(t: Type2): Set<Type2> = run {
                            val tSuperTree = getSuperTypeTree(t)
                            val tDefinition = t.definition
                            if (tDefinition is TypeFormal) {
                                setOf(t)
                            } else if (tSuperTree.hasSymbol(imuSymbol)) {
                                // We don't consider an Imu sub-type as implying anything about type args
                                emptySet()
                            } else if (tSuperTree.hasSymbol(partialImuSymbol)) {
                                check(tDefinition is TypeShape) // Not formal above
                                buildSet {
                                    for ((i, binding) in t.bindings.withIndex()) {
                                        val f = tDefinition.formals.getOrNull(i)
                                        if (f is TypeFormal && f.variance != Variance.Contravariant) {
                                            addAll(implied(binding))
                                        }
                                    }
                                }
                            } else {
                                emptySet()
                            }
                        }
                        presumedImuForDeepCheck.addAll(implied(superTypeArg))
                    }

                    for (typeFormal in typeShape.typeParameters) {
                        val type = MkType2(typeFormal.definition).get()
                        val nonImuPart = findNonImuPart(type, presumedImuForDeepCheck)
                        if (nonImuPart != null) {
                            passes = false
                            logSink.log(
                                ImuMessage.DeepPartialImuTypeDoesNotImplySuperTypeImu,
                                typeFormal.declarationPos,
                                listOf(
                                    typeName,
                                    typeFormal.definition.diagnosticTypeName,
                                    partialImuSuper,
                                    nonImuPart,
                                ),
                            )
                        }
                    }
                }
            }
        }
        return passes
    }

    private fun checkBackedProperties(
        typeShape: TypeShape,
        typeName: TemperName,
        claimed: Symbol,
        presumedImu: Set<Type2> = emptySet(),
    ): Boolean {
        var passes = true
        val contravariantTypeParameters = contravariantTypeParametersFor(typeShape)
        for (property in typeShape.properties) {
            when (property.abstractness) {
                Abstractness.Abstract -> {}
                Abstractness.Concrete -> {
                    if (
                        !checkBackedProperty(
                            property,
                            typeName,
                            contravariantTypeParameters,
                            claimed,
                            presumedImu = presumedImu,
                        )
                    ) {
                        passes = false
                    }
                }
            }
        }
        return passes
    }

    private fun checkBackedProperty(
        property: PropertyShape,
        typeName: TemperName,
        contravariantTypeParameters: Set<TypeDefinition>,
        claimed: Symbol,
        presumedImu: Set<Type2> = emptySet(),
    ): Boolean {
        var passes = true
        val propertyName = ParsedName(property.symbol.text)
        val staticType = property.descriptor
        if (property.metadata.containsKey(varSymbol)) {
            passes = false
            logSink.log(
                ImuMessage.ImuTypeHasVar,
                property.declarationPos,
                listOf(typeName, claimed.text, propertyName),
            )
        }
        if (staticType != null) {
            val nonImuPart = findNonImuPart(staticType, presumedImu = presumedImu)
            if (nonImuPart != null) {
                passes = false
                if (nonImuPart != staticType) {
                    val typePos = property.metadata.getEdges(typeSymbol).firstOrNull()?.target?.pos
                        ?: property.declarationPos
                    logSink.log(ImuMessage.ExpectedImuType, typePos, listOf(nonImuPart))
                }
                logSink.log(
                    ImuMessage.ImuClassPropertyIsNotImu,
                    property.declarationPos,
                    listOf(typeName, claimed.text, propertyName, staticType),
                )
            } else {
                val contravariantParamUsed = mentionedInType(staticType, contravariantTypeParameters)
                if (contravariantParamUsed != null) {
                    passes = false
                    logSink.log(
                        ImuMessage.ImuClassPropertyUsesContravariantType,
                        property.declarationPos,
                        listOf(typeName, claimed.text, propertyName, staticType, contravariantParamUsed.name),
                    )
                }
            }
        } else {
            passes = false
            logSink.log(
                Log.Error,
                MessageTemplate.MissingType,
                property.declarationPos,
                listOf("class $typeName.$propertyName"),
            )
        }
        return passes
    }

    private fun contravariantTypeParametersFor(typeShape: TypeShape): Set<TypeDefinition> =
        buildSet {
            typeShape.typeParameters.mapNotNullTo(this) {
                val def = it.definition
                if (def.variance == Variance.Contravariant) { def } else { null }
            }
        }

    private fun findNonImuPart(type: Type2, presumedImu: Set<Type2>): Type2? {
        if (type in presumedImu) { return null }
        val superTypeTree = getSuperTypeTree(type)
        if (superTypeTree.hasSymbol(imuSymbol)) {
            return null
        }

        if (
            superTypeTree.hasSymbol(partialImuSymbol) &&
            // Type formals do not have parameters so PartialImu makes little sense there.
            type.definition is TypeShape
        ) {
            if (type.bindings.size != type.definition.formals.size) {
                return type
            }
            for (actual in type.bindings) {
                val problem = findNonImuPart(actual, presumedImu)
                if (problem != null) {
                    return problem
                }
            }
            return null
        }
        return type
    }

    private fun getSuperTypeTree(nominalType: Type2) =
        // TODO Also cache imuSymbol and partialImuSymbol lookups?
        superTypesCache.getOrPut(nominalType) {
            SuperTypeTree2.of(nominalType)
        }
}

private fun Collection<TypeDefinition>.hasSymbol(symbol: Symbol): Boolean = run {
    any { it.metadata.containsKey(symbol) }
}

private fun SuperTypeTree2<Type2>.hasSymbol(symbol: Symbol): Boolean = run {
    byDefinition.keys.hasSymbol(symbol)
}

private val MemberShape.declarationPos get() =
    this.stay?.pos ?: this.enclosingType.pos.leftEdge

private fun mentionedInType(type: Type2, typeDefs: Set<TypeDefinition>): TypeDefinition? =
    if (typeDefs.isEmpty()) {
        null
    } else {
        fun helper(t: Type2?): TypeDefinition? = when (t) {
            null -> null
            else -> {
                if (t.definition in typeDefs) {
                    t.definition
                } else {
                    var def: TypeDefinition? = null
                    for (actual in t.bindings) {
                        def = helper(actual)
                        if (def != null) {
                            break
                        }
                    }
                    def
                }
            }
        }
        helper(type)
    }

private val TypeDefinition.diagnosticTypeName: TemperName get() =
    (this.name as? ResolvedParsedName)?.baseName ?: this.name

private fun Type2.references(formal: TypeFormal): Boolean {
    return bindings.any { binding ->
        when (binding) {
            is TypeParamRef -> binding.definition == formal
            else -> binding.references(formal)
        }
    }
}
