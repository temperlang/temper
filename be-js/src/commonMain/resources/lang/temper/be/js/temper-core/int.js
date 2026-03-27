/**
 * Right shift (sign-extending) with Int64 semantics.
 *
 * BigInt shift does not mask the shift amount.
 *
 * @param {BigInt} a
 * @param {number} b
 * @return {BigInt}
 */
export const bitwiseShr64 = (a, b) => {
  b &= 0x3f;
  return a >> globalThis.BigInt(b & 0x3f);
};

/**
 * Right shift (non-sign-extending) with Int32 semantics.
 *
 * Emulate it with a library function.
 * We can't just use the builtin shift operator because it
 * internally invokes toUInt32 which, when the shift amount
 * is zero (post-masking), is not identity for negative numbers.
 *
 * @param {number} a
 * @param {number} b
 * @return {number}
 */
export const bitwiseShrUnsigned32 = (a, b) => {
  b &= 0x1f;
  return b ? a >>> b : a;
  // Assumes b is not NaN
};

/**
 * Right shift (non-sign-extending) with Int64 semantics.
 *
 * BigInt does not support unsigned right shift.
 * Emulate it with a library function.
 * We can't just mask and then shift, because every shift by a
 * non-zero amount will yield a non-negative value, but a shift by zero
 * (post right operand masking) does not.
 *
 * @param {BigInt} a
 * @param {number} b
 * @return {BigInt}
 */
export const bitwiseShrUnsigned64 = (a, b) => {
  b &= 0x3f;
  return b ? (a & 0xFFFF_FFFF_FFFF_FFFFn) >> globalThis.BigInt(b) : a;
  // Assumes b is not NaN
};

/**
 * Left shift with Int64 semantics.
 *
 * BigInt does not support unsigned right shift.
 * Emulate it with a library function.
 * We can't just mask and then shift, because every shift by a
 * non-zero amount will yield a non-negative value, but a shift by zero
 * (post right operand masking) does not.
 *
 * @param {BigInt} a
 * @param {number} b
 * @return {BigInt}
 */
export const bitwiseShl64 = (a, b) => {
  a = (a & 0xffff_ffff_ffff_ffffn) << globalThis.BigInt(b & 0x3f);
  return (a & 0x8000_0000_0000_0000n) // Sign bit is set
    ? ~((~a) & 0x7fff_ffff_ffff_ffffn)
    : a & 0x7fff_ffff_ffff_ffffn;
};
