-- The runtime's `listbuilder_sort` and `listed_sorted`, for
-- `functional.rs`'s `runtime_sorts`: against a reference stable sort on
-- fixed and fuzzed inputs, and with a comparison that fails. Prints
-- `ok` at the end; any failure raises, naming the input.

local temper = require("temper-core")

-- Park-Miller, exact on every Lua, so each run sees the same inputs.
local seed = 1172
local function rand(m)
    seed = (seed * 16807) % 2147483647
    return seed % m
end

local compares = 0
local function compare(a, b)
    compares = compares + 1
    if a.k < b.k then return -1 elseif a.k > b.k then return 1 else return 0 end
end

-- Items keyed by `keys`, each knowing its first place.
local function items(keys)
    local list = {}
    for i = 1, #keys do list[i] = {k = keys[i], i = i} end
    return list
end

-- Stable by construction: ties fall back on first places.
local function reference(list)
    local out = {}
    for i = 1, #list do out[i] = list[i] end
    table.sort(out, function(a, b)
        if a.k ~= b.k then return a.k < b.k end
        return a.i < b.i
    end)
    return out
end

local function same(a, b, what)
    assert(#a == #b, what .. ": length " .. #a .. " ~= " .. #b)
    for i = 1, #a do
        assert(a[i] == b[i], what .. ": differs at " .. i)
    end
end

local shapes = {
    sorted = function(n) local k = {}; for i = 1, n do k[i] = i end; return k end,
    reversed = function(n) local k = {}; for i = 1, n do k[i] = n - i end; return k end,
    -- Descending, but not strictly: equal neighbors keep their order.
    reversed_dups = function(n) local k = {}; for i = 1, n do k[i] = math.floor((n - i) / 3) end; return k end,
    equal = function(n) local k = {}; for i = 1, n do k[i] = 7 end; return k end,
    dups = function(n) local k = {}; for i = 1, n do k[i] = rand(4) end; return k end,
    sawtooth = function(n) local k = {}; for i = 1, n do k[i] = i % 5 end; return k end,
    random = function(n) local k = {}; for i = 1, n do k[i] = rand(n + 1) end; return k end,
    -- Sorted, then one out of place at the end.
    nearly = function(n) local k = {}; for i = 1, n do k[i] = i end; if n > 1 then k[n] = 0 end; return k end,
    -- Sorted, then two swapped in the middle.
    swapped = function(n)
        local k = {}; for i = 1, n do k[i] = i end
        local m = math.floor(n / 2)
        if m > 0 then k[m], k[m + 1] = k[m + 1], k[m] end
        return k
    end,
    -- Sorted, then a few in any order.
    sorted_tail = function(n)
        local k = {}; for i = 1, n do k[i] = i end
        for i = math.max(1, n - 2), n do k[i] = rand(n + 1) end
        return k
    end,
    -- Strictly descending, then one above them all.
    descending_big = function(n) local k = {}; for i = 1, n do k[i] = n - i end; if n > 0 then k[n] = n end; return k end,
    -- Strictly descending for two thirds, then any order.
    descending_rest = function(n)
        local k = {}; local m = math.floor(2 * n / 3)
        for i = 1, n do k[i] = i <= m and n - i or rand(n + 1) end
        return k
    end,
}
local names = {}
for name in pairs(shapes) do names[#names + 1] = name end
table.sort(names)

local sizes = {}
for n = 0, 40 do sizes[#sizes + 1] = n end
for _, n in ipairs({63, 64, 65, 100, 257, 1000, 3001}) do sizes[#sizes + 1] = n end

for _, name in ipairs(names) do
    for _, n in ipairs(sizes) do
        for _ = 1, 3 do
            local list = items(shapes[name](n))
            local want = reference(list)
            local before = {}
            for i = 1, #list do before[i] = list[i] end
            local what = name .. " " .. n
            -- `sorted` gives a new list, leaving its input alone.
            same(temper.listed_sorted(list, compare), want, what .. " sorted")
            same(list, before, what .. " sorted's input")
            temper.listbuilder_sort(list, compare)
            same(list, want, what)
        end
    end
end

-- Sorted and strictly reversed input take one pass.
for _, name in ipairs({"sorted", "reversed"}) do
    compares = 0
    temper.listbuilder_sort(items(shapes[name](1000)), compare)
    assert(compares == 999, name .. " input: " .. compares .. " compares")
end

-- Comparisons stay near n log2 n, and nearly sorted input, including a
-- long strictly descending run, takes a few passes.
local linear = {nearly = true, swapped = true, sorted_tail = true, descending_big = true}
for _, name in ipairs(names) do
    for _, n in ipairs({1000, 3001}) do
        compares = 0
        temper.listbuilder_sort(items(shapes[name](n)), compare)
        local most = n * math.ceil(math.log(n) / math.log(2)) + n
        if linear[name] then most = 3 * n end
        assert(compares <= most, name .. " " .. n .. ": " .. compares .. " compares, over " .. most)
    end
end

-- Only a comparison's sign matters, not its size.
for _, scale in ipairs({1, 0.25, 1e6}) do
    local function difference(a, b) return (a.k - b.k) * scale end
    for _, name in ipairs(names) do
        for _, n in ipairs({2, 13, 100, 1000}) do
            local list = items(shapes[name](n))
            local want = reference(list)
            local what = name .. " " .. n .. " by difference * " .. scale
            same(temper.listed_sorted(list, difference), want, what .. " sorted")
            temper.listbuilder_sort(list, difference)
            same(list, want, what)
        end
    end
end

-- A comparison that fails, at each call in turn: the error comes
-- through as it was raised, and the list is as it was.
local failure = {}
for _, name in ipairs(names) do
    for _, n in ipairs({2, 3, 13, 40, 100}) do
        local calls = 0
        repeat
            local list = items(shapes[name](n))
            local before = {}
            for i = 1, #list do before[i] = list[i] end
            local limit = calls
            local count = 0
            local ok, err = pcall(temper.listbuilder_sort, list, function(a, b)
                count = count + 1
                if count > limit then error(failure) end
                return compare(a, b)
            end)
            local what = name .. " " .. n .. " failing at " .. (limit + 1)
            if ok then
                break -- The sort needed no more calls than that.
            end
            assert(err == failure, what .. ": " .. tostring(err))
            same(list, before, what)
            calls = calls + 1
        until false
    end
end

print("ok")
