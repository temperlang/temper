local temper = require("temper-core")

---@class build.StringBuilderOwner
---@field builder temper.StringBuilder

---@class build.StringBuilderOwner.type: temper.Type
---@operator call: build.StringBuilderOwner
local StringBuilderOwner = temper.type("build.StringBuilderOwner")

---@param this build.StringBuilderOwner
---@param builder temper.StringBuilder
function StringBuilderOwner.constructor(this, builder)
  this.builder = builder
end

local builder = temper.stringbuilder_constructor()
temper.stringbuilder_append(builder, "Hi ")
temper.stringbuilder_appendcodepoint(builder, 127759)
local source = "Wow!! Neato!"
local bangIndex = temper.string_step(source, 1, 3)
local spaceIndex = temper.string_step(source, bangIndex, 2)
temper.stringbuilder_appendbetween(builder, source, bangIndex, spaceIndex)
local built = temper.stringbuilder_tostring(builder)
temper.stringbuilder_append(builder, "\n")
temper.stringbuilder_append(builder, built)
print(temper.stringbuilder_tostring(builder))
if not temper.catch(function()
  temper.stringbuilder_appendcodepoint(builder, 1114112)
end, temper.InvalidCodePoint) then
  print("High code blocked.")
end
local t, e = temper.catch(function()
  temper.stringbuilder_appendcodepoint(builder, 55296)
end, temper.InvalidCodePoint)
if not t then
  print("Surrogate 0x" .. temper.int32_tostring(e.codePoint, 16) .. " blocked.")
end
print(temper.stringbuilder_tostring(builder))
temper.stringbuilder_clear(builder)
temper.stringbuilder_append(builder, "Back to square zero.")
print(temper.stringbuilder_tostring(builder))
temper.stringbuilder_clear(builder)
temper.stringbuilder_append(builder, "a??=b??/c??>d???e")
local marks = temper.stringbuilder_tostring(builder)
print(marks)
print("Same: " .. tostring(marks == "a??=b??/c??>d???e"))
temper.stringbuilder_clear(builder)
temper.stringbuilder_append(builder, "??")
if not temper.catch(function()
  temper.stringbuilder_appendcodepoint(builder, 1)
end, temper.InvalidCodePoint) then
  print("No U+1.")
end
temper.stringbuilder_append(builder, "0")
if not temper.catch(function()
  temper.stringbuilder_appendcodepoint(builder, 8364)
end, temper.InvalidCodePoint) then
  print("No euro.")
end
temper.stringbuilder_append(builder, "1")
print("Digits after escapes: " .. tostring(temper.stringbuilder_tostring(builder) == "??\0010€1"))
local owner = StringBuilderOwner(temper.stringbuilder_constructor())
temper.stringbuilder_append(owner.builder, "Hi from inside.")
print(temper.stringbuilder_tostring(owner.builder))

return {}
