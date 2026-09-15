package lang.temper.type

import java.util.IdentityHashMap

class ButNotRecursively {
    val tentativeResults = mutableMapOf<TaskKey<*, *>, IdentityHashMap<Any, Any>>()

    interface TaskKey<I : Any, O : Any>
}

inline fun <I : Any, O : Any> ButNotRecursively.compute(
    taskKey: ButNotRecursively.TaskKey<I, O>,
    inp: I,
    tentativeOutput: O,
    computeIt: () -> O,
): O {
    @Suppress("UNCHECKED_CAST")
    val identityMap = tentativeResults.getOrPut(taskKey) {
        IdentityHashMap()
    } as IdentityHashMap<I, O>

    val priorResult = identityMap[inp]
    if (priorResult != null) {
        return priorResult
    }

    identityMap[inp] = tentativeOutput
    val computedResult = computeIt()
    identityMap.remove(inp)
    return computedResult
}
