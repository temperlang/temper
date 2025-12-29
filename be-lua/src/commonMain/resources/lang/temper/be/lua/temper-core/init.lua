
local TEMPER_LUA_DEBUG_PCALL = TEMPER_LUA_DEBUG_PCALL

local temper = {}

local temper_meta = {}

function temper_meta.__index(tab, ent)
    error("bad connected key: " .. ent)
end

local map_key_order = {}

setmetatable(temper, temper_meta)

package.loaded['temper-core/prelude'] = temper

local error = error
local math = math
local next = next
local pairs = pairs
local pcall = pcall
local print = print
local rawget = rawget
local rawset = rawset
local require = require
local select = select
local setmetatable = setmetatable
local tonumber = tonumber
local tostring = tostring
local type = type
local load = load or loadstring

local coro_wrap = coroutine.wrap
local coro_yield = coroutine.yield

local math_exp = math.exp
local math_floor = math.floor
local math_fmod = math.fmod
local math_huge = math.huge
local math_log = math.log
local math_max = math.max
local math_min = math.min
local math_modf = math.modf

local string_byte = string.byte
local string_char = string.char
local string_find = string.find
local string_format = string.format
local string_gsub = string.gsub
local string_len = string.len
local string_match = string.match
local string_sub = string.sub

local table_concat = concat or table.concat
local table_insert = table.insert
local table_remove = table.remove
local table_unpack = unpack or table.unpack


function temper.wrap_func(f)
    return f
end

local temper_bit, temper_int
do
    local success, int
    success, int = pcall(require, "temper-core/intnew")
    if success then
        temper_bit = int.bitops
        temper_int = int.temper
    else
        local int = require("temper-core/intold")
        temper_bit = int.bitops
        temper_int = int.temper
    end
end
for k, v in pairs(temper_int) do
    temper[k] = v
end

function temper.codepoint_fallback(s, i)
    -- returns the codepoint and the index of the next character in the stream.
    local b0, b1, b2, b3
    b0 = string.byte(s, i)
    if b0 == nil then return 0xFFFD; end

    if b0 < 128 then return b0; end

    if b0 < 192 then return 0xFFFD; end

    if b0 < 224 then
        b1 = string.byte(s, i + 1)
        -- TODO: these checks should really be (b1 & 192) == 128
        if b1 == nil or b1 >= 192 then return 0xFFFD; end
        return (b0 - 192)*64 + b1
    end

    if b0 < 240 then
        b1 = string.byte(s, i + 1)
        b2 = string.byte(s, i + 2)
        if b2 == nil or b1 >= 192 or b2 >= 192 then return 0xFFFD; end
        return (b0 - 224)*4096 + b1%64*64 + b2%64
    end

    if b0 < 248 then
        b1 = string.byte(s, i + 1)
        b2 = string.byte(s, i + 2)
        b3 = string.byte(s, i + 3)
        if b3 == nil or b1 >= 192 or b2 >= 192 or b3 >= 192 then return 0xFFFD; end
        return (b0 - 240)*262144 + b1%64*4096 + b2%64*64 + b3%64
    end

    return 0xFFFD
end

local temper_utf8 = {}
if utf8 ~= nil then
    temper_utf8 = utf8
else
    temper_utf8 = {}

    function temper_utf8.char(code)
        if code <= 0x7F then
            return string_char(code)
        end

        if code <= 0x7FF then
            local b0 = 0xC0 + math_floor(code / 0x40)
            local b1 = 0x80 + (code % 0x40)
            return string_char(b0, b1)
        end

        if code <= 0xFFFF then
            local b0 = 0xE0 +  math_floor(code / 0x1000)
            local b1 = 0x80 + (math_floor(code / 0x40) % 0x40)
            local b2 = 0x80 + (code % 0x40)
            return string_char(b0, b1, b2)
        end

        if code <= 0x10FFFF then
            local code = code
            local b3= 0x80 + (code % 0x40)
            code       = math_floor(code / 0x40)
            local b2= 0x80 + (code % 0x40)
            code       = math_floor(code / 0x40)
            local b1= 0x80 + (code % 0x40)
            code       = math_floor(code / 0x40)
            local b0= 0xF0 + code

            return string_char(b0, b1, b2, b3)
        end

        temper.bubble("temper_utf8.char > 0x10FFFF")
    end

    function temper_utf8.len(str)
        return select(2, string.gsub(str, "[^\128-\193]", ""))
    end

    function temper_utf8.codepoint(str, i)
        return temper.codepoint_fallback(str, i)
    end
end

temper.null = setmetatable({}, {
    __tostring = function()
        return "temper.null"
    end
})
temper.type_tag = setmetatable({}, {
    __tostring = function()
        return "temper.type_tag"
    end
})

function temper.null_to_nil(x)
    if x == temper.null then
        return nil
    end
    return x
end

temper.pos_inf = math_huge
temper.neg_inf = -math_huge
temper.nan = 0.0 / 0.0

local utf8len_tab = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 3, 4}

local function utf8len(str, index)
    local len = utf8len_tab[math_floor(string_byte(str, index) / 16) + 1]
    local max_len = string_len(str) - index + 1
    if len > max_len then
        return max_len
    else
        return len
    end
end

do
    local bubble_meta = {
        __tostring = function(self)
            return tostring(self.msg or "Bubble")
        end
    }

    local bubble_object = setmetatable({
        msg = "Bubble"
    }, bubble_meta)

    if TEMPER_LUA_DEBUG_PCALL then
        function temper.pcall(f, ...)
            local res = {pcall(f, ...)}
            if res[1] then
                return table_unpack(res)
            end
            if type(res[2]) ~= 'table' then
                io.stderr:write(res[2] .. '\n')
                error(res[2])
            else
                return table_unpack(res)
            end
        end
    else
        temper.pcall = pcall
    end

    function temper.bubble(msg)
        if msg ~= nil then
            error(setmetatable({
                msg = msg
            }, bubble_meta))
        else
            error(bubble_object)
        end
    end
