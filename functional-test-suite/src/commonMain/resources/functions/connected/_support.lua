local exports = {}

---@class Support
local Support = {}
Support.__index = Support
exports.Support = Support

function Support.new()
    return setmetatable({}, Support)
end

---@param i integer
---@param j integer
---@return integer
function Support:prod(i, j)
    return i * j
end

return exports
