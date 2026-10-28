package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.raknet.UdpConnection;

@Patch(
        className = "zombie.network.packets.RegisterZonePacket",
        methodName = "processClient",
        warmUp = true,
        strictMatch = true)
public final class PatchRegisterZonePacketProcessClient {
    private PatchRegisterZonePacketProcessClient() {
    }

    @Patch.OnEnter(skipOn = true)
    public static boolean enter(
            @Patch.This Object packet,
            @Patch.Argument(0) UdpConnection connection) {
        return FishingStateSync.processFishingZonePacket(packet);
    }

}
