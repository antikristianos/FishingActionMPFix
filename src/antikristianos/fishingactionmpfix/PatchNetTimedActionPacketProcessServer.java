package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.NetTimedAction;
import zombie.core.raknet.UdpConnection;
import zombie.network.PacketTypes;

@Patch(
        className = "zombie.network.packets.NetTimedActionPacket",
        methodName = "processServer",
        warmUp = true,
        strictMatch = true)
public final class PatchNetTimedActionPacketProcessServer {
    private PatchNetTimedActionPacketProcessServer() {
    }

    @Patch.OnEnter
    public static void enter(
            @Patch.This NetTimedAction packet,
            @Patch.Argument(0) PacketTypes.PacketType packetType,
            @Patch.Argument(1) UdpConnection connection) {
        TimedActionFishingContext.push(packet);
    }

    @Patch.OnExit(onThrowable = Throwable.class, suppress = RuntimeException.class)
    public static void exit() {
        TimedActionFishingContext.pop();
    }
}