end

function temper.yield()
    coro_yield()
end

function temper.adapt_generator_fn(f)
    -- TODO: how do we forwards args to f on initial call?
    -- Do we need a wrapper from the translator instead of
    -- wrapping here?
    return function()
        return coro_wrap(f)
    end
end

function temper.generator_next(f)
    return f()
end

do
    local inst_meta = {
        __index = function(self, k)
            local tab = rawget(self, temper.type_tag)
            local getter = tab.get[k]
            if getter ~= nil then
                return getter(self, k)
            end
            local method = tab.methods[k]
            if method ~= nil then
                return method
            end
            local value = rawget(self, k)
            if value ~= nil then
                return value
            end
            temper.bubble("no such key, getter, or method on " .. tab.typename .. ": " .. k)
        end,
        __newindex = function(self, k, v)
            local tab = rawget(self, temper.type_tag)
            local setter = tab.set[k]
            if setter ~= nil then
                return setter(self, v)
            else
                return rawset(self, k, v)
            end
        end
    }

    local type_meta = {
        __call = function(self, ...)
            local inst = {}
            inst[temper.type_tag] = self
            setmetatable(inst, inst_meta)
            self.constructor(inst, ...)
            return inst
        end,
        __index = {
            constructor = function(self)
            end
        },
        __tostring = function(self)
            return self.typename
        end
    }

    function temper.type(typename, ...)
        local get = {}
        local set = {}
        local methods = {}
        local super = {}

        for i = 1, select('#', ...) do
            local t = select(i, ...)
            for k, v in pairs(t.get) do
                get[k] = v
            end
            for k, v in pairs(t.set) do
                set[k] = v
            end
            for k, v in pairs(t.methods) do
                methods[k] = v
            end
            for k, v in pairs(t.super) do
                super[k] = true
            end
        end

        local tab = {
            get = get,
            set = set,
            methods = methods,
            super = super,
            typename = typename
        }

        tab.super[typename] = true

        for i = 1, #super do
            local t = super[i]
            for k, v in pairs(t) do
                tab[k] = tab[k] or v
            end
        end

        setmetatable(tab, type_meta)

        return tab
    end
end

-- Math constants which we leave as functions for now for convenience.

do
    local e = math.exp(1)
    function temper.float64_e()
        return e
    end
end

do
    local pi = math.pi
    function temper.float64_pi()
        return pi
    end
end

-- Easy functions that are already supported well in builtin lua math.

temper.float64_abs = math.abs
temper.float64_acos = math.acos
temper.float64_asin = math.asin
temper.float64_atan = math.atan
temper.float64_ceil = math.ceil
temper.float64_cos = math.cos
temper.float64_exp = math.exp
temper.float64_floor = math.floor
temper.float64_log = math.log
temper.float64_sin = math.sin
temper.float64_sqrt = math.sqrt
temper.float64_tan = math.tan

-- Functions that take more effort.

if math.atan(1, -1) > 1 then
    -- Must be newer lua with optional-arg atan.
    temper.float64_atan2 = math.atan
else
    temper.float64_atan2 = math.atan2
end

if math.cosh ~= nil then
    -- Officially deprecated but might be available.
    temper.float64_cosh = math.cosh
else
    function temper.float64_cosh(x)
        -- TODO Numerical stability?
        return (math_exp(x) + math_exp(-x)) * 0.5
    end
end

function temper.float64_expm1(x)
    return math_exp(x) - 1
end

function temper.float64_log10(x)
    return math_log(x, 10)
end

function temper.float64_log1p(x)
    return math_log(x + 1)
end

function temper.float64_max(x, y)
    if y ~= y then
        -- Nan if either is nan
        return y
    else
        return math_max(x, y)
    end
end

function temper.float64_min(x, y)
    if y ~= y then
        -- Nan if either is nan
        return y
    else
        return math_min(x, y)
    end
end

function temper.float64_near(x, y, rel_tol, abs_tol)
    rel_tol = temper.null_to_nil(rel_tol) or 1e-9
    abs_tol = temper.null_to_nil(abs_tol) or 0.0
    local scale = temper.float64_max(temper.float64_abs(x), temper.float64_abs(y))
    local margin = temper.float64_max(scale * rel_tol, abs_tol)
    return temper.float64_abs(x - y) < margin
end

function temper.float64_round(x)
    -- TODO Do we worry about rounding modes?
    return math_floor(x + 0.5)
end

function temper.float64_sign(x)
    if x == 0 or x ~= x then
        -- Maintain sign for 0 and nan.
        return x
    elseif x < 0 then
        return -1.0
    else
        return 1.0
    end
end

if math.sinh ~= nil then
    -- Officially deprecated but might be available.
    temper.float64_sinh = math.sinh
else
    function temper.float64_sinh(x)
        -- TODO Numerical stability?
        return (math_exp(x) - math_exp(-x)) * 0.5
    end
end

if math.tanh ~= nil then
    -- Officially deprecated but might be available.
    temper.float64_tanh = math.tanh
else
    function temper.float64_tanh(x)
        -- TODO Numerical stability?
        local e2x = math_exp(2 * x)
        return (e2x - 1) / (e2x + 1)
    end
end

-- Other float functions.

