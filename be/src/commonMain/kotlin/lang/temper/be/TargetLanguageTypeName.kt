package lang.temper.be

import lang.temper.format.TokenSerializable

/**
 * May be used to define a relationship between a Temper type and a target
 * language type.
 *
 * This extends [TokenSerializable] to allow rendering to a type name
 * recognizable in the debug view of a [TmpL module][lang.temper.be.tmpl.TmpL.Module].
 *
 * It should bundle together information relevant to a
 * [backend's translation flow][lang.temper.be.Backend.translate]
 * For example, different target languages might want to
 * connect a Temper type like *Date* to types in their language:
 *
 * - JavaScript might want to connect to a JS Temporals type
 *   by representing `globalThis.Temporal.PlainDate` as an
 *   instance of a class that the *JsTranslator* understands.
 * - The Java backend might want to connect to
 *   `java.time.LocalDate` and bundle `java.time` and
 *   `LocalDate` separately along with any Maven dependencies
 *   that requires.
 * - Python might want to pack the Python module name `datetime`
 *   and the imported name `date` into a bundle object.
 * - Some older languages might want to connect to their
 *   *String* type but assuming that the string is formatted
 *   like "YYYYMMDD".  Notating that latter fact might help
 *   when generating auto-documentation comments.
 *
 * @see lang.temper.be.tmpl.TmpL.ConnectedToTypeName
 */
interface TargetLanguageTypeName : TargetLanguageName
