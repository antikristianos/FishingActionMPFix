package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "generateSplashInRadius",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerGenerateSplashInRadius {
    private PatchFishSchoolManagerGenerateSplashInRadius() {
    }

    @Patch.OnEnter(skipOn = true)
    public static boolean enter(
            @Patch.Argument(0) int x,
            @Patch.Argument(1) int y,
            @Patch.Argument(2) float radius) {
        return FishingBubbleAuthority.shouldSuppressSplashAtAnchor(x, y);
    }
}
