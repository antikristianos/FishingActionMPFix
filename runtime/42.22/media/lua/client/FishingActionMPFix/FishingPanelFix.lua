require "PZAPI/ui/organisms/FishWindow"
require "ISUI/ISWorldObjectContextMenu"
require "FishingActionMPFix/FishingPanelPersistence"

FishingActionMPFix = FishingActionMPFix or {}

local PLAYER_UPDATE_INTERVAL_MS = 1000
local lastPlayerUpdateByPlayer = {}

local function synchronizeCaughtFish(player)
    FishingActionMPFix.synchronizeCaughtFish(player)
    if player ~= nil then
        lastPlayerUpdateByPlayer[player] = getTimestampMs()
    end
end

local function synchronizeAllLocalPlayers()
    for playerIndex = 0, getNumActivePlayers() - 1 do
        synchronizeCaughtFish(getSpecificPlayer(playerIndex))
    end
end

local function onPlayerUpdate(player)
    if player == nil then
        return
    end

    local now = getTimestampMs()
    local lastUpdate = lastPlayerUpdateByPlayer[player]
    if lastUpdate ~= nil and now >= lastUpdate and now - lastUpdate < PLAYER_UPDATE_INTERVAL_MS then
        return
    end

    FishingActionMPFix.synchronizeCaughtFish(player)
    lastPlayerUpdateByPlayer[player] = now
end

local function closeFishWindow(button)
    if button == nil then
        return
    end

    if button.sounds ~= nil and button.sounds.activate ~= nil then
        getSoundManager():playUISound(button.sounds.activate)
    end

    local bar = button.parent
    local window = bar and bar.parent or nil
    if window == nil then
        return
    end

    if PZAPI.UI.instances ~= nil and PZAPI.UI.instances.fishWindow == window then
        PZAPI.UI.instances.fishWindow = nil
    end
    if window.javaObj ~= nil then
        UIManager.RemoveElement(window.javaObj)
    end
end

local function installCloseHandler()
    local fishWindow = PZAPI.UI.FishWindow
    local closeButton = fishWindow
        and fishWindow.children
        and fishWindow.children.bar
        and fishWindow.children.bar.children
        and fishWindow.children.bar.children.closeButton
        or nil
    if closeButton ~= nil then
        closeButton.onLeftClick = closeFishWindow
    end

    local instance = PZAPI.UI.instances and PZAPI.UI.instances.fishWindow or nil
    local instanceButton = instance
        and instance.children
        and instance.children.bar
        and instance.children.bar.children
        and instance.children.bar.children.closeButton
        or nil
    if instanceButton ~= nil then
        instanceButton.onLeftClick = closeFishWindow
    end
end

local function onCreatePlayer(playerIndex, player)
    synchronizeCaughtFish(player or getSpecificPlayer(playerIndex))
end

if not FishingActionMPFix.fishingPanelPatched then
    FishingActionMPFix.fishingPanelPatched = true

    installCloseHandler()

    local originalOpenFishWindow = ISWorldObjectContextMenu.openFishWindow
    ISWorldObjectContextMenu.openFishWindow = function(...)
        synchronizeAllLocalPlayers()
        local result = originalOpenFishWindow(...)
        installCloseHandler()
        return result
    end

    Events.OnCreatePlayer.Add(onCreatePlayer)
    Events.OnGameStart.Add(synchronizeAllLocalPlayers)
    Events.OnPlayerUpdate.Add(onPlayerUpdate)
end
