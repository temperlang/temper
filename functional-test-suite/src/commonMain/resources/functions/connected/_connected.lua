local _connected = {}

do
  local s = require("work._support")

  ---@param i integer
  ---@param j integer
  ---@param bonus integer
  ---@return integer
  function _connected.sum(i, j, bonus)
    return sumOf3(i, j, bonus)
  end

  ---@param hidden work.Hidden TODO Actually define types in our Lua.
  ---@param j integer
  ---@return integer
  function _connected.prod(hidden, j)
    local support = s.Support.new()
    return support:prod(hidden.i, j)
  end

  ---@param s string|nil Or actually temper.null instead of nil ...
  ---@return integer
  function _connected.length(s)
    if s == temper.null then
      return -1
    else
      return #s
    end
  end

  -- Temper's connected Counter class, with the members Temper would generate:
  -- call the class to construct, and `:` for methods.
  local Counter = {}
  Counter.__index = Counter
  setmetatable(Counter, {
    __call = function(cls)
      return setmetatable({ _total = 0 }, cls)
    end,
  })
  _connected.Counter = Counter

  ---@param i integer
  function Counter:add(i)
    self._total = self._total + i
  end

  ---@return integer
  function Counter:total()
    return self._total
  end
end
