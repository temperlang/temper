package lang.temper.be.py

import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.idReach
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.name.identifiers.IdentStyle

internal fun propertyDecorator(pos: Position) = Py.Decorator(
    pos,
    name = listOf(pyIdent(pos, "property")),
    args = listOf(),
    called = false,
)

internal fun String.privateGetter() = "_get_$this"
internal fun String.privateSetter() = "_set_$this"
internal fun String.privatize() = "_$this"

internal fun TmpL.StaticProperty.adjustedNameText(): String {
    val name = temperToPython(name.name.displayName)
    return when (visibility.visibility) {
        TmpL.Visibility.Public -> name
        else -> name.privatize()
    }
}

internal fun TmpL.Subject.typeDefinition() = when (this) {
    is TmpL.Expression -> type.definition
    is TmpL.TypeName -> sourceDefinition
}

fun PyTranslator.name(name: TmpL.Id): Py.Name = pyNames.name(name).asPyName(name.pos)

fun PyTranslator.name(
    pos: Position,
    name: ResolvedName,
): Py.Name = pyNames.name(name).asPyName(pos)

fun PyTranslator.ident(
    name: TmpL.Id,
): Py.Identifier = pyNames.name(name).asPyId(name.pos)

fun PyTranslator.dotName(name: TmpL.DotName, source: ResolvedName? = null): OutName =
    OutName(temperToPython(name.dotNameText), source)

fun PyTranslator.dotName(member: TmpL.DotAccessible, source: ResolvedName? = null): OutName {
    return when (member.visibility.idReach()) {
        TmpL.IdReach.External -> dotName(member.dotName, source)
        TmpL.IdReach.Internal -> pyNames.name(member.name)
    }
}

fun PyTranslator.methodReferenceNameText(method: TmpL.MethodReference): String {
    return method.method?.let { methodShape ->
        when (methodShape.visibility.idReach()) {
            TmpL.IdReach.Internal -> (methodShape.name as? ResolvedName)?.let { pyNames.name(it).outputNameText }
            TmpL.IdReach.External -> null
        }
    } ?: temperToPython(method.methodName.dotNameText) // default to public view
}

fun PyTranslator.testName(name: TmpL.Id): Py.Identifier = pyNames.testName(name)

fun PyTranslator.pyName(pos: Position, name: String): Py.Name = Py.Name(pos, PyIdentifierName(name))
fun PyTranslator.pyPropertyName(dotName: TmpL.DotName): Py.Name =
    pyName(dotName.pos, temperToPython(dotName.dotNameText))

fun PyTranslator.pyPropertyName(tProp: TmpL.PropertyId): Py.Identifier = when (tProp) {
    is TmpL.InternalPropertyId -> ident(tProp.name)
    is TmpL.ExternalPropertyId -> pyIdent(
        tProp.name.pos,
        temperToPython(tProp.name.dotNameText),
    )
}

fun temperToPython(name: String) = avoidReserved(IdentStyle.Camel.convertTo(IdentStyle.Snake, name))
