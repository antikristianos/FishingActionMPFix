package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "getNumberOfFishInPoint",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerVisibleFishCount {
    private PatchFishSchoolManagerVisibleFishCount() {
    }

    @Patch.OnExit(suppress = RuntimeException.class)
    public static void exit(
            @Patch.Argument(0) int x,
            @Patch.Argument(1) int y,
            @Patch.Return(readOnly = false) int returnValue) {
        returnValue = FishingBubbleAuthority.authoritativeFishCountForSplash(x, y, returnValue);
    }
}
