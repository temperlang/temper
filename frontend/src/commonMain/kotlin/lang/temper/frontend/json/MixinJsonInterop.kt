package lang.temper.frontend.json

import lang.temper.ast.TreeVisit
import lang.temper.common.Log
import lang.temper.common.firstOrNullAs
import lang.temper.common.forEachFiltering
import lang.temper.common.putMultiList
import lang.temper.common.putMultiSet
import lang.temper.common.soleElementOrNull
import lang.temper.frontend.ClassDefinitionMacro
import lang.temper.frontend.InterfaceDefinitionMacro
import lang.temper.frontend.Module
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.TypeDefinitionMacro
import lang.temper.frontend.syntax.isExtendsCall
import lang.temper.interp.importExport.Export
import lang.temper.interp.importExport.Importer
import lang.temper.interp.importExport.STANDARD_LIBRARY_FILEPATH
import lang.temper.lexer.Genre
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.last
import lang.temper.name.BuiltinName
import lang.temper.name.ModuleLocation
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.Abstractness
import lang.temper.type.DotHelper
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.TypeShape
import lang.temper.type.TypeShapeImpl
import lang.temper.type.Visibility
import lang.temper.type.extractNominalTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.MkType2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.Document
import lang.temper.value.FunTree
import lang.temper.value.InterpreterCallback.NullInterpreterCallback.logSink
import lang.temper.value.LeafTree
import lang.temper.value.NameLeaf
import lang.temper.value.Planting
import lang.temper.value.ReifiedType
import lang.temper.value.StayLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TFunction
import lang.temper.value.TList
import lang.temper.value.TString
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.TreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.constructorSymbol
import lang.temper.value.extensionSymbol
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.getterSymbol
import lang.temper.value.implicitSymbol
import lang.temper.value.impliedThisSymbol
import lang.temper.value.initSymbol
import lang.temper.value.jsonExtraSymbol
import lang.temper.value.jsonNameSymbol
import lang.temper.value.jsonSymbol
import lang.temper.value.lookThroughDecorations
import lang.temper.value.methodSymbol
import lang.temper.value.sealedTypeSymbol
import lang.temper.value.setterSymbol
import lang.temper.value.staticExtensionSymbol
import lang.temper.value.staticTypeContained
import lang.temper.value.symbolContained
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeDefinitionAtLeafOrNull
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.typeSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vStaticSymbol
import lang.temper.value.vSuperSymbol
import lang.temper.value.vTypeFormalSymbol
import lang.temper.value.vVisibilitySymbol
import lang.temper.value.vWordSymbol
import lang.temper.value.valueContained
import lang.temper.value.void
import lang.temper.value.wordSymbol

/**
 * Add types and methods to support `@json` based auto-encoding and decoding of types to/from JSON.
 */
