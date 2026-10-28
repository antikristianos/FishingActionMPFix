package antikristianos.fishingactionmpfix;

import java.util.ArrayList;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "isFishPoint",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerVisibleFishPoint {
    private PatchFishSchoolManagerVisibleFishPoint() {
    }

    @Patch.OnExit(suppress = RuntimeException.class)
    public static void exit(
            @Patch.Argument(0) int x,
            @Patch.Argument(1) int y,
            @Patch.Argument(2) ArrayList<int[]> nearbyNoFishZones,
            @Patch.Return(readOnly = false) boolean returnValue) {
        returnValue = FishingBubbleAuthority.authoritativeFishPointForSplash(x, y, returnValue);
    }
}
