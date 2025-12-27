local bitops

-- TODO Deal better with bnot(0) or other differences between these.
-- TODO Bit ops for int64.
local bitops = bit or bit32

if bitops == nil then
    bitops = {}
    local OR, XOR, AND = 1, 3, 4

    -- Changed from here: https://stackoverflow.com/a/32389020/2748187
    local function bitwise(op)
        return function(a, b)
            -- This still doesn't handle all cases, including lacking support
            -- for negatives.
            -- TODO A better fallback for lua 5.1.
            local r, m = 0, 2 ^ 31
            repeat
                local s = a + b + m
                a, b = a % m, b % m
                r, m = r + m * op % (s - a - b), m / 2
            until m < 1
            return r
        end
    end

    bitops.band = bitwise(AND)
    bitops.bxor = bitwise(XOR)
    bitops.bor = bitwise(OR)
end

local temper = {}
local int64 = {}
int64.__index = int64

-- Helpers

local int32_maxvalue = 0x7fffffff
local int32_minvalue = -0x80000000
local uint32_halfmax = -int32_minvalue
local uint32_maxvalue = 0xffffffff
local int32_over = 0x100000000
local mantissa_max = 0x1fffffffffffff

local function frac(x)
    local _, result = math.modf(x)
    return result
end

local function int32_mul_parts(a, b)
    -- Multiply could overflow 52 bits, so subdivide here.
    local a_lo = bitops.band(a, 0xFFFF)
    local a_hi = bitops.rshift(a, 16)
    local b_lo = bitops.band(b, 0xFFFF)
    local b_hi = bitops.rshift(b, 16)
    local lo_lo = a_lo * b_lo
    local hi_lo = a_hi * b_lo
    local lo_hi = a_lo * b_hi
    -- TODO Product hi*hi is unused in some cases. Ok cost?
    return a_hi * b_hi, hi_lo + lo_hi, lo_lo
end

local function int64_cmp(a, b)
    local a_neg = a.hi >= uint32_halfmax
    local b_neg = b.hi >= uint32_halfmax
    if a_neg ~= b_neg then
        return a_neg and -1 or 1
    end
    if a.hi ~= b.hi then
        return a.hi < b.hi and -1 or 1
    else
        return a.lo < b.lo and -1 or (a.lo > b.lo and 1 or 0)
    end
end

local function int64_divmod(a, b)
    assert(b.hi ~= 0 or b.lo ~= 0, "division by zero")
    -- Handle sign, so we can work unsigned below.
    local neg = false
    if a.hi >= uint32_halfmax then
        a = -a
        neg = not neg
    end
    if b.hi >= uint32_halfmax then
        b = -b
        neg = not neg
    end
    -- Define helpers adjusted for local use cases.
    local function shl1(v)
        local hi = v.hi * 2
        local lo = v.lo * 2
        if lo >= int32_over then
            hi = hi + 1
            lo = lo - int32_over
        end
        v.hi = hi % int32_over
        v.lo = lo
    end
    local function ge(a, b)
        return a.hi > b.hi or a.hi == b.hi and a.lo >= b.lo
    end
    local function sub(a, b)
        local hi = a.hi - b.hi
        local lo = a.lo - b.lo
        if lo < 0 then
            -- Only called when a > b, so we must have something to borrow.
            hi = hi - 1
            lo = lo + int32_over
        end
        a.hi = hi
        a.lo = lo
    end
    -- Divide, finding quotient and remainder.
    local q = int64.new(0)
    local r = int64.new(0)
    for i = 63, 0, -1 do
        shl1(r)
        local bit
        -- TODO Just shift a copy of `a` right each time instead of `^`?
        if i >= 32 then
            bit = math.floor(a.hi / 2 ^ (i - 32)) % 2
        else
            bit = math.floor(a.lo / 2 ^ i) % 2
        end
        r.lo = r.lo + bit
        if r.lo >= int32_over then
            r.lo = r.lo - int32_over
            r.hi = r.hi + 1
        end
        -- TODO Just shift a copy of `q` left each time instead of `^`?
        if ge(r, b) then
            sub(r, b)
            if i >= 32 then
                q.hi = q.hi + 2 ^ (i - 32)
            else
                q.lo = q.lo + 2 ^ i
            end
        end
    end
    -- Apply sign.
    if neg then
        q = -q
    end
    return q, r
