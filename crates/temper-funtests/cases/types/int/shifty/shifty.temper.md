# Bitwise operations on Int32s and Int64s

For each bit-twiddling operator, we define a function that logs
so that the numeric operations themselves are not inlined during
compilation.

Then we log lines.

A line like `0x:...` means that the numbers within are in hex
even though not prefixed themselves, though shift distances
(the right operand to `<<`, `>>`, or `>>>`) are in decimal.

Some tests require lots of digits.
For example, a logical shift of a small negative number yields
a large positive number.

insertUnderscores inserts `_` between every four digits, and renders
in hex so that bits are easy to deduce but the representation is still
compact.

    let insertUnderscores(hexString: String): String {
      let sb = new StringBuilder();

      var idx = String.begin;
      let end = hexString.end;
      if (idx < end) {
        let possibleSign = hexString[idx];
        if (possibleSign == char'+' || possibleSign == char'-') {
          idx = hexString.next(idx);
          sb.appendBetween(hexString, String.begin, idx);
        }
      }

      // All groups except the last have four digits, so special
      // case thdse first.
      let nFirstGroup = hexString.countBetween(idx, end) % 4;
      var needsUnderscore = false;
      if (nFirstGroup != 0) {
        let groupStart = idx;
        for (var i = 0; i < nFirstGroup; ++i) {
          idx = hexString.next(idx);
        }
        sb.appendBetween(hexString, groupStart, idx);
        needsUnderscore = true;
      }
      while (idx < end) {
        let groupStart = idx;
        idx = hexString.step(idx, 4);
        if (needsUnderscore) {
          sb.append("_");
        }
        sb.appendBetween(hexString, groupStart, idx);
        needsUnderscore = true;
      }
      sb.toString()
    }

fmt32 and fmt64 use insertUnderscores to format numbers of those
bit-widths.

    let fmt32(n: Int32): String {
      insertUnderscores(n.toString(16))
    }
    let fmt64(n: Int64): String {
      insertUnderscores(n.toString(16))
    }

## Bitwise And

    let and32(a: Int32, b: Int32): Void {
      let c = a & b;
      console.log("0x:${fmt32(a)} & ${fmt32(b)} == ${fmt32(c)}");
    }

    let and64(a: Int64, b: Int64): Void {
      let c = a & b;
      console.log("0x:${fmt64(a)} & ${fmt64(b)} == ${fmt64(c)}");
    }

    console.log("and32");
    and32(0, 0);
    and32(3, 1);
    and32(2, 3);
    and32(-2, 7);
    and32(-0x303efdb1, 0x35b53f03);

```log
and32
0x:0 & 0 == 0
0x:3 & 1 == 1
0x:2 & 3 == 2
0x:-2 & 7 == 6
0x:-303e_fdb1 & 35b5_3f03 == 581_0203
```

    console.log("and64");
    and64(0i64, 0i64);
    and64(3i64, 1i64);
    and64(2i64, 3i64);
    and64(-2i64, 7i64);
    and64(0x420a7a213c8786di64, -0x30a4ef949458f4d2i64);

```log
and64
0x:0 & 0 == 0
0x:3 & 1 == 1
0x:2 & 3 == 2
0x:-2 & 7 == 6
0x:420_a7a2_13c8_786d & -30a4_ef94_9458_f4d2 == 400_0022_0380_082c
```

## Bitwise Or

    let or32(a: Int32, b: Int32): Void {
      let c = a | b;
      console.log("0x:${fmt32(a)} | ${fmt32(b)} == ${fmt32(c)}");
    }

    let or64(a: Int64, b: Int64): Void {
      let c = a | b;
      console.log("0x:${fmt64(a)} | ${fmt64(b)} == ${fmt64(c)}");
    }

    console.log("or32");
    or32(0, 0);
    or32(0, 1);
    or32(2, 1);
    or32(2, 2);
    or32(3, 1);
    or32(2, 3);
    or32(-2, 7);
    or32(-0x303efdb1, 0x35b53f03);

```log
or32
0x:0 | 0 == 0
0x:0 | 1 == 1
0x:2 | 1 == 3
0x:2 | 2 == 2
0x:3 | 1 == 3
0x:2 | 3 == 3
0x:-2 | 7 == -1
0x:-303e_fdb1 | 35b5_3f03 == -a_c0b1
```

    console.log("or64");
    or64(0i64, 0i64);
    or64(0i64, 1i64);
    or64(2i64, 1i64);
    or64(2i64, 2i64);
    or64(3i64, 1i64);
    or64(2i64, 3i64);
    or64(-2i64, 7i64);
    or64(0x420a7a213c8786di64, -0x30a4ef949458f4d2i64);

