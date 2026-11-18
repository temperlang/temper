import { bubble } from "./core.js";

/**
 * Implements std/temporal.type Date.constructor()
 * @param {number} year
 * @param {number} month
 * @param {number} day
 * @returns {Date}
  */
export const dateConstructor = (year, month, day) => {
  let d = new Date(0);
  // If we were to pass year into `new Date`, then it would
  // have 1900 added when in the range [0, 99].
  // developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/Date#year
  d.setUTCFullYear(year, month - 1 /* JS months are zero indexed */, day);
  // JS rolls a day or month out of range over into the next; Temper bubbles.
  if (
    d.getUTCFullYear() !== year || d.getUTCMonth() !== month - 1 ||
      d.getUTCDate() !== day
  ) {
    bubble();
  }
  return d;
};

/**
 * Implements std/temporal.type Date.fromIsoString(), bubbling for what
 * isn't a date such as "2000-12-31".
 * @param {string} isoString
 * @returns {Date}
 */
export const dateFromIsoString = (isoString) => {
  const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoString);
  if (!parts) {
    return bubble();
  }
  return dateConstructor(+parts[1], +parts[2], +parts[3]);
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