function temper.float64_tostring(n)
    if n == nil or n ~= n then
        return "NaN"
    elseif n == math_huge then
        return "Infinity"
    elseif n == -math_huge then
        return "-Infinity"
    elseif n == 0 then
        local ret = tostring(n)
        if not string_find(ret, '.', 1, true) then
            ret = ret .. '.0'
        end
        return ret
    else
        local s = string_format("%.16g", n)
        -- Presumes we never see "N.0+" but possibly "N.M0+". TODO Examples?
        s = string_gsub(s, "0+e", "e")
        -- Ensure ".0" on integer significand.
        if string_find(s, ".", nil, true) == nil then
            if string_find(s, "e") then
                s = string_gsub(s, "e", ".0e")
            else
                s = s .. ".0"
            end
        end
        -- Strip leading exponent zeros on windows or elsewhere.
        s = string_gsub(s, "([eE][-+]?)0+(%d)", "%1%2")
        return s
    end
end

temper.int32_max = math_max
temper.int32_min = math_min

temper.int32_maxvalue = 0x7fffffff
temper.int32_minvalue = -0x80000000

function temper.is_safe_int32(n)
    return temper.int32_minvalue <= n and n <= temper.int32_maxvalue
end

function temper.is_safe_integer(n)
    local ret, _ = temper.pcall(function()
        return string_format("%i", n)
    end)
    return ret
end

function temper.float64_toint32(n)
    local ret = 0
    if n < 0 then
        ret = -math_floor(-n)
    else
        ret = math_floor(n)
    end
    if temper.is_safe_int32(ret) then
        return ret
    else
        return temper.bubble("Float64::toInt32 failed")
    end
end

function temper.float64_toint32unsafe(n)
    if n < 0 then
        return -math_floor(-n)
    else
        return math_floor(n)
    end
end

-- Other functions.

function temper.listof(...)
    return {...}
end

function temper.listbuilder_constructor(list)
    local ret = {
        [temper.type_tag] = 'ListBuilder'
    }
    if list ~= nil then
        for i = 1, #list do
            ret[i] = list[i]
        end
    end
    return ret
end

function temper.list_length(list)
    if list == nil then
        return 0
    end
    return #list
end

function temper.deque_constructor()
    return {
        head = 1,
        tail = 1,
        [temper.type_tag] = 'Deque'
    }
end

function temper.deque_add(deque, obj)
    local tail = deque.tail
    deque[tail] = obj
    deque.tail = tail + 1
end

function temper.deque_isempty(deque)
    return deque.head == deque.tail
end

function temper.deque_removefirst(deque)
    local head = deque.head
    if head == deque.tail then
        return temper.bubble("Deque::removeFirst on empty deque")
    end
    local first = deque[head]
    deque.head = head + 1
    deque[head] = nil
    return first
end

function temper.empty()
    return {}
end

do
    local function temper_reduce_from_index(list, initial, index, accumulate)
        local result = initial
        for i = index, #list do
            result = accumulate(result, list[i])
        end
        return result
    end

    function temper.listed_reduce(list, accumulate)
        local initial = temper.listed_get(list, 0)
        return temper_reduce_from_index(list, initial, 2, accumulate)
    end

    function temper.listed_reducefrom(list, initial, accumulate)
        return temper_reduce_from_index(list, initial, 1, accumulate)
    end
end

function temper.listed_slice(list, start, stop)
    local len = #list
    start = temper.null_to_nil(start) or 0
    stop = temper.null_to_nil(stop) or len
    if start < 0 then
        start = 0
    end
    if stop > len then
        stop = len
    end
    local ret = {}
    local head = 1
    for i = start + 1, stop do
        ret[head] = list[i]
        head = head + 1
    end
    return ret
end

function temper.listed_sorted(list, compare)
    local ret = temper.list_tolistbuilder(list)
    temper.listbuilder_sort(ret, compare)
    return ret
end

do
    local function temper_length(list)
        return #list
    end

    temper.list_length = temper_length
    temper.listed_length = temper_length
    temper.listbuilder_length = temper_length
end

do
    local function temper_tolist(list)
        if list == nil then
            return {}
        end
        local ret = {}
        for i = 1, #list do
            ret[i] = list[i]
        end
        return ret
    end

    temper.list_tolist = temper_tolist
    temper.listed_tolist = temper_tolist
    temper.listbuilder_tolist = temper_tolist
    temper.list_tolistbuilder = temper_tolist
    temper.listed_tolistbuilder = temper_tolist
    temper.listbuilder_tolistbuilder = temper_tolist
end

do
    local function temper_isempty(thing)
        return #thing == 0
    end

    temper.list_isempty = temper_isempty
    temper.listed_isempty = temper_isempty
    temper.listbuilder_isempty = temper_isempty
end

do
    local function temper_foreach(l, f)
        for i=1, #l do
            f(l[i])
        end
    end

    temper.list_foreach = temper_foreach
    temper.listed_foreach = temper_foreach
    temper.listbuilder_foreach = temper_foreach
end

function temper.listed_map(l, f)
    local r = {}
    for i = 1, #l do
        r[i] = f(l[i])
    end
    return r
end

function temper.listed_filter(l, f)
    local ret = {}
    local head = 1
    for i = 1, #l do
        local cur = l[i]
        if f(cur) then
            ret[head] = cur
            head = head + 1
        end
    end
    return ret
end

function temper.listed_join(list, join, cb)
    local newlist = {}
    for i = 1, #list do
        newlist[i] = tostring(cb(list[i]))
    end
    return table_concat(newlist, join)
end

function temper.listed_getor(list, index, orvalue)
    local got = list[index + 1]
    if got == nil then
        return orvalue
    end
    return got
end

