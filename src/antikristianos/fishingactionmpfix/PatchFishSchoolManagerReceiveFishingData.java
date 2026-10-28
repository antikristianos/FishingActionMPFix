package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.network.ByteBufferReader;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "receiveFishingData",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerReceiveFishingData {
    private PatchFishSchoolManagerReceiveFishingData() {
    }

    @Patch.OnExit(suppress = RuntimeException.class)
    public static void exit(@Patch.Argument(0) ByteBufferReader reader) {
        FishingStateSync.invalidateFishingZoneCache();
    }
}
