package lang.temper.kcodegen.outgrammar

import lang.temper.common.Log
import lang.temper.common.allMapToSameElseNull
import lang.temper.common.mapFirst
import lang.temper.common.putMulti
import lang.temper.common.sprintf
import lang.temper.common.subListToEnd
import lang.temper.common.toStringViaBuilder
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position

/**
 * Converts *NodeType*s and *EnumType*s after all the information about their parts and
 * relationships have been gathered into Kotlin [TypeDefinition]s with Kotlin
 * properties and members.
 */
internal class TypeDefinitionBuilder(
    private val gp: GrammarProcessor,
) {
    /** The type for [operatorDefinition][lang.temper.format.FormattableTree.operatorDefinition] */
    private val operatorDefinitionType = PropertyTypeAndCount(
        gp.getOperatorDefinitionType(),
        PropertyCount.ZeroOrOne,
    )

    fun build(types: Map<Id, PropertyType>): List<TypeDefinition> {
        val nameToTypeDefinition = mutableMapOf<Id, TypeDefinition>()
        // Partition the name to type declaration map.
        val enumTypes = mutableListOf<EnumType>()
        val nodeTypes = mutableListOf<NodeType>()

        fun addType(pt: PropertyType) {
            when (pt) {
                is KotlinCode -> Unit // Needs no definition
                is EnumType -> enumTypes.add(pt)
                is NodeType -> nodeTypes.add(pt)
                is UnknownType -> when (val resolution = pt.resolution) {
                    null ->
                        gp.logSink.log(
                            OutputGrammarCodeGeneratorMessageTemplate.ImpliedNotDeclared,
                            pt.pos,
                            listOf(pt.name),
                        )
                    else -> addType(resolution)
                }
            }
        }
        for (pt in types.values) {
            addType(pt)
        }

        // Before we emit anything, handle derivations.
        // Explicit "Name from OtherName;" derivation relationships
        // may imply other relationships.
        //
        //     Foo(bar%Bar);
        //     data FooData from Foo;
        //
        // In this, FooData needs a bar%BarData.
        //
        // This pass creates BarData from Bar based on those implied
        // properties, and fleshes out the existing FooData's definition
        // with super-type and property lists from Foo.
        deriveNodeTypes(nodeTypes)

        // Enum types are easy.  Let's emit those first.
        for (enumType in enumTypes) {
            val (name, _, members) = enumType
            if (members == null) {
                gp.fail(enumType.pos, MessageTemplate.MissingDeclaration, listOf(name))
            } else {
                nameToTypeDefinition[name] = EnumDefinition(name, members, enumType.docComment)
            }
        }

        val completeNodeTypes = foldSuperTypeInformationIntoSubTypes(nodeTypes)
        completeNodeTypes.values.forEach { nodeType ->
            nameToTypeDefinition[nodeType.name] = createNodeTypeDefinition(nodeType)
        }
        val completeNodeTypeNames = buildSet {
            completeNodeTypes.values.mapTo(this) { it.name }
        }
        // Check for unneeded quotes around node type names.
        // This can lead to failure to link parent/child nodes which can
        // cause auto-generated deepCopy() methods to do odd things and
        // can cause recursive analyses by backends of their tentative
        // translations to miss entire subtrees.
        for (completeNodeType in completeNodeTypes.values) {
            for (p in completeNodeType.properties.values) {
                when (val pType = p.typeAndCount.type) {
                    is KotlinCode -> {
                        var text = pType.sourceText.trim()
                        if (text.endsWith("?")) {
                            text = text.dropLast(1).trim()
                        }
                        if (Id.isValidId(text) && Id(text) in completeNodeTypeNames) {
                            gp.fail(
                                p.pos,
                                OutputGrammarCodeGeneratorMessageTemplate.BackticksAroundNodeTypeName,
                                listOf("${completeNodeType.nameWithKind}.${p.name}", text),
                            )
                        }
                    }
                    is NamedPropertyType -> {}
                }
            }
        }
        return nameToTypeDefinition.values.toList()
    }

    private fun deriveNodeTypes(nodeTypes: MutableList<NodeType>) {
        val nodeTypeByName = mutableMapOf<Id, NodeType>()
        nodeTypes.associateByTo(nodeTypeByName) { it.name }

        // Flesh out type kinds.  Either we have an explicit one, or we infer one from super-types.
        val seen = mutableSetOf<NodeType>()
        fun inferTypeKind(nt: NodeType): TypeKind? {
            val typeKind = nt.typeKind
            if (typeKind != null) { return typeKind }
            val superTypes = nt.superTypes
            if (superTypes.isEmpty()) {
                return typeKind.orDefault.also {
                    nt.typeKind = it
                }
            }
            // Halt supertype cycles which will be error checked later
            if (nt in seen) { return null }
            seen.add(nt)
            try {
                for (st in superTypes) {
                    when (st) {
                        is NodeTypeReference -> {
                            val superTypeKind = inferTypeKind(st.nodeType)
                            if (superTypeKind != null) {
                                return superTypeKind.also {
                                    nt.typeKind = superTypeKind
                                }
                            }
                        }
                        is KotlinCode -> {}
                    }
                }
            } finally {
                seen.remove(nt)
            }
            return null
        }
        for (nodeType in nodeTypes) {
            inferTypeKind(nodeType)
        }
        // Anything for which inference failed can be the default.
        for (nodeType in nodeTypes) {
            nodeType.typeKind = nodeType.typeKind.orDefault
        }

        // Now we know which type kinds are derived from which, we have a basis
        // for auto-derivation.
        val equivalentsByKind = mutableMapOf<Pair<NodeType, TypeKind>, NodeType>()
        // Find equivalents by following derivedFrom entries
        for (nodeType in nodeTypes) {
            val equivalentTo = nodeType.derivation?.sourceReference?.nodeType
            if (equivalentTo != null) {
                equivalentsByKind[equivalentTo to nodeType.typeKind!!] = nodeType
            }
        }
        // Fill in reverse equivalences.
        for ((nodeTypeAndKind, nodeType) in equivalentsByKind.entries.toList()) {
            val otherNodeType = nodeTypeAndKind.first
            val key = nodeType to otherNodeType.typeKind!!
            if (key !in equivalentsByKind) {
                equivalentsByKind[key] = otherNodeType
            }
        }

        // Now, look at all the derived types.
        val derivationQueue = ArrayDeque<NodeType>()
        derivationQueue.addAll(equivalentsByKind.values)

        fun equivalentOf(nodeType: NodeType, typeKind: TypeKind, pos: Position, requiredBy: NodeType): NodeType? {
            val inputTypeKind = nodeType.typeKind!!
            if (inputTypeKind == typeKind) {
                return nodeType
            }
            val key = nodeType to typeKind
            val preexisting = equivalentsByKind[key]
            if (preexisting != null) {
                return preexisting
            }
            // An implied type.  Create it and enqueue it.
            val derivedNameText = buildString {
                append(nodeType.name.text)
                val removedSuffix = inputTypeKind.optionalDerivationSuffix()
                val addedSuffix = typeKind.requiredDerivationSuffix()
                if (this.endsWith(removedSuffix)) {
                    this.setLength(this.length - removedSuffix.length)
                }
                append(addedSuffix)
            }
            val derivedName = Id(derivedNameText)

            val potentialConflict = nodeTypeByName[derivedName]
            // It's only a conflict if it doesn't have the right TypeKind.
            // We can declare a (non-derived) equivalent using the naming convention.
            if (potentialConflict?.typeKind == typeKind) {
                return potentialConflict.also {
                    equivalentsByKind[key] = it
                }
            }
            if (potentialConflict != null) {
                gp.fail(
                    pos,
                    OutputGrammarCodeGeneratorMessageTemplate.CannotAutoDerive,
                    listOf(derivedNameText, nodeType.name, requiredBy.name, potentialConflict.pos),
                )
                return null
            }

            // No conflicts, derive a type.
            val derivation = NodeType(derivedName, pos)
            derivation.derivation = DerivesFrom(NodeTypeReference(pos, nodeType))
            derivation.typeKind = typeKind
            derivation.isExplicitlyDeclared = nodeType.isExplicitlyDeclared
            nodeTypes.add(derivation)
            nodeTypeByName[derivedName] = derivation
            equivalentsByKind[key] = derivation
            val revKey = derivation to nodeType.typeKind!!
            if (revKey !in equivalentsByKind) {
                equivalentsByKind[revKey] = nodeType
            }
            derivationQueue.add(derivation)
            return derivation
        }
        fun equivalentOf(
            propertyType: PropertyType?,
            typeKind: TypeKind,
            pos: Position,
            requiredBy: NodeType,
        ): PropertyType? = when (propertyType) {
            is KotlinCode -> propertyType
            is EnumType -> propertyType
            is NodeType -> equivalentOf(
                nodeType = propertyType, typeKind = typeKind, pos = pos, requiredBy = requiredBy,
            )
            is UnknownType -> equivalentOf(propertyType.resolution, typeKind, pos, requiredBy)
            null -> null
        }
        while (derivationQueue.isNotEmpty()) {
            val derivedType = derivationQueue.removeFirst()
            val source = when (val derivation = derivedType.derivation) {
                is EquivalentTo? -> continue
                is DerivesFrom -> derivation.sourceReference.nodeType
            }
            val derivedKind = derivedType.typeKind!!

            // Extract the things derivedType needs to borrow
            val enumeratedSubTypes = source.enumeratedSubTypes
            val properties = source.localProperties

            // We'll add supertypes after we've found all the implied type.
            // We add only supertypes for which there are equivalents required for other
            // reasons.

            // Equivalents of enumerated subtypes are needed.
            // data MyUnionType
            enumeratedSubTypes.mapNotNullTo(derivedType.enumeratedSubTypes) { subTypeRef ->
                equivalentOf(subTypeRef.nodeType, derivedKind, subTypeRef.pos, derivedType)?.let {
                    NodeTypeReference(subTypeRef.pos, it)
                }
            }

            val preexistingPropertyNames = derivedType.localProperties.keys.toSet()
            for ((propName, p) in properties) {
                if (propName in preexistingPropertyNames) {
                    // Overrode the property.
                    // This is useful when a source type has a getter that assumes
                    // something about the source type kind.
                    continue
                }
                val derivedPropertyType =
                    equivalentOf(p.propertyType, derivedKind, derivedType.pos, derivedType)
                val derivedProperty = p.copy(propertyType = derivedPropertyType)
                derivedType.localProperties[derivedProperty.propertyName] = derivedProperty
            }

            derivedType.syntaxDeclaration = derivedType.syntaxDeclaration ?: source.syntaxDeclaration
            derivedType.operatorDefinition = derivedType.operatorDefinition ?: source.operatorDefinition
            derivedType.renderTo = derivedType.renderTo ?: source.renderTo
            derivedType.extraBodyContent = derivedType.extraBodyContent ?: source.extraBodyContent

            fun maybeCopyKtCode(from: Map<Id, KotlinCode>, into: MutableMap<Id, KotlinCode>) {
                for ((id, ktCode) in from) {
                    if (id !in into) {
                        into[id] = ktCode
                    }
                }
            }
            maybeCopyKtCode(from = source.defaultExpressions, into = derivedType.defaultExpressions)
            maybeCopyKtCode(from = source.propertyOverrides, into = derivedType.propertyOverrides)

            for (req in source.requirements) {
                if (req !in derivedType.requirements) {
                    derivedType.requirements.addAll(source.requirements)
                }
            }
        }

        // Add equivalent supertypes.  If a supertype has no equivalent,
        // but one of its supertypes does, add that equivalent.
        val derivedTypes = buildMap {
            for (nodeType in nodeTypes) {
                when (val derivation = nodeType.derivation) {
                    is DerivesFrom -> this[nodeType] = derivation.sourceReference.nodeType
                    is EquivalentTo? -> {}
                }
            }
        }
        // If one derived type is a needed super type of another, make sure we expand its super type
        // list first.
        val expanded = mutableSetOf<NodeType>()
        fun expandSuperTypeList(derivedType: NodeType) {
            if (derivedType in expanded) {
                return
            }
            expanded.add(derivedType)

            val source = derivedTypes[derivedType] ?: return
            val derivedKind = derivedType.typeKind!!

            val superKotlinTypes = mutableSetOf<KotlinCode>()
            val equivalentSuperNodeTypes = mutableMapOf<NodeType, Position>()
            val superTypeQueue = ArrayDeque(source.superTypes)
            while (true) {
                when (val st = superTypeQueue.removeFirstOrNull() ?: break) {
                    is NodeTypeReference -> {
                        val superTypeRef = st
                        val superType = superTypeRef.nodeType
                        val key = superType to derivedKind
                        val equivalent = equivalentsByKind[key]
                        if (equivalent != null) {
                            equivalentSuperNodeTypes[equivalent] = superTypeRef.pos
                        } else {
                            if (superType in derivedTypes) {
                                expandSuperTypeList(superType)
                            }
                            superTypeQueue.addAll(superType.superTypes)
                        }
                    }
                    is KotlinCode -> superKotlinTypes.add(st)
                }
            }
            equivalentSuperNodeTypes.entries.mapTo(derivedType.superTypes) { (superType, pos) ->
                NodeTypeReference(pos, superType)
            }
            derivedType.superTypes.addAll(superKotlinTypes)
        }
        for (derivedType in derivedTypes.keys) {
            expandSuperTypeList(derivedType)
        }
    }

    private fun foldSuperTypeInformationIntoSubTypes(
        nodeTypes: List<NodeType>,
    ): Map<NodeType, CompleteNodeType> {
        // First, produce a list of all node types ordered so that when we process a subtypes
        // we have complete results from its supertypes.
        val nodeTypesSuperTypesFirst = mutableListOf<NodeType>()
        val nonLeafTypeNames = mutableSetOf<Id>()
        run {
            val nodeTypesOrdered = mutableSetOf<NodeType>()
            fun orderNodeType(nodeType: NodeType) {
                if (nodeType !in nodeTypesOrdered) {
                    nodeTypesOrdered.add(nodeType)
                    nodeType.superTypes.forEach {
                        when (it) {
                            is NodeTypeReference -> {
                                nonLeafTypeNames.add(it.nodeType.name)
                                orderNodeType(it.nodeType)
                            }
                            is KotlinCode -> {}
                        }
                    }
                    nodeTypesSuperTypesFirst.add(nodeType)
                }
            }
            nodeTypes.forEach { orderNodeType(it) }
        }
        // Create complete type definitions for each NodeType.
        val completeNodeTypes = mutableMapOf<NodeType, CompleteNodeType>()
        for (nodeType in nodeTypesSuperTypesFirst) {
            if (!nodeType.isExplicitlyDeclared) {
                gp.logSink.log(
                    OutputGrammarCodeGeneratorMessageTemplate.ImpliedNotDeclared,
                    nodeType.pos,
                    listOf(nodeType.nameWithKind),
                )
            }
            val hasSuperTypeCycle = run {
                // Look for super-type cycles.
                val supers = mutableSetOf<NodeType>() // transitive closure of super-types
                val queue = ArrayDeque<NodeType>()
                nodeType.superTypes.mapNotNullTo(queue) {
                    (it as? NodeTypeReference)?.nodeType
                }
                var hasSuperTypeCycle = false
                while (queue.isNotEmpty()) {
                    val st = queue.removeFirst()
                    if (st !in supers) {
                        if (st == nodeType) {
                            hasSuperTypeCycle = true
                            break
                        }
                        supers.add(st)
                        st.superTypes.mapNotNullTo(queue) {
                            (it as? NodeTypeReference)?.nodeType
                        }
                    }
                }
                hasSuperTypeCycle
            }
            if (hasSuperTypeCycle) {
                gp.fail(
                    nodeType.pos,
                    OutputGrammarCodeGeneratorMessageTemplate.SuperTypeCycle,
                    listOf(nodeType.name),
                )
                continue
            }

            val completeSuperKotlinTypes = mutableListOf<CompleteKotlinSuperType>()
            val completeSuperNodeTypes = mutableListOf<CompleteNodeType>()
            for (superType in nodeType.superTypes) {
                when (superType) {
                    is KotlinCode -> completeSuperKotlinTypes.add(CompleteKotlinSuperType(superType))
                    is NodeTypeReference -> {
                        val completeSuperType = completeNodeTypes[superType.nodeType]
                        if (completeSuperType != null) {
                            completeSuperNodeTypes.add(completeSuperType)
                        } else {
                            gp.fail(
                                superType.pos,
                                OutputGrammarCodeGeneratorMessageTemplate.SuperTypeCycle,
                                listOf(superType.nodeType.name),
                            )
                        }
                    }
                }
            }

            // Merge definitions from super types.
            // We might have two super-types that specialize a property's type in inconsistent
            // ways, but are mooted by `nodeType`'s specialization.
            // To handle all that, we need to collect all the info in one place before issuing any
            // error messages
            val inheritedProperties = mutableMapOf<Id, MutableList<CompleteProperty>>()
            for (superType in completeSuperNodeTypes) {
                for (inheritedProperty in superType.properties) {
                    inheritedProperties.putMulti(inheritedProperty.key, inheritedProperty.value) {
                        mutableListOf()
                    }
                }
            }

            val allPropertyNames = inheritedProperties.keys +
                nodeType.localProperties.keys +
                nodeType.propertyOverrides.keys +
                nodeType.defaultExpressions.keys

            val formatStringDigest: InheritedOrDeclared<FormatStringDigest>? =
                digestFormatString(nodeType).declaredOrNull()
                    ?: completeSuperNodeTypes.find {
                        it.formatStringDigest != null
                    }?.formatStringDigest?.content.inheritedOrNull()

            // Infer property type based on use in format string condition
            val impliedTypes = mutableMapOf<Id, PropertyType>()
            if (formatStringDigest != null) {
                for (syntaxPath in formatStringDigest.content.syntaxPaths) {
                    for (condition in syntaxPath.conditions) {
                        val wanted = condition.wanted
                        val propertyName = condition.propertyName
                        val possiblePropertyType = when (wanted) {
                            is SimpleBoolean -> KotlinCode("Boolean", condition.pos)
                            is EnumReference -> wanted.enumName?.let {
                                gp.getDeclaredPropertyType(it) as? EnumType
                            }
                            Truthy -> null
                        }
                        if (possiblePropertyType != null) {
                            impliedTypes[propertyName] = possiblePropertyType
                        }
                        // Since we're here, if there's an enum reference where we don't know the
                        // enum name, fill that in.
                        if (wanted is EnumReference && wanted.enumName == null) {
                            val propType = nodeType.localProperties[propertyName]?.propertyType
                            wanted.enumName = (propType as? NamedPropertyType)?.name
                        }
                    }
                }
            }

            val properties = allPropertyNames.mapNotNull { propertyName ->
                // If it's defined in nodeType, then ignore super-types.
                // This avoids trying to reconcile conflicting specifications where unnecessary.
                // The Kotlin compiler will check that anyway.
                val inherited = inheritedProperties[propertyName] ?: emptyList()
                val local = nodeType.localProperties[propertyName]
                // Whether any part of the property signature differs from that inherited.
                var needsDeclaration = inherited.isEmpty()
                val propertyPos = local?.pos ?: nodeType.pos.leftEdge

                val inheritedType = inherited.allMapToSameElseNull {
                    it.typeAndCount.type
                }
                val type = local?.propertyType?.resolve()
                    ?: impliedTypes[propertyName]?.resolve()
                    ?: inheritedType
                    ?: run {
                        if (inherited.isNotEmpty()) {
                            gp.fail(
                                nodeType.pos,
                                OutputGrammarCodeGeneratorMessageTemplate.InconsistentPropertyType,
                                listOf(nodeType.name, propertyName, inherited.map { it.declarer }),
                            )
                        } else {
                            gp.fail(
                                nodeType.pos,
                                OutputGrammarCodeGeneratorMessageTemplate.MissingPropertyType,
                                listOf(nodeType.name, propertyName),
                            )
                        }
                        return@mapNotNull null
                    }
                if (type != inheritedType) {
                    needsDeclaration = true
                }

                val inheritedCount = inherited.allMapToSameElseNull {
                    it.typeAndCount.count
                }
                val count = local?.propertyCount
                    ?: maybeOverridePropertyCountBasedOnFormatGrammarRequirements(
                        inheritedCount,
                        formatStringDigest?.content?.inferredCounts?.get(propertyName),
                    )
                    ?: if (inherited.isEmpty()) {
                        PropertyCount.One
                    } else {
                        gp.fail(
                            nodeType.pos,
                            OutputGrammarCodeGeneratorMessageTemplate.InconsistentPropertyCount,
                            listOf(nodeType.name, propertyName, inherited.map { it.declarer }),
                        )
                        return@mapNotNull null
                    }
                if (count != inheritedCount) { needsDeclaration = true }

                val inheritedGetterProvider = inherited.filter { it.getter != null }
                val inheritedGetter = inheritedGetterProvider.allMapToSameElseNull { it.getter }
                val getter = nodeType.propertyOverrides[propertyName]
                    ?: when {
                        local?.isOverride == true -> null
                        inheritedGetterProvider.isEmpty() -> null
                        inheritedGetter != null -> inheritedGetter
                        else -> {
                            gp.fail(
                                nodeType.pos,
                                OutputGrammarCodeGeneratorMessageTemplate.InconsistentGetter,
                                listOf(
                                    nodeType.name,
                                    propertyName,
                                    inherited.mapNotNull {
                                        if (it.getter != null) { it.declarer } else { null }
                                    },
                                ),
                            )
                            return@mapNotNull null
                        }
                    }
                if (getter != inheritedGetter) { needsDeclaration = true }
                val defaultExprProviders = inherited.filter { it.defaultExpression != null }
                val inheritedDefaultExpr = defaultExprProviders.allMapToSameElseNull {
                    it.defaultExpression
                }
                val defaultExpression = nodeType.defaultExpressions[propertyName]
                    ?: inheritedDefaultExpr
                    ?: if (defaultExprProviders.isNotEmpty()) {
                        gp.fail(
                            nodeType.pos,
                            OutputGrammarCodeGeneratorMessageTemplate.InconsistentDefault,
                            listOf(
                                nodeType.name,
                                propertyName,
                                defaultExprProviders.map {
                                    it.declarer
                                },
                            ),
                        )
                        return@mapNotNull null
                    } else {
                        null
                    }

                propertyName to CompleteProperty(
                    pos = propertyPos,
                    name = propertyName,
                    typeAndCount = PropertyTypeAndCount(type, count),
                    getter = getter,
                    defaultExpression = defaultExpression,
                    declarer = nodeType.name,
                    needsDeclaration = needsDeclaration,
                    docComment = local?.docComment,
                )
            }.toMap()

            val isConcrete = nodeType.name !in nonLeafTypeNames
            val allRequirements = (
                completeSuperNodeTypes.flatMap {
                    it.allRequirements
                } +
                    nodeType.requirements
                ).toSet().toList()

            val operatorDefinition: InheritedOrDeclared<KotlinCode>? =
                nodeType.operatorDefinition.declaredOrNull()
                    ?: run {
                        val superTypesWithOpDef = completeSuperNodeTypes.filter {
                            it.operatorDefinition != null
                        }
                        if (superTypesWithOpDef.isEmpty()) {
                            null
                        } else {
                            superTypesWithOpDef.allMapToSameElseNull {
                                it.operatorDefinition?.content
                            }.inheritedOrNull()
                                ?: run {
                                    gp.fail(
                                        nodeType.pos,
                                        OutputGrammarCodeGeneratorMessageTemplate.InconsistentOperatorDefinition,
                                        listOf(nodeType.name, superTypesWithOpDef.map { it.name }),
                                    )
                                    null
                                }
                        }
                    }

            val renderTo: InheritedOrDeclared<KotlinCode>? = nodeType.renderTo.declaredOrNull()
                ?: run {
                    val superTypesWithRenderTo = completeSuperNodeTypes.filter {
                        it.renderTo != null
                    }
                    if (superTypesWithRenderTo.isEmpty()) {
                        null
                    } else {
                        superTypesWithRenderTo.allMapToSameElseNull {
                            it.renderTo?.content
                        }.inheritedOrNull()
                            ?: run {
                                gp.fail(
                                    nodeType.pos,
                                    OutputGrammarCodeGeneratorMessageTemplate.InconsistentRenderTo,
                                    listOf(nodeType.name, superTypesWithRenderTo.map { it.name }),
                                )
                                null
                            }
                    }
                }

            var constructorArgumentOrder: List<Id>? = null
            if (isConcrete) {
                val ordered = mutableSetOf<Id>()
                val propertiesInOrder = mutableListOf<Id>()
                // Add properties used in the format string in the order they appear
                if (formatStringDigest != null) {
                    for (propertyName in formatStringDigest.content.usedInSyntax) {
                        if (properties[propertyName]?.getter == null) {
                            ordered.add(propertyName)
                            propertiesInOrder.add(propertyName)
                        }
                    }
                }

                // But we also need to put nodes before non-nodes.
                // That way, things that are flag-like appear towards the end.
                // This makes it convenient to pass the nodes in positional order, and use
                // named parameters for flags, which is especially important when node names
                // are long and flags includes booleans which are, by themselves,
                // not meaningful.
                for (prop in properties.values) {
                    if (
                        prop.typeAndCount.type is NodeType &&
                        prop.getter == null &&
                        prop.name !in ordered
                    ) {
                        ordered.add(prop.name)
                        propertiesInOrder.add(prop.name)
                    }
                }
                // Finally, order any flags.
                for (prop in properties.values) {
                    if (prop.getter == null && prop.name !in ordered) {
                        ordered.add(prop.name)
                        propertiesInOrder.add(prop.name)
                    }
                }

                constructorArgumentOrder = propertiesInOrder.toList()
            }

            val typeKind = nodeType.typeKind ?: run {
                var inherit: CompleteNodeType? = null
                val firstSuper = completeSuperNodeTypes.firstOrNull()
                if (firstSuper != null && completeSuperNodeTypes.all { it.typeKind == firstSuper.typeKind }) {
                    inherit = firstSuper
                }
                inherit?.typeKind.orDefault
            }

            // Nodes of one TypeKind should not try to store nodes of another.
            if (isConcrete) {
                for (property in properties.values) {
                    if (property.getter == null) {
                        // A backed property.
                        var propertyType = property.typeAndCount.type
                        while (propertyType is UnknownType) {
                            propertyType = propertyType.resolution ?: break
                        }
                        val propertyTypeKind = (propertyType as? NodeType)?.typeKind
                        if (propertyTypeKind != null &&
                            !mayContainProperty(container = typeKind, contained = propertyTypeKind)
                        ) {
                            gp.fail(
                                nodeType.pos,
                                OutputGrammarCodeGeneratorMessageTemplate.MayNotContainProperty,
                                listOf(
                                    "${nodeType.name}.${property.name}",
                                    propertyTypeKind,
                                ),
                            )
                        }
                    }
                }
            }

            completeNodeTypes[nodeType] = CompleteNodeType(
                pos = nodeType.pos,
                name = nodeType.name,
                original = nodeType,
                typeKind = typeKind,
                superTypes = buildList {
                    addAll(completeSuperNodeTypes)
                    addAll(completeSuperKotlinTypes)
                },
                isConcrete = isConcrete,
                properties = properties,
                allRequirements = allRequirements,
                extraBodyContent = nodeType.extraBodyContent,
                operatorDefinition = operatorDefinition,
                renderTo = renderTo,
                formatStringDigest = formatStringDigest,
                constructorArgumentOrder = constructorArgumentOrder,
                docComment = nodeType.docComment,
            )
        }
        return completeNodeTypes
    }

    private fun createNodeTypeDefinition(nodeType: CompleteNodeType): NodeTypeDefinition {
        val isConcrete = nodeType.isConcrete
        val typeKind = nodeType.typeKind
        val lPos = nodeType.pos.leftEdge

        val members = mutableListOf<MemberDefinition>()
        // Emit properties that we know about
        when (val operatorDefinition = nodeType.operatorDefinition) {
            null ->
                if (isConcrete) {
                    members.add(
                        PropertyDefinition(
                            Id("operatorDefinition"),
                            overrides = true,
                            type = operatorDefinitionType,
                            getter = KotlinCode("null", lPos),
                            setter = null,
                            containingTypeIsConcrete = isConcrete,
                            containingTypeKind = typeKind,
                            defaultExpression = null,
                            docComment = null,
                        ),
                    )
                }
            is Declared -> {
                val sourceText = operatorDefinition.content.sourceText
                val type = if (sourceText.trim() == "null") {
                    // Avoid warnings that Nothing? is inferred type
                    PropertyTypeAndCount(operatorDefinitionType.type, PropertyCount.ZeroOrOne)
                } else {
                    // Let null-ness be inferred from type
                    null
                }
                members.add(
                    PropertyDefinition(
                        Id("operatorDefinition"),
                        overrides = true,
                        type = type,
                        getter = operatorDefinition.content,
                        setter = null,
                        containingTypeIsConcrete = isConcrete,
                        containingTypeKind = typeKind,
                        defaultExpression = null,
                        docComment = null,
                    ),
                )
            }
            is Inherited -> Unit
        }
        val renderTo = nodeType.renderTo
        if (renderTo is Declared) {
            members.add(
                MethodDefinition(
                    Id("renderTo"),
                    overrides = true,
                    params = listOf(
                        Id("tokenSink") to PropertyTypeAndCount(
                            KotlinCode("TokenSink", renderTo.content.pos),
                            PropertyCount.One,
                        ),
                    ),
                    returnType = null,
                    body = renderTo.content,
                ),
            )
        }

        members.addAll(
            maybeDefineFormatStringProperty(gp, nodeType),
        )

        val initCode = mutableListOf<KotlinCode>()
        val constructorArguments = nodeType.constructorArgumentOrder?.toSet() ?: emptySet()
        val propertyDeclOrder = (nodeType.constructorArgumentOrder ?: emptyList()) +
            nodeType.properties.entries.mapNotNull { (name, property) ->
                if (name in constructorArguments || !property.needsDeclaration) {
                    null
                } else {
                    name
                }
            }

        fun isChildProperty(p: CompleteProperty) =
            (p.typeAndCount.type as? NodeType)?.typeKind == typeKind
        for (propName in propertyDeclOrder) {
            val prop = nodeType.properties[propName] ?: continue
            val (type, count) = prop.typeAndCount
            val propertyOverride = prop.getter

            val typeCode = when (type) {
                is NodeType -> type.name.text
                is EnumType -> type.name.text
                is KotlinCode -> type.sourceText
                // When someone defines a property like
                //     TypeName.propertyName = `...`
                // we can let Kotlin do type inference on the getter defined in `...`
                is UnknownType -> if (propertyOverride == null) { "/*ERROR*/Any?" } else { null }
            }
            val typeAndCount = typeCode?.let {
                PropertyTypeAndCount(KotlinCode(it, type.pos), count)
            }
            val isChild = isChildProperty(prop)

            val (getter, setter) = when {
                propertyOverride != null -> propertyOverride to null
                type is NodeType && isConcrete && typeKind == TypeKind.Ast -> {
                    // Create private a backing property to hold the node.
                    // And create a setter that takes care to maintain parent/child links.
                    val backing = "$BACKED_PROPERTY_NAME_PREFIX${propName.text}"
                    val backingPropertyName = Id(backing)
                    members.add(
                        PropertyDefinition(
                            name = backingPropertyName,
                            overrides = false,
                            type = typeAndCount,
                            getter = null,
                            setter = null,
                            containingTypeIsConcrete = true,
                            containingTypeKind = typeKind,
                            defaultExpression = null,
                            visibility = KotlinVisibility.Private,
                            docComment = null,
                        ),
                    )
                    // Copy constructor parameter to backing property via setter
                    initCode.add(
                        KotlinCode(
                            when {
                                isChild && count == PropertyCount.Many ->
                                    // Do a list copy into existing mutableList
                                    "updateTreeConnections(this.$backing, ${propName.text})"
                                isChild ->
                                    // Give protected helper first access
                                    "this.$backing = updateTreeConnection(null, ${propName.text})"
                                count == PropertyCount.Many ->
                                    "this.$backing.addAll(${propName.text})"
                                else ->
                                    "this.$backing = ${propName.text}"
                            },
                            prop.pos,
                        ),
                    )
                    Pair(
                        // getter
                        KotlinCode(backing, prop.pos),
                        // setter
                        KotlinCode(
                            when {
                                isChild && count == PropertyCount.Many ->
                                    "updateTreeConnections($backing, $SETTER_ARG_NAME)"
                                isChild ->
                                    "$backing = updateTreeConnection($backing, $SETTER_ARG_NAME)"
                                count == PropertyCount.Many ->
                                    "$backing.replaceSubList(0, $backing.size, $SETTER_ARG_NAME)"
                                else ->
                                    "$backing = $SETTER_ARG_NAME"
                            },
                            prop.pos,
                        ),
                    )
                }
                else -> null to null
            }
            if (type is UnknownType && getter == null) {
                gp.fail(
                    prop.pos,
                    MessageTemplate.MissingType,
                    listOf("${nodeType.name}.${propName.text}"),
                )
            }
            val overrides =
                nodeType.superTypes.any { it is CompleteNodeType && propName in it.properties }
            val defaultExpression: KotlinCode? = prop.defaultExpression
            members.add(
                PropertyDefinition(
                    propName,
                    overrides = overrides,
                    type = typeAndCount,
                    getter = getter,
                    setter = setter,
                    containingTypeIsConcrete = isConcrete,
                    containingTypeKind = typeKind,
                    defaultExpression = defaultExpression,
                    docComment = prop.docComment,
                ),
            )
        }

        // Every AST node type has its own deepCopy operator
        // Even if this is an interface, users of the interface should be guaranteed to get an
        // instance of the interface, so we return just to adjust the return type.
        if (typeKind == TypeKind.Ast) {
            members.add(
                MethodDefinition(
                    Id("deepCopy"),
                    overrides = true,
                    params = emptyList(),
                    returnType = PropertyTypeAndCount(nodeType.original, PropertyCount.One),
                    body = if (isConcrete) {
                        KotlinCode(
                            "return ${nodeType.name}(pos${
                                constructorArguments.joinToString("") { argName ->
                                    val prop = nodeType.properties[argName]
                                        ?: return@joinToString ", ERROR"
                                    val (_, propName: Id, typeAndCount: PropertyTypeAndCount) = prop
                                    ", ${propName.text} = this.${propName.text}${
                                        if (isChildProperty(prop)) {
                                            when (typeAndCount.count) {
                                                PropertyCount.ZeroOrOne -> "?.deepCopy()"
                                                PropertyCount.One -> ".deepCopy()"
                                                PropertyCount.Many -> ".deepCopy()"
                                            }
                                        } else {
                                            ""
                                        }
                                    }"
                                }
                            })",
                            lPos,
                        )
                    } else {
                        null
                    },
                ),
            )
        }

        // Generate child member relationships as extra code
        val needsCmr = when (nodeType.formatStringDigest) {
            null -> isConcrete
            is Declared -> true
            is Inherited -> false
        }

        val companionObjectCode = if (needsCmr) {
            val formatStringDigest = nodeType.formatStringDigest?.content
            members.add(
                PropertyDefinition(
                    Id("childMemberRelationships"),
                    overrides = true,
                    type = null,
                    getter = KotlinCode("cmr", lPos),
                    setter = null,
                    containingTypeIsConcrete = isConcrete,
                    containingTypeKind = typeKind,
                    defaultExpression = null,
                    docComment = null,
                ),
            )
            val usedInSyntax = formatStringDigest?.usedInSyntax ?: emptySet()
            val childMemberProperties = buildSet {
                // Children should include them in syntax order then if not used in syntax, but are
                // actually backed properties the constructor arguments.
                // Including constructorArguments is important so that moving a tree from
                // using output syntax to using a custom `renderTo` method does not affect
                // tree traversal via `.children`.
                for (idList in listOf(usedInSyntax, constructorArguments)) {
                    for (propertyName in idList) {
                        if (propertyName !in this) {
                            val prop = nodeType.properties[propertyName]
                            if (prop != null) {
                                if (isChildProperty(prop) && prop.getter == null) {
                                    add(propertyName)
                                }
                            } else {
                                // Some other error must have triggered.
                                // Make sure we've got an error about this.
                                gp.logSink.log(
                                    OutputGrammarCodeGeneratorMessageTemplate.MissingPropertyInfo,
                                    nodeType.pos,
                                    listOf(
                                        nodeType.nameWithKind,
                                        propertyName,
                                    ),
                                )
                            }
                        }
                    }
                }
            }.toList()
            KotlinCode(
                sprintf(
                    """
                        |companion object {
                        |    private val cmr = ChildMemberRelationships(%s%s)
                        |}
                    """.trimMargin(),
                    listOf(
                        childMemberProperties.joinToString("") { propName ->
                            "\n        { n -> (n as ${nodeType.name}).$propName },"
                        },
                        if (childMemberProperties.isNotEmpty()) { "\n    " } else { "" },
                    ),
                ),
                lPos,
            )
        } else {
            null
        }

        val superTypes = mutableListOf<KotlinCode>()
        superTypes.add(
            KotlinCode(
                when {
                    isConcrete && typeKind == TypeKind.Ast -> commonSuperTreeClassName
                    !isConcrete && typeKind == TypeKind.Ast -> commonSuperTreeInterfaceName
                    isConcrete && typeKind == TypeKind.Data -> commonSuperDataClassName
                    !isConcrete && typeKind == TypeKind.Data -> commonSuperDataInterfaceName
                    else -> error("typeKind=$typeKind")
                }.text,
                nodeType.pos.leftEdge,
            ),
        )
        for (superType in nodeType.superTypes) {
            if (superType is CompleteNodeType && superType.typeKind != typeKind) {
                gp.logSink.log(
                    Log.Fatal,
                    OutputGrammarCodeGeneratorMessageTemplate.DataTypeCannotMixWithAstType,
                    nodeType.original.superTypes.mapFirst {
                        if (it is NodeTypeReference && it.nodeType == superType.original) {
                            it
                        } else {
                            null
                        }
                    }!!.pos,
                    listOf(nodeType.nameWithKind, superType.nameWithKind),
                )
            }
            superTypes.add(
                when (superType) {
                    is CompleteNodeType -> KotlinCode(superType.name.text, nodeType.pos.leftEdge)
                    is CompleteKotlinSuperType -> superType.kotlinType
                },
            )
        }

        val requirementsCodeLines = mutableListOf<String>()
        if (isConcrete) {
            val allRequirements = nodeType.allRequirements
            // Turn requirements into code lines
            for (requirement in allRequirements) {
                when (requirement) {
                    is PropertyRequirement -> {
                        val count =
                            nodeType.properties[requirement.propertyName]?.typeAndCount?.count
                        if (count != PropertyCount.One) {
                            val nameText = requirement.propertyName.text
                            requirementsCodeLines.add(
                                "require(OutTree.propertyValueTruthy(this.$nameText))",
                            )
                        }
                    }
                    is CodeRequirement -> {
                        val lines = requirement.kotlinCode.sourceText.split(newlinePattern).filter {
                            it.isNotBlank()
                        }
                        if (lines.size == 1) {
                            requirementsCodeLines.add("require(${lines[0]})")
                        } else {
                            requirementsCodeLines.add("require(")
                            lines.forEachIndexed { i, it ->
                                val suffix = if (i == lines.lastIndex) { "," } else { "" }
                                requirementsCodeLines.add("    $it$suffix")
                            }
                            requirementsCodeLines.add(")")
                        }
                    }
                }
            }
        }

        if (isConcrete && typeKind == TypeKind.Ast) {
            // Override .equals and .hashCode for structural equivalence that ignores pos metadata.
            // For TypeKind.Data we don't have pos metadata so just use the Kotlin generated equals/hashCode.
            members.add(
                MethodDefinition(
                    name = Id("equals"),
                    overrides = true,
                    params = listOf(
                        Id("other") to
                            PropertyTypeAndCount(KotlinCode("Any", lPos), PropertyCount.ZeroOrOne),
                    ),
                    returnType = PropertyTypeAndCount(KotlinCode("Boolean", lPos), PropertyCount.One),
                    body = KotlinCode(
                        "return other is ${nodeType.name.text}${
                            constructorArguments.joinToString("") { propertyName ->
                                val propertyNameText = propertyName.text
                                " && this.$propertyNameText == other.$propertyNameText"
                            }
                        }",
                        lPos,
                    ),
                ),
            )
            members.add(
                MethodDefinition(
                    name = Id("hashCode"),
                    overrides = true,
                    params = listOf(),
                    returnType = PropertyTypeAndCount(KotlinCode("Int", lPos), PropertyCount.One),
                    body = KotlinCode(
                        toStringViaBuilder { bodyCode ->
                            fun argHashFor(constructorArgument: Id): String {
                                val count = nodeType.properties[constructorArgument]
                                    ?.typeAndCount?.count
                                return if (count == PropertyCount.ZeroOrOne) {
                                    "(${constructorArgument.text}?.hashCode() ?: 0)"
                                } else {
                                    "${constructorArgument.text}.hashCode()"
                                }
                            }

                            val constructorArgumentsList = constructorArguments.toList()
                            when (constructorArgumentsList.size) {
                                0 -> bodyCode.append("return 0")
                                1 -> bodyCode.append("return ${argHashFor(constructorArgumentsList.first())}")
                                else -> {
                                    bodyCode.append("var hc = ")
                                    bodyCode.append(argHashFor(constructorArgumentsList.first()))
                                    bodyCode.append('\n')
                                    for (constructorArgument in constructorArgumentsList.subListToEnd(1)) {
                                        val argHash = argHashFor(constructorArgument)
                                        bodyCode.append("hc = 31 * hc + ")
                                        bodyCode.append(argHash)
                                        bodyCode.append('\n')
                                    }
                                    bodyCode.append("return hc")
                                }
                            }
                        },
                        lPos,
                    ),
                ),
            )
        }

        return NodeTypeDefinition(
            name = nodeType.name,
            typeKind = nodeType.typeKind,
            isConcrete = isConcrete,
            superTypes = superTypes.toList(),
            members = members,
            extraCode = mergeExtraClassBodyCode(
                listOfNotNull(
                    if (initCode.isNotEmpty()) {
                        KotlinCode(
                            "init {\n${
                                initCode.joinToString("\n") { "    ${it.sourceText}" }
                            }\n}\n",
                            lPos,
                        )
                    } else {
                        null
                    },
                    nodeType.extraBodyContent,
                    companionObjectCode,
                    // Check requirements last of all after child links have been set up.
                    if (requirementsCodeLines.isNotEmpty()) {
                        KotlinCode(
                            "init {\n${
                                requirementsCodeLines.joinToString("\n") { "    $it" }
                            }\n}\n",
                            lPos,
                        )
                    } else {
                        null
                    },
                ),
                nodeType.pos,
                gp.logSink,
            ),
            docComment = nodeType.docComment,
        )
    }
}

