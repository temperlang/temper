package lang.temper.common

import kotlin.random.Random

/** Uniformly distributed. */
fun (Random).nextByte(): Byte =
    this.nextInt(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt() + 1).toByte()

/** Uniformly distributed including surrogates. */
fun (Random).nextChar(): Char =
    this.nextInt(Char.MIN_VALUE.code, Char.MAX_VALUE.code + 1).toChar()

/** Uniformly distributed. */
fun (Random).nextShort(): Short =
    this.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt() + 1).toShort()
