-- A specification: `NotFound` is the library's own failure type, a
-- metatable on its module's exports called to make one, as the Lua
-- backend will name it; raising it is a failure, and a string a host
-- error.
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
    error(require("host-failures").NotFound(key))
  end
end
