import {
  dateConstructor,
  dateFromIsoString,
  dateToday,
  dateYearsBetween,
  listedGet,
} from "@temperlang/core";

const d = dateConstructor(2023, 6, 29);
console.log(`year    = ${d.getUTCFullYear()}\nmonth   = ${d.getUTCMonth() + 1}\nday     = ${d.getUTCDate()}\nweekday = ${d.getUTCDay() || 7}`);
console.log(`ISO 8601: ${d.toISOString().split("T")[0]}`);
const yonksAgo = dateConstructor(12, 11, 10);
console.log(`ISO 8601 yonks: ${yonksAgo.toISOString().split("T")[0]}`);
console.log(`yonksAgo.year = ${yonksAgo.getUTCFullYear()}`);
{
  /** @type {readonly number[]} */
  const list = Object.freeze([1700, 1800, 1900, 2000, 2100, 2400]);
  const length = list.length;
  let i = 0;
  while (i < length) {
    const y = listedGet(/** @type {number[]} */ (list), i);
    i = (i + 1) | 0;
    console.log(`${y}: Jan 1 weekday ${dateConstructor(y, 1, 1).getUTCDay() || 7}, Mar 1 weekday ${dateConstructor(y, 3, 1).getUTCDay() || 7}`);
  }
}
dateToday();
const start = dateConstructor(2010, 3, 21);
const firstYears = dateYearsBetween(start, dateConstructor(2012, 3, 20));
console.log(`First years between: ${firstYears}`);
const secondYears = dateYearsBetween(start, dateConstructor(2012, 3, 21));
console.log(`Second years between: ${secondYears}`);
const rdc = dateFromIsoString("1596-03-31");
console.log(`Happy birthday, René: day=${rdc.getUTCDate()}, month=${rdc.getUTCMonth() + 1}, year=${rdc.getUTCFullYear()}`);
