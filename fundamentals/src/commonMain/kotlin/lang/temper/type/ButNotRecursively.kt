package lang.temper.type

import java.util.IdentityHashMap

/**
 * Short circuits recursive object graph walks by allowing storing a
 * tentative result for an operation and then using that in lieu of a recursive call.
 *
 * For example, the JVM's `Object.equals(Object)` will fail with a stack overflow
 * if applied to an indirectly self-referential object.
 *
 * This helper allows defining that in terms of `MyClass.equals(MyClass, ButNotRecursively)`
 * where the latter argument is threaded through equality so that it can track which
 * questions are outstanding.
 *
 * Safe equality can then be implemented using [ButNotRecursively.compute] where it
 * stores `true` as the tentative result of "x equals y."
 * Then it considers all possible counter-evidence that doesn't involve recursively
 * checking that "x equals y," and returns false if any counter-evidence is found.
 */
class ButNotRecursively {
    val tentativeResults = mutableMapOf<TaskKey<*, *>, IdentityHashMap<Any, Any>>()

    /**
     * A key into the map above that allows for type safe operations when used via
     * [ButNotRecursively.compute] only.
     *
     * Each distinct sub-task should have its own key so that tentative results
     * for one don't collide with another.
     */
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
