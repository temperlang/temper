local _connected = {}

do
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
