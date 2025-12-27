package lang.temper.frontend.typestage

import lang.temper.common.removeMatching
import lang.temper.name.TemperName
import lang.temper.type.NominalType
import lang.temper.type.VisibleMemberShape

internal fun filterOutMaskedMembers(
    membersGrouped: MutableMap<NominalType, MutableSet<VisibleMemberShape>>,
) {
    val masked = mutableSetOf<TemperName>()
    for (members in membersGrouped.values) {
        for (member in members) {
            for (memberOverride in (member.overriddenMembers ?: continue)) {
                masked.add(memberOverride.superTypeMember.name)
            }
        }
    }
    for (members in membersGrouped.values) {
        members.retainAll { it.name !in masked }
    }
    membersGrouped.removeMatching { it.value.isEmpty() }
}
