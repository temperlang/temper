# Temporal

We're creating an initial Date type to help with developing
Temper's machinery to connect to existing Date types in
target languages.

Some facts about the Gregorian calendar.

    /** Indexed by the month number: 1 = January */
    let daysInMonth = [
      0,
      /* January   */ 31,
      /* February  */ 28, // Special case leap days
      /* March     */ 31,
      /* April     */ 30,
      /* May       */ 31,
      /* June      */ 30,
      /* July      */ 31,
      /* August    */ 31,
      /* September */ 30,
      /* October   */ 31,
      /* November  */ 30,
      /* December  */ 31,
    ];

    let isLeapYear(year: Int): Boolean {
       year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }

    /**
     * If the decimal representation of \|num\| is longer than [minWidth],
     * then appends that representation.
     * Otherwise any sign for [num] followed by enough zeroes to bring the
     * whole length up to [minWidth].
     *
     * ```temper
     * // When the width is greater than the decimal's length,
     * // we pad to that width.
     * "0123" == do {
     *   let sb = new StringBuilder();
     *   padTo(4, 123, sb);
     *   sb.toString()
     * }
     *
     * // When the width is the same or lesser, we just use the string form.
     * "123" == do {
     *   let sb = new StringBuilder();
     *   padTo(2, 123, sb);
     *   sb.toString()
     * }
     *
     * // The sign is always on the left.
     * "-01" == do {
     *   let sb = new StringBuilder();
     *   padTo(3, -1, sb);
     *   sb.toString()
     * }
     * ```
     */
    let padTo(minWidth: Int, num: Int, sb: StringBuilder): Void {
      let decimal = num.toString(10);
      var decimalIndex = String.begin;
      let decimalEnd = decimal.end;
      if (decimalIndex < decimalEnd && decimal[decimalIndex] == char'-') {
        sb.append("-");
        decimalIndex = decimal.next(decimalIndex);
      }
      var nNeeded = minWidth - decimal.countBetween(decimalIndex, decimalEnd);
      while (nNeeded > 0) {
        sb.append('0');
        nNeeded -= 1;
      }
      sb.appendBetween(decimal, decimalIndex, decimalEnd);
    }

    // Relates months (one-indexed) to numbers used in day-of-week
    // computations non-leapy.
    let dayOfWeekLookupTableLeapy: List<Int> = [
      0, // Not a month
      0, 3, 4, 0, 2, 5, 0, 3, 6, 1, 4, 6,
    ];
    let dayOfWeekLookupTableNotLeapy: List<Int> = [
      0, // Not a month
      0, 3, 3, 6, 1, 4, 6, 2, 5, 0, 3, 5,
    ];

