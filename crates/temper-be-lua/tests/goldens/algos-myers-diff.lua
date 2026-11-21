local temper = require("temper-core")

---@type string
local ChangeTypeAddition
---@type string
local ChangeTypeDeletion
---@type string
local ChangeTypeUnchanged

---@class myers-diff.Change
---@field type string
---@field leftIndex integer
---@field rightIndex integer
---@field items any[]

---@class myers-diff.Change.type: temper.Type
---@operator call: myers-diff.Change
local Change = temper.type("myers-diff.Change")

---@param this myers-diff.Change
---@param type_ string
---@param leftIndex integer
---@param rightIndex integer
---@param items any[]
function Change.constructor(this, type_, leftIndex, rightIndex, items)
  this.type = type_
  this.leftIndex = leftIndex
  this.rightIndex = rightIndex
  this.items = items
end

---@class myers-diff.Patch
---@field changes myers-diff.Change[]

---@class myers-diff.Patch.type: temper.Type
---@operator call: myers-diff.Patch
local Patch = temper.type("myers-diff.Patch")

---@param this myers-diff.Patch
---@param changes myers-diff.Change[]
function Patch.constructor(this, changes)
  this.changes = changes
end

---@param patch myers-diff.Patch
---@return string
local function formatPatch(patch)
  local formatted = ""
  local changes = patch.changes
  local n = temper.list_length(changes)
  do
    local i = 0
    while i < n do
      local change = temper.list_get(changes, i)
      local changeType = change.type
      local items = change.items
      local prefix = changeType == ChangeTypeAddition and "+" or (changeType == ChangeTypeDeletion and "-" or " ")
      local nItems = temper.list_length(items)
      do
        local j = 0
        while j < nItems do
          local item = temper.list_get(items, j)
          formatted = formatted .. prefix .. item .. "\n"
          j = temper.int32_add(j, 1)
        end
      end
      i = temper.int32_add(i, 1)
    end
  end
  return formatted
end

---@class myers-diff.DiffPath
---@field leftIndex integer
---@field rightIndex integer
---@field changeType string
---@field previous myers-diff.DiffPath|temper.Null

---@class myers-diff.DiffPath.type: temper.Type
---@operator call: myers-diff.DiffPath
local DiffPath = temper.type("myers-diff.DiffPath")

---@param this myers-diff.DiffPath
---@param leftIndex integer
---@param rightIndex integer
---@param changeType string
---@param previous myers-diff.DiffPath|temper.Null
function DiffPath.constructor(this, leftIndex, rightIndex, changeType, previous)
  this.leftIndex = leftIndex
  this.rightIndex = rightIndex
  this.changeType = changeType
  this.previous = previous
end

