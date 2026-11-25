-- `NotFound` is the library's own failure type, a local of its module,
-- which this is inlined in; calling it makes one, and raising that is a
-- failure, and a string a host error.
local _connected = {}

do
  ---@param key string
  ---@return integer
  function _connected.hostLookup(key)
    if key == "bug" then
      error("host bug")
    end
    if key == "a" then
      return 1
    end
    error(NotFound(key))
  end
end
