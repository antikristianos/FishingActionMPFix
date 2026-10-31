FishingActionMPFix = FishingActionMPFix or {}

local HAND_CAPTURE_MODULE = "FishingActionMPFix"
local HAND_CAPTURE_COMMAND = "rememberBrokenRodHands"
local VANILLA_MODULE = "fishing"
local VANILLA_COMMAND = "brokeRodLine"
local PENDING_TICKS = 120

local pendingHandStates = {}

local function captureHandState(player, args)
    if player == nil or args == nil or args.itemID == nil then
        return false
    end

    local inventory = player:getInventory()
    local rodItem = inventory and inventory:getItemWithID(args.itemID)
    if rodItem == nil then
        pendingHandStates[player] = nil
        return false
    end

    local primaryItem = player:getPrimaryHandItem()
    local secondaryItem = player:getSecondaryHandItem()
    if primaryItem ~= rodItem and secondaryItem ~= rodItem then
        pendingHandStates[player] = nil
        return false
    end

    pendingHandStates[player] = {
        itemID = args.itemID,
        rodItem = rodItem,
        primaryItem = primaryItem,
        secondaryItem = secondaryItem,
        breakObserved = false,
        remainingTicks = PENDING_TICKS,
    }
    return true
end

local function restoreHandsAfterBreak(player, state)
    if player == nil or state == nil then
        return false
    end

    local inventory = player:getInventory()
    if inventory == nil or inventory:getItemWithID(state.itemID) ~= nil then
        return false
    end

    local primaryItem = state.primaryItem
    local secondaryItem = state.secondaryItem
    if primaryItem == state.rodItem then
        primaryItem = nil
    end
    if secondaryItem == state.rodItem then
        secondaryItem = nil
    end
    player:setPrimaryHandItem(primaryItem)
    player:setSecondaryHandItem(secondaryItem)
    return true
end

local function observeVanillaBreak(player, args)
    local state = pendingHandStates[player]
    if state == nil or args == nil or state.itemID ~= args.itemID then
        return false
    end

    state.breakObserved = true
    if restoreHandsAfterBreak(player, state) then
        pendingHandStates[player] = nil
        return true
    end
    return false
end

local function onClientCommand(module, command, player, args)
    if module == HAND_CAPTURE_MODULE and command == HAND_CAPTURE_COMMAND then
        captureHandState(player, args)
        return
    end
    if module == VANILLA_MODULE and command == VANILLA_COMMAND then
        observeVanillaBreak(player, args)
    end
end

local function onTick()
    for player, state in pairs(pendingHandStates) do
        if state.breakObserved and restoreHandsAfterBreak(player, state) then
            pendingHandStates[player] = nil
        else
            state.remainingTicks = state.remainingTicks - 1
            if state.remainingTicks <= 0 then
                pendingHandStates[player] = nil
            end
        end
    end
end

FishingActionMPFix.captureBrokenRodHandState = captureHandState
FishingActionMPFix.restoreHandsAfterBrokenRodReplacement = restoreHandsAfterBreak

Events.OnClientCommand.Add(onClientCommand)
Events.OnTick.Add(onTick)
