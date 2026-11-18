---@meta temper-core

-- The parts of `temper-core`, Kotlin Temper's Lua runtime, that generated
-- code calls, with their types, for LuaLS's checker (the runtime has no
-- annotations, and copies its integer helpers into its table in a loop,
-- which LuaLS can't follow). The tests check generated code against this
-- in place of the runtime, and check that the runtime defines everything
-- here (tests/tables.rs).

---@class temper-core
local temper = {}

---@param a integer
---@param b integer
---@return integer
function temper.int32_add(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.int32_sub(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.int32_mul(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.int32_div(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.int32_mod(a, b) end

---@param a integer
---@return integer
function temper.int32_unm(a) end

---@param a integer
---@param b integer
---@return integer
function temper.int_cmp(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.int64_div(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.int64_mod(a, b) end

---@param a integer
---@return integer
function temper.int64_unm(a) end

---@param a integer
---@param b integer
---@return integer
function temper.int64_cmp(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.band(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.bor(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.bxor(a, b) end

---@param a integer
---@return integer
function temper.bnot(a) end

---@param a integer
---@param b integer
---@return integer
function temper.shl32(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.shr32(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.ushr32(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.shl64(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.shr64(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.ushr64(a, b) end

---@param a number
---@param b number
---@return number
function temper.fmod(a, b) end

---@param a number
---@param b number
---@return boolean
function temper.float_eq(a, b) end

---@param a number
---@param b number
---@return integer
function temper.float_cmp(a, b) end

---@param a string
---@param b string
---@return integer
function temper.str_cmp(a, b) end

---@param a boolean
---@param b boolean
---@return integer
function temper.bool_cmp(a, b) end

---@param n integer
---@param radix integer
---@return string
function temper.int32_tostring(n, radix) end

---Raises Temper's bubble, as an error; it never returns.
---@return any
function temper.bubble() end

---Null, as Temper's nullable types hold it: a value of its own, since
---Lua's `nil` can't be a table's value.
---@class temper.Null

---@type temper.Null
temper.null = {}

---Whether a value is null: `temper.null`, or `nil`.
---@param a any
---@return boolean
function temper.is_null(a) end

---Runs a function, as Lua's `pcall`: whether it ran without an error,
---as `orelse` asks of its body, and what it returned: for a body that
---jumps out of itself, a jump's signal (`v, "return"` or `"break",
---"flow"`).
---@param f fun(): any?, string?
---@return boolean ok
---@return any value
---@return string? kind
function temper.pcall(f) end

---The value, if it's a string; else it bubbles.
---@param thing any
---@return string
function temper.cast_to_string(thing) end

---The value, if it's an integer; else it bubbles.
---@param thing any
---@return integer
function temper.cast_to_int(thing) end

---The value, if it's a number; else it bubbles.
---@param thing any
---@return number
function temper.cast_to_float64(thing) end

---The value, if it's a Boolean; else it bubbles.
---@param thing any
---@return boolean
function temper.cast_to_boolean(thing) end

---Core's conversions, which bubble where Temper's do.

---@param n number
---@return integer
function temper.float64_toint32(n) end

---@param n number
---@return integer
function temper.float64_toint32unsafe(n) end

---@param n number
---@return integer
function temper.float64_toint64(n) end

---@param n number
---@return integer
function temper.float64_toint64unsafe(n) end

---@param n number
---@return string
function temper.float64_tostring(n) end

---A float's sign: -1.0, 1.0, or itself for zero and NaN.
---@param x number
---@return number
function temper.float64_sign(x) end

---@param n integer
---@return number
function temper.int32_tofloat64(n) end

---An `Int64`, from its value, or from its high (signed) and low 32 bits.
---@param hi integer
---@param lo? integer
---@return integer
function temper.int64_constructor(hi, lo) end

---@param n integer
---@return integer
function temper.int32_toint64(n) end

---@param n integer
---@return integer
function temper.int64_toint32(n) end

---@param a integer
---@param b integer
---@return integer
function temper.int64_max(a, b) end

---@param a integer
---@param b integer
---@return integer
function temper.int64_min(a, b) end

---@param n integer
---@return number
function temper.int64_tofloat64(n) end

---@param n integer
---@return number
function temper.int64_tofloat64unsafe(n) end

---@param n integer
---@return integer
function temper.int64_toint32unsafe(n) end

---@param n integer
---@param radix? integer
---@return string
function temper.int64_tostring(n, radix) end

---@param s string
---@return number
function temper.string_tofloat64(s) end

---@param s string
---@param radix? integer
---@return integer
function temper.string_toint32(s, radix) end

---@param s string
---@param radix? integer
---@return integer
function temper.string_toint64(s, radix) end

---What a test failure's message starts with, for LuaUnit.
---@type string
temper.test_failure_prefix = ""

---Runs a test's body with a new test, failing if it errs or the test has
---failures.
---@param name string
---@param body fun(test: table)
function temper.test(name, body) end

---std's `Test.assert`: records the message if `ok` is false.
---@param test table
---@param ok boolean
---@param get_msg fun(): string
function temper.test_assert(test, ok, get_msg) end

---std's `Test.assertHard`: fails the test now if `ok` is false.
---@param test table
---@param ok boolean
---@param msg fun(): string
function temper.test_asserthard(test, ok, msg) end

---std's `Test.bail()`: bubbles.
function temper.test_bail() end

---std's `Test.passing`.
---@param test table
---@return boolean
function temper.test_passing(test) end

---std's `Test.messages()`.
---@param test table
---@return string[]
function temper.test_messages(test) end

---std's `Test.failedOnAssert`.
---@param test table
---@return boolean
function temper.test_failedonassert(test) end

---A class's or interface's table, as `temper.type` makes it: its getters,
---setters, and methods, by name, its constructor, and its statics.
---@class temper.Type
---@field get table<string, fun(this: any): any>
---@field set table<string, fun(this: any, value: any)>
---@field methods table<string, function>
---@field constructor fun(this: any, ...: any)
---@field typename string
---@field [string] any

---Makes a type, with its supertypes' getters, setters, and methods.
---@param typename string
---@param ... temper.Type
---@return temper.Type
function temper.type(typename, ...) end

---Whether a value is an instance of any of the types.
---@param thing any
---@param ... temper.Type
---@return boolean
function temper.instance_of(thing, ...) end

---The value, if it's an instance of the type; else it bubbles.
---@param thing any
---@param type temper.Type
---@return any
function temper.cast_to(thing, type) end

---A list's item; it bubbles out of bounds.
---@generic T
---@param list T[]
---@param index integer
---@return T
function temper.list_get(list, index) end

---A list's item; it bubbles out of bounds.
---@generic T
---@param list T[]
---@param index integer
---@return T
function temper.listed_get(list, index) end

---A list's item, or the fallback out of bounds.
---@generic T
---@param list T[]
---@param index integer
---@param orvalue T
---@return T
function temper.listed_getor(list, index, orvalue) end

---A list's length.
---@generic T
---@param list T[]
---@return integer
function temper.list_length(list) end

---A list's length.
---@generic T
---@param list T[]
---@return integer
function temper.listbuilder_length(list) end

---A list's length.
---@generic T
---@param list T[]
---@return integer
function temper.listed_length(list) end

---Whether a list is empty.
---@generic T
---@param list T[]
---@return boolean
function temper.list_isempty(list) end

---Whether a list is empty.
---@generic T
---@param list T[]
---@return boolean
function temper.listbuilder_isempty(list) end

---Whether a list is empty.
---@generic T
---@param list T[]
---@return boolean
function temper.listed_isempty(list) end

---A copy of a list's items.
---@generic T
---@param list T[]
---@return T[]
function temper.list_tolist(list) end

---A copy of a list's items.
---@generic T
---@param list T[]
---@return T[]
function temper.listbuilder_tolist(list) end

---A copy of a list's items.
---@generic T
---@param list T[]
---@return T[]
function temper.listed_tolist(list) end

---A copy of a list's items.
---@generic T
---@param list T[]
---@return T[]
function temper.list_tolistbuilder(list) end

---A copy of a list's items.
---@generic T
---@param list T[]
---@return T[]
function temper.listbuilder_tolistbuilder(list) end

---A copy of a list's items.
---@generic T
---@param list T[]
---@return T[]
function temper.listed_tolistbuilder(list) end

---A list's items from start to before stop.
---@generic T
---@param list T[]
---@param start integer
---@param stop integer
---@return T[]
function temper.listed_slice(list, start, stop) end

---A list's items, each transformed.
---@generic T, O
---@param l T[]
---@param f fun(arg0: T): O
---@return O[]
function temper.listed_map(l, f) end

---A list's items that pass a predicate.
---@generic T
---@param l T[]
---@param f fun(arg0: T): boolean
---@return T[]
function temper.listed_filter(l, f) end

---A list's items, each made a string, with a separator between.
---@generic T
---@param list T[]
---@param join string
---@param cb fun(arg0: T): string
---@return string
function temper.listed_join(list, join, cb) end

---A list's items, sorted by a comparator, stably.
---@generic T
---@param list T[]
---@param compare fun(arg0: T, arg1: T): integer
---@return T[]
function temper.listed_sorted(list, compare) end

---A list's items reduced, from its first.
---@generic T
---@param list T[]
---@param accumulate fun(arg0: T, arg1: T): T
---@return T
function temper.listed_reduce(list, accumulate) end

---A list's items reduced, from a first value.
---@generic T, O
---@param list T[]
---@param initial O
---@param accumulate fun(arg0: O, arg1: T): O
---@return O
function temper.listed_reducefrom(list, initial, accumulate) end

---Sorts a list builder in place, by a comparator, stably.
---@generic T
---@param list T[]
---@param compare fun(arg0: T, arg1: T): integer
function temper.listbuilder_sort(list, compare) end

---A new list builder.
---@return any[]
function temper.listbuilder_constructor() end

---Adds an item, at the end or at a place.
---@generic T
---@param builder T[]
---@param obj T
---@param at integer?
function temper.listbuilder_add(builder, obj, at) end

---Adds items, at the end or at a place.
---@generic T
---@param builder T[]
---@param from T[]
---@param at integer?
function temper.listbuilder_addall(builder, from, at) end

---Removes every item.
---@generic T
---@param builder T[]
function temper.listbuilder_clear(builder) end

---Removes the last item and gives it.
---@generic T
---@param builder T[]
---@return T
function temper.listbuilder_removelast(builder) end

---Removes `remove` items from `at` (all after it if nil) and puts `new`
---in their place, giving those removed.
---@generic T
---@param builder T[]
---@param at integer?
---@param remove integer?
---@param new T[]?
---@return T[]
function temper.listbuilder_splice(builder, at, remove, new) end

---Reverses the items in place.
---@generic T
---@param builder T[]
function temper.listbuilder_reverse(builder) end

---Sets an item.
---@generic T
---@param builder T[]
---@param index integer
---@param value T
function temper.listbuilder_set(builder, index, value) end

---A map of pairs.
---@generic K, V
---@param list { key: K, value: V }[]
---@return table<K, V>
function temper.map_constructor(list) end

---A new map builder.
---@return table<any, any>
function temper.mapbuilder_constructor() end

---Sets a key's value.
---@generic K, V
---@param builder table<K, V>
---@param key K
---@param value V
function temper.mapbuilder_set(builder, key, value) end

---Removes a key and gives its value; it bubbles if the key is missing.
---@generic K, V
---@param builder table<K, V>
---@param key K
---@return V
function temper.mapbuilder_remove(builder, key) end

---Removes every key.
---@generic K, V
---@param builder table<K, V>
function temper.mapbuilder_clear(builder) end

---A key's value; it bubbles if the key is missing.
---@generic K, V
---@param mapped table<K, V>
---@param key K
---@return V
function temper.mapped_get(mapped, key) end

---A key's value, or the fallback.
---@generic K, V
---@param mapped table<K, V>
---@param key K
---@param otherwise V
---@return V
function temper.mapped_getor(mapped, key, otherwise) end

---Whether a map has a key.
---@generic K, V
---@param mapped table<K, V>
---@param key K
---@return boolean
function temper.mapped_has(mapped, key) end

---How many keys a map has.
---@generic K, V
---@param mapped table<K, V>
---@return integer
function temper.mapped_length(mapped) end

---A map's keys, in order.
---@generic K, V
---@param mapped table<K, V>
---@return K[]
function temper.mapped_keys(mapped) end

---A map's values, in order.
---@generic K, V
---@param mapped table<K, V>
---@return V[]
function temper.mapped_values(mapped) end

---A map's pairs, in order.
---@generic K, V
---@param mapped table<K, V>
---@return { key: K, value: V }[]
function temper.mapped_tolist(mapped) end

---A list of what a function gives for each key and value, in order.
---@generic K, V, T
---@param mapped table<K, V>
---@param func fun(arg0: K, arg1: V): T
---@return T[]
function temper.mapped_tolistwith(mapped, func) end

---A map of a map's entries.
---@generic K, V
---@param mapped table<K, V>
---@return table<K, V>
function temper.mapped_tomap(mapped) end

---A map builder of a map's entries.
---@generic K, V
---@param mapped table<K, V>
---@return table<K, V>
function temper.mapped_tomapbuilder(mapped) end

---Calls a function with each key and value.
---@generic K, V
---@param mapped table<K, V>
---@param func fun(arg0: K, arg1: V)
function temper.mapped_foreach(mapped, func) end

---A pair.
---@generic K, V
---@param a K
---@param b V
---@return { key: K, value: V }
function temper.pair_constructor(a, b) end

---Core's other types and members. String indices are byte offsets from
---1, or -1 for none.

---@param s string
---@return integer
function temper.string_end(s) end

---The code point at an index; it bubbles past the end.
---@param s string
---@param i integer
---@return integer
function temper.string_get(s, i) end

---@param s string
---@param i integer
---@return boolean
function temper.string_hasindex(s, i) end

---@param s string
---@param i integer
---@return integer
function temper.string_next(s, i) end

---@param s string
---@param i integer
---@return integer
function temper.string_prev(s, i) end

---@param s string
---@param i integer
---@param by integer
---@return integer
function temper.string_step(s, i, by) end

---@param s string
---@param begin integer
---@param end_ integer
---@return integer
function temper.string_countbetween(s, begin, end_) end

---@param s string
---@param begin integer
---@param end_ integer
---@param min_count integer
---@return boolean
function temper.string_hasatleast(s, begin, end_, min_count) end

---@param s string
---@param begin integer
---@param end_ integer
---@return string
function temper.string_slice(s, begin, end_) end

---@param s string
---@param separator string
---@return string[]
function temper.string_split(s, separator) end

---@param s string
---@param target string
---@param start? integer
---@return integer
function temper.string_indexof(s, target, start) end

---It bubbles for other than a Unicode scalar value.
---@param code_point integer
---@return string
function temper.string_fromcodepoint(code_point) end

---@param code_points integer[]
---@return string
function temper.string_fromcodepoints(code_points) end

---The index, if it's one; else it bubbles.
---@param i integer
---@return integer
function temper.require_string_index(i) end

----1, if it's no index; else it bubbles.
---@param i integer
---@return integer
function temper.require_no_string_index(i) end

---A string builder: the parts appended so far.
---@class temper.StringBuilder

---@return temper.StringBuilder
function temper.stringbuilder_constructor() end

---@param b temper.StringBuilder
---@param s string
function temper.stringbuilder_append(b, s) end

---It bubbles for other than a Unicode scalar value.
---@param b temper.StringBuilder
---@param code_point integer
function temper.stringbuilder_appendcodepoint(b, code_point) end

---@param b temper.StringBuilder
---@param s string
---@param begin integer
---@param end_ integer
function temper.stringbuilder_appendbetween(b, s, begin, end_) end

---@param b temper.StringBuilder
function temper.stringbuilder_clear(b) end

---@param b temper.StringBuilder
---@return integer
function temper.stringbuilder_end(b) end

---@param b temper.StringBuilder
---@return string
function temper.stringbuilder_tostring(b) end

---A double-ended queue, items added at the end and removed at the front.
---@class temper.Deque

---@return temper.Deque
function temper.deque_constructor() end

---@param d temper.Deque
---@param item any
function temper.deque_add(d, item) end

---@param d temper.Deque
---@return boolean
function temper.deque_isempty(d) end

---The first item, removed; it bubbles if there's none.
---@param d temper.Deque
---@return any
function temper.deque_removefirst(d) end

---A vector of bits, all false at first.
---@class temper.DenseBitVector

---@param capacity integer
---@return temper.DenseBitVector
function temper.densebitvector_constructor(capacity) end

---@param v temper.DenseBitVector
---@param i integer
---@return boolean
function temper.densebitvector_get(v, i) end

---@param v temper.DenseBitVector
---@param i integer
---@param bit boolean
function temper.densebitvector_set(v, i, bit) end

---Core's `Empty`, a table of its own.
---@return table
function temper.empty() end

---@return number
function temper.float64_e() end

---@param y number
---@param x number
---@return number
function temper.float64_atan2(y, x) end

---@param x number
---@return number
function temper.float64_cosh(x) end

---@param x number
---@return number
function temper.float64_sinh(x) end

---@param x number
---@return number
function temper.float64_tanh(x) end

---@param x number
---@return number
function temper.float64_expm1(x) end

---@param x number
---@return number
function temper.float64_log10(x) end

---@param x number
---@return number
function temper.float64_log1p(x) end

---NaN if either is NaN.
---@param x number
---@param y number
---@return number
function temper.float64_max(x, y) end

---NaN if either is NaN.
---@param x number
---@param y number
---@return number
function temper.float64_min(x, y) end

---@param x number
---@return number
function temper.float64_round(x) end

---Whether two floats are close, as Python's `math.isclose`.
---@param x number
---@param y number
---@param rel_tol? number
---@param abs_tol? number
---@return boolean
function temper.float64_near(x, y, rel_tol, abs_tol) end

-- std's `Date`, a table of `year`, `month`, and `day`, which std's
-- connected members are on Lua, as Kotlin's.

---Bubbles for a date that doesn't exist.
---@param year integer
---@param month integer
---@param day integer
---@return any
function temper.date_constructor(year, month, day) end

---@param date any
---@return integer
function temper.date_year(date) end

---@param date any
---@return integer
function temper.date_month(date) end

---@param date any
---@return integer
function temper.date_day(date) end

---ISO's weekday, Monday 1 to Sunday 7.
---@param date any
---@return integer
function temper.date_dayofweek(date) end

---`YYYY-MM-DD`.
---@param date any
---@return string
function temper.date_tostring(date) end

---Bubbles for a string that isn't `YYYY-MM-DD` or a date that doesn't exist.
---@param isostring string
---@return any
function temper.date_fromisostring(isostring) end

---Today, in UTC.
---@return any
function temper.date_today() end

---@param start any
---@param end_ any
---@return integer
function temper.date_yearsbetween(start, end_) end

---A generator: each call runs it to its next pause, giving `true` and
---what it yielded, or nothing once it's done.
---@alias temper.Generator fun(): (boolean?, any)

---@param f fun(...)
---@return fun(...): temper.Generator
function temper.adapt_generator_fn(f) end

---@param generator temper.Generator
---@return boolean?, any
function temper.generator_next(generator) end

---Pauses the generator, giving what it yields, if anything.
---@param ... any
function temper.yield(...) end

---A promise, which an `async` block awaits.
---@class temper.Promise
local Promise = {}

---Its value once it's settled; it bubbles if it's broken.
---@return any
function Promise:await() end

---@class temper.PromiseBuilder

---@return temper.PromiseBuilder
function temper.promisebuilder_constructor() end

---@param builder temper.PromiseBuilder
---@param value any
function temper.promisebuilder_complete(builder, value) end

---@param builder temper.PromiseBuilder
function temper.promisebuilder_breakpromise(builder) end

---@param builder temper.PromiseBuilder
---@return temper.Promise
function temper.promisebuilder_promise(builder) end

---std/net's request, through the client (`temper.set_net_client`).
---@param url string
---@param method string
---@param body_content string|temper.Null
---@param body_mime_type string|temper.Null
---@return temper.Promise
function temper.sendrequest(url, method, body_content, body_mime_type) end

---@param response temper.NetResponse
---@return integer
function temper.netresponse_status(response) end

---@param response temper.NetResponse
---@return string|temper.Null
function temper.netresponse_contenttype(response) end

---@param response temper.NetResponse
---@return temper.Promise
function temper.netresponse_bodycontent(response) end

---A request, as std/net's client gets it.
---@class temper.NetRequest
---@field url string
---@field method string
---@field body_content string?
---@field body_mime_type string?

---A response, as std/net's client gives it.
---@class temper.NetResponse
---@field status integer
---@field content_type string?
---@field body_content string?

---std/net's client: `send` calls `respond` once, with a response, or
---with nil if none came.
---@class temper.NetClient
---@field send fun(self: temper.NetClient, request: temper.NetRequest, respond: fun(response: temper.NetResponse?))

---The client std/net sends through from now on; nil restores the
---default, LuaSocket's.
---@param client temper.NetClient?
function temper.set_net_client(client) end

---I/O that the runner waits for when no step is ready: `source(block)`
---waits for some, blocking only if `block`, and says whether more is in
---flight.
---@param source fun(block: boolean): boolean
function temper.add_io_source(source) end

---Runs an `async` block's generator.
---@param generator_factory fun(): temper.Generator
function temper.async(generator_factory) end

return temper
