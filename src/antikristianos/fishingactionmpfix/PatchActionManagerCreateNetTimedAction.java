package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;
import se.krka.kahlua.vm.KahluaTable;
import zombie.characters.IsoPlayer;
import zombie.network.GameClient;

@Patch(
        className = "zombie.core.ActionManager",
        methodName = "createNetTimedAction",
        warmUp = true,
        strictMatch = true)
public final class PatchActionManagerCreateNetTimedAction {
    private PatchActionManagerCreateNetTimedAction() {
    }

    @Patch.OnEnter
    public static void enter(
            @Patch.Argument(0) IsoPlayer player,
            @Patch.Argument(1) KahluaTable action) {
        if (GameClient.client) {
            VanillaActionAccess.reserveTimedActionId();
        }
    }
}
