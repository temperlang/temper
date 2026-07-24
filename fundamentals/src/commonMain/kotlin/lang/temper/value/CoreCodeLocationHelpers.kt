package lang.temper.value

import lang.temper.name.CoreCodeLocation
import lang.temper.name.NamingContext
import lang.temper.type.ANY_VALUE_TYPE_NAME_TEXT
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape

val TypeDefinition?.isAnyValueType: Boolean
    get() =
        this is TypeShape &&
            this.name.origin.isCore &&
            this.word?.text == ANY_VALUE_TYPE_NAME_TEXT

val NamingContext.isCore
    get() = this.loc is CoreCodeLocation

val DocumentContext.isCore
    get() = this.namingContext.isCore

val Document.isCore
    get() = this.nameMaker.namingContext.isCore
