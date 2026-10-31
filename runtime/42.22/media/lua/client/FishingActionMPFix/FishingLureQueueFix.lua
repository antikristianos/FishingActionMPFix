require "ISUI/ISInventoryPaneContextMenu"
require "TimedActions/Fishing/TimedActions/AIAttachLureAction"

FishingActionMPFix = FishingActionMPFix or {}

if FishingActionMPFix.inventoryAddLureOriginal == nil then
    FishingActionMPFix.inventoryAddLureOriginal = ISInventoryPaneContextMenu.addLure

    function ISInventoryPaneContextMenu.addLure(player, fishingRod, lure)
        local playerObj = getSpecificPlayer(player)

        ISInventoryPaneContextMenu.equipWeapon(lure, false, false, player)
        ISInventoryPaneContextMenu.equipWeapon(fishingRod, true, false, player)
        ISTimedActionQueue.add(AIAttachLureAction:new(playerObj, fishingRod, lure))
    end
end
