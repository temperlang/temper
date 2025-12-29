package lang.temper.common.dtree

import lang.temper.common.KBitSet
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import kotlin.math.log2

/**
 * A decision tree is an if-then-else style tree that uses [DISCRIMINANT]s
 * to filter [CASE]s.
 */
sealed class DecisionTree<CASE, DISCRIMINANT>(
    val cases: List<CASE>,
) : Structured {
    abstract val children: List<DecisionTree<CASE, DISCRIMINANT>>

    class Leaf<CASE, DISCRIMINANT>(
        cases: List<CASE>,
    ) : DecisionTree<CASE, DISCRIMINANT>(cases) {
        override val children get() = listOf<DecisionTree<CASE, DISCRIMINANT>>()

        override fun destructure(structureSink: StructureSink) =
            structureSink.arr {
                cases.forEach { value(it) }
            }
    }

    class Inner<CASE, DISCRIMINANT>(
        val discriminant: DISCRIMINANT,
        cases: List<CASE>,
        val choices: Map<Any, DecisionTree<CASE, DISCRIMINANT>>,
    ) : DecisionTree<CASE, DISCRIMINANT>(cases) {
        override val children
            get() = choices.values.toList()

        override fun destructure(structureSink: StructureSink) =
            structureSink.obj {
                key("discriminant") {
                    value(discriminant)
                }
                key("options") {
                    arr {
                        choices.forEach { (category, choice) ->
                            arr {
                                value(category)
                                choice.destructure(this)
                            }
                        }
                    }
                }
            }
    }
}

/**
 * Build a decision tree based on greedy information gain.
 */
fun <CASE, DISCRIMINANT> buildDecisionTree(
    cases: List<CASE>,
    features: List<DISCRIMINANT>,
    /**
     * Apply a discriminant to case to produce a list of **hashable** keys
     * which each identify a category that case falls into.
     *
     * Booleans are perfectly good outputs.
     *
     * If a classification is ambiguous the output list may have multiple
     * elements.  For example, an ambiguous boolean classification would
     * look like `listOf(false, true)`.  Order is not significant.
     */
    categorize: (CASE, DISCRIMINANT) -> List<Any>,
): DecisionTree<CASE, DISCRIMINANT> {
    val totalCount = cases.size
    if (totalCount <= 1 || features.isEmpty()) {
        return DecisionTree.Leaf(cases)
    }

    val baseEntropy = entropy(intArrayOf(totalCount))

    var bestFeatureIndex = -1
    var bestInfoGain: Double = Double.NaN
    // Relates categorize outputs to unions of case indices.
    var bestSplit: MutableMap<Any, KBitSet>? = null
    for ((featureIndex, feature) in features.withIndex()) {
        val partition = mutableMapOf<Any, KBitSet>()
        for ((caseIndex, case) in cases.withIndex()) {
            categorize(case, feature).forEach { partitionKey ->
                partition.getOrPut(partitionKey) { KBitSet() }
                    .set(caseIndex)
            }
        }

        val counts = IntArray(partition.size)
        partition.values.forEachIndexed { index, caseIndexSet ->
            counts[index] = caseIndexSet.cardinality()
        }
        val featureEntropy = entropy(counts)
        val infoGain = featureEntropy - baseEntropy

        if (bestInfoGain.isNaN() || infoGain > bestInfoGain) {
            bestFeatureIndex = featureIndex
            bestInfoGain = infoGain
            bestSplit = partition
        }
    }

    if (bestFeatureIndex < 0 || bestSplit == null || bestInfoGain.isNaN()) {
        return DecisionTree.Leaf(cases)
    }

    if (bestSplit.size == 1) {
        return DecisionTree.Leaf(cases)
    }

    val remainingFeatures = buildList {
        features.filterIndexedTo(this) { index, _ ->
            index != bestFeatureIndex
        }
    }

    val choices = bestSplit.mapValues { (_, caseIndexSet) ->
        val subCases = buildList(capacity = caseIndexSet.cardinality()) {
            var caseIndex = -1
            while (true) {
                caseIndex = caseIndexSet.nextSetBit(caseIndex + 1)
                if (caseIndex < 0) { break }
                add(cases[caseIndex])
            }
        }
        buildDecisionTree(subCases, remainingFeatures, categorize)
    }

    return DecisionTree.Inner(features[bestFeatureIndex], cases, choices)
}

private fun entropy(counts: IntArray): Double {
    val total = counts.sum().toDouble()
    var totalEntropy = 0.0
    for (count in counts) {
        if (count == 0) { continue }
        val p = count.toDouble() / total
        totalEntropy -= p * log2(p)
    }
    return totalEntropy
}
