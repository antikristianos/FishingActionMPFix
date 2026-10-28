package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "generateSplashes",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerGenerateSplashes {
    private PatchFishSchoolManagerGenerateSplashes() {
    }

    @Patch.OnEnter
    public static void enter() {
        FishingBubbleAuthority.beginClientSplashPass();
    }

    @Patch.OnExit(onThrowable = Throwable.class, suppress = RuntimeException.class)
    public static void exit() {
        FishingBubbleAuthority.endClientSplashPass();
    }
}
