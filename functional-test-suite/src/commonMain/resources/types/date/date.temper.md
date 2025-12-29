# Date Operations

This tests common date operations.

A *Date* is a {year, month, day} triple representing a
day in the proleptic Gregorian calendar.

    let {Date} = import("std/temporal");

Date values need to connect to target languages' backend
types.

https://hackmd.io/i26Rm3q0SPuMqQ0Lrpij8A collects some
standards efforts around *Date*s and other temporal values
and caveats around different languages' core libraries'
representations of dates.

There are a few sources of errors that we'd be
wise to look out for:

- timezone skew when there is no distinction between
  a *DateTime* and a *Date* leading to the day being
  one off.
- when a month field is represented differently, for example:
  `0` means January, so `1` means February instead of `2`.
- when a year field expects a two-digit year, so you need
  to adjust by 1900.

TODO: We need to allow Temper code to properly
stringify and compare dates per https://hackmd.io/@temper/ByBe5Ztco
but in the meantime, lets make sure we can at least
stringify them to an ISO date consistently across backends.

We can create a date:

    let d = { year: 2023, month: /* June */ 6, day: 29 };

Let's look at the fields programmatically:

    console.log(
      """
      "year    = ${ d.year.toString() }
      "month   = ${ d.month.toString() }
      "day     = ${ d.day.toString() }
      "weekday = ${ d.dayOfWeek.toString() }
    );

```log
year    = 2023
month   = 6
day     = 29
weekday = 4
```

And then try to stringify it which should double-check those
assumptions:

    console.log("ISO 8601: ${ d }");

```log
ISO 8601: 2023-06-29
```

Doing some stuff with a legitimately 2-digit year
flushes out a surprising number of bugs.

    let yonksAgo = { year: 12, month: 11, day: 10 };

    console.log("ISO 8601 yonks: ${ yonksAgo }");

    // Make sure the year is 12 and not 1912 as by the
    // "add 1900" rule or 2012 as by the "add 1900 if in [50,99]
    // or add 2000 if in [0, 50)" rule.
    console.log("yonksAgo.year = ${ yonksAgo.year }");

```log
ISO 8601 yonks: 0012-11-10
yonksAgo.year = 12
```

TODO: test negative year support once we've got a
story for Python whose datetime.MIN_YEAR == 1.

## Current date

Also, just prove we can get today's date. Hard to say much about what it should
be, though. Could test some bounds, but eh.

    Date.today();

## Date math

We don't have lots of date math yet, but test what we do have.

    let start = new Date(2010, 3, 21);
    let firstYears = Date.yearsBetween(start, new Date(2012, 3, 20));
    console.log("First years between: ${firstYears}");
    let secondYears = Date.yearsBetween(start, new Date(2012, 3, 21));
    console.log("Second years between: ${secondYears}");

```log
First years between: 1
Second years between: 2
```

## Date parsing

A static method parses dates from an ISO 8601 string.

    let rdc = Date.fromIsoString("1596-03-31");
    console.log(
        "Happy birthday, René: day=${ rdc.day.toString()
        }, month=${ rdc.month.toString()
        }, year=${ rdc.year.toString()
        }")

```log
Happy birthday, René: day=31, month=3, year=1596
```