Here's just enough of a Date type to get us started.

    /**
     * A Date identifies a day in the proleptic Gregorian calendar.
     * It is unconnected to a time of day or a timezone.
     */
    @json
    @connected("Date")
    export class Date {
      /** The year.  1900 means 1900. */
      @connected("Date::getYear")
      public year: Int;
      /** The month of the year in [1, 12]. */
      @connected("Date::getMonth")
      public month: Int;
      /**
       * The day of the month in [1, 31]
       * additionally constrained by the length of [month].
       */
      @connected("Date::getDay")
      public day: Int;

      @connected("Date::constructor")
      public constructor(year: Int, month: Int, day: Int): Void throws Bubble {
        if (1 <= month && month <= 12 &&
            1 <= day && (
              if (month != 2 || day != 29) {
                day <= daysInMonth[month]
              } else {
                isLeapYear(year)
              })) {
          this.year = year;
          this.month = month;
          this.day = day;
        } else {
          bubble();
        }
      }

      /** An ISO 8601 Date string with dashes like "2000-12-31". */
      @connected("Date::toString")
      public toString(): String {
        let sb = new StringBuilder();
        padTo(4, year, sb);
        sb.append("-");
        padTo(2, month, sb);
        sb.append("-");
        padTo(2, day, sb);
        return sb.toString();
      }

      /** Parses a Date from an ISO 8601 Date string with dashes like "2000-12-21". */
      @connected("Date::fromIsoString")
      public static fromIsoString(isoString: String): Date throws Bubble {
        let end = isoString.end;
        var strIndex = isoString.prev(isoString.prev(end));
        // strIndex at '^'
        // YYYY-MM-^DD
        let beforeDay = strIndex;
        strIndex = isoString.prev(strIndex);
        // YYYY-MM^-DD
        let afterMonth = strIndex;
        if (!isoString.hasIndex(afterMonth) || isoString[strIndex] != char'-') {
          bubble();
        }
        strIndex = isoString.prev(isoString.prev(strIndex));
        // YYYY-^MM-DD
        let beforeMonth = strIndex;
        strIndex = isoString.prev(strIndex);
        // YYYY^-MM-DD
        if (isoString[strIndex] != char'-' ||
            !isoString.hasAtLeast(String.begin, strIndex, 4)) {
          bubble();
        }
        let day   = isoString.slice(beforeDay,    end)       .toInt32(10);
        let month = isoString.slice(beforeMonth,  afterMonth).toInt32(10);
        let year  = isoString.slice(String.begin, strIndex)  .toInt32(10);
        return new Date(year, month, day);
      }

      /**
       * The count of whole years between the two dates.
       *
       * Think of this as floor of the magnitude of a range:
       *
       *     ⌊ [start, end] ⌋
       *
       * If you think of it as subtraction, you have to reverse
       * the order of arguments.
       *
       *     ⌊ end - start ⌋, NOT ⌊ start - end ⌋
       *
       * "Whole year" is based on month/day calculations, not
       * day-of-year.  This means that there is one full year
       * between 2020-03-01 and 2021-03-01 even though, because
       * February of 2020 has 29 days, 2020-03-01 is the 61st
       * day of 2020 but 2021-03-01 is only the 60th day of
       * that year.
       */
      @connected("Date::yearsBetween")
      public static let yearsBetween(start: Date, end: Date): Int {
        let yearDelta = end.year - start.year;
        let monthDelta = end.month - start.month;
        yearDelta - (
            // If the end month/day is before the start's then we
            // don't have a full year.
            if (monthDelta < 0 || monthDelta == 0 && end.day < start.day) {
              1
            } else {
              0
            })
      }

      /** Today's date in UTC */
      // TODO: take a zone
      @connected("Date::today")
      public static let today(): Date;

      /**
       * ISO 8601 weekday number.
       *
       * | Number | Weekday  |
       * | ------ | -------- |
       * |      1 | Monday   |
       * |      2 | Tuesday  |
       * |      3 | Monday   |
       * |      4 | Thursday |
       * |      5 | Friday   |
       * |      6 | Saturday |
       * |      7 | Sunday   |
       */
      @connected("Date::getDayOfWeek")
      public get dayOfWeek(): Int {
        // Gauss's method.
        let y = year;
        let c = if (y >= 0) { y / 100 } else { -(-y / 100) };
        let yy = y - (c * 100);
        // See note below about avoiding negative modulus to see why
        // some of the offsets differ from Wikipedia's rendering of
        // Gauss's formula.
        let janFirst = (8 + 5*((yy + 3) % 4) + 3*(yy - 1) + 5*(c % 4)) % 7;
        let table = if (isLeapYear(y)) {
          dayOfWeekLookupTableLeapy
        } else {
          dayOfWeekLookupTableNotLeapy
        };
        let monthOffset = table[month];
        // Gauss's method produces a number in 0..6 but
        // ISO assigns 1..7 where all values are the same
        // except that Sunday is 7 instead of 0.
        // Below we do (day + 6) since that is equivalent to
        // (day - 1) where we end up % 7 but avoids any chance
        // of a negative left operand to `%`.
        let gaussWeekday = (janFirst + (day + 6) + monthOffset) % 7;
        if (gaussWeekday == 0) { 7 } else { gaussWeekday }
      }

      public encodeToJson(p: JsonProducer): Void {
        p.stringValue(toString());
      }

      public static decodeFromJson(
        t: JsonSyntaxTree,
        ic: InterchangeContext,
      ): Date throws Bubble {
        Date.fromIsoString((t as JsonString).content)
      }
    };

Dates marshal to and from JSON via the ISO string form.

    let {
      InterchangeContext,
      JsonProducer,
      JsonString,
      JsonSyntaxTree
    } = import("../json");

TODO: an auto-balancing Date builder.
Other temporal values
Day of week
