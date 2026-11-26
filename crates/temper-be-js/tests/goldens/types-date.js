import {
  dateConstructor,
  dateFromIsoString,
  dateToday,
  dateYearsBetween,
  listedGet,
  listedJoin,
  listedMap,
  panic,
} from "@temperlang/core";
import {
  InvalidDate,
  InvalidIsoDate,
  TemporalError,
} from "@temperlang/std/temporal";

/**
 * @param {number} year
 * @param {number} month
 * @param {number} day
 * @returns {string}
 */
function checkDate(year, month, day) {
  try {
    return dateConstructor(year, month, day, InvalidDate).toISOString().split("T")[0];
  } catch (e) {
    if (!(e instanceof TemporalError)) throw e;
    return /** @type {Error} */ (/** @type {unknown} */ (e)).message;
  }
}

const d = dateConstructor(2023, 6, 29, InvalidDate);
console.log(`year    = ${d.getUTCFullYear()}\nmonth   = ${d.getUTCMonth() + 1}\nday     = ${d.getUTCDate()}\nweekday = ${d.getUTCDay() || 7}`);
console.log(`ISO 8601: ${d.toISOString().split("T")[0]}`);
const yonksAgo = dateConstructor(12, 11, 10, InvalidDate);
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
    console.log(`${y}: Jan 1 weekday ${dateConstructor(y, 1, 1, InvalidDate).getUTCDay() || 7}, Mar 1 weekday ${dateConstructor(y, 3, 1, InvalidDate).getUTCDay() || 7}`);
  }
}
console.log(`${checkDate(2024, 2, 29)} ${checkDate(2023, 2, 29)}`);

/**
 * @param {string} s
 * @returns {string}
 */
function fn(s) {
  try {
    return dateFromIsoString(s, InvalidIsoDate).toISOString().split("T")[0];
  } catch (e$) {
    if (!(e$ instanceof TemporalError)) throw e$;
    return "none";
  }
}

/** @type {readonly string[]} */
const parsed = listedMap(/** @type {string[]} */ (Object.freeze(["2024-02-29", "2023-02-29", "2023-2-1"])), fn);
console.log(listedJoin(/** @type {string[]} */ (parsed), " ", (it) => it));
dateToday();
const start = dateConstructor(2010, 3, 21, InvalidDate);
const firstYears = dateYearsBetween(start, dateConstructor(2012, 3, 20, InvalidDate));
console.log(`First years between: ${firstYears}`);
const secondYears = dateYearsBetween(start, dateConstructor(2012, 3, 21, InvalidDate));
console.log(`Second years between: ${secondYears}`);
/** @type {globalThis.Date} */
let rdc;
try {
  rdc = dateFromIsoString("1596-03-31", InvalidIsoDate);
} catch (e$) {
  if (!(e$ instanceof TemporalError)) throw e$;
  rdc = panic("panic", e$);
}
console.log(`Happy birthday, René: day=${rdc.getUTCDate()}, month=${rdc.getUTCMonth() + 1}, year=${rdc.getUTCFullYear()}`);
