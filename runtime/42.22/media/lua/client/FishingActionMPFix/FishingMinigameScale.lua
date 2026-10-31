require "ISUI/ISUIElement"
require "Fishing/TensionUI"

FishingActionMPFix = FishingActionMPFix or {}
local tensionUI = Fishing.ISTensionUI

if FishingActionMPFix.tensionPrerenderOriginal == nil then
    FishingActionMPFix.tensionPrerenderOriginal = tensionUI.prerender
    FishingActionMPFix.tensionRenderOriginal = tensionUI.render
    local sessionDisplay

    local function prepareSessionDisplay()
        -- Resolution and all scaled geometry are fixed for this entire game session.
        local height = getCore():getScreenHeight()
        local scale = height >= 2160 and 2 or (height >= 1440 and 1.5 or 1)
        sessionDisplay = { scale = scale, size = 120 * scale, halfSize = 60 * scale }
        if scale == 1 then return end

        local texture = getTexture("media/ui/PointTensionN.png")
        sessionDisplay.texture = texture
        if not texture then return end
        local halfWidth = math.floor(texture:getWidth() / 2) * scale
        local halfHeight = math.floor(texture:getHeight() / 2) * scale
        local positions = {}
        sessionDisplay.positions = positions
        -- Vanilla tension maps to -70..70 degrees. One-degree steps bound visual error
        -- to half a degree, without changing the continuous gameplay tension value.
        for angle = -70, 70 do
            local radians = math.rad(180 + angle)
            local cos, sin = math.cos(radians), math.sin(radians)
            local a, b, c, d = cos * halfWidth, sin * halfWidth, cos * halfHeight, sin * halfHeight
            positions[angle + 71] = { a - d, c + b, -a - d, c - b,
                -a + d, -c - b, a + d, -c + b }
        end
    end
    Events.OnGameStart.Add(prepareSessionDisplay)

    local function screenCenter(self)
        local player = self.player
        local x, y, z = player:getX(), player:getY(), player:getZ() + 0.6
        -- The player-relative center still moves; the scaled shape never changes.
        return isoToScreenX(self.playerIndex, x, y, z),
            isoToScreenY(self.playerIndex, x, y, z) + 20
    end

    function tensionUI:prerender()
        local state = sessionDisplay
        if not state or state.scale == 1 then
            return FishingActionMPFix.tensionPrerenderOriginal(self)
        end
        local x, y = screenCenter(self)
        self:drawTextureScaled(self.bckgTex,
            x - self:getAbsoluteX() - state.halfSize,
            y - self:getAbsoluteY() - state.halfSize,
            state.size, state.size, 1, 1, 1, 1)
    end

    function tensionUI:render()
        local state = sessionDisplay
        if not state or state.scale == 1 then
            return FishingActionMPFix.tensionRenderOriginal(self)
        end
        if not state.positions or not self:isVisible() then return end
        local angle = math.max(-70, math.min(70, math.floor(self.val + 0.5)))
        local corners = state.positions[angle + 71]
        local x, y = screenCenter(self)
        -- drawTextureAllPoint takes absolute screen coordinates, with no UI offset.
        self:drawTextureAllPoint(state.texture,
            x + corners[1], y + corners[2], x + corners[3], y + corners[4],
            x + corners[5], y + corners[6], x + corners[7], y + corners[8],
            1, 1, 1, 1)
    end
end
