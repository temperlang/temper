package lang.temper.interp.docgenalts

import lang.temper.value.MacroValue

sealed interface DocGenAltFn : MacroValue

/** Alternative `if` */
interface DocGenAltIfFn : DocGenAltFn

/** Alternative `return` */
interface DocGenAltReturnFn : DocGenAltFn

/** Alternative `while` */
interface DocGenAltWhileFn : DocGenAltFn

/** Alternative implied result that should probably be within the documentation fold. */
interface DocGenAltImpliedResultFn : DocGenAltFn
