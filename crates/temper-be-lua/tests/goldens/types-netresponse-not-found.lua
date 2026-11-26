local temper = require("temper-core")
local NetResponse = require("std/net").NetResponse
local NetRequest = require("std/net").NetRequest

---@type string
local url

url = "http://127.0.0.1:" .. 28461 .. "/missing"

local function fn()
  if not temper.catch(function()
    local resp = NetRequest(url):send():await()
    print("status " .. temper.netresponse_status(resp))
    local subject = temper.netresponse_bodycontent(resp):await()
    local body = temper.is_null(subject) and "missing" or (subject --[[@as string]])
    print("body " .. body)
  end, temper.BrokenPromise) then
    print("broken promise")
  end
  local t, e = temper.catch(function()
    NetRequest("http://127.0.0.1:1/"):send():await()
    print("port 1 answered")
  end, temper.BrokenPromise)
  if not t then
    print("no response from port 1: " .. e.message)
  end
end

temper.async(temper.adapt_generator_fn(fn))

return {}
