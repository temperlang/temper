package lang.temper.value

import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.NamingContext
import lang.temper.type.ANY_VALUE_TYPE_NAME_TEXT
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape

val TypeDefinition?.isAnyValueType: Boolean
    get() =
        this is TypeShape &&
            this.name.origin.isImplicits &&
            this.word?.text == ANY_VALUE_TYPE_NAME_TEXT

val NamingContext.isImplicits
    get() = this.loc is ImplicitsCodeLocation

val DocumentContext.isImplicits
    get() = this.namingContext.isImplicits

val Document.isImplicits
    get() = this.nameMaker.namingContext.isImplicits
