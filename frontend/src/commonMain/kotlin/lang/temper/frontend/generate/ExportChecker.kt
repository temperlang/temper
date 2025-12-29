package lang.temper.frontend.generate

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Log
import lang.temper.frontend.Module
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.ExportedName
import lang.temper.name.Symbol
import lang.temper.type.MethodKind
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Descriptor
import lang.temper.type2.NonNullType
import lang.temper.type2.PositionedType
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.hackSynthesizedFunInterfaceSymbol
import lang.temper.type2.withType
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.TType
import lang.temper.value.functionContained
import lang.temper.value.impliedThisSymbol
import lang.temper.value.nameContained
import lang.temper.value.returnDeclSymbol
import lang.temper.value.symbolContained
import lang.temper.value.typeSymbol
import lang.temper.value.wordSymbol

internal class ExportChecker(val module: Module) {
    private val exportedTypeNames = buildSet exportedTypes@{
        exports@ for (export in module.exports ?: listOf()) {
            val value = export.value ?: continue@exports
            val type = TType.unpackOrNull(value)?.type2 as? NonNullType ?: continue@exports
            add(type.definition.name)
        }
    }

    private val funTrees = buildMap {
        TreeVisit.startingAt(module.generatedCode!!).forEachContinuing { tree ->
            tree is CallTree && tree.size > 2 || return@forEachContinuing
            tree.child(0).functionContained == BuiltinFuns.setLocalFn || return@forEachContinuing
            val name = tree.child(1).nameContained ?: return@forEachContinuing
            val function = (tree.child(2) as? FunTree) ?: return@forEachContinuing
            put(name, function)
        }.visitPreOrder()
    }

    fun checkExports() {
        exportChecks@ for (export in module.exports ?: return) {
            val value = export.value
            when (value?.typeTag) {
                is TType -> TType.unpack(value).type2.let { type ->
                    (type as? NonNullType)?.let { checkTypeDefinition(it.definition) }
                }

                else -> checkTypeRefs(
                    export.typeInferences?.type?.let { hackMapOldStyleToNew(it) },
                    pos = export.position,
                    value = funTrees[export.name],
                )
            }
        }
    }

    private fun checkSignature2(
        sig: Signature2,
        funTree: FunTree?,
        pos: Position,
        reportedProperties: Set<Symbol> = setOf(),
        valueFormalsOnly: Boolean = false,
    ): Boolean {
        val parts = funTree?.parts ?: return checkSignatureAlone(sig, pos, valueFormalsOnly = valueFormalsOnly)
        var anyErrors = false
        // Type formal bounds.
        if (!valueFormalsOnly) {
            anyErrors = anyErrors or checkTypeFormals(parts.typeFormals.mapNotNull { it.second })
        }
        // Formals.
        formals@ for (formal in parts.formals) {
            val formalParts = formal.parts
            val formalType = formalParts?.name?.typeInferences?.type?.let { hackMapOldStyleToNew(it) }
                ?: continue@formals
            impliedThisSymbol in formalParts.metadataSymbolMap && continue@formals
            formalParts.metadataSymbolMap[wordSymbol]?.symbolContained in reportedProperties && continue@formals
            val formalTypePos = formalParts.metadataSymbolMap[typeSymbol]?.target?.pos ?: continue@formals
            anyErrors = anyErrors or checkTypeRefs(formalType, pos = formalTypePos)
        }
        // Return type.
        if (!valueFormalsOnly) {
            val returnPos = parts.metadataSymbolMap[returnDeclSymbol]?.target?.pos
            anyErrors = anyErrors or checkTypeRefs(sig.returnType2, pos = returnPos ?: pos)
        }
        return anyErrors
    }

    private fun checkSignatureAlone(sig: Signature2, pos: Position, valueFormalsOnly: Boolean): Boolean {
        var anyErrors = false
        // Type formal bounds.
        if (!valueFormalsOnly) {
            anyErrors = anyErrors or checkTypeFormals(sig.typeFormals)
        }
        // Formals.
        formals@ for (formal in sig.allValueFormals) {
            val formalType = formal.type
            anyErrors = anyErrors or checkTypeRefs(formalType, pos = pos)
        }
        // Return type.
        if (!valueFormalsOnly) {
            anyErrors = anyErrors or checkTypeRefs(sig.returnType2, pos = pos)
        }
        return anyErrors
    }