internal fun mixinJsonInterop(
    module: Module,
    root: BlockTree,
    logSink: LogSink,
) {
    val stdJsonImportRecord: Importer.OkImportRecord? = module.importRecords.firstOrNullAs {
        // Anything mentioning `@json` has an implicit connection to "std/json"
        it.isBlockingImport && it.exporterLocation.isStdJson
    }
    val stdJsonModule = stdJsonImportRecord?.exporter as? Module
        ?: return
    val (typeDecls, typeShapeToTree) = findJsonDecoratedTypeDecls(root)
        ?: return

    fun lookupStdJsonExport(nameText: String): TypeShape? {
        val baseName = ParsedName(nameText)
        val e = stdJsonModule.exports?.firstOrNull { it.name.baseName == baseName }
        val ts = (TType.unpackOrNull(e?.value)?.type2 as? DefinedNonNullType)
            ?.definition
        if (ts == null) {
            logSink.log(
                Log.Warn,
                MessageTemplate.ImportFromFailed,
                // A position that points to the @json decoration as relevant.
                typeDecls.first().pos,
                listOf(baseName, stdJsonModule.loc),
            )
        }
        return ts
    }

    // TODO: maybe use ImportMe to generate the references.
    val stdJson = JsonInteropDetails.StdJson(
        typeJsonAdapter = lookupStdJsonExport("JsonAdapter") ?: return,
        typeJsonProducer = lookupStdJsonExport("JsonProducer") ?: return,
        typeInterchangeContext = lookupStdJsonExport("InterchangeContext") ?: return,
        typeJsonSyntaxTree = lookupStdJsonExport("JsonSyntaxTree") ?: return,
        typeJsonObject = lookupStdJsonExport("JsonObject") ?: return,
        typeJsonBoolean = lookupStdJsonExport("JsonBoolean") ?: return,
        typeJsonFloat64 = lookupStdJsonExport("JsonFloat64") ?: return,
        typeJsonInt = lookupStdJsonExport("JsonInt") ?: return,
        typeJsonNull = lookupStdJsonExport("JsonNull") ?: return,
        typeJsonNumeric = lookupStdJsonExport("JsonNumeric") ?: return,
        typeJsonString = lookupStdJsonExport("JsonString") ?: return,
        typeOrNullJsonAdapter = lookupStdJsonExport("OrNullJsonAdapter") ?: return,
    )
    val details = JsonInteropDetails(typeDecls.associateBy { it.name }, stdJson)
    val pass = JsonInteropPass(root.document, logSink, details)
    val changes = pass.computeChanges()
    stageGeneratedCodeAndFoldIntoModule(
        module = module,
        destinationRoot = root,
        localImports = (stdJsonModule.exports ?: emptyList()).filter {
            // Import extensions like `Int.jsonAdapter()`.
            extensionSymbol in it.declarationMetadata ||
                staticExtensionSymbol in it.declarationMetadata
        },
        details = details,
        changes = changes,
        places = typeShapeToTree,
    )
}

