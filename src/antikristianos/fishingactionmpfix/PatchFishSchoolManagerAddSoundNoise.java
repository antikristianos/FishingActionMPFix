package antikristianos.fishingactionmpfix;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(
        className = "zombie.iso.FishSchoolManager",
        methodName = "addSoundNoise",
        warmUp = true,
        strictMatch = true)
public final class PatchFishSchoolManagerAddSoundNoise {
    private PatchFishSchoolManagerAddSoundNoise() {
    }

    @Patch.OnEnter
    public static void enter(
            @Patch.Argument(0) int x,
            @Patch.Argument(1) int y,
            @Patch.Argument(2) int radius,
            @Patch.Local("fishingNoiseSignature") long fishingNoiseSignature) {
        fishingNoiseSignature = FishingStateSync.noiseKeySignature();
    }

    @Patch.OnExit(suppress = RuntimeException.class)
    public static void exit(@Patch.Local("fishingNoiseSignature") long fishingNoiseSignature) {
        FishingStateSync.afterSoundNoise(fishingNoiseSignature);
    }
}
