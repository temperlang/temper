package lang.temper.common.structure

import kotlin.reflect.KProperty

interface StructuredViaReflection : Structured {
    override fun destructure(structureSink: StructureSink) {
        val structuredValue = this
        return structureSink.obj {
            val type = structuredValue::class
            val naturalOrder = run {
                val nameList = mutableListOf<String>()
                type.annotations.forEach {
                    if (it is NaturalOrder) {
                        nameList.addAll(it.propertyNames)
                    }
                }
                nameList.toSet()
            }

            val members = type.members
            val membersInOrder = if (naturalOrder.isNotEmpty()) {
                val nameToMember = members.map { it.name to it }.toMap()
                naturalOrder.mapNotNull {
                    nameToMember[it]
                } + nameToMember.mapNotNull { (name, member) ->
                    if (name !in naturalOrder) { member } else { null }
                }
            } else {
                members
            }

            val hints = mutableSetOf<StructureHint>()
            for (member in membersInOrder) {
                if (member is KProperty) {
                    hints.clear()
                    if (member.name in naturalOrder) {
                        hints.add(StructureHint.NaturallyOrdered)
                    }
                    // TODO: This relation between member names and hints can probably be cached
                    // per KClass.
                    member.annotations.forEach {
                        if (it is StructuredProperty) {
                            hints.addAll(it.hints)
                        }
                    }
                    key(member.name, hints.toSet()) {
                        val getter = member.getter
                        value(getter.call(structuredValue))
                    }
                }
            }
        }
    }
}

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class StructuredProperty(val hints: Array<StructureHint>)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NaturalOrder(val propertyNames: Array<String>)
