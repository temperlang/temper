package lang.temper.frontend.define

import lang.temper.type.TypeShape
import lang.temper.value.TEdge

/**
 * The local type definitions, and the edge after where the last member was
 * extracted.
 */
internal data class ConvertedTypeInfo(
    val typesAndEdgePastLastMember: List<Pair<TypeShape, TEdge?>>,
)