/**
 * The code generator needs to put some things in `companion object { ... }` and definitions in the
 * grammar file can do the same.
 *
 * It's easier to merge them post-facto by classifying lines as *companion-object* lines and
 * other lines.
 *
 * This is not resistant to multiline comment and multiline string tokens since it does not do a
 * full parse.
 */
private fun mergeExtraClassBodyCode(
    codeBlocks: Iterable<KotlinCode>,
    pos: Position,
    logSink: LogSink,
): KotlinCode? {
    // Lines from merged `init { ... }` blocks
    val initLines = mutableListOf<String>()
    // Lines from merged `companion object { ... }` blocks
    val companionObjectLines = mutableListOf<String>()
    val otherLines = mutableListOf<String>()

    for (codeBlock in codeBlocks) {
        // If we see a line like `companion object {` we look for a line
        // like `}` with the same indentation.
        // This variable holds that indenting whitespace.
        // (I know this is a hack.  Indent your code.)
        var closingPrefix: String? = null
        var lineReceiver = otherLines
        for (line in codeBlock.sourceText.split(newlinePattern)) {
            when {
                // handle `companion object`
                closingPrefix == null && companionObjectPattern.matches(line) -> {
                    closingPrefix = companionObjectPattern.find(line)!!.groups[1]!!.value
                    lineReceiver = companionObjectLines
                    continue
                }
                // handle `init`
                closingPrefix == null && initBlockPattern.matches(line) -> {
                    closingPrefix = initBlockPattern.find(line)!!.groups[1]!!.value
                    lineReceiver = initLines
                    continue
                }
                // Handle matching `}`
                closingPrefix != null &&
                    closeCurlyPattern.find(line)?.groups?.get(1)?.value == closingPrefix -> {
                    closingPrefix = null
                    lineReceiver = otherLines
                    continue
                }
                else -> {
                    lineReceiver.add(line)
                }
            }
        }
        if (closingPrefix != null) {
            logSink.log(
                level = Log.Fatal,
                template = OutputGrammarCodeGeneratorMessageTemplate.UnmergeableKotlinCode,
                pos = pos,
                values = emptyList(),
            )
        }
    }

    // Remove adjacent blank lines
    for (lineList in listOf(companionObjectLines, otherLines)) {
        for (i in lineList.lastIndex downTo 0) {
            if (
                lineList[i].isBlank() &&
                (i + 1 == lineList.size || i == 0 || lineList[i - 1].isBlank())
            ) {
                lineList.removeAt(i)
            }
        }
    }

    val allLines = mutableListOf<String>()
    if (initLines.isNotEmpty()) {
        allLines.add("init {")
        allLines.addAll(initLines)
        allLines.add("}")
    }
    if (companionObjectLines.isNotEmpty()) {
        allLines.add("companion object {")
        allLines.addAll(companionObjectLines)
        allLines.add("}")
    }
    allLines.addAll(otherLines)

    return if (allLines.isEmpty()) {
        null
    } else {
        KotlinCode(allLines.joinToString("\n"), pos)
    }
}

