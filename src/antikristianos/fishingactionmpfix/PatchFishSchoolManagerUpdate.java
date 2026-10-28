package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.iso.FishSchoolManager;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "update",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerUpdate {
    private PatchFishSchoolManagerUpdate() {
    }

    @Patch.OnExit(suppress = RuntimeException.class)
    public static void exit(@Patch.This Object manager) {
        FishSchoolManager fishSchoolManager = (FishSchoolManager) manager;
        FishingStateSync.flushPendingFishingData(fishSchoolManager);
        FishingBubbleAuthority.flushServerDeltas(fishSchoolManager);
    }
}