end

local function to_int32(a)
    a = a % int32_over
    if a >= uint32_halfmax then
        a = a - int32_over
    end
    return a
end

local function trunc(x)
    -- Avoid second result value from math.modf.
    local result = math.modf(x)
    return result
end

-- Int32

function temper.int32_add(a, b)
    return to_int32(a + b)
end

function temper.int32_div(a, b)
    return to_int32(trunc(a / b))
end

temper.int32_max = math.max
temper.int32_min = math.min

function temper.int32_mod(a, b)
    return trunc(math.fmod(a, b))
end

function temper.int32_unm(a)
    return to_int32(-a)
end

function temper.int32_mul(a, b)
    local _, mid, lo = int32_mul_parts(a, b)
    return to_int32(bitops.lshift(mid, 16) + lo)
end

function temper.int32_sub(a, b)
    return to_int32(a - b)
end

-- Int64

function format_hex32(a)
    local hi = bitops.band(bitops.rshift(a, 16), 0xffff)
    return string.format("%04x_%04x", hi, bitops.band(a, 0xffff))
end

function format_hex64(a)
    return string.format("%s_%s", format_hex32(a.hi), format_hex32(a.lo))
end

function temper.float64_toint64(x)
    if math.abs(x) <= mantissa_max then
        return temper.float64_toint64unsafe(x)
    end
    error("out of range")
end

function temper.float64_toint64unsafe(x)
    x = trunc(x)
    local hi = math.floor(x / int32_over)
    local lo = x - hi * int32_over
    return int64.new(hi, lo)
end

function int64.new(hi, lo)
    -- print(string.format("int64_constructor %s %s", hi, lo))
    if lo == nil then
        if type(hi) == string then
            -- Allow full convenient digits as a string.
            return temper.string_toint64(hi)
        elseif frac(hi) == 0 then
            -- Attempt at a larger int.
            return temper.float64_toint64(hi)
        else
            error("invalid int64")
        end
    end
    -- print(string.format("0x%s_%sL", format_hex32(hi), format_hex32(lo)))
    return setmetatable({
        hi = (hi % int32_over),
        lo = (lo % int32_over)
    }, int64)
end

temper.int64_constructor = int64.new

function temper.int64_max(a, b)
    return a > b and a or b
end

function temper.int64_min(a, b)
    return a < b and a or b
end

temper.int64_maxvalue = int64.new(int32_maxvalue, uint32_maxvalue)
temper.int64_minvalue = int64.new(uint32_halfmax, 0)

function int64:__eq(other)
    return int64_cmp(self, other) == 0
end

function int64:__le(other)
    return int64_cmp(self, other) <= 0
end

function int64:__lt(other)
    return int64_cmp(self, other) < 0
end

function temper.int64_tofloat64(x)
    if math.abs(x) <= mantissa_max then
        return temper.int64_tofloat64unsafe(x)
    end
    error("out of range")
end

function temper.int64_tofloat64unsafe(x)
    local sign = 1
    if x.hi >= uint32_halfmax then
        sign = -1
        x = -x
    end
    return sign * (x.hi * int32_over + x.lo)
end

function temper.int64_toint32(x)
    if math.abs(x) <= mantissa_max then
        return temper.int64_toint32unsafe(x)
    end
    error("out of range")
end

function temper.int64_toint32unsafe(x)
    return x.lo
end

local all_digits = "0123456789abcdefghijklmnopqrstuvwzyz"

