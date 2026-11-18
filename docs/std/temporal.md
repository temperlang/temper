# `std/temporal`: dates

```temper
let { Date } = import("std/temporal");
```

`Date` is a calendar date in the Gregorian calendar, without a time or
a time zone. Each backend uses its language's own date type where it
has one: `DateTime` in C#, `java.time.LocalDate` in Java, JavaScript's
`Date`, and Python's `datetime.date`.

```temper
let { Date } = import("std/temporal");

let d = new Date(2024, 2, 29);
console.log(d.toString());                    //!outputs "2024-02-29"
console.log(d.dayOfWeek.toString());          //!outputs "4"
let later = Date.fromIsoString("2026-03-01") orelse panic();
console.log(Date.yearsBetween(d, later).toString()); //!outputs "2"
// ✅
```

| Member | What it does |
|---|---|
| `new Date(year, month, day)` | A date; `month` is 1 to 12 and `day` 1 to 31, checked against the month; bubbles if out of range |
| `year`, `month`, `day` | Its parts; 1900 means 1900, and January is 1 |
| `toString()` | ISO 8601 with dashes, as `"2000-12-31"` |
| `Date.fromIsoString(s)` | Parses that form back; bubbles if malformed |
| `Date.today()` | Today's date, in UTC |
| `Date.yearsBetween(start, end)` | Whole years from `start` to `end` |
| `dayOfWeek` | ISO 8601 weekday: 1 for Monday to 7 for Sunday |

> **Note:** its doc comment says the result is the floor of the
> range's magnitude, but `Date.yearsBetween(later, earlier)` is
> negative, and off by one, on both compilers' interpreters: `-3` for
> the dates above ([inconsistencies](../inconsistencies.md#i21-dateyearsbetween-with-the-later-date-first)).

`Date` is `@json`, written as its ISO string. The module is a first step;
times, durations, and time zones aren't in it yet.

Source: [temporal.temper.md](../../lib/std/temporal/temporal.temper.md).
