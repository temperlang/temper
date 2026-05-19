package lang.temper.frontend.typestage

import lang.temper.common.Log
import lang.temper.log.LeveledMessageTemplate
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.TemperName
import lang.temper.type.Abstractness
import lang.temper.type.MemberShape
import lang.temper.type.PropertyShape
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape
import lang.temper.type.TypeFormal
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes.imuTypeDefinition
import lang.temper.type.WellKnownTypes.partialImuTypeDefinition
import lang.temper.type2.MkType2
import lang.temper.type2.SuperTypeTree2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.DeclTree
import lang.temper.value.Tree
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.typeSymbol
import lang.temper.value.varSymbol

enum class ImuMessage(
    override val formatString: String,
) : LeveledMessageTemplate {
    ImuTypeHasVar("Class %s extends %s but has a `var` property, %s"),
    ImuClassPropertyIsNotImu(
        "Class %s extends %s but property %s has type %s which is not Imu",
    ),
    ImuClassPropertyUsesContravariantType(
        "Class %s extends %s but property %s: %s uses contravariant type %s",
    ),
    PartialImuTypeHasNoTypeParameters(
        "Type %s extends PartialImu but has no type parameters",
    ),
    ExpectedImuType("Expected Imu type but got %s"),
    DeepPartialImuTypeDoesNotImplySuperTypeImu(
        "PartialImu interface %s's type parameter <%s> would not be Imu" +
            " when cast to its effectively Imu super-type %s because %s is not Imu",
    ),
    DeepPartialImuTypeCanNeverBeImu(
        "PartialImu interface %s could have Imu parameters but it extends" +
            " %s which cannot be Imu because %s is not",
    ),
    ;

    override val suggestedLevel: Log.Level = Log.Error
}

private val imuTypeName = imuTypeDefinition.diagnosticTypeName
private val partialImuTypeName = partialImuTypeDefinition.diagnosticTypeName

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
        return when {
            typeShape == imuTypeDefinition || typeShape == partialImuTypeDefinition ->
                true
            supers[imuTypeDefinition].isNotEmpty() ->
                checkImu(typeShape, typeShape.diagnosticTypeName)
            supers[partialImuTypeDefinition].isNotEmpty() ->
                checkPartialImu(typeShape, typeShape.diagnosticTypeName)
            else -> true
        }
    }

    private fun checkImu(typeShape: TypeShape, typeName: TemperName): Boolean {
        return checkBackedProperties(typeShape, typeName, imuTypeName)
    }

    private fun checkPartialImu(typeShape: TypeShape, typeName: TemperName): Boolean {
        val presumedImu = buildSet {
            typeShape.formals.mapTo(this) {
                MkType2(it).get()
            }
        }
        var passes = true
        if (presumedImu.isEmpty()) {
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
                partialImuTypeName,
                presumedImu = presumedImu,
            )
        ) {
            passes = false
        }

        when (typeShape.abstractness) {
            Abstractness.Concrete -> {}
            Abstractness.Abstract -> {
                // For an interface type, look at its super-types that are partial imu and make sure that
                // its parameterizations of them imply consistently
                // They all need to have effectively imu types when type bindings are imu.

                // Find the shallowest super-types that are partial imu.
                val partialImuSupers = typeShape.superTypes.filter { superType ->
                    superType.definition != partialImuTypeDefinition
                }.map { superType ->
                    hackMapOldStyleToNew(superType)
                }.filter { superType ->
                    getSuperTypeTree(superType)[partialImuTypeDefinition].isNotEmpty()
                }

                // For each of them, the type projection has to imply consistency when up-cast.
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
                            } else if (tSuperTree[imuTypeDefinition].isNotEmpty()) {
                                // We don't consider an Imu sub-type as implying anything about type args
                                emptySet()
                            } else if (tSuperTree[partialImuTypeDefinition].isNotEmpty()) {
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
        extended: TemperName,
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
                            extended,
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
        extended: TemperName,
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
                listOf(typeName, extended, propertyName),
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
                    listOf(typeName, extended, propertyName, staticType),
                )
            } else {
                val contravariantParamUsed = mentionedInType(staticType, contravariantTypeParameters)
                if (contravariantParamUsed != null) {
                    passes = false
                    logSink.log(
                        ImuMessage.ImuClassPropertyUsesContravariantType,
                        property.declarationPos,
                        listOf(typeName, extended, propertyName, staticType, contravariantParamUsed.name),
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
        if (superTypeTree[imuTypeDefinition].isNotEmpty()) {
            return null
        }

        if (
            superTypeTree[partialImuTypeDefinition].isNotEmpty() &&
            type.definition != partialImuTypeDefinition &&
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
        superTypesCache.getOrPut(nominalType) {
            SuperTypeTree2.of(nominalType)
        }
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
