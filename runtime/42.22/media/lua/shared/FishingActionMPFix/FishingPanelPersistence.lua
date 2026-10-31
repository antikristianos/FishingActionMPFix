require "TimedActions/Fishing/TimedActions/ISPickupFishAction"

FishingActionMPFix = FishingActionMPFix or {}

local CATCH_MARKER_PREFIX = "fishing_CatchDone_"

local function synchronizeCaughtFish(player)
    if player == nil then
        return nil
    end

    local modData = player:getModData()
    if modData == nil then
        return nil
    end

    if modData.fishing_catchedFish == nil then
        modData.fishing_catchedFish = {}
    end

    local caughtFish = modData.fishing_catchedFish
    if type(caughtFish) ~= "table" then
        return nil
    end

    for key, value in pairs(modData) do
        if value == true and type(key) == "string" and string.sub(key, 1, #CATCH_MARKER_PREFIX) == CATCH_MARKER_PREFIX then
            local fullType = string.sub(key, #CATCH_MARKER_PREFIX + 1)
            if fullType ~= "" then
                caughtFish[fullType] = true
            end
        end
    end

    for fullType, value in pairs(caughtFish) do
        if value == true and type(fullType) == "string" and fullType ~= "" then
            modData[CATCH_MARKER_PREFIX .. fullType] = true
        end
    end

    return caughtFish
end

local function markCaughtFish(player, fullType)
    if player == nil or type(fullType) ~= "string" or fullType == "" then
        return false
    end

    local caughtFish = synchronizeCaughtFish(player)
    if caughtFish == nil then
        return false
    end

    local modData = player:getModData()
    caughtFish[fullType] = true
    modData[CATCH_MARKER_PREFIX .. fullType] = true
    return true
end

FishingActionMPFix.synchronizeCaughtFish = synchronizeCaughtFish
FishingActionMPFix.markCaughtFish = markCaughtFish

if not FishingActionMPFix.fishingPickupPersistencePatched then
    FishingActionMPFix.fishingPickupPersistencePatched = true

    local originalStart = ISPickupFishAction.start
    ISPickupFishAction.start = function(self, ...)
        local result = originalStart(self, ...)
        if not isServer() and self.item ~= nil then
            local fullType = self.item:getFullType()
            if markCaughtFish(self.character, fullType) then
                self.fishingActionMPFixCatchMirrored = true
            end
        end
        return result
    end

    local originalPickupFishUpdate = ISPickupFishAction.PickupFishUpdate
    ISPickupFishAction.PickupFishUpdate = function(self, ...)
        local result = originalPickupFishUpdate(self, ...)
        if self.fishInInv and not self.fishingActionMPFixCatchMirrored and self.item ~= nil then
            local fullType = self.item:getFullType()
            if markCaughtFish(self.character, fullType) then
                self.fishingActionMPFixCatchMirrored = true
            end
        end
        return result
    end
end