do
    local function temper_get(list, index)
        local got = list[index + 1]
        if got == nil then
            return temper.bubble("Listed::get(" .. tostring(index) .. ") index out of bounds 0 .. " .. tostring(#list))
        end
        return got
    end

    temper.list_get = temper_get
    temper.listed_get = temper_get
end

do
    local function temper_get(mapped, index)
        local got = mapped[index]
        if got == nil then
            temper.bubble("Mapped::get no such key " .. tostring(index))
        end
        return got
    end

    temper.map_get = temper_get
    temper.mapped_get = temper_get
    temper.mapbuilder_get = temper_get
end

do
    local function temper_getor(mapped, index, otherwise)
        local got = mapped[index]
        if got == nil then
            return otherwise
        end
        return got
    end

    temper.map_getor = temper_getor
    temper.mapped_getor = temper_getor
    temper.mapbuilder_getor = temper_getor
end

function temper.mapped_has(mapped, key)
    return mapped[key] ~= nil
end

function temper.mapped_length(mapped)
    return #mapped[map_key_order]
end

function temper.mapped_keys(mapped)
    local list = {}
    local i = 1
    for k, v in temper.pairs(mapped) do
        list[i] = k
        i = i + 1
    end
    return list
end

function temper.mapped_values(mapped)
    local list = {}
    local i = 1
    for k, v in temper.pairs(mapped) do
        list[i] = v
        i = i + 1
    end
    return list
end

do
    local function temper_tolist(mapped)
        local list = {}
        local i = 1
        for k, v in temper.pairs(mapped) do
            list[i] = temper.pair_constructor(k, v)
            i = i + 1
        end
        return list
    end

    temper.mapped_tolist = temper_tolist
    temper.mapped_tolistbuilder = temper_tolist
end

do
    local function temper_tolistwith(mapped, func)
        local list = {}
        local i = 1
        for k, v in temper.pairs(mapped) do
            list[i] = func(k, v)
            i = i + 1
        end
        return list
    end

    temper.mapped_tolistwith = temper_tolistwith
    temper.mapped_tolistbuilderwith = temper_tolistwith
end

function temper.mapped_foreach(mapped, func)
    for k, v in temper.pairs(mapped) do
        func(k, v)
    end
end

function temper.list_foreach(list, func)
    for _, v in ipairs(list) do
        func(v)
    end
end

function temper.listbuilder_set(list, index, value)
    list[index + 1] = value
end

-- Defer loading powersort, because it also loads temper-core.
local powersort
function temper.listbuilder_sort(list, compare)
    if powersort == nil then
        powersort = require("temper-core/powersort").powersort
    end
    powersort(list, compare)
end

function temper.import(mod, ent)
    return require(mod)[ent]
end

function temper.listbuilder_splice(builder, at, remove, new)
    local len = #builder
    at = temper.null_to_nil(at) or 0
    remove = temper.null_to_nil(remove) or len
    new = temper.null_to_nil(new) or {}
    if at < 0 or at > len then
        return temper.bubble("ListBuilder::splice index to high or too many to remove")
    end
    local ret = {}
    for i = 1, remove do
        ret[i] = table_remove(builder, at + 1)
    end
    temper.listbuilder_addall(builder, new, at)
    return ret
end

function temper.ignore()
end

function temper.listbuilder_addall(builder, from, at)
    local len = #builder
    at = temper.null_to_nil(at) or len
    if at < 0 or at > len then
        return temper.bubble("ListBuilder::addAll index " .. tostring(at) .. "out of range 0 .. " .. tostring(len))
    end
    if at == len then
        for read = 1, #from do
            at = at + 1
            builder[at] = from[read]
        end
    else
        for read = 1, #from do
            at = at + 1
            table_insert(builder, at, from[read])
        end
    end
end

function temper.listbuilder_add(builder, obj, at)
    local len = #builder
    at = temper.null_to_nil(at) or len
    if at < 0 or at > len then
        return temper.bubble("ListBuilder::add index " .. tostring(at) .. "out of range 0 .. " .. tostring(len))
    end
    if at == len then
        builder[at + 1] = obj
    else
        table_insert(builder, at + 1, obj)
    end
end

function temper.table()
    return {}
end

function temper.getindex(t, k)
    return t[k]
end

function temper.setindex(t, k, v)
    t[k] = v
end

temper.int32_toint64 = temper.int64_constructor

function temper.int32_tofloat64(n)
    return n
end

function temper.void(...)
    return nil
end

function temper.log(msg)
    print(msg)
end

function temper.bool_not(a)
    return not a
end

temper.bor = temper_bit.bor
temper.band = temper_bit.band

function temper.concat(...)
    return table_concat({...})
end

local function zero_cmp(a, b)
    local ret = 0
    if 1 / a > 0 then
        ret = ret + 1
    end
    if 1 / b > 0 then
        ret = ret - 1
    end
    return ret
end

function temper.float_eq(a, b)
    if a ~= a and b ~= b then
        return true
    end
    if a == 0 and b == 0 then
        return zero_cmp(a, b) == 0
    end
    return a == b
end

function temper.float_ne(a, b)
    if (a ~= a) ~= (b ~= b) then
        return true
    end
    if a == 0 and b == 0 then
        return zero_cmp(a, b) ~= 0
    end
    return a ~= b
end

function temper.float_lt(a, b)
    if b ~= b then
        return a == a
    end
    if a == 0 and b == 0 then
        return zero_cmp(a, b) < 0
    end
    return a < b
end

function temper.float_le(a, b)
    if b ~= b then
        return true
    end
    if a == 0 and b == 0 then
        return zero_cmp(a, b) <= 0
    end
    return a <= b
end

function temper.float_gt(a, b)
    if a ~= a then
        return b == b
    end
    if a == 0 and b == 0 then
        return zero_cmp(a, b) > 0
    end
    return a > b
end

function temper.float_ge(a, b)
    if a ~= a then
        return true
    end
    if a == 0 and b == 0 then
        return zero_cmp(a, b) >= 0
    end
    return a >= b
end

function temper.generic_cmp(a, b)
    if temper.generic_lt(a, b) then
        return -1
    elseif temper.generic_gt(a, b) then
        return 1
    else
        return 0
    end
end

function temper.generic_eq(a, b)
    if a ~= a and b ~= b then
        return true
    end
    if a == 0 and b == 0 then
        return zero_cmp(a, b) == 0
    end
    return a == b
end

function temper.generic_ne(a, b)
    if a ~= a and b ~= b then
        return false
    end
    if a == 0 and b == 0 then
        return zero_cmp(a, b) ~= 0
    end
    return a ~= b
end

function temper.generic_lt(a, b)
    if a == 0 and b == 0 then
        return zero_cmp(a, b) < 0
    end
    return a < b
end

function temper.generic_le(a, b)
    if a == 0 and b == 0 then
        return zero_cmp(a, b) <= 0
    end
    return a <= b
end

function temper.generic_gt(a, b)
    if a == 0 and b == 0 then
        return zero_cmp(a, b) > 0
    end
    return a > b
end

function temper.generic_ge(a, b)
    if a == 0 and b == 0 then
        return zero_cmp(a, b) >= 0
    end
    return a >= b
end

function temper.str_eq(a, b)
    return a == b
end

function temper.str_ne(a, b)
    return a ~= b
end

function temper.str_lt(a, b)
    return a < b
end

function temper.str_le(a, b)
    return a <= b
end

function temper.str_gt(a, b)
    return a > b
end

function temper.str_ge(a, b)
    return a >= b
end

function temper.unm(a)
    return -a
end

function temper.add(a, b)
    return a + b
end

function temper.sub(a, b)
    return a - b
end

function temper.mul(a, b)
    return a * b
end

function temper.pow(a, b)
    return a ^ b
end

function temper.fdiv(a, b)
    return a / b
end

function temper.fmod(a, b)
    return math_fmod(a, b)
end

function temper.is_null(a)
    return a == temper.null or a == nil
end

do
    local digits = "0123456789abcdefghijklmnopqrstuvwzyz"

    local function temper_tostring(num, base)
        if num < 0 then
            return "-" .. temper_tostring(-num, base)
        elseif num >= base then
            local mod = num % base + 1
            return temper_tostring(math_floor(num / base), base) .. string_sub(digits, mod, mod)
        else
            local mod = num % base + 1
            return string_sub(digits, mod, mod)
        end
    end

    function temper.int32_tostring(num, base)
        base = temper.null_to_nil(base) or 10
        if base == 10 then
            return string_format("%i", num)
        else
            return temper_tostring(num, base)
        end
    end

    if rawget(temper, "int64_tostring") == nil then
        temper.int64_tostring = temper.int32_tostring
    end

    local function parse_int64(s, base)
        base = base or 10
        if base < 2 or base > 36 then
            temper.bubble("bad base")
        end
        -- Trim whitespace.
        s = s:match("^%s*(.-)%s*$")
        if s == "" then
            temper.bubble("empty")
        end
        -- Check sign.
        local neg = s:sub(1, 1) == "-"
        if neg then
            s = s:sub(2)
        end
        if s == "" then
            temper.bubble("empty")
        end
        -- Trim leading zeros.
        s = s:match("^0*(.*)")
        local max = temper.int64_maxvalue
        local min = temper.int64_minvalue
        local n = temper.int64_constructor(0)
        local b64 = temper.int64_constructor(base)
        for c in s:gmatch(".") do
            local d = 100
            if c >= "0" and c <= "9" then
                d = string.byte(c) - 48
            elseif c >= "A" and c <= "Z" then
                d = string.byte(c) - 55
            elseif c >= "a" and c <= "z" then
                d = string.byte(c) - 87
            end
            if d >= base then
                temper.bubble("bad digit")
            end
            d = temper.int64_constructor(d)
            if neg then
                if -n < temper.int64_div((min + d), b64) then
                    temper.bubble("underflow")
                end
            else
                if n > temper.int64_div((max - d), b64) then
                    temper.bubble("overflow")
                end
            end
            n = n * b64 + d
        end
        if neg then
            n = -n
        end
        return n
    end

    temper.string_toint64 = parse_int64
end

function temper.stringindex_compareto(a, b)
    if a < b then
        return -1
    elseif a > b then
        return 1
    else
        return 0
    end
end

function temper.string_isempty(s)
    return string_len(s) == 0
end

function temper.string_fromcodepoint(code_point)
    if
        code_point < 0
        or (code_point >= 0xD800 and code_point <= 0xDFFF)
        or code_point > 0x10FFFF
    then
        temper.bubble("String::fromCodePoint invalid scalar value")
    end
    return temper_utf8.char(code_point)
end

function temper.string_fromcodepoints(code_points)
    -- Buffer table expected faster for large arrays, whereas .. might be faster
    -- for small ones.
    local buffer = {}
    for _, code_point in ipairs(code_points) do
        -- This also checks the bounds of each.
        -- TODO Instead call splatted math_max in batches for bounds check?
        table_insert(buffer, temper.string_fromcodepoint(code_point))
    end
    return table_concat(buffer)
end

function temper.stringbuilder_constructor()
    return {
        [temper.type_tag] = 'StringBuilder'
    }
end

function temper.stringbuilder_append(builder, substring)
    builder[#builder + 1] = substring
end

function temper.stringbuilder_appendcodepoint(builder, cp)
    builder[#builder + 1] = temper.string_fromcodepoint(cp)
end

function temper.stringbuilder_appendbetween(builder, source, begin, end_)
    builder[#builder + 1] = temper.string_slice(source, begin, end_)
end

function temper.stringbuilder_tostring(builder)
    return table_concat(builder)
end

function temper.stringindexoption_compareto(a, b)
    return a - b
end

function temper.stringindexoption_compareto_eq(a, b)
    return a == b
end

function temper.stringindexoption_compareto_le(a, b)
    return a <= b
end

function temper.stringindexoption_compareto_lt(a, b)
    return a < b
end

function temper.stringindexoption_compareto_ge(a, b)
    return a >= b
end

function temper.stringindexoption_compareto_gt(a, b)
    return a > b
end

function temper.stringindexoption_compareto_ne(a, b)
    return a ~= b
end

function temper.string_tostring(s)
    return s
end

function temper.listbuilder_clear(lb)
    for k in pairs(lb) do
        if k ~= temper.type_tag then
            lb[k] = nil
        end
    end
end

function temper.listbuilder_removelast(lb)
    local len = #lb
    if len == 0 then
        return temper.bubble("ListBuilder::removeLast on empty list")
    end
    local got = lb[len]
    lb[len] = nil
    return got
end

function temper.listbuilder_reverse(lb)
    for head = 1, #lb / 2 do
        local tail = #lb - (head - 1)
        local tail_value = lb[tail]
        local head_value = lb[head]
        lb[head] = tail_value
        lb[tail] = head_value
    end

    return lb
end

function temper.string_split(str, sep)
    local b = 0
    local res = {}

    if #sep == 0 then
        local res = {
            [temper.type_tag] = 'List'
        }
        local index = 1
        local max = string_len(str)
        local head = 1
        while index <= max do
            local len = utf8len(str, index)
            res[head] = string_sub(str, index, index + len - 1)
            head = head + 1
            index = index + len
        end
        return res
    end

    while b <= #str do
        local e, e2 = string_find(str, sep, b, true)
        if e then
            res[#res + 1] = string_sub(str, b, e - 1)
            b = e2 + 1
            if b > #str then
                res[#res + 1] = ""
            end
        else
            res[#res + 1] = string_sub(str, b)
            break
        end
    end
    return res
end

local special_float_table = {
    ["Infinity"] = math_huge,
    ["-Infinity"] = -math_huge,
    ["NaN"] = 0.0 / 0.0
}

function temper.string_tofloat64(str)
    local ret = special_float_table[str]
    if ret ~= nil then
        return ret
    end
    local match = {string_match(str, "^%s*(-?)%d+(%.?)(%d*)[eE]?[-+]?%d*%s*$")}
    if next(match) then
        local sign, dot, frac = table_unpack(match)
        if dot == "" or frac ~= "" then
            ret = tonumber(str)
            if ret == 0 and sign == "-" then -- and dot == "", but meh
                ret = -0.0
            end
            if ret then
                return ret
            end
        end
    end
    temper.bubble("String::toFloat64 failed")
end

function temper.string_toint32(str, radix)
    if radix == nil then
        radix = 10
    end
    local ret = tonumber(str, radix)
    if temper.is_safe_int32(ret) then
        return ret
    end
    temper.bubble("String::toInt32 failed")
end

function temper.string_end(str)
    return #str + 1 -- String::begin is 1 and range ends need to be exclusive
end

function temper.string_slice(str, begin, end_)
    local str_end = #str + 1
    begin = math_min(begin, str_end)
    end_ = math_max(math_min(end_, str_end), end_)
    if begin == end_ then
        return ""
    end
    return string_sub(str, begin, end_ - 1)
end

function temper.string_get(str, i)
    if i > #str then
        temper.bubble("String::get failed")
    end
    local ok, cp = pcall(temper_utf8.codepoint, str, i)
    if ok then return cp; end
    return temper.codepoint_fallback(str, i)
end

function temper.string_countbetween(str, begin, end_)
    local str_end = #str + 1
    begin = math_min(begin, str_end)
    end_ = math_max(math_min(end_, str_end), begin)
    if (begin == end_) then
        return 0
    end
    return temper_utf8.len(str, begin, end_ - 1)
end

function temper.string_hasatleast(str, begin, end_, min_count)
    local str_end = #str + 1
    begin = math_min(begin, str_end)
    end_ = math_max(math_min(end_, str_end), begin)
    if (begin == end_) then
        return false
    end
    local n_bytes = end_ - begin
    if (n_bytes < min_count) then
        return false
    end
    if n_bytes >= min_count * 4 then
        return true
    end
    -- TODO: do not iterate more than we need
    -- just count enough bytes ignoring ones that match 10xxxxxx
    return temper_utf8.len(str, begin, end_ - 1) >= min_count
end

function temper.string_hasindex(str, i)
    return 1 <= i and i <= #str
end

function temper.string_indexof(str, target, i)
    return string.find(str, target, i, true) or 0
end

function temper.string_next(str, i)
    local str_end = #str + 1
    if i >= #str then
        return str_end
    elseif i < 1 then
        return 1
    end
    i = i + 1
    while i < str_end do
        if temper.band(string_byte(str, i), 0xC0) == 0x80 then
            i = i + 1
        else
            break
        end
    end
    return i
end

function temper.string_prev(str, i)
    local str_end = #str + 1
    if i > str_end then
        return str_end
    elseif i <= 2 then
        return 1
    end
    i = i - 1
    while i > 1 do
        if temper.band(string_byte(str, i), 0xC0) == 0x80 then
            i = i - 1
        else
            break
        end
    end
    return i
end

function temper.string_step(str, i, by)
    local old_i
    if by >= 0 then
        for _ = 1, by do
            old_i = i
            i = temper.string_next(str, i)
            if i == old_i then
                break
            end
        end
    else
        for _ = 1, -by do
            old_i = i
            i = temper.string_prev(str, i)
            if i == old_i then
                break
            end
        end
    end
    return i
end

function temper.is_no_string_index(i)
    return i < 1
end

function temper.is_string_index(i)
    return i >= 1
end

function temper.require_no_string_index(i)
    if temper.is_no_string_index(i) then
        return -1
    else
        temper.bubble("required no string index but got " .. i)
    end
end

function temper.require_string_index(i)
    if temper.is_string_index(i) then
        return i
    else
        temper.bubble("required string index but got " .. i)
    end
end

function temper.string_foreach(str, f)
    for _, c in utf8.codes(str) do
        f(c)
    end
end

function temper.boolean_tostring(b)
    if b then
        return "true"
    else
        return "false"
    end
end

function temper.pairs(self)
    local key_order = rawget(self, map_key_order)
    local i = 0
    return function()
        i = i + 1
        local key = key_order[i]
        if key == nil then
            return nil
        end
        return key, rawget(self, key)
    end
end

function temper.map_constructor(list)
    local key_order = {}
    local ret = {
        [map_key_order] = key_order,
        [temper.type_tag] = 'Map'
    }
    if list ~= nil then
        for i = 1, #list do
            local cur = list[i]
            local key = cur.key
            key_order[i] = key
            ret[key] = cur.value
        end
    end
    return ret
end

local mapbuilder_meta = {
    __newindex = function(self, key, value)
        local store = rawget(self, map_key_order)
        store[#store + 1] = key
        rawset(self, key, value)
    end
}

function temper.mapbuilder_constructor()
    return setmetatable({
        [map_key_order] = {},
        [temper.type_tag] = 'MapBuilder'
    }, mapbuilder_meta)
end

function temper.mapbuilder_set(builder, key, value)
    builder[key] = value
end

function temper.mapbuilder_clear(lb)
    for k in pairs(lb) do
        if k ~= temper.type_tag and k ~= map_key_order then
            lb[k] = nil
        end
    end
    local store = rawget(lb, map_key_order)
    for k in pairs(store) do
        store[k] = nil
    end
end

function temper.mapbuilder_remove(builder, key)
    local got = builder[key]
    if got == nil then
        temper.bubble("MapBuilder::remove key not found: " .. tostring(key))
    end
    builder[key] = nil
    return got
end

do
    local function temper_tomap(mapped)
        if mapped[temper.type_tag] == 'Map' then
            return mapped
        end
        local map = {
            [temper.type_tag] = 'Map'
        }
        local order = {}
        local i = 1
        for k, v in temper.pairs(mapped) do
            order[i] = k
            map[k] = v
            i = i + 1
        end
        map[map_key_order] = order
        return map
    end

    temper.mapped_tomap = temper_tomap
    temper.map_tomap = temper_tomap
    temper.mapbuilder_tomap = temper_tomap
end

do
    local function temper_tomapbuilder(mapped)
        local mapbuilder = temper.mapbuilder_constructor()
        for k, v in temper.pairs(mapped) do
            mapbuilder[k] = v
        end
        return mapbuilder
    end

    temper.mapped_tomapbuilder = temper_tomapbuilder
    temper.map_tomapbuilder = temper_tomapbuilder
    temper.mapbuilder_tomapbuilder = temper_tomapbuilder
end

function temper.pair_constructor(a, b)
    return {
        key = a,
        value = b,
        [temper.type_tag] = 'Pair'
    }
end

do
    local is_leap_year_then_days = {
        [false] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31},
        [true] = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31}
    }

    local function is_leap_year(year)
        return year % 4 == 0 and (year % 100 ~= 0 or year % 400 == 0)
    end

    local function days_in_month_of_year(year, month)
        return is_leap_year_then_days[is_leap_year(year)][month]
    end

    function temper.date_constructor(year, month, day)
        if not (1 <= month and month <= 12) then
            temper.bubble("Date::constructor bad month " .. month)
        end
        if not (1 <= day and day <= days_in_month_of_year(year, month)) then
            temper.bubble("Date::constructor can only go up to day " .. days_in_month_of_year(year, month) .. ", got " .. day)
        end
        return {
            year = year,
            month = month,
            day = day,
            [temper.type_tag] = 'Date'
        }
    end
end

function temper.date_getyear(date)
    return date.year
end

function temper.date_getmonth(date)
    return date.month
end

function temper.date_getday(date)
    return date.day
end

function temper.date_getdayofweek(date)
    -- os.date week days are 1 indexed: Sunday is 1, Monday is 2, ... Saturday is 7
    local wday = os.date("*t", os.time {
        year = date.year,
        month = date.month,
        day = date.day
    }).wday
    -- convert to iso wday: Monday = 1, ... Sunday = 7
    if wday == 1 then
        return 7
    else
        return wday - 1
    end
end

do
    local function pad_num_with(pad, num)
        local str = string_format("%i", num)
        local sign
        if string_byte(str, 1) == 45 then
            sign = "-"
            str = string_sub(pad, 2, string_len(str))
        else
            sign = ""
        end
        local needpad = string_len(pad) - string_len(str)
        if needpad <= 0 then
            return str
        else
            return sign .. string_sub(pad, 1, needpad) .. str
        end
    end

    function temper.date_tostring(date)
        local t = {}
        t[1] = pad_num_with("0000", date.year)
        t[2] = pad_num_with("00", date.month)
        t[3] = pad_num_with("00", date.day)
        return table_concat(t, "-")
    end
end

function temper.date_fromisostring(isostring)
    if string_len(isostring) ~= 10 or string_byte(isostring, 5) ~= 45 or string_byte(isostring, 8) ~= 45 then
        temper.bubble(isostring)
    end
    local year  = tonumber(string_sub(isostring, 1, 4))
    local month = tonumber(string_sub(isostring, 6, 7))
    local day   = tonumber(string_sub(isostring, 9, 10))
    if year == nil or month == nil or day == nil then
        temper.bubble(isostring)
    end
    return temper.date_constructor(year, month, day)
end

function temper.date_today()
    -- The '!' below causes formatting according to UTC.
    return temper.date_fromisostring(os.date('!%Y-%m-%d', os.time()))
end

function temper.date_yearsbetween(start, end_)
    local yearDelta = end_.year - start.year
    local monthDelta = end_.month - start.month
    local adj = 0
    if monthDelta < 0 or (monthDelta == 0 and end_.day < start.day) then
        adj = -1
    end
    return yearDelta + adj
end

function temper.densebitvector_constructor(size)
    local vec = {
        [temper.type_tag] = 'DenseBitVector'
    }
    return vec
end

function temper.densebitvector_get(vec, key)
    return not not vec[key]
end

function temper.densebitvector_set(vec, key, setto)
    vec[key] = setto
end

function temper.cast_to_boolean(thing)
    if type(thing) ~= 'boolean' then
        return temper.bubble("cast to Boolean")
    end
    return thing
end

function temper.cast_to_float64(thing)
    if type(thing) ~= 'number' then
        return temper.bubble("cast to Float64")
    end
    return thing
end

function temper.cast_to_int(thing)
    if type(thing) ~= 'number' or thing ~= math_floor(thing) then
        return temper.bubble("cast to Int")
    end
    return thing
end

function temper.cast_to_string(thing)
    if type(thing) ~= 'string' then
        return temper.bubble("cast to String")
    end
    return thing
end

function temper.cast_to_function(thing)
    if type(thing) ~= 'function' then
        return temper.bubble("cast to Function")
    end
    return thing
end

function temper.cast_to_list(thing)
    if type(thing) ~= 'table' or thing[temper.type_tag] ~= 'List' then
        return temper.bubble("cast to List")
    end
    return thing
end

function temper.cast_to_listbuilder(thing)
    if type(thing) ~= 'table' or thing[temper.type_tag] ~= 'ListBuilder' then
        return temper.bubble("cast to ListBuilder")
    end
    return thing
end

function temper.cast_to_map(thing)
    if type(thing) ~= 'table' or thing[temper.type_tag] ~= 'Map' then
        return temper.bubble("cast to Map")
    end
    return thing
end

function temper.cast_to_mapbuilder(thing)
    if type(thing) ~= 'table' or thing[temper.type_tag] ~= 'MapBuilder' then
        return temper.bubble("cast to MapBuilder")
    end
    return thing
end

function temper.cast_to_null(thing)
    if thing ~= temper.null then
        return temper.bubble("cast to null")
    end
    return thing
end

function temper.cast_to(thing, type)
    if thing and thing[temper.type_tag].super[type.typename] then
        return thing
    else
        return temper.bubble("cast to " .. tostring(type) .. " type (was a " .. tostring(thing[temper.type_tag]) .. ")")
    end
end

function temper.instance_of(thing, tag)
    return type(thing) == "table" and thing[temper.type_tag].super[tag.typename]
end

function temper.test_bail()
    return temper.bubble()
end

function temper.test_asserthard(test, ok, msg)
    if not ok then
        return temper.test_bail(msg)
    end
end

function temper.test_passing(test)
    return #test.failures == 0
end

function temper.test_messages(test)
    return temper.listbuilder_tolist(test.failures)
end

function temper.test_failedonassert(test)

end

-- Use for explicitly coordinating LuaUnit in generated test modules.
temper.test_failure_prefix = 'temper test FAILURE: '

function temper.test(name, cb)
    local test = {
        failures = {
            [temper.type_tag] = 'ListBuilder'
        }
    }
    local ok, err = temper.pcall(function()
        cb(test)
    end)

    -- Presume any error/bubble was just a hard assertion by default.
    -- TODO Instead check if failed on assert.
    if #test.failures ~= 0 then
        error(temper.test_failure_prefix .. table_concat(test.failures, ", "), 2)
    elseif not ok then
        -- TODO Does this lose the original stack trace? Sad if so.
        error(err)
    end
end

function temper.test_assert(test, ok, get_msg)
    if not ok then
        test.failures[#test.failures + 1] = get_msg()
    end
end

do
    local TemperRegexNFA = nil

    function temper.regex_format(data)
        if TemperRegexNFA == nil then
            require('temper-core/regex/runtime')
            TemperRegexNFA = require("temper-regex-engine/nfa/nfa").TemperRegexNFA
        end
        -- return TemperRegexNFA.fromRegex(regex.data)
        -- retutrn TemperRegexNFA.fromRegexDFA(regex.data)
        return TemperRegexNFA.fromRegexLua(data, function(str)
            return load(str)()
        end)
    end

    function temper.regex_compiledfind(self, pat, text, begin)
        local got = pat:find(text, begin)
        if got.full.name ~= "" then
            return got
        end
        return temper.bubble('could not text in pattern')
    end

    function temper.regex_compiledfound(self, pat, text)
        return pat:found(text)
    end

    function temper.regex_compileformatted(self, regex)
        return regex
    end

    function temper.regex_compiledreplace(self, pat, text, format, refs)
        return pat:replace(text, format)
    end

    function temper.regex_compiledsplit(self, pat, text)
        return pat:split(text)
    end
end

return temper
