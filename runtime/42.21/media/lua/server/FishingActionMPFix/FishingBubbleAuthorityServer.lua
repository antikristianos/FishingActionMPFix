local MODULE = "FishingActionMPFix"
local RESET_REQUEST_COMMAND = "requestFishingBubbleReset"

local FishingBubbleAuthority = FishingActionMPFix and FishingActionMPFix.FishingBubbleAuthority

if not FishingBubbleAuthority then
    print("[FishingActionMPFix] Server fishing-bubble reset bridge is unavailable.")
    return
end

local function onClientCommand(module, command, player, args)
    if module ~= MODULE or command ~= RESET_REQUEST_COMMAND then
        return
    end
    local called, err = pcall(FishingBubbleAuthority.requestServerReset, player)
    if not called then
        print("[FishingActionMPFix] Failed to request a server fishing-bubble reset: " .. tostring(err))
    end
end

Events.OnClientCommand.Add(onClientCommand)