    private fun checkType2(type: Type2, pos: Position): Boolean {
        // Check the bindings.
        for (binding in type.bindings) {
            checkTypeRefs(binding, pos = (binding as? PositionedType)?.pos ?: pos)
        }

        // If the name is exported or is well-known, it's ok.
        val definition = type.definition
        val name = definition.name
        name is ExportedName && return false
        name in exportedTypeNames && return false
        isWellKnown(type) && return false

        // Check the type itself.
        when (definition) {
            // If it's a type formal, it's ok.
            is TypeFormal -> return false
            is TypeShape -> {
                // If it's from another location, don't worry about it.
                // Types can be aliased by local declarations, including those
                // generated by import.
                // Let each module worry about its own exports.
                val definitionLocation = definition.stayLeaf?.document?.nameMaker?.namingContext?.loc
                if (definitionLocation != module.loc) {
                    return false
                }

                // None of the ok options worked out, so error.
                module.logSink.log(
                    level = Log.Error,
                    template = MessageTemplate.ExportNeedsNonExported,
                    pos = pos,
                    values = listOf(name.displayName),
                )
                return true
            }
        }
    }

    private fun checkTypeDefinition(def: TypeDefinition) {
        val shape = def as? TypeShape
        // General stay leaf includes constructor.
        // val pos = shape?.stayLeaf?.pos ?: def.pos
        // Name alone excludes extends clause, but less likely to be huge or include other types.
        val pos = (shape?.stayLeaf?.incoming?.source as? DeclTree)?.parts?.name?.pos ?: def.pos
        // Param bounds.
        for (formal in def.formals) {
            for (bound in formal.upperBounds) {
                checkType2(hackMapOldStyleToNew(bound), pos = pos)
            }
        }
        // Super types.
        for (type in def.superTypes) {
            // TODO More precise pos. And I'm not sure this one is even in the tree by now.
            // TODO Add stay leaves early on for extends clauses?
            checkType2(hackMapOldStyleToNew(type), pos = pos)
        }
        // Instance properties, which can be better positioned than generated methods.
        shape == null && return
        val reportedProperties = mutableSetOf<Symbol>()
        properties@ for (member in shape.properties) {
            member.visibility == Visibility.Private && continue@properties
            member.descriptor?.let { memberType ->
                val memberDecl = member.stay?.incoming?.source as? DeclTree
                val memberTypePos = memberDecl?.parts?.metadataSymbolMap?.get(typeSymbol)?.target?.pos
                val anyErrors = checkTypeRefs(memberType, pos = memberTypePos ?: pos)
                if (anyErrors) {
                    // Remember properties with errors so we don't double report on accessor methods.
                    reportedProperties.add(member.symbol)
                }
            }
        }
        // Instance methods.
        methods@ for (member in shape.methods) {
            member.visibility == Visibility.Private && continue@methods
            member.symbol in reportedProperties && continue@methods
            val isConstructor = member.methodKind == MethodKind.Constructor
            member.descriptor?.let { memberSig ->
                checkSignature2(
                    memberSig,
                    funTree = funTrees[member.name],
                    pos = pos,
                    reportedProperties = when {
                        // And no easy access to constructor params property status, but infer by name.
                        // The main thing here is to avoid duplicate reports, which are less than ideal.
                        isConstructor -> reportedProperties
                        else -> setOf()
                    },
                    valueFormalsOnly = member.methodKind == MethodKind.Constructor,
                )
            }
        }
        // Static methods.
        staticMethods@ for (member in shape.staticProperties) {
            member.visibility == Visibility.Private && continue@staticMethods
            member.descriptor?.let { memberDescriptor ->
                val memberDecl = member.stay?.incoming?.source as? DeclTree
                val memberNamePos = memberDecl?.parts?.name?.pos
                checkTypeRefs(memberDescriptor, pos = memberNamePos ?: pos, value = funTrees[member.name])
            }
        }
    }

    private fun checkTypeFormals(typeFormals: Iterable<TypeFormal>): Boolean {
        var anyErrors = false
        typeFormals@ for (typeFormal in typeFormals) {
            val formalPos = typeFormal.pos
            for (bound in typeFormal.upperBounds) {
                anyErrors = anyErrors or checkType2(hackMapOldStyleToNew(bound), pos = formalPos)
            }
        }
        return anyErrors
    }

    private fun checkTypeRefs(desc: Descriptor?, pos: Position, value: Any? = null): Boolean {
        return when (desc) {
            null -> false
            is Type2 -> {
                (
                    hackSynthesizedFunInterfaceSymbol in desc.definition.metadata ||
                        // ^ Do not complain about Fn__123 not being exported
                        checkType2(desc, pos = pos)
                    ) or
                    withType(
                        desc,
                        fallback = { false },
                        fn = { _, sig, _ ->
                            checkTypeRefs(sig, pos = pos, value = value)
                        },
                    )
            }
            is Signature2 -> checkSignature2(desc, funTree = value as? FunTree, pos = pos)
        }
    }
}

private fun isWellKnown(type: Type2) =
    (type.definition as? TypeShape)?.let { WellKnownTypes.isWellKnown(it) } == true
