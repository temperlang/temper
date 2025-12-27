package lang.temper.be.csharp

import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.TypedArg
import lang.temper.be.tmpl.isPureVirtual
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.log.spanningPosition
import lang.temper.name.OutName
import lang.temper.name.TemperName
import lang.temper.name.identifiers.IdentStyle
import lang.temper.type.Abstractness
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Nullity
import lang.temper.type2.Type2

fun TmpL.BlockStatement?.isPureVirtual() = isPureVirtual(pureVirtualBuiltin)

fun CSharp.Expression.returnOrNot(pos: Position, returnType: TmpL.Type): CSharp.Statement {
    return when {
        returnType.isVoidish() -> CSharp.ExpressionStatement(pos, this as CSharp.StatementExpression)
        else -> CSharp.ReturnStatement(pos, this)
    }
}

fun CSharp.Expression.returnOrNot(pos: Position, returnType: TmpL.AType): CSharp.Statement =
    this.returnOrNot(pos, returnType.ot)

fun CSharp.Expression.wrapCollectionTypeIfNeeded(
    type: TypeDefinition?,
    wantedType: TypeDefinition?,
) = when (wantedType) {
    WellKnownTypes.listedTypeDefinition -> when (type) {
        WellKnownTypes.listBuilderTypeDefinition -> CSharp.InvocationExpression(
            pos,
            expr = StandardNames.temperCoreListedAsReadOnly.toStaticMember(pos),
            args = listOf(this),
        )

        else -> null
    }

    WellKnownTypes.mappedTypeDefinition -> when (type) {
        WellKnownTypes.mapBuilderTypeDefinition -> CSharp.InvocationExpression(
            pos,
            expr = StandardNames.temperCoreMappedAsReadOnly.toStaticMember(pos),
            args = listOf(this),
        )

        else -> null
    }

    else -> null
} ?: this

fun CSharp.Identifier.maybeToInterfaceName(isInterface: Boolean): CSharp.Identifier {
    if (isInterface) {
        outName = OutName(outName.outputNameText.toInterfaceName(), outName.sourceName)
    }
    return this
}

fun CSharp.ModAccess.emit(sink: TokenSink, default: CSharp.ModAccess? = null) = when (this) {
    default -> null
    CSharp.ModAccess.Private -> "private"
    CSharp.ModAccess.Internal -> "internal"
    CSharp.ModAccess.Protected -> "protected"
    CSharp.ModAccess.ProtectedInternal -> "protected internal"
    CSharp.ModAccess.Public -> "public"
}?.let { sink.word(it) }

fun CSharp.ModAccessorKind.emit(sink: TokenSink) = when (this) {
    CSharp.ModAccessorKind.Get -> "get"
    CSharp.ModAccessorKind.Set -> "set"
}.let { sink.word(it) }

fun CSharp.ModNew.emit(sink: TokenSink) = when (this) {
    CSharp.ModNew.Implied -> null
    CSharp.ModNew.New -> "new"
}?.let { sink.word(it) }

fun CSharp.ModStatic.emit(sink: TokenSink) = when (this) {
    CSharp.ModStatic.Instance -> null
    CSharp.ModStatic.Static -> "static"
}?.let { sink.word(it) }

fun CSharp.ModTypeKind.emit(sink: TokenSink) = when (this) {
    CSharp.ModTypeKind.Class -> "class"
    CSharp.ModTypeKind.Interface -> "interface"
    CSharp.ModTypeKind.Struct -> "struct"
}.let { sink.word(it) }

fun CSharp.ModWritable.emit(sink: TokenSink) = when (this) {
    CSharp.ModWritable.ReadOnly -> "readonly"
    CSharp.ModWritable.ReadWrite -> null
}?.let { sink.word(it) }

fun CSharp.PrimaryExpression.callMethod(
    id: String,
    vararg args: CSharp.Expression,
    typeArgs: List<CSharp.Type> = emptyList(),
    pos: Position = this.pos,
): CSharp.PrimaryExpression {
    return CSharp.InvocationExpression(
        pos = pos,
        expr = CSharp.MemberAccess(
            pos = pos,
            expr = this,
            id = id.toIdentifier(pos),
        ),
        typeArgs = typeArgs,
        args = args.toList(),
    )
}

fun CSharp.Statement.toBlock(pos: Position = this.pos): CSharp.BlockStatement = when (this) {
    is CSharp.BlockStatement -> this
    else -> CSharp.BlockStatement(pos, listOf(this))
}