```log
or64
0x:0 | 0 == 0
0x:0 | 1 == 1
0x:2 | 1 == 3
0x:2 | 2 == 2
0x:3 | 1 == 3
0x:2 | 3 == 3
0x:-2 | 7 == -1
0x:420_a7a2_13c8_786d | -30a4_ef94_9458_f4d2 == -3084_4814_8410_8491
```

## Bitwise Complement

    let bComp32(n: Int32): Void {
      console.log("0x:~${fmt32(n)} == ${fmt32(~n)}");
    }
    let bComp64(n: Int64): Void {
      console.log("0x:~${fmt64(n)} == ${fmt64(~n)}");
    }

    console.log("bComp32");
    bComp32(0);
    bComp32(1);
    bComp32(-1);
    bComp32(2);
    bComp32(-2);
    bComp32(-0x303e_fdb1);
    bComp32(0x35b5_3f03);

```log
bComp32
0x:~0 == -1
0x:~1 == -2
0x:~-1 == 0
0x:~2 == -3
0x:~-2 == 1
0x:~-303e_fdb1 == 303e_fdb0
0x:~35b5_3f03 == -35b5_3f04
```

    console.log("bComp64");
    bComp64(0i64);
    bComp64(1i64);
    bComp64(-1i64);
    bComp64(2i64);
    bComp64(-2i64);
    bComp64(0x420a7a213c8786di64);
    bComp64(-0x30a4ef949458f4d2i64);

```log
bComp64
0x:~0 == -1
0x:~1 == -2
0x:~-1 == 0
0x:~2 == -3
0x:~-2 == 1
0x:~420_a7a2_13c8_786d == -420_a7a2_13c8_786e
0x:~-30a4_ef94_9458_f4d2 == 30a4_ef94_9458_f4d1
```

## Left Shift

    let shl32(a: Int32, b: Int32): Void {
      let c = a << b;
      console.log("0x:${fmt32(a)} << ${b} == ${fmt32(c)}");
    }

    let shl64(a: Int64, b: Int32): Void {
      let c = a << b;
      console.log("0x:${fmt64(a)} << ${b} == ${fmt64(c)}");
    }

    console.log("shl32");
    shl32(0x05, 0);
    shl32(0x05, 1);
    shl32(0x05, 2);
    shl32(0x05, 3);
    shl32(0x05, 4);
    shl32(0x05, 5);
    shl32(0x05, 20);
    shl32(0x05, 28);
    shl32(0x05, 29); // Shift into sign bit
    shl32(0x05, 30);
    shl32(0x05, 31);
    // Shift operands are truncated
    shl32(0x05, 32);
    shl32(0x05, 33);
    // Don't use negative shift operands, but if you do, they're truncated.
    shl32(0x05, -1); // 0x...fff truncates to 0x1f
    shl32(0x05, -30); // -30 & 0x1f == 2
    // Negative shiftands are fine
    shl32(-1, 0);
    shl32(-1, 1);
    shl32(-1, 2);

```log
shl32
0x:5 << 0 == 5
0x:5 << 1 == a
0x:5 << 2 == 14
0x:5 << 3 == 28
0x:5 << 4 == 50
0x:5 << 5 == a0
0x:5 << 20 == 50_0000
0x:5 << 28 == 5000_0000
0x:5 << 29 == -6000_0000
0x:5 << 30 == 4000_0000
0x:5 << 31 == -8000_0000
0x:5 << 32 == 5
0x:5 << 33 == a
0x:5 << -1 == -8000_0000
0x:5 << -30 == 14
0x:-1 << 0 == -1
0x:-1 << 1 == -2
0x:-1 << 2 == -4
```

Int64 shifts are pretty much the same but the distance operand
truncating is different.

    console.log("shl64");
    shl64(0x05i64, 0);
    shl64(0x05i64, 1);
    shl64(0x05i64, 2);
    shl64(0x05i64, 3);
    shl64(0x05i64, 4);
    shl64(0x05i64, 5);
    // Shift operands are not truncated at 32
    shl64(0x05i64, 33);
    // But they are at 64
    shl64(0x05i64, 62);
    shl64(0x05i64, 63); // Shift into sign bit
    shl64(0x05i64, 64);
    // Don't use negative shift operands, but if you do, they're truncated.
    shl64(0x05i64, -1); // 0x...fff truncates to 0x1f
    shl64(0x05i64, -60); // -30 & 0x1f == 2
    // Negative shiftands are fine
    shl64(-1i64, 0);
    shl64(-1i64, 1);
    shl64(-1i64, 2);

