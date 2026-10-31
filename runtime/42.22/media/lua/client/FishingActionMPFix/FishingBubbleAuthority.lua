local MODULE = "FishingActionMPFix"
local SNAPSHOT_COMMAND = "fishingBubbleDelta"
local RESET_REQUEST_COMMAND = "requestFishingBubbleReset"
local RESET_RETRY_MS = 1000

local FishingBubbleAuthority = FishingActionMPFix and FishingActionMPFix.FishingBubbleAuthority

if not FishingBubbleAuthority then
    print("[FishingActionMPFix] Server-authoritative fishing-bubble bridge is unavailable.")
    return
end

local awaitingReset = true
local nextResetRequestMs = 0
local requestLogged = false

local function clearAnchors()
    local cleared, err = pcall(FishingBubbleAuthority.clearClientAnchors)
    if not cleared then
        print("[FishingActionMPFix] Failed to clear fishing-bubble anchors: " .. tostring(err))
    end
end

local function requestReset(player)
    if not awaitingReset or not isClient() or player == nil or not player:isLocalPlayer() then
        return false
    end
    local now = getTimestampMs()
    if now < nextResetRequestMs then
        return false
    end
    sendClientCommand(player, MODULE, RESET_REQUEST_COMMAND, {})
    nextResetRequestMs = now + RESET_RETRY_MS
    if not requestLogged then
        requestLogged = true
        print("[FishingActionMPFix] Requested a full server-authoritative fishing-bubble reset.")
    end
    return true
end

local function beginResetHandshake()
    clearAnchors()
    awaitingReset = true
    nextResetRequestMs = 0
    requestReset(getPlayer())
end

local function onDisconnect()
    clearAnchors()
    awaitingReset = true
    nextResetRequestMs = 0
end

local function onPlayerUpdate(player)
    requestReset(player)
end

local function onServerCommand(module, command, args)
    if module ~= MODULE or command ~= SNAPSHOT_COMMAND then
        return
    end

    local payload = tostring(args and args.payload or "")
    if awaitingReset and payload:sub(1, 2) ~= "R|" then
        requestReset(getPlayer())
        return
    end
    local applied, err = pcall(FishingBubbleAuthority.applyClientDelta, payload)
    if not applied then
        clearAnchors()
        awaitingReset = true
        nextResetRequestMs = 0
        requestReset(getPlayer())
        print("[FishingActionMPFix] Rejected fishing-bubble delta: " .. tostring(err))
        return
    end
    if payload:sub(1, 2) == "R|" then
        awaitingReset = false
    end
end

Events.OnConnected.Add(beginResetHandshake)
Events.OnDisconnect.Add(onDisconnect)
Events.OnPlayerUpdate.Add(onPlayerUpdate)
Events.OnServerCommand.Add(onServerCommand)
