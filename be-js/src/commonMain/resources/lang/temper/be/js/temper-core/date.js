/**
 * Implements Date::constructor
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
  d.setUTCFullYear(year);
  d.setUTCMonth(month - 1 /* JS months are zero indexed */);
  d.setUTCDate(day);  // UTCDay is day of the week
  return d;
};

/**
 * Implements Date::today
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
 * Implements Date::yearsBetween
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