```log
shl64
0x:5 << 0 == 5
0x:5 << 1 == a
0x:5 << 2 == 14
0x:5 << 3 == 28
0x:5 << 4 == 50
0x:5 << 5 == a0
0x:5 << 33 == a_0000_0000
0x:5 << 62 == 4000_0000_0000_0000
0x:5 << 63 == -8000_0000_0000_0000
0x:5 << 64 == 5
0x:5 << -1 == -8000_0000_0000_0000
0x:5 << -60 == 50
0x:-1 << 0 == -1
0x:-1 << 1 == -2
0x:-1 << 2 == -4
```

## Right Shift (sign extending)

    let shr32(a: Int32, b: Int32): Void {
      let c = a >> b;
      console.log("0x:${fmt32(a)} >> ${b} == ${fmt32(c)}");
    }

    let shr64(a: Int64, b: Int32): Void {
      let c = a >> b;
      console.log("0x:${fmt64(a)} >> ${b} == ${fmt64(c)}");
    }

    console.log("shr32");
    shr32(0x80, 0);
    shr32(0x80, 1);
    shr32(0x80, 2);
    shr32(0x80, 3);
    shr32(0x80, 4);
    shr32(0x80, 5);
    shr32(0x80, 6);
    shr32(0x80, 7);
    shr32(0x80, 8);
    shr32(0x80, 9);
    // Shift operands are truncated
    shr32(0x80, 31);
    shr32(0x80, 32);
    shr32(0x80, 33);
    // Don't use negative shift operands, but if you do, they're truncated.
    shr32(0x80, -1); // 0x...fff truncates to 0x1f
    shr32(0x80, -30); // -30 & 0x1f == 2
    // Negative shiftands
    shr32(-0x80, 0);
    shr32(-0x80, 1);
    shr32(-0x80, 2);
    shr32(-0x80, 3);
    shr32(-0x80, 4);

```log
shr32
0x:80 >> 0 == 80
0x:80 >> 1 == 40
0x:80 >> 2 == 20
0x:80 >> 3 == 10
0x:80 >> 4 == 8
0x:80 >> 5 == 4
0x:80 >> 6 == 2
0x:80 >> 7 == 1
0x:80 >> 8 == 0
0x:80 >> 9 == 0
0x:80 >> 31 == 0
0x:80 >> 32 == 80
0x:80 >> 33 == 40
0x:80 >> -1 == 0
0x:80 >> -30 == 20
0x:-80 >> 0 == -80
0x:-80 >> 1 == -40
0x:-80 >> 2 == -20
0x:-80 >> 3 == -10
0x:-80 >> 4 == -8
```

    console.log("shr64");
    shr64(0x80i64, 0);
    shr64(0x80i64, 1);
    shr64(0x80i64, 2);
    shr64(0x80i64, 3);
    shr64(0x80i64, 4);
    shr64(0x80i64, 5);
    shr64(0x80i64, 6);
    shr64(0x80i64, 7);
    shr64(0x80i64, 8);
    shr64(0x80i64, 9);
    // Shift operands are truncated but at 64 not at 32
    shr64(0x80i64, 32);
    shr64(0x80i64, 63);
    shr64(0x80i64, 64);
    shr64(0x80i64, 65);
    // Don't use negative shift operands, but if you do, they're truncated.
    shr64(0x80i64, -1); // 0x...fff truncates to 0x1f
    shr64(0x80i64, -62); // -62 & 0x3f == 2
    // Negative shiftands
    shr64(-0x80i64, 0);
    shr64(-0x80i64, 1);
    shr64(-0x80i64, 2);
    shr64(-0x80i64, 3);
    shr64(-0x80i64, 4);

```log
shr64
0x:80 >> 0 == 80
0x:80 >> 1 == 40
0x:80 >> 2 == 20
0x:80 >> 3 == 10
0x:80 >> 4 == 8
0x:80 >> 5 == 4
0x:80 >> 6 == 2
0x:80 >> 7 == 1
0x:80 >> 8 == 0
0x:80 >> 9 == 0
0x:80 >> 32 == 0
0x:80 >> 63 == 0
0x:80 >> 64 == 80
0x:80 >> 65 == 40
0x:80 >> -1 == 0
0x:80 >> -62 == 20
0x:-80 >> 0 == -80
0x:-80 >> 1 == -40
0x:-80 >> 2 == -20
0x:-80 >> 3 == -10
0x:-80 >> 4 == -8
```