private fun findJsonDecoratedTypeDecls(
    root: BlockTree,
): Pair<List<JsonInteropDetails.TypeDecl>, Map<TypeShape, FunTree>>? {
    val typeShapeToTree = mutableMapOf<TypeShape, FunTree>()
    val jsonTypeShapes = mutableListOf<TypeShape>()
    val nameToTypeShape = mutableMapOf<ResolvedName, TypeShape>()
    val extendsCalls = mutableMapOf<TypeShape, MutableSet<NominalType>>()
    val constructors = mutableMapOf<TypeShape, MutableList<FunTree>>()

    // Walk the tree and pull information into side tables.
    // This helps us since we're in a sensitive stage between assigning unique names to
    // members and re-running the type macros to update the shape metadata.
    val q = ArrayDeque<Pair<Tree, TypeShape?>>()
    q.add(root to null)
    while (q.isNotEmpty()) {
        val (t, typeShape) = q.removeFirst()
        var typeShapeForChildren = typeShape
        when (t) {
            is FunTree -> t.parts?.let { parts ->
                val typeDefined = parts.metadataSymbolMap[typeDefinedSymbol]
                    ?.target?.typeDefinitionAtLeafOrNull as? TypeShape
                if (typeDefined != null) {
                    typeShapeForChildren = typeDefined
                    nameToTypeShape[typeDefined.name] = typeDefined
                    if (jsonSymbol in typeDefined.metadata) {
                        jsonTypeShapes.add(typeDefined)
                    }
                    typeShapeToTree[typeDefined] = t
                }
            }

            is CallTree -> if (isExtendsCall(t)) {
                val (_, subT, superT) = t.children
                val subTypeShape = subT.staticTypeContained as? NominalType
                val superType = superT.typeShapeAtLeafOrNull
                if (subTypeShape != null && superType != null) {
                    extendsCalls.putMultiSet(superType, subTypeShape)
                }
            }

            is DeclTree -> t.parts?.let { parts ->
                if (typeShape != null) {
                    val metadata = parts.metadataSymbolMap
                    val init = metadata[initSymbol]?.target
                    val methodSymbol = metadata[methodSymbol]?.target?.symbolContained
                    if (getterSymbol in metadata || setterSymbol in metadata) {
                        // Not a constructor or property
                    } else if (init is FunTree && methodSymbol == constructorSymbol) {
                        constructors.putMultiList(typeShape, init)
                    }
                }
            }

            else -> {}
        }
        for (child in t.children) {
            q.add(child to typeShapeForChildren)
        }
    }
    if (jsonTypeShapes.isEmpty()) {
        return null
    }

    return buildList {
        for (typeShape in nameToTypeShape.values) {
            val jsonDecoration = typeShape.metadata.getEdges(jsonSymbol).firstOrNull()
            fun methodPresenceOf(dotName: Symbol): JsonInteropDetails.MethodPresence {
                val method = typeShape.membersMatching(dotName).firstOrNull {
                    // TODO: look for extension methods in scope
                    it is MethodShape && it.methodKind == MethodKind.Normal &&
                        it.visibility == Visibility.Public
                }
                return when {
                    method == null -> JsonInteropDetails.MethodPresence.Absent
                    method.enclosingType == typeShape -> JsonInteropDetails.MethodPresence.Present
                    else -> JsonInteropDetails.MethodPresence.Inherited
                }
            }

            val properties = buildList {
                typeShape.properties.forEach { p ->
                    val decl = p.stay?.incoming?.source as? DeclTree
                    val parts = decl?.parts ?: return@forEach
                    val type = parts.metadataSymbolMap[typeSymbol]?.target?.staticTypeContained
                    var jsonPropertyKey = p.symbol.text
                    var shouldEncode = p.abstractness == Abstractness.Concrete
                    parts.metadataSymbolMap[jsonNameSymbol]?.let { customNameEdge ->
                        // The value is vetted by the jsonName decorator
                        jsonPropertyKey = customNameEdge.target.valueContained(TString)!!
                        shouldEncode = true
                    }
                    add(
                        JsonInteropDetails.PropertyDecl(
                            pos = parts.name.pos,
                            name = p.name as ResolvedName,
                            symbol = p.symbol,
                            abstractness = p.abstractness,
                            type = type,
                            knownValue = null,
                            shouldEncode = shouldEncode,
                            jsonPropertyKey = jsonPropertyKey,
                        ),
                    )
                }
                typeShape.metadata.getEdges(jsonExtraSymbol).forEach { e ->
                    // The jsonExtra decorator takes care to vet the types of metadata before packing them.
                    val (propertyNameValue, jsonKnownValue) = e.valueContained(TList)!!
                    val propertyNameText = TString.unpack(propertyNameValue)
                    add(
                        JsonInteropDetails.PropertyDecl(
                            pos = e.target.pos,
                            name = BuiltinName(propertyNameText),
                            symbol = Symbol(propertyNameText),
                            abstractness = Abstractness.Abstract,
                            type = null,
                            knownValue = jsonKnownValue,
                            shouldEncode = true,
                        ),
                    )
                }
            }

            var howToConstruct: JsonInteropDetails.HowToConstruct =
                JsonInteropDetails.HowToConstruct.CannotIsAbstract
            if (typeShape.abstractness == Abstractness.Concrete && jsonDecoration != null) {
                howToConstruct = JsonInteropDetails.HowToConstruct.CannotNoConstructor
                val constructorFn = constructors[typeShape]?.soleElementOrNull
                val constructorFnParts = constructorFn?.parts
                if (constructorFn != null && constructorFnParts != null) {
                    var associatesWithProperties = true
                    val encodedPropertiesBySymbol = buildMap {
                        properties.forEach { p ->
                            if (p.shouldEncode) {
                                if (p.symbol in this) {
                                    associatesWithProperties = false
                                } else {
                                    this[p.symbol] = p
                                }
                            }
                        }
                    }
                    val propertyNames = mutableListOf<ResolvedName>()
                    for (formal in constructorFnParts.formals) {
                        val formalParts = formal.parts
                        if (formalParts == null) {
                            associatesWithProperties = false
                            break
                        }
                        if (impliedThisSymbol in formalParts.metadataSymbolMultimap) {
                            continue
                        }
                        val word = formalParts.word?.target?.symbolContained
                        val property = encodedPropertiesBySymbol[word]
                        if (property != null) {
                            propertyNames.add(property.name)
                        } else {
                            associatesWithProperties = false
                            break
                        }
                    }

                    if (
                        associatesWithProperties &&
                        // Do we have a place to put each decoded property?
                        propertyNames.size == encodedPropertiesBySymbol.size
                    ) {
                        howToConstruct = JsonInteropDetails.HowToConstruct.ViaConstructor(
                            propertyNames.toList(),
                        )
                    }
                }
            }
            var sealedSubTypes: List<JsonInteropDetails.SealedSubType>? = null
            if (sealedTypeSymbol in typeShape.metadata) {
                sealedSubTypes = buildList {
                    extendsCalls[typeShape]?.forEach { subType ->
                        add(
                            JsonInteropDetails.SealedSubType(
                                subType.definition.name,
                                subType.bindings.map { it as StaticType },
                            ),
                        )
                    }
                }
            }
            val fromJsonPresence = if (
                typeShape.staticProperties.any {
                    it.symbol == decodeFromJsonDotName && it.visibility == Visibility.Public
                }
            ) {
                JsonInteropDetails.MethodPresence.Present
            } else {
                JsonInteropDetails.MethodPresence.Absent
            }
            var toJsonPresence = methodPresenceOf(encodeToJsonDotName)
            if (
                toJsonPresence == JsonInteropDetails.MethodPresence.Inherited &&
                fromJsonPresence == JsonInteropDetails.MethodPresence.Absent
            ) {
                // A @json type can inherit an encoder if it specifies a decoder.
                toJsonPresence = JsonInteropDetails.MethodPresence.Absent
            }

            add(
                JsonInteropDetails.TypeDecl(
                    pos = jsonDecoration?.target?.pos ?: typeShape.pos,
                    definition = typeShape,
                    properties = properties,
                    howToConstruct = howToConstruct,
                    hasJsonDecoration = jsonDecoration != null,
                    toJsonMethod = toJsonPresence,
                    fromJsonMethod = fromJsonPresence,
                    sealedSubTypes = sealedSubTypes?.toList(),
                ),
            )
        }
    } to typeShapeToTree.toMap()
}

