import {
  denseBitVectorConstructor,
  denseBitVectorGet,
  denseBitVectorSet,
} from "@temperlang/core";

const v = denseBitVectorConstructor(2);
denseBitVectorSet(v, 5, false);
console.log(`v[5]=${denseBitVectorGet(v, 5)}`);
console.log("before");
denseBitVectorSet(v, -1, false);
console.log("not reached");