## Logical right shifts

    let uShr32(a: Int32, b: Int32): Void {
      let c = a >>> b;
      console.log("0x:${fmt32(a)} >>> ${b} == ${fmt32(c)}");
    }

    let uShr64(a: Int64, b: Int32): Void {
      let c = a >>> b;
      console.log("0x:${fmt64(a)} >>> ${b} == ${fmt64(c)}");
    }

    console.log("uShr32");
    uShr32(0x80, 0);
    uShr32(0x80, 1);
    uShr32(0x80, 2);
    uShr32(0x80, 3);
    uShr32(0x80, 4);
    uShr32(0x80, 5);
    uShr32(0x80, 6);
    uShr32(0x80, 7);
    uShr32(0x80, 8);
    uShr32(0x80, 9);
    // Shift operands are truncated
    uShr32(0x80, 31);
    uShr32(0x80, 32);
    uShr32(0x80, 33);
    // Don't use negative shift operands, but if you do, they're truncated.
    uShr32(0x80, -1); // 0x...fff truncates to 0x1f
    uShr32(0x80, -30); // -30 & 0x1f == 2
    // Negative shiftands
    uShr32(-0x80, 0);
    uShr32(-0x80, 1);
    uShr32(-0x80, 2);
    uShr32(-0x80, 3);
    uShr32(-0x80, 4);

```log
uShr32
0x:80 >>> 0 == 80
0x:80 >>> 1 == 40
0x:80 >>> 2 == 20
0x:80 >>> 3 == 10
0x:80 >>> 4 == 8
0x:80 >>> 5 == 4
0x:80 >>> 6 == 2
0x:80 >>> 7 == 1
0x:80 >>> 8 == 0
0x:80 >>> 9 == 0
0x:80 >>> 31 == 0
0x:80 >>> 32 == 80
0x:80 >>> 33 == 40
0x:80 >>> -1 == 0
0x:80 >>> -30 == 20
0x:-80 >>> 0 == -80
0x:-80 >>> 1 == 7fff_ffc0
0x:-80 >>> 2 == 3fff_ffe0
0x:-80 >>> 3 == 1fff_fff0
0x:-80 >>> 4 == fff_fff8
```

    console.log("uShr64");
    uShr64(0x80i64, 0);
    uShr64(0x80i64, 1);
    uShr64(0x80i64, 2);
    uShr64(0x80i64, 3);
    uShr64(0x80i64, 4);
    uShr64(0x80i64, 5);
    uShr64(0x80i64, 6);
    uShr64(0x80i64, 7);
    uShr64(0x80i64, 8);
    uShr64(0x80i64, 9);
    // Shift operands are truncated but at 64 not at 32
    uShr64(0x80i64, 32);
    uShr64(0x80i64, 63);
    uShr64(0x80i64, 64);
    uShr64(0x80i64, 65);
    // Don't use negative shift operands, but if you do, they're truncated.
    uShr64(0x80i64, -1); // 0x...fff truncates to 0x1f
    uShr64(0x80i64, -62); // -62 & 0x3f == 2
    // Negative shiftands
    uShr64(-0x80i64, 0);
    uShr64(-0x80i64, 1);
    uShr64(-0x80i64, 2);
    uShr64(-0x80i64, 3);
    uShr64(-0x80i64, 4);

```log
uShr64
0x:80 >>> 0 == 80
0x:80 >>> 1 == 40
0x:80 >>> 2 == 20
0x:80 >>> 3 == 10
0x:80 >>> 4 == 8
0x:80 >>> 5 == 4
0x:80 >>> 6 == 2
0x:80 >>> 7 == 1
0x:80 >>> 8 == 0
0x:80 >>> 9 == 0
0x:80 >>> 32 == 0
0x:80 >>> 63 == 0
0x:80 >>> 64 == 80
0x:80 >>> 65 == 40
0x:80 >>> -1 == 0
0x:80 >>> -62 == 20
0x:-80 >>> 0 == -80
0x:-80 >>> 1 == 7fff_ffff_ffff_ffc0
0x:-80 >>> 2 == 3fff_ffff_ffff_ffe0
0x:-80 >>> 3 == 1fff_ffff_ffff_fff0
0x:-80 >>> 4 == fff_ffff_ffff_fff8
```
