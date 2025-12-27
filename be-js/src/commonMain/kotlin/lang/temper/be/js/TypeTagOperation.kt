package lang.temper.be.js

import lang.temper.be.tmpl.TmpL
import lang.temper.log.Position
import lang.temper.type.TypeShape
import lang.temper.value.TypeTag

/**
 * Allows re-using strategies relating Temper [type tag][TypeTag]s to JS type introspection
 * to produce JS runtime-code.
 */
internal interface TypeTagOperation<in IN, out OUT> {
    /**
     * Called when all values of [type] when passed to JavaScript's `typeof` operator
     * produce the same result: [jsTypeOf], and only those values produce that result.
     *
     * The operation may find it convenient to do
     *
     *     "${jsTypeof.stringValue}" === typeof x
     *
     * in implementing the operation.
     *
     * @param [pos] may be used when [OUT] is an AST that needs position metadata.
     */
    fun allHaveTypeof(
        pos: Position,
        type: TmpL.Type,
        jsTypeOf: JsTypeOf,
        x: IN,
    ): OUT

    /**
     * Called when there is only one value of [type] that we can compare to.
     * This is true for Temper's [*Null*][lang.temper.value.TNull] and
     * [*Void*][lang.temper.value.TVoid] types.
     *
     * The operation may find it convenient to do
     *
     *     x === soleValue
     *
     * in implementing the operation.
     *
     * Our runtime-support code defines needed sets based on the sorted and joined `typeof` values.
     * So there is a `typeofSetBigintNumber`.
     *
     * @param jsValue produces a side effect free, efficiently copyable, JS expression that
     *    can be compared to using `===` (so not `NaN`).
     *    The caller may import names into the JS scope being processed to make [jsValue] valid,
     *    so this expression may not be readily transported across module boundaries.
     */
    fun singleton(
        pos: Position,
        type: TmpL.Type,
        jsValue: Js.Expression,
        x: IN,
    ): OUT

    /**
     * Called when the [type] is [*Int*][lang.temper.value.TInt].
     * This operation may assume that Temper's static analysis has ruled out
     * [*Float64*][lang.temper.value.TFloat64] in the context of [x].
     *
     * The operation may find it convenient to do
     *
     *     Number.isSafeInteger(x) // Note: excludes bigints
     *
     * in implementing the operation.
     */
    fun isInt(
        pos: Position,
        type: TmpL.Type,
        x: IN,
    ): OUT

    /**
     * Called when the [type] is [*Float64*][lang.temper.value.TFloat64].
     * This operation may assume that Temper's static analysis has ruled out
     * [*Int*][lang.temper.value.TInt] in the context of [x].
     *
     * The operation may find it convenient to do
     *
     *     typeof x === 'number'
     *
     * in implementing the operation.
     */
    fun isFloat64(
        pos: Position,
        type: TmpL.Type,
        x: IN,
    ): OUT = allHaveTypeof(pos, type, JsTypeOf.number, x)

    /**
     * Called when all values in [type] are represented using JS class or something
     * else that can validly appear on the right of a JS `instanceof` check.
     *
     * The operation may find it convenient to do
     *
     *     x instanceof JsTypeName
     *
     * @param [jsTypeRef] A reference to the Temper type name in the current scope.
     *     The caller may import names to make this valid, so do not assume this type name can
     *     move across module boundaries.
     */
    fun instanceOf(
        pos: Position,
        type: TmpL.Type,
        typeShape: TypeShape,
        jsTypeRef: Js.Identifier,
        x: IN,
    ): OUT

    /**
     * Called when all values in [type] are represented using JS arrays.
     *
     * The operation may find it convenient to do
     *
     *     Array.isArray(x)
     */
    fun isArray(
        pos: Position,
        type: TmpL.Type,
        x: IN,
    ): OUT
}
