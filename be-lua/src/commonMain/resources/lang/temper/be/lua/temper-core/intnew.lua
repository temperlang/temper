-- For actual bitwise operators that don't parse until Lua 5.3.
-- By being separate, we can recover from attempted require.
local bitops = {}

function bitops.band(a, b)
    return a & b
end

function bitops.bor(a, b)
    return a | b
end

function bitops.bxor(a, b)
    return a ~ b
end

local temper = {}

-- Helper

local int64_maxvalue = 0x7fffffffffffffff
local int64_minvalue = 0x8000000000000000
local mantissa_max = 0x1fffffffffffff

local function trunc(n)
    if n > 0 then
        return math.floor(n)
    else
        return math.ceil(n)
    end
end

local function int64_div(a, b)
    local result = a // b
    if (a < 0) ~= (b < 0) and a % b ~= 0 then
        -- Truncate instead of flooring.
        result = result + 1
    end
    return result
end

local function int64_mod(a, b)
    return a - b * int64_div(a, b)
end

local function int64_toint32unsafe(n)
    local result = n & 0xffffffff
    if result >= 0x80000000 then
        result = result - 0x100000000
    end
    return result
end

-- Int32

function temper.int32_add(a, b)
    return int64_toint32unsafe(a + b)
end

function temper.int32_div(a, b)
    return int64_toint32unsafe(int64_div(a, b))
end

temper.int32_max = math.max
temper.int32_min = math.min

function temper.int32_mod(a, b)
    return int64_toint32unsafe(int64_mod(a, b))
end

function temper.int32_mul(a, b)
    return int64_toint32unsafe(a * b)
end

function temper.int32_sub(a, b)
    return int64_toint32unsafe(a - b)
end

function temper.int32_unm(a)
    return int64_toint32unsafe(-a)
end

-- Int64

function temper.float64_toint64(n)
    if -mantissa_max <= n and n <= mantissa_max then
        return temper.float64_toint64unsafe(n)
    end
    error("out of range")
end

function temper.float64_toint64unsafe(x)
    local result = math.tointeger(trunc(x))
    if result == nil then
        if x > 0 then
            result = int64_maxvalue
        elseif x < 0 then
            result = int64_minvalue
        else -- nan
            result = 0
        end
    end
    return result
end

function temper.int64_constructor(hi, lo)
    if type(hi) == "string" then
        return temper.string_toint64(hi)
    else
        local ret = hi
        if lo ~= nil then
            -- Optional second arg to allow non-string int64 init on old lua.
            -- For now, presume we're on lua53+ so it's good enough.
            -- TODO Call into the int64 abstraction layer.
            ret = (ret << 32) | lo
        end
        return ret
    end
end

temper.int64_div = int64_div
temper.int64_mod = int64_mod

temper.int64_max = math.max
temper.int64_min = math.min

temper.int64_maxvalue = int64_maxvalue
temper.int64_minvalue = int64_minvalue

function temper.int64_tofloat64(n)
    if -mantissa_max <= n and n <= mantissa_max then
        return temper.int64_tofloat64unsafe(n)
    end
    error("out of range")
end

function temper.int64_tofloat64unsafe(n)
    return n + 0.0
end

function temper.int64_unm(n)
    return -n
end

temper.int64_toint32unsafe = int64_toint32unsafe

function temper.string_toint64(str, radix)
    if radix == nil then
        radix = 10
    end
    local ret = tonumber(str, radix)
    if temper.int64_is_safe(ret) then
        return ret
    end
    error("bad parse")
end

return {
    bitops = bitops,
    temper = temper
}
