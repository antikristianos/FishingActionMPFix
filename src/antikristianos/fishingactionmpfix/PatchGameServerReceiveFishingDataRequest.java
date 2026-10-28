package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.raknet.UdpConnection;

@Patch(
        className = "zombie.network.GameServer",
        methodName = "receiveFishingDataRequest",
        warmUp = true,
        strictMatch = true)
public final class PatchGameServerReceiveFishingDataRequest {
    private PatchGameServerReceiveFishingDataRequest() {
    }

    @Patch.OnExit(suppress = RuntimeException.class)
    public static void exit(@Patch.Argument(1) UdpConnection connection) {
        FishingStateSync.sendFishingZones(connection);
    }
}