---@generic T
---@param left T[]
---@param right T[]
---@param eq fun(arg0: T, arg1: T): boolean
---@return myers-diff.Patch
local function diff(left, right, eq)
  local leftLength = temper.list_length(left)
  local rightLength = temper.list_length(right)
  local reachedRowLength = temper.int32_add(leftLength, 1)
  local reachedColLength = temper.int32_add(rightLength, 1)
  local reached = temper.densebitvector_constructor(temper.int32_mul(reachedRowLength, reachedColLength))
  local costZeroEdges = temper.deque_constructor()
  temper.deque_add(costZeroEdges, temper.null)
  local costOneEdges = temper.deque_constructor()
  while true do
    local diffPath = not temper.deque_isempty(costZeroEdges) and temper.deque_removefirst(costZeroEdges) or (not temper.deque_isempty(costOneEdges) and temper.deque_removefirst(costOneEdges) or temper.null)
    local subject = temper.is_null(diffPath) and temper.null or (diffPath --[[@as myers-diff.DiffPath]]).leftIndex
    local leftIndex = temper.is_null(subject) and 0 or (subject --[[@as integer]])
    local subject2 = temper.is_null(diffPath) and temper.null or (diffPath --[[@as myers-diff.DiffPath]]).rightIndex
    local rightIndex = temper.is_null(subject2) and 0 or (subject2 --[[@as integer]])
    if leftIndex == leftLength and rightIndex == rightLength then
      local pathElements = temper.listbuilder_constructor()
      do
        ---@type myers-diff.DiffPath|temper.Null
        local pathElement = diffPath
        while not temper.is_null(pathElement) do
          local path = temper.cast_to(pathElement, DiffPath)
          temper.listbuilder_add(pathElements, path)
          pathElement = path.previous
        end
      end
      temper.listbuilder_reverse(pathElements)
      local leftPatchIndex = 0
      local rightPatchIndex = 0
      local changes = temper.listbuilder_constructor()
      local n = temper.listbuilder_length(pathElements)
      do
        local i = 0
        ---@type integer
        local end_
        while i < n do
          local changeType = temper.listed_get(pathElements, i).changeType
          end_ = temper.int32_add(i, 1)
          while end_ < n and changeType == temper.listed_get(pathElements, end_).changeType do
            end_ = temper.int32_add(end_, 1)
          end
          local nItems = temper.int32_sub(end_, i)
          ---@type any[]
          local items
          local nLeftItems = 0
          local nRightItems = 0
          if changeType == ChangeTypeAddition then
            items = temper.listed_slice(right, rightPatchIndex, temper.int32_add(rightPatchIndex, nItems))
            nRightItems = nItems
          else
            items = temper.listed_slice(left, leftPatchIndex, temper.int32_add(leftPatchIndex, nItems))
            if changeType == ChangeTypeUnchanged then
              nRightItems = nItems
            end
            nLeftItems = nItems
          end
          temper.listbuilder_add(changes, Change(changeType, leftPatchIndex, rightPatchIndex, items))
          leftPatchIndex = temper.int32_add(leftPatchIndex, nLeftItems)
          rightPatchIndex = temper.int32_add(rightPatchIndex, nRightItems)
          i = end_
        end
      end
      return Patch(temper.listbuilder_tolist(changes))
    end
    if leftIndex < leftLength then
      if rightIndex < rightLength then
        local sameReachedIndex = temper.int32_add(temper.int32_add(leftIndex, 1), temper.int32_mul(temper.int32_add(rightIndex, 1), reachedRowLength))
        if not temper.densebitvector_get(reached, sameReachedIndex) and eq(temper.list_get(left, leftIndex), temper.list_get(right, rightIndex)) then
          temper.densebitvector_set(reached, sameReachedIndex, true)
          temper.deque_add(costZeroEdges, DiffPath(temper.int32_add(leftIndex, 1), temper.int32_add(rightIndex, 1), ChangeTypeUnchanged, diffPath))
        end
      end
      local delReachedIndex = temper.int32_add(temper.int32_add(leftIndex, 1), temper.int32_mul(rightIndex, reachedRowLength))
      if not temper.densebitvector_get(reached, delReachedIndex) then
        temper.densebitvector_set(reached, delReachedIndex, true)
        temper.deque_add(costOneEdges, DiffPath(temper.int32_add(leftIndex, 1), rightIndex, ChangeTypeDeletion, diffPath))
      end
    end
    if rightIndex < rightLength then
      local addReachedIndex = temper.int32_add(leftIndex, temper.int32_mul(temper.int32_add(rightIndex, 1), reachedRowLength))
      if not temper.densebitvector_get(reached, addReachedIndex) then
        temper.densebitvector_set(reached, addReachedIndex, true)
        temper.deque_add(costOneEdges, DiffPath(leftIndex, temper.int32_add(rightIndex, 1), ChangeTypeAddition, diffPath))
      end
    end
  end
end

ChangeTypeAddition = "Addition"
ChangeTypeDeletion = "Deletion"
ChangeTypeUnchanged = "Unchanged"
temper.log(formatPatch(diff(temper.string_split("Hello,\nWorld!", "\n"), temper.string_split("Hello,\nCosmos!", "\n"), function(a, b) return a == b end)))

return {
  Change = Change,
  Patch = Patch,
  ChangeTypeAddition = ChangeTypeAddition,
  ChangeTypeDeletion = ChangeTypeDeletion,
  formatPatch = formatPatch,
  ChangeTypeUnchanged = ChangeTypeUnchanged,
  diff = diff,
}
