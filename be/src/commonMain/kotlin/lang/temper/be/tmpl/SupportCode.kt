package lang.temper.be.tmpl

import lang.temper.ast.OutTree
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.log.unknownPos
import lang.temper.name.DashedIdentifier
import lang.temper.name.ParsedName
import lang.temper.type2.Descriptor
import lang.temper.type2.Type2
import lang.temper.value.BuiltinOperatorId
import lang.temper.value.MetadataMap

/**
 * Support code includes snippets of code that a backend inlines as needed by the library being
 * compiled.
 *
 * One piece of support code might depend on others.  For example, support code for builtin `==`
 * and `<` operators might depend on a trinary *compare* function.  Or, in a different backend,
 * a trinary compare might depend on standalone `==` and `<` operators instead.
 */
sealed interface SupportCode : TokenSerializable {
    val builtinOperatorId: BuiltinOperatorId? get() = null

    /**
     * Support codes that this requires.
     * This allows one support code to require another one.
     *
     * This is especially useful for [InlineSupportCode] that might need to import or otherwise
     * refer to code in an external library.  For example, the JS support code for integer
     * division needs access to `Math.trunc` regardless of whether the name `Math` is masked.
     * So it uses this to make sure that the local scope has something like the below by
     * adding a dependency that is recognized as unpacking a property from a builtin
     * by the JS translator.
     *
     *     const { trunc: trunc_123 } = globalThis.Math;
     *
     * It is the responsibility of [SupportNetwork] implementations to ensure that there
     * are no cycles between requirements so that support codes can be emitted without
     * forward reference in *TmpL*.
     */
    val requires: List<SupportCodeRequirement> get() = emptyList()
}

/** Allows us to list dependencies */
sealed class SupportCodeRequirement

/** A dependency from support code on other support code */
data class OtherSupportCodeRequirement(
    val required: SupportCode,
    val type: Descriptor,
    val metadataMap: MetadataMap = emptyMap(),
) : SupportCodeRequirement()

/** A dependency from support code on a library */
data class LibrarySupportCodeRequirement(
    val libraryName: DashedIdentifier,
) : SupportCodeRequirement()

/** Support code that provides a hint as to the name used when generating shared definitions. */
interface NamedSupportCode : SupportCode {
    /** A name hint for the [TmpL.SupportCodeDeclaration]  */
    val baseName: ParsedName?
}

/** Support code that may be called such as that generated from a builtin function. */
interface FunctionSupportCode : SupportCode

/**
 * Bundles the argument expression and its original type, to assist in cases where the type field is thrown
 * away by the backend translation.
 */
data class TypedArg<T> (val expr: T, val type: Type2)

/**
 * Support code that the [SupportNetwork] intends to be inlined.
 * For example, calls to `String::isEmpty` could be inlined to a use of a builtin operator
 * on some backends.
 *
 * Instances may be inlined when used in [TmpL.CallExpression.fn].
 *
 * @param <TREE> the kind of AST produced.
 */
interface InlineSupportCode<TREE : OutTree<TREE>, TRANSLATOR> : FunctionSupportCode {
    /**
     * If the arguments need `this` as the first argument
     */
    val needsThisEquivalent: Boolean

    /** Inlines a call to a native expression. */
    fun inlineToTree(
        /** The position for the call being translated */
        pos: Position,
        /** The translated arguments */
        arguments: List<TypedArg<TREE>>,
        /** The expected result type of the call */
        returnType: Type2,
        /**
         * An object that directs translation.
         * This is meaningful to the [SupportNetwork] that produced this support code,
         * so the support code should be able to cast this to a meaningful type.
         */
        translator: TRANSLATOR,
    ): TREE
}

/**
 * An [InlineSupportCode] that the [TmpLTranslator] will attempt to inline
 * during [TmpL] tree generation.
 */
interface InlineTmpLSupportCode : InlineSupportCode<TmpL.Tree, TmpLTranslator> {
    override fun inlineToTree(
        pos: Position,
        arguments: List<TypedArg<TmpL.Tree>>,
        returnType: Type2,
        translator: TmpLTranslator,
    ): TmpL.Expression

    companion object {
        fun of(
            needsThis: Boolean = false,
            inline: (
                Position,
                List<TypedArg<TmpL.Tree>>,
                Type2?,
                translator: TmpLTranslator?,
                strict: Boolean,
            ) -> TmpL.Expression,
        ) = object : InlineTmpLSupportCode {
            override fun inlineToTree(
                pos: Position,
                arguments: List<TypedArg<TmpL.Tree>>,
                returnType: Type2,
                translator: TmpLTranslator,
            ): TmpL.Expression = inline(pos, arguments, returnType, translator, true)

            override val needsThisEquivalent: Boolean get() = needsThis

            override fun renderTo(tokenSink: TokenSink) {
                val e = inline(unknownPos, listOf(), null, null, false)
                (e as TmpL.BaseTree).formatTo(tokenSink)
            }
        }
    }
}

/**
 * A reference to code compiled separately.
 */
interface SeparatelyCompiledSupportCode : SupportCode {
    val source: DashedIdentifier
    val stableKey: ParsedName

    override val requires: List<SupportCodeRequirement>
        get() = listOf(LibrarySupportCodeRequirement(source))
}
