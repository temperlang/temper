package lang.temper.common

enum class LeftOrRight {
    Left,
    Right,
    ;

    val other get() = when (this) {
        Left -> Right
        Right -> Left
    }
}
