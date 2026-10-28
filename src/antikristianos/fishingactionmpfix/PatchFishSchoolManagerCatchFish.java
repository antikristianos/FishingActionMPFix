package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "catchFish",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerCatchFish {
    private PatchFishSchoolManagerCatchFish() {
    }

    @Patch.OnExit(suppress = RuntimeException.class)
    public static void exit(
            @Patch.Argument(0) int x,
            @Patch.Argument(1) int y) {
        FishingStateSync.afterFishCaught(x, y);
    }
}
