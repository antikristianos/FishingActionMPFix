package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.network.GameServer;
import zombie.network.fields.character.PlayerID;

@Patch(
        className = "zombie.core.ActionManager",
        methodName = "stopPlayerActions",
        warmUp = true,
        strictMatch = true)
public final class PatchActionManagerStopPlayerActions {
    private PatchActionManagerStopPlayerActions() {
    }

    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.Argument(0) PlayerID playerId) {
        return GameServer.server && VanillaActionAccess.stopForTimedAction(
                playerId, TimedActionFishingContext.current());
    }
}