private fun maybeOverridePropertyCountBasedOnFormatGrammarRequirements(
    inheritedCount: PropertyCount?,
    inferredFromGrammar: PropertyCount?,
): PropertyCount? = when {
    inferredFromGrammar == null -> inheritedCount
    inheritedCount == null -> inferredFromGrammar
    // If the grammar requirements narrow the count, then use that.
    // For example, when the super-type says
    //     foo%Foo?
    // but the sub-type's grammar says
    //     "foo" & "=" & foo
    // then the sub-type needs to override `foo` to be non-nullable.
    inheritedCount == PropertyCount.ZeroOrOne && inferredFromGrammar == PropertyCount.One ->
        PropertyCount.One
    else -> inheritedCount
}

private val initBlockPattern = Regex("""^(\s*)init\s*[{]\s*$""")
private val companionObjectPattern = Regex("""^(\s*)companion\s+object\s*[{]\s*$""")
internal const val BACKED_PROPERTY_NAME_PREFIX = "_"

private fun <T : Any> (T?).inheritedOrNull() =
    if (this != null) { Inherited(this) } else { null }
private fun <T : Any> (T?).declaredOrNull() =
    if (this != null) { Declared(this) } else { null }

internal fun mayContainProperty(
    container: TypeKind,
    contained: TypeKind,
): Boolean = when {
    container == contained -> true
    contained == TypeKind.Data -> true
    // Data may not contain non-serializable
    else -> false
}
