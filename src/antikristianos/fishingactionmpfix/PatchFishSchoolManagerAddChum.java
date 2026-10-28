package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "addChum",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerAddChum {
    private PatchFishSchoolManagerAddChum() {
    }

    @Patch.OnExit(suppress = RuntimeException.class)
    public static void exit(
            @Patch.Argument(0) int x,
            @Patch.Argument(1) int y,
            @Patch.Argument(2) int amount) {
        FishingStateSync.afterChumChange();
    }
}
