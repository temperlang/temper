### 🆕 Bitwise operators

Previously, Temper had `&` and `|` for operating on *Int32* and
*Int64* values but was missing some operators from the usual suite.

Temper now has these operators:

- Prefix `~` performs bitwise complement on an integer.
- Infix `^` performs bitwise exclusive-or on two same-width integers.
- Infix `<<` can take an integer of either width on the left and an
  *Int32* on the right, and shifts left.
- Infix `>>` does a sign-extending right shift, and take the same
  kinds of arguments as the left shift.
- Infix `>>>` does a non sign-extending right shift.

These are documented under the builtin operators reference.

All the shift operators ignore any bit the five (for 32b shifts) or
six (for 64b shifts) least significant bits which emulates common
hardware practice.

None of these operands panic on underflow or overflow.

Negative integers have defined semantics for shifting, as per their
2's complement representation, and shifting a positive value's bit
left into the most significant bit produces a negative integer again
per 2's complement.
