package lang.temper.env

import lang.temper.common.AtomicCounter
import lang.temper.name.NamingContext
import lang.temper.name.TemperName

/** A [NamingContext] that exposes its top-level names. */
abstract class BindingNamingContext(counter: AtomicCounter) : NamingContext(counter) {
    abstract fun getTopLevelBinding(name: TemperName): DeclarationBinding?
    abstract val topLevelBindingNames: Iterable<TemperName>
}