function temper.int64_tostring(a, base)
    base = base or 10
    assert(base >= 2 and base <= 36, "base must be 2..36")
    local hi, lo = a.hi, a.lo
    -- two's-complement negative
    local negative = hi >= uint32_halfmax
    if negative then
        a = -a
        hi, lo = a.hi, a.lo
    end
    if hi == 0 and lo == 0 then
        return "0"
    end
    local digits = {}
    local function divmod64(h, l, b)
        -- perform (h<<32 | l) // b and (h<<32 | l) % b safely for small b
        -- process high word first
        local rem = 0
        local temp = h -- 0 * int32_over + h
        local qh = math.floor(temp / b)
        rem = temp - qh * b
        temp = rem * int32_over + l
        local ql = math.floor(temp / b)
        rem = temp - ql * b
        return qh, ql, rem
    end
    while hi ~= 0 or lo ~= 0 do
        hi, lo, rem = divmod64(hi, lo, base)
        -- rem is 0..base-1; lua string indices are 1-based
        table.insert(digits, 1, string.sub(all_digits, rem + 1, rem + 1))
    end
    local str = table.concat(digits)
    if negative then
        str = "-" .. str
    end
    return str
end

function int64:__add(other)
    local lo_sum = self.lo + other.lo
    local lo = bitops.band(lo_sum, uint32_maxvalue)
    local carry = math.floor(lo_sum / int32_over)
    local hi = self.hi + other.hi + carry
    return int64.new(to_int32(hi), lo)
end

function temper.int64_div(a, b)
    if a.hi == 0 and b.hi == 0 then
        -- Fast path for small ints. TODO Also for small negative or other?
        return int64.new(trunc(a.lo / b.lo))
    end
    local q, _ = int64_divmod(a, b)
    return q
end

function temper.int64_mod(a, b)
    if a.hi == 0 and b.hi == 0 then
        -- Fast path for small ints. TODO Also for small negative or other?
        return int64.new(trunc(math.fmod(a.lo, b.lo)))
    end
    local _, r = int64_divmod(a, b)
    if a.hi >= uint32_halfmax then
        -- Negative result for negative dividend.
        r = -r
    end
    return r
end

function int64:__mul(other)
    -- print(string.format("mul %s %s %s %s", self, other, format_hex64(self), format_hex64(other)))
    local lo_lo_hi, lo_lo_mid, lo_lo_lo = int32_mul_parts(self.lo, other.lo)
    -- print(string.format("%s %s %s", format_hex32(lo_lo_hi), format_hex32(lo_lo_mid), format_hex32(lo_lo_lo)))
    local _, hi_lo_mid, hi_lo_lo = int32_mul_parts(self.hi, other.lo)
    -- print(string.format("%s %s %s", format_hex32(_), format_hex32(hi_lo_mid), format_hex32(hi_lo_lo)))
    local _, lo_hi_mid, lo_hi_lo = int32_mul_parts(self.lo, other.hi)
    -- print(string.format("%s %s %s", format_hex32(_), format_hex32(lo_hi_mid), format_hex32(lo_hi_lo)))
    -- Label 16-bit groups by where they start, then combine lo 32.
    local bits00 = lo_lo_lo
    local bits16 = lo_lo_mid * 0x10000
    local lo = bits16 + bits00
    -- print(string.format("%s %s", format_hex32(bits16), format_hex32(bits00)))
    -- Now combine hi 32.
    local carry = temper.int32_div(lo, int32_over)
    -- print(string.format("%s %s %s", lo, int32_over, format_hex32(carry)))
    local bits32 = lo_lo_hi + hi_lo_lo + lo_hi_lo + carry
    local bits48 = (hi_lo_mid + lo_hi_mid) * 0x10000
    -- print(string.format("%s %s", format_hex32(bits48), format_hex32(bits32)))
    local hi = bits48 + bits32
    return int64.new(hi, lo)
end

function int64:__sub(other)
    return self + -other
end

function int64:__tostring()
    -- print(self.lo, self.lo % 10)
    return temper.int64_tostring(self)
end

function int64:__unm()
    local lo = bitops.bnot(self.lo)
    if lo < 0 then
        -- TODO Adjust bitops directly to avoid this fix here?
        lo = lo + int32_over
    end
    lo = lo + 1
    local hi = bitops.bnot(self.hi)
    if lo >= int32_over then
        lo = lo - int32_over
        hi = hi + 1
    end
    return int64.new(hi, lo)
end

function temper.int64_unm(a)
    return -a
end

return {
    bitops = bitops,
    temper = temper
}