private fun stageGeneratedCodeAndFoldIntoModule(
    module: Module,
    destinationRoot: BlockTree,
    localImports: List<Export>,
    details: JsonInteropDetails,
    changes: JsonInteropChanges,
    places: Map<TypeShape, FunTree>,
) {
    if (changes.isEmpty()) { return }

    val document = destinationRoot.document
    val namingContext = module.namingContext

    val submodule = Module(
        projectLogSink = module.projectLogSink,
        loc = module.loc,
        console = module.console,
        continueCondition = module.continueCondition,
        mayRun = false,
        sharedLocationContext = module.sharedLocationContext,
        genre = Genre.Library,
        allowDuplicateLogPositions = module.allowDuplicateLogPositions,
        // Generate top-level names in module's naming context space
        namingContext = namingContext,
    )
    submodule.addEnvironmentBindings(extraSubmoduleBindings)

    data class StoredLocalTypeInfo(
        val typeShape: TypeShapeImpl,
        val stayLeaf: StayLeaf?,
    )
    val storedLocalTypeInfo = details.localTypes.mapValues { (_, td) ->
        val typeShape = td.definition
        StoredLocalTypeInfo(typeShape as TypeShapeImpl, typeShape.stayLeaf)
    }

    namingContext.hijackTo(submodule) {
        document.hijackTo(submodule) {
            submodule.deliverContent(
                document.treeFarm.grow {
                    plantClassesFor(
                        submodule,
                        document,
                        localImports, details, changes,
                    )
                },
            )

            while (
                submodule.canAdvance() &&
                submodule.stageCompleted.let { it == null || it < Stage.Define }
            ) {
                submodule.advance()
            }
        }
    }

    val adoptedRoot = submodule.generatedCode
    if (submodule.stageCompleted != Stage.Define || !submodule.ok || adoptedRoot == null) {
        logSink.log(
            Log.Error,
            MessageTemplate.MixinCannotBeReincorporated,
            Position(submodule.loc, 0, 0),
            listOf("@json"),
        )
        return
    }

    // Adopt the tree into the original document, so its parts can coexist with the original.
    // We need to reincorporate the content:
    // - For top level declarations, store them in case they're used.
    //   They may be used inside DotHelpers.
    // - For classes/interfaces, if it pre-exists, pull its parts into the existing type
    //   envelope identified by places.
    // - For added classes/interfaces, put them in the existing document.
    val adoptedTopLevels = mutableListOf<AdoptedTopLevel>()
    for (e in adoptedRoot.edges) {
        val topLevel = e.target
        if (topLevel is LeafTree) {
            // Drop it
            continue
        }

        var adopted: AdoptedTopLevel? = null

        val through = lookThroughDecorations(e).target
        if (through is DeclTree) {
            adopted = AdoptedTopLevelDecl(e.target, through)
        }

        if (adopted == null && through is CallTree) {
            val callee = through.childOrNull(0)?.functionContained
            if (callee is TypeDefinitionMacro) {
                val fn = through.lastChild as? FunTree
                val fnParts = fn?.parts
                if (fnParts != null) {
                    val typeShape = fnParts.metadataSymbolMap[typeDefinedSymbol]
                        ?.target?.typeShapeAtLeafOrNull
                    val body = fnParts.body as? BlockTree
                    if (typeShape != null && body != null) {
                        adopted = AdoptedTypeDefinition(topLevel, typeShape, body)
                    }
                }
            }
        }

        if (adopted == null) {
            adopted = OtherAdoptedTopLevel(topLevel)
        }

        adoptedTopLevels.add(adopted)
    }

    // Figure out which adopted top levels uses what names
    val declared = adoptedTopLevels.mapNotNull {
        (it as? AdoptedTopLevelDecl)?.declTree?.parts?.name?.content as? ResolvedName
    }.toMutableSet()
    // These names are declared in the original document, not the adopted document.
    declared.removeAll(details.localTypes.keys)

    for (atl in adoptedTopLevels) {
        TreeVisit.startingAt(atl.topLevel)
            .forEachContinuing { t ->
                if (t is NameLeaf) {
                    atl.namesUsed.add(t.content as ResolvedName)
                } else if (t is ValueLeaf) {
                    val v = t.content
                    when (v.typeTag) {
                        TFunction -> (TFunction.unpack(v) as? DotHelper)?.let { dotHelper ->
                            dotHelper.extensions.mapTo(atl.namesUsed) {
                                it.resolution
                            }
                        }
                        TType -> TType.unpack(v).let { reifiedType ->
                            extractNominalTypes(reifiedType.type).mapTo(atl.namesUsed) {
                                it.definition.name
                            }
                        }
                        else -> {}
                    }
                }
            }
            .visitPreOrder()
        atl.namesUsed.retainAll(declared)
        (atl as? AdoptedTopLevelDecl)?.declTree?.parts?.name?.content?.let {
            atl.namesUsed.remove(it)
        }
    }

    // Make sure that type shapes of types for which we synthesized `class ... { ... }` wrappers
    // above have a stay that points into the original document.
    for (atl in adoptedTopLevels) {
        if (atl is AdoptedTypeDefinition) {
            val stored = storedLocalTypeInfo[atl.typeShape.name] ?: continue
            val typeShape = stored.typeShape
            typeShape.stayLeaf = stored.stayLeaf
        }
    }

    // Keep track of which top level decls we'll need.
    val neededDecls = mutableSetOf<ResolvedName>()
    // As we incorporate in different ways, we remove items from it.
    val unincorporated = adoptedTopLevels.toMutableList()

    // Incorporate added members into existing types
    unincorporated.forEachFiltering { atl ->
        if (atl is AdoptedTypeDefinition) {
            val typeShape = atl.typeShape
            val stayLeaf = typeShape.stayLeaf
            val existingTypeBody = places[typeShape]?.parts?.body as? BlockTree
            if (existingTypeBody != null && stayLeaf != null) {
                // typeShape is the shape of a pre-existing type.
                this@forEachFiltering.remove()

                neededDecls.addAll(atl.namesUsed)

                val adoptedBodyElements = atl.body.children.toList()
                atl.body.removeChildren(atl.body.indices)
                existingTypeBody.insert {
                    adoptedBodyElements.forEach { Replant(it) }
                }

                // TODO: Do we need to do anything with unapplied decorators?
            }
        }
    }

    // Incorporate new type definitions and non-declaration top-levels as needed.
    var newTypeDefinitionPosition = 0
    var newTypeDefinitionOwner = destinationRoot
    // Sometimes, type definitions are defined in blocks.
    // For example, a REPL or snippet runner may insert a `do {...}` block.
    // So find a block to own the type definitions.
    // This preserves terminal expressions without unnecessarily nesting
    // generated type definitions inside functions.
    run {
        var bestBlockDepth = Int.MAX_VALUE
        var bestIndexInBlock = 0
        var bestBlock: BlockTree? = null
        // Incorporate them after the lexically last type definition in the
        // shallowest block that contains an `@json` type definition.
        for (d in details.localTypes.values) {
            if (!d.hasJsonDecoration) { continue }
            var block: BlockTree? = null
            var indexInBlock = 0
            var blockDepth = 0
            var e = d.definition.stayLeaf?.incoming
            while (e != null) {
                val source = e.source ?: break
                if (source is BlockTree) {
                    if (e.target !is BlockTree) {
                        block = source
                        indexInBlock = e.edgeIndex
                    }
                    blockDepth += 1
                } else {
                    blockDepth = 0
                }
                e = source.incoming
            }
            if (blockDepth < bestBlockDepth ||
                blockDepth == bestBlockDepth && indexInBlock > bestIndexInBlock
            ) {
                bestBlockDepth = blockDepth
                bestIndexInBlock = indexInBlock
                bestBlock = block
            }
        }

        if (bestBlock != null) {
            newTypeDefinitionOwner = bestBlock
            // Just after the last type definition in the shallowest block that contains one
            newTypeDefinitionPosition = bestIndexInBlock + 1
        }
    }

    unincorporated.forEachFiltering { atl ->
        if (atl is AdoptedTypeDefinition) {
            this@forEachFiltering.remove()
            neededDecls.addAll(atl.namesUsed)

            newTypeDefinitionOwner.insert(at = newTypeDefinitionPosition++) {
                Replant(freeTree(atl.topLevel))
            }
        }
    }

    // Incorporate needed supported declarations
    while (true) {
        var insertionPointForDecl = 0
        val nUnincorporatedBefore = unincorporated.size
        unincorporated.forEachFiltering { atl ->
            check(atl is AdoptedTopLevelDecl)
            val name = atl.declTree.parts!!.name.content
            if (name in neededDecls) {
                this@forEachFiltering.remove()
                neededDecls.addAll(atl.namesUsed)

                destinationRoot.insert(at = insertionPointForDecl++) {
                    Replant(freeTree(atl.topLevel))
                }
            }
        }
        if (nUnincorporatedBefore == unincorporated.size) { break }
    }
}

