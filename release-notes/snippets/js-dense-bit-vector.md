### *DenseBitVector* keeps far bits on JavaScript and C++

On JavaScript, setting a *DenseBitVector* bit at index 128 or above could
silently drop it, whatever capacity was requested. On C++, getting or setting
any bit past the capacity bubbled. Both now behave as documented: setting a bit
grows the vector enough to hold it, and unset bits read as `false`.
