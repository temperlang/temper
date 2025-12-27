package lang.temper.be

/**
 * For [Backend.finishTmpL] and [Backend.collate], share the computation of
 * some side-table across all siblings.
 */
inline fun <E, D : Any> computeAndShare(
    siblingData: SiblingData<E>,
    get: (Backend<*>) -> D?,
    set: (D) -> Unit,
    customize: (D) -> D = { it },
    compute: (siblingData: SiblingData<E>) -> D,
): D {
    val (_, firstBackend: Backend<*>) = siblingData.backendsByLibraryRoot.entries.first()
    val precomputed = get(firstBackend)
    return customize(
        if (precomputed != null) {
            set(precomputed)
            precomputed
        } else {
            val data = compute(siblingData)
            set(data)
            data
        },
    )
}
