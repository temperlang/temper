package lang.temper.common

// Kotlin common doesn't have non-boxing binary search for IntArray.  Sigh.

/** Finds insertion point à la Java's binarySearch API */
fun binarySearch(
    sortedArray: IntArray,
    target: Int,
): Int {
    // Algo from
    // www.khanacademy.org/computing/computer-science/algorithms/binary-search/a/
    // implementing-binary-search-of-an-array

    // 1. Let min = 0 and max = n-1.
    var min = 0
    var max = sortedArray.size - 1
    if (max < 0) {
        return -1
    }
    do {
        // 2. Compute guess as the average of max and min, rounded down (so that it is an integer).
        val guess = (max + min) / 2
        val got = sortedArray[guess]
        // 3. If array[guess] equals target, then stop. You found it! Return guess.
        if (got == target) {
            return guess
        }
        // 4. If the guess was too low, that is, array[guess] < target, then set min = guess + 1.
        if (got < target) {
            min = guess + 1
            // 5. Otherwise, the guess was too high. Set max = guess - 1.
        } else {
            max = guess - 1
        }
        // 6. Go back to step 2.
    } while (min <= max)

    return if (max < 0) {
        // sortedArray[0] was too high via step 5 above
        -1
    } else {
        // we know that max is in the array.
        (max + (if (sortedArray[max] < target) 1 else 0)).inv()
    }
}

/** Finds insertion point à la Java's binarySearch API */
fun binarySearch(
    sortedArray: CharArray,
    target: Char,
): Int {
    // Algo from
    // www.khanacademy.org/computing/computer-science/algorithms/binary-search/a/
    // implementing-binary-search-of-an-array

    // 1. Let min = 0 and max = n-1.
    var min = 0
    var max = sortedArray.size - 1
    if (max < 0) {
        return -1
    }
    do {
        // 2. Compute guess as the average of max and min, rounded down (so that it is an integer).
        val guess = (max + min) / 2
        val got = sortedArray[guess]
        // 3. If array[guess] equals target, then stop. You found it! Return guess.
        if (got == target) {
            return guess
        }
        // 4. If the guess was too low, that is, array[guess] < target, then set min = guess + 1.
        if (got < target) {
            min = guess + 1
            // 5. Otherwise, the guess was too high. Set max = guess - 1.
        } else {
            max = guess - 1
        }
        // 6. Go back to step 2.
    } while (min <= max)

    return if (max < 0) {
        // sortedArray[0] was too high via step 5 above
        -1
    } else {
        // we know that max is in the array.
        (max + (if (sortedArray[max] < target) 1 else 0)).inv()
    }
}

/** Finds insertion point à la Java's binarySearch API */
fun <T : Comparable<T>> binarySearch(
    sortedList: List<T>,
    target: T,
): Int = binarySearch(sortedList, target) { a, b -> a.compareTo(b) }

/** Finds insertion point à la Java's binarySearch API */
fun <T, K> binarySearch(
    sortedList: List<T>,
    target: K,
    cmp: (T, K) -> Int,
): Int {
    // Algo from
    // www.khanacademy.org/computing/computer-science/algorithms/binary-search/a/
    // implementing-binary-search-of-an-array

    // 1. Let min = 0 and max = n-1.
    var min = 0
    var max = sortedList.lastIndex
    if (max < 0) {
        return -1
    }
    do {
        // 2. Compute guess as the average of max and min, rounded down (so that it is an integer).
        val guess = (max + min) / 2
        val got = sortedList[guess]
        // 3. If array[guess] equals target, then stop. You found it! Return guess.
        if (got == target) {
            return guess
        }
        // 4. If the guess was too low, that is, array[guess] < target, then set min = guess + 1.
        if (cmp(got, target) < 0) {
            min = guess + 1
            // 5. Otherwise, the guess was too high. Set max = guess - 1.
        } else {
            max = guess - 1
        }
        // 6. Go back to step 2.
    } while (min <= max)

    return if (max < 0) {
        // sortedArray[0] was too high via step 5 above
        -1
    } else {
        // we know that max is in the array.
        (max + (if (cmp(sortedList[max], target) < 0) 1 else 0)).inv()
    }
}