fun List<CSharp.Statement>.toBlock(pos: Position): CSharp.BlockStatement =
    if (size == 1) {
        first().toBlock(pos)
    } else {
        CSharp.BlockStatement(spanningPosition(pos), this)
    }

fun List<CSharp.Statement>.toStatementOrBlock(pos: Position) =
    toStatementBlockOrNull(pos) ?: makeEmptyBlock(pos)

fun List<CSharp.Statement>.toStatementBlockOrNull(pos: Position) = when {
    isEmpty() -> null
    size == 1 -> first()
    else -> toBlock(pos)
}

fun makeEmptyBlock(pos: Position): CSharp.BlockStatement =
    CSharp.BlockStatement(pos, listOf())

fun OutName.toIdentifier(pos: Position) = CSharp.Identifier(pos, this)

fun Type2.isNullable(): Boolean = this.nullity == Nullity.OrNull

fun Type2.isValueType(): Boolean =
    // TODO User-defined value types, if we support those in the future.
    when (definition) {
        WellKnownTypes.booleanTypeDefinition,
        WellKnownTypes.float64TypeDefinition,
        WellKnownTypes.intTypeDefinition,
        WellKnownTypes.int64TypeDefinition,
        // TODO: what about these?
        // WellKnownTypes.noStringIndexTypeDefinition,
        // WellKnownTypes.stringIndexOptionTypeDefinition,
        WellKnownTypes.stringIndexTypeDefinition,
        -> true

        else -> false
    }

fun String.camelToPascal(): String {
    return IdentStyle.Camel.convertTo(IdentStyle.Pascal, this)
}

internal fun String.cleaned(): String {
    // We've sometimes seen doubled-up temporaries with "#" inside, so deal with those.
    // TODO Any other cases needed?
    return replace("#", "___")
}

fun String.dashToPascal(): String {
    return IdentStyle.Dash.convertTo(IdentStyle.Pascal, this)
}

fun String.toIdentifier(pos: Position) = toOutName().toIdentifier(pos)

fun String.toInterfaceName() = "I$this"

fun String.toOutName() = OutName(this, null)

internal fun TemperName.toStyle(style: NameStyle): String {
    return when (style) {
        NameStyle.PrettyCamel -> {
            val pretty = displayName
            when {
                pretty in csharpAllKeywords -> "@$pretty"
                else -> pretty
            }
        }

        NameStyle.PrettyPascal -> displayName.camelToPascal()
        NameStyle.Ugly -> "$this"
    }
}

fun TmpL.Type.isVoidish(): Boolean {
    return when (this) {
        is TmpL.NominalType -> when (typeName.sourceDefinition) {
            WellKnownTypes.voidTypeDefinition -> true
            else -> false
        }

        is TmpL.BubbleType -> true
        is TmpL.TypeUnion -> this.types.all { it.isVoidish() }
        else -> false
    }
}

fun TmpL.AType.isVoidish(): Boolean = ot.isVoidish()

fun TypedArg<CSharp.Tree>.asExpr() = this.expr as CSharp.Expression

fun TypeDefinition.isInterface() = (this as? TypeShape)?.abstractness == Abstractness.Abstract

internal enum class NameStyle {
    PrettyCamel,
    PrettyPascal,
    Ugly,
}

fun CSharp.QualTypeName.matches(name: TypeName): Boolean {
    val names = name.space.names
    val lastName = name.name

    val ids = this.id

    if (ids.size != names.size + 1) { return false }
    return lastName == ids.last().outName &&
        names.indices.all { ids[it].outName == names[it] }
}

val CSharp.Type.isBuiltinValueType: Boolean
    get() {
        if (this !is CSharp.UnboundType) { return false }
        val typeName = this.name
        return typeName is CSharp.Identifier &&
            typeName.outName.outputNameText in builtinValueTypeNames
    }

private val builtinValueTypeNames = setOf(
    // learn.microsoft.com/en-us/dotnet/csharp/language-reference/builtin-types/built-in-types
    "bool", // to "System.Boolean"
    "byte", // to "System.Byte"
    "sbyte", // to "System.SByte"
    "char", // to "System.Char"
    "decimal", // to "System.Decimal"
    "double", // to "System.Double"
    "float", // to "System.Single"
    "int", // to "System.Int32"
    "uint", // to "System.UInt32"
    "nint", // to "System.IntPtr"
    "nuint", // to "System.UIntPtr"
    "long", // to "System.Int64"
    "ulong", // to "System.UInt64"
    "short", // to "System.Int16"
    "ushort", // to "System.UInt16"
)