private fun Planting.plantClassesFor(
    submodule: Module,
    document: Document,
    localImports: List<Export>,
    details: JsonInteropDetails,
    changes: JsonInteropChanges,
): TreeTemplate<BlockTree> {
    val leftPos = Position(submodule.loc, 0, 0)

    return Block(leftPos) {
        // First, add any local declarations for extension methods we might want.
        localImports.forEach { export ->
            Decl(leftPos, document.nameMaker.unusedSourceName(export.name.baseName)) {
                V(initSymbol)
                Rn(export.name)
                V(implicitSymbol)
                V(void)
                export.declarationMetadata.forEach { (symbol, values) ->
                    values.forEach { value ->
                        if (value != null) {
                            V(symbol)
                            V(value)
                        }
                    }
                }
            }
        }
        // Make sure local types are visible.
        for (typeDef in details.localTypes.values) {
            if (typeDef.name !in changes.typeNameToAddedMethods) {
                val typeShape = typeDef.definition
                Decl(leftPos, typeDef.name) {
                    V(initSymbol)
                    V(
                        Value(
                            ReifiedType(
                                MkType2(typeShape)
                                    .actuals(typeShape.formals.map { MkType2(it).get() })
                                    .get(),
                            ),
                        ),
                    )
                }
            }
        }
        fun Planting.emitAddedMethod(
            addedMethod: JsonInteropChanges.AddedMethod,
        ) {
            Decl(ParsedName(addedMethod.name.text)) methodDecl@{
                if (addedMethod.isStatic) {
                    V(vStaticSymbol)
                    V(void)
                }
                V(vVisibilitySymbol)
                V(addedMethod.visibility.toSymbol())
                V(vInitSymbol)
                addedMethod.body(this@methodDecl)
            }
        }
        changes.typeNameToAddedMethods.forEach { (typeName, addedMethods) ->
            val typeDecl = details.localTypes.getValue(typeName)
            val type = MkType2(typeDecl.definition)
                .actuals(typeDecl.typeFormals.map { MkType2(it).get() })
                .get()
            Call(
                when (typeDecl.abstractness) {
                    Abstractness.Abstract -> InterfaceDefinitionMacro
                    Abstractness.Concrete -> ClassDefinitionMacro
                },
            ) {
                typeDecl.definition.word?.let { word ->
                    V(wordSymbol)
                    Ln(ParsedName(word.text))
                }
                Fn {
                    V(typeDefinedSymbol)
                    V(Value(ReifiedType(type)))
                    Block {
                        addedMethods.forEach { addedMethod ->
                            emitAddedMethod(addedMethod)
                        }
                    }
                }
            }
        }
        changes.adapterClasses.forEach { addedType ->
            Call(
                when (addedType.abstractness) {
                    Abstractness.Abstract -> InterfaceDefinitionMacro
                    Abstractness.Concrete -> ClassDefinitionMacro
                },
            ) {
                V(vWordSymbol)
                Ln(addedType.name)
                addedType.typeFormals.forEach {
                    V(vTypeFormalSymbol)
                    TODO("$it")
                }
                addedType.superTypes.forEach {
                    V(vSuperSymbol)
                    V(Value(ReifiedType(hackMapOldStyleToNew(it))))
                }
                Fn {
                    Block {
                        addedType.properties.forEach { addedProperty ->
                            TODO("$addedProperty")
                        }
                        addedType.methods.forEach { addedMethod ->
                            emitAddedMethod(addedMethod)
                        }
                    }
                }
            }
        }
        V(void)
    }
}

