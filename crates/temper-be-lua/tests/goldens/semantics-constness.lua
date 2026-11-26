local temper = require("temper-core")

do
  local nci = 1
  temper.log("nci = " .. nci)
  nci = 2
  local _ = nil
  temper.log("nci = " .. nci)
  ---@type integer
  local nc
  nc = 1
  temper.log("nc = " .. nc)
  nc = 2
  local _ = nil
  temper.log("nc = " .. nc)
  do
    local i = 0
    while i < 3 do
      ---@type integer
      local ncl
      ncl = i
      local _ = nil
      temper.log("ncl = " .. ncl)
      i = temper.int32_add(i, 1)
    end
  end
  local ci = 1
  temper.log("ci = " .. ci)
  temper.log("failed to set ci")
  temper.log("ci = " .. ci)
  ---@type integer
  local c
  c = 1
  temper.log("c = " .. c)
  temper.log("failed to set c")
  temper.log("c = " .. c)
  do
    local i2 = 0
    while i2 < 3 do
      ---@type integer
      local cl
      cl = i2
      local _ = nil
      temper.log("cl = " .. cl)
      i2 = temper.int32_add(i2, 1)
    end
  end
end

return {}
