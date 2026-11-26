/**
 * The date, or null where there's no such day.
 * @param {number} year
 * @param {number} month
 * @param {number} day
 * @returns {Date | null}
 */
const dateOrNull = (year, month, day) => {
  let d = new Date(0);
  // If we were to pass year into `new Date`, then it would
  // have 1900 added when in the range [0, 99].
  // developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/Date#year
  d.setUTCFullYear(year, month - 1 /* JS months are zero indexed */, day);
  // JS rolls a day or month out of range over into the next; Temper doesn't.
  if (
    d.getUTCFullYear() !== year || d.getUTCMonth() !== month - 1 ||
      d.getUTCDate() !== day
  ) {
    return null;
  }
  return d;
};

/**
 * Implements std/temporal.type Date.constructor(), throwing std's
 * `InvalidDate`, which generated code gives, as this runtime can't
 * import std, where there's no such day.
 * @param {number} year
 * @param {number} month
 * @param {number} day
 * @param {new (year: number, month: number, day: number) => Error} invalidDate
 * @returns {Date}
 */
export const dateConstructor = (year, month, day, invalidDate) => {
  const d = dateOrNull(year, month, day);
  if (d === null) {
    throw new invalidDate(year, month, day);
  }
  return d;
};

/**
 * Implements std/temporal.type Date.fromIsoString(), throwing std's
 * `InvalidIsoDate`, which generated code gives, for what isn't a date
 * such as "2000-12-31".
 * @param {string} isoString
 * @param {new (text: string) => Error} invalidIsoDate
 * @returns {Date}
 */
export const dateFromIsoString = (isoString, invalidIsoDate) => {
  const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoString);
  const d = parts && dateOrNull(+parts[1], +parts[2], +parts[3]);
  if (!d) {
    throw new invalidIsoDate(isoString);
  }
  return d;
};

/**
 * Implements std/temporal.type Date.today()
 * @returns {Date}
 */
export const dateToday = () => {
  let d = new Date(Date.now());
  // Get rid of the time component.
  d.setTime(
    Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())
  );
  return d;
};

/**
 * Implements std/temporal.type Date.yearsBetween()
 * @param {Date} start
 * @param {Date} end
 * @returns {number}
 */
export const dateYearsBetween = (start, end) => {
  let yearDelta = end.getUTCFullYear() - start.getUTCFullYear();
  let monthDelta = end.getUTCMonth() - start.getUTCMonth();
  return yearDelta -
    // If the end month/day is before the start's then we
    // don't have a full year.
    (monthDelta < 0 || monthDelta === 0 && end.getUTCDate() < start.getUTCDate());
};
