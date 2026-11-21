local temper = require("temper-core")

---@type temper.Deque
local deque

local function checkEmpty()
  temper.log(temper.deque_isempty(deque) and "Empty" or "Not empty")
end

deque = temper.deque_constructor()
checkEmpty()
if not temper.deque_isempty(deque) then
  temper.log(temper.deque_removefirst(deque))
end
temper.deque_add(deque, "foo")
temper.deque_add(deque, "bar")
temper.log(temper.deque_removefirst(deque))
temper.deque_add(deque, "baz")
temper.log(temper.deque_removefirst(deque))
temper.deque_add(deque, "boo")
checkEmpty()
while not temper.deque_isempty(deque) do
  temper.log(temper.deque_removefirst(deque))
end
checkEmpty()

return {}