private sealed class AdoptedTopLevel {
    abstract val topLevel: Tree
    val namesUsed = mutableSetOf<ResolvedName>()
}

/** A type definition */
private data class AdoptedTypeDefinition(
    override val topLevel: Tree,
    val typeShape: TypeShape,
    val body: BlockTree,
) : AdoptedTopLevel()

/**
 * A declaration with an initializer that is a name or a value that should be
 * reincorporated if it is used in another part that is reincorporated.
 */
private data class AdoptedTopLevelDecl(
    override val topLevel: Tree,
    val declTree: DeclTree,
) : AdoptedTopLevel()

private data class OtherAdoptedTopLevel(
    override val topLevel: Tree,
) : AdoptedTopLevel()

private val ModuleLocation.isStdJson: Boolean
    get() {
        val name = this as? ModuleName
        if (name?.libraryRootSegmentCount == 1 && !name.isPreface) {
            val sourceFile = name.sourceFile
            if (sourceFile.dirName() == STANDARD_LIBRARY_FILEPATH) {
                var fileName = sourceFile.last().fullName
                val dot = fileName.indexOf('.')
                if (dot >= 0) {
                    fileName = fileName.substring(0, dot)
                }
                return fileName == "json"
            }
        }
        return false
    }

private val extraSubmoduleBindings = mapOf<TemperName, Value<*>>(
    StagingFlags.haltBeforeMixingIn to TBoolean.valueTrue,
)
