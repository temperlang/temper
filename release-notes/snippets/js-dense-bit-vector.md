### JavaScript *DenseBitVector* keeps far bits

On JavaScript, setting a *DenseBitVector* bit more than twice past its
capacity silently dropped it. The vector now grows enough to hold the bit.
