local _connected = {}

do
  ---@param n integer
  ---@return integer
  function _connected.hostFails(n)
    error("host bug " .. n)
  end
end
