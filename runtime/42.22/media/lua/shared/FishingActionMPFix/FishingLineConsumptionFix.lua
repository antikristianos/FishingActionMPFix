require "TimedActions/Fishing/TimedActions/ISChangeFishingRodEquip"

FishingActionMPFix = FishingActionMPFix or {}

local function repairFishingLineConsumption(character, item, usesBefore)
    if character == nil or item == nil or usesBefore == nil or usesBefore <= 0 then
        return false
    end

    local expectedUses = math.max(usesBefore - 1, 0)
    local usesAfter = item:getCurrentUses()
    if usesAfter <= expectedUses then
        return false
    end

    item:setCurrentUses(expectedUses)
    syncItemFields(character, item)

    if expectedUses <= 0 then
        local inventory = character:getInventory()
        inventory:Remove(item)
        sendRemoveItemFromContainer(inventory, item)
    end

    return true
end

FishingActionMPFix.repairFishingLineConsumption = repairFishingLineConsumption

if FishingActionMPFix.changeFishingRodEquipCompleteOriginal == nil then
    FishingActionMPFix.changeFishingRodEquipCompleteOriginal = ISChangeFishingRodEquip.complete

    function ISChangeFishingRodEquip:complete()
        local lineItem = self.item
        local usesBefore = nil
        if lineItem ~= nil and lineItem:hasTag(ItemTag.FISHING_LINE) then
            usesBefore = lineItem:getCurrentUses()
        end

        local result = FishingActionMPFix.changeFishingRodEquipCompleteOriginal(self)
        repairFishingLineConsumption(self.character, lineItem, usesBefore)
        return result
    end
end
