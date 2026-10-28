package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "updateSeed",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerUpdateSeed {
    private PatchFishSchoolManagerUpdateSeed() {
    }

    @Patch.OnExit(suppress = RuntimeException.class)
    public static void exit() {
        FishingStateSync.afterSeedUpdated();
    }
}
