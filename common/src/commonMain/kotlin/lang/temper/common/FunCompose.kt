package lang.temper.common

private val noOp: () -> Unit = {}

infix fun ((() -> Unit)?).compose(f: (() -> Unit)?): () -> Unit = when {
    f == null || f === noOp -> this ?: noOp
    this == null || this === noOp -> f
    else -> (
        {
            // g ∘ f = g(f(x))
            f()
            this()
        }
        )
}
