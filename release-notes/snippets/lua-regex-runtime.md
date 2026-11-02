### Faster, more accurate regexes on Lua

Lua has no regex engine of its own, so `std/regex` there runs on a bundled
build of the temper-regex-engine library. An updated build fixes several
differences from other backends:

- Reluctant repeats like `/a+?/` and alternatives like `/a|ab/` now choose the
  same match as elsewhere.
- Zero-length matches in replace and split no longer loop forever.
- Empty code sets like `/[]*/` and empty groups like `/(?:)/` match the empty
  string.
- `\s` matches a space, and `.` matches every code point except newline.
- Text that isn't valid UTF-8 no longer raises an error while searching.

Searches are also several times faster, and nested repeats like `/(a+a)+b/`
no longer take exponential time.
