local temper = require("temper-core")
local NetResponse = require("std/net").NetResponse
local NetRequest = require("std/net").NetRequest

---@type string
local url

url = "http://127.0.0.1:" .. 28461 .. "/missing"

local function fn()
  if not temper.pcall(function()
    local resp = NetRequest(url):send():await()
    temper.log("status " .. temper.netresponse_status(resp))
    local subject = temper.netresponse_bodycontent(resp):await()
    local body = temper.is_null(subject) and "missing" or (subject --[[@as string]])
    temper.log("body " .. body)
  end) then
    temper.log("broken promise")
  end
  if not temper.pcall(function()
    NetRequest("http://127.0.0.1:1/"):send():await()
    temper.log("port 1 answered")
  end) then
    temper.log("no response from port 1")
  end
end

temper.async(temper.adapt_generator_fn(fn))

return {}
