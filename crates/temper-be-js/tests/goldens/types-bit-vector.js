import {
  denseBitVectorConstructor,
  denseBitVectorGet,
  denseBitVectorSet,
} from "@temperlang/core";

const v = denseBitVectorConstructor(16);
console.log(`v[2]=${denseBitVectorGet(v, 2)}`);
console.log(`v[3]=${denseBitVectorGet(v, 3)}`);
console.log("v[3] <- true");
denseBitVectorSet(v, 3, true);
console.log(`v[2]=${denseBitVectorGet(v, 2)}`);
console.log(`v[3]=${denseBitVectorGet(v, 3)}`);
console.log("v[3] <- false");
denseBitVectorSet(v, 3, false);
console.log(`v[2]=${denseBitVectorGet(v, 2)}`);
console.log(`v[3]=${denseBitVectorGet(v, 3)}`);
denseBitVectorSet(v, 100, true);
denseBitVectorSet(v, 200, false);
console.log(`v[99]=${denseBitVectorGet(v, 99)} v[100]=${denseBitVectorGet(v, 100)} v[101]=${denseBitVectorGet(v, 101)} v[200]=${denseBitVectorGet(v, 200)} v[-1]=${denseBitVectorGet(v, -1)}`);
denseBitVectorSet(v, 1000, true);
console.log(`v[999]=${denseBitVectorGet(v, 999)} v[1000]=${denseBitVectorGet(v, 1000)} v[100]=${denseBitVectorGet(v, 100)}`);
const u = denseBitVectorConstructor(0);
denseBitVectorSet(u, 8, true);
denseBitVectorSet(u, 32, true);
denseBitVectorSet(u, 64, true);
console.log(`${denseBitVectorGet(u, 7)} ${denseBitVectorGet(u, 8)} ${denseBitVectorGet(u, 9)} ${denseBitVectorGet(u, 31)} ${denseBitVectorGet(u, 32)} ${denseBitVectorGet(u, 33)} ${denseBitVectorGet(u, 63)} ${denseBitVectorGet(u, 64)} ${denseBitVectorGet(u, 65)}`);
denseBitVectorSet(v, 1000, false);
console.log(`v[1000]=${denseBitVectorGet(v, 1000)} v[999]=${denseBitVectorGet(v, 999)}`);
const w = denseBitVectorConstructor(-9);
denseBitVectorSet(w, 1, true);
console.log(`w[0]=${denseBitVectorGet(w, 0)} w[1]=${denseBitVectorGet(w, 1)}`);
console.log("before");
denseBitVectorSet(v, -1, true);
console.log("not reached");
