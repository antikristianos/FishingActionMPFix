require "Fishing/FishingRod"

FishingActionMPFix = FishingActionMPFix or {}

local HAND_CAPTURE_MODULE = "FishingActionMPFix"
local HAND_CAPTURE_COMMAND = "rememberBrokenRodHands"

local function clearBrokenRodHandReferences(player, rodItem)
    if player == nil or rodItem == nil then
        return false
    end

    local changed = false
    if player:getPrimaryHandItem() == rodItem then
        player:setPrimaryHandItem(nil)
        changed = true
    end
    if player:getSecondaryHandItem() == rodItem then
        player:setSecondaryHandItem(nil)
        changed = true
    end
    return changed
end

FishingActionMPFix.clearBrokenRodHandReferences = clearBrokenRodHandReferences

local function requestBrokenRodHandCapture(player, rodItem)
    if player == nil or rodItem == nil then
        return false
    end

    local sent, err = pcall(
        sendClientCommandV,
        player,
        HAND_CAPTURE_MODULE,
        HAND_CAPTURE_COMMAND,
        "itemID",
        rodItem:getID()
    )
    if not sent then
        print("[FishingActionMPFix] Failed to capture server hand state before fishing-line break: "
                .. tostring(err))
    end
    return sent
end

FishingActionMPFix.requestBrokenRodHandCapture = requestBrokenRodHandCapture

if FishingActionMPFix.fishingRodBrokeLineOriginal == nil then
    FishingActionMPFix.fishingRodBrokeLineOriginal = Fishing.FishingRod.brokeLine

    function Fishing.FishingRod:brokeLine()
        local rodItem = self.rodItem
        local clientRuntime = isClient()

        if clientRuntime then
            requestBrokenRodHandCapture(self.player, rodItem)
        end

        local result = FishingActionMPFix.fishingRodBrokeLineOriginal(self)

        if clientRuntime then
            clearBrokenRodHandReferences(self.player, rodItem)
            self.rodItem = nil
        end

        return result
    end
end
