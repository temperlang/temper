local temper = require("temper-core")
local NetRequest = require("std/net").NetRequest
local NetResponse = require("std/net").NetResponse

---@type string
local url

url = "http://127.0.0.1:" .. 28461

local function fn()
  if not temper.catch(function()
    local req = NetRequest(url)
    req:post("[]", "application/json")
    local resp = req:send():await()
    if temper.netresponse_status(resp) == 200 then
      local subject = temper.netresponse_bodycontent(resp):await()
      local body = temper.is_null(subject) and "missing" or (subject --[[@as string]])
      print("Thanks, server.  I got " .. body .. ".")
    else
      print("HTTP status was not 200: " .. temper.netresponse_status(resp) .. "!")
    end
  end, temper.BrokenPromise) then
    print("failed")
  end
end

temper.async(temper.adapt_generator_fn(fn))

return {}
