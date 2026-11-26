local temper = require("temper-core")
local Queue = require("std/collections").Queue

---@type temper.Queue
local queue

local function checkEmpty()
  print(temper.queue_isempty(queue) and "Empty" or "Not empty")
end

queue = temper.queue_constructor()
checkEmpty()
if not temper.queue_isempty(queue) then
  print(temper.queue_removefirst(queue))
end
temper.queue_add(queue, "foo")
temper.queue_add(queue, "bar")
print(temper.queue_removefirst(queue))
temper.queue_add(queue, "baz")
print(temper.queue_removefirst(queue))
temper.queue_add(queue, "boo")
checkEmpty()
while not temper.queue_isempty(queue) do
  print(temper.queue_removefirst(queue))
end
checkEmpty()
local numbers = temper.queue_constructor()
local sum = 0
do
  local i = 0
  while i < 100 do
    temper.queue_add(numbers, i)
    temper.queue_add(numbers, temper.null)
    if temper.int32_mod(i, 3) == 0 then
      local int32 = sum
      local subject = temper.queue_removefirst(numbers)
      sum = temper.int32_add(int32, temper.is_null(subject) and -1000 or (subject --[[@as integer]]))
      local value = sum
      local subject2 = temper.queue_removefirst(numbers)
      sum = temper.int32_add(value, temper.is_null(subject2) and 1 or (subject2 --[[@as integer]]))
    end
    i = temper.int32_add(i, 1)
  end
end
local order = true
local expected = 34
while not temper.queue_isempty(numbers) do
  local n = temper.queue_removefirst(numbers)
  if not temper.is_null(n) then
    if (n --[[@as integer]]) ~= expected then
      order = false
    end
    expected = temper.int32_add(expected, 1)
  end
end
print("sum " .. sum .. ", in order " .. tostring(order) .. ", through " .. expected)

return {}
