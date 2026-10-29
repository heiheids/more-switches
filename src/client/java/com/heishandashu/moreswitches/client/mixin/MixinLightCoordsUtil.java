package com.heishandashu.moreswitches.client.mixin;

import com.heishandashu.moreswitches.client.light.ClientHeldLight;
import com.heishandashu.moreswitches.light.LightPreviewRule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a held light source appear to light the blocks around it.
 *
 * <p>The target is the brightness lookup that every block face goes through while the level is
 * rendered. The injection rewrites the packed light of that one position: the sky light is
 * preserved and only the block light is raised, so the result looks like a torch was placed there
 * rather than like the whole screen got brighter.</p>
 *
 * <p>Two properties matter for correctness:</p>
 * <ul>
 *   <li>it is per position, so the brightening falls off with distance exactly like vanilla block
 *       light instead of glowing evenly;</li>
 *   <li>it only changes what the renderer is told — nothing is written to the level, the light
 *       engine or any saved data, so the world's real light is untouched and the effect vanishes
 *       the moment the switch is turned off.</li>
 * </ul>
 *
 * <p>Taking the maximum instead of adding keeps an already-bright spot from becoming brighter than
 * the simulated source itself, which is how vanilla treats overlapping light sources.</p>
 */
@Mixin(LightCoordsUtil.class)
public class MixinLightCoordsUtil {

    // The full descriptor is required: the class also has a two-argument getLightCoords, and a
    // bare name would let Mixin pick that overload and fail at apply time.
    @Inject(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;"
            + "Lnet/minecraft/world/level/BlockAndLightGetter;"
            + "Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true, require = 1)
    private static void moreswitches$addHeldLight(
            LightCoordsUtil.BrightnessGetter getter,
            BlockAndLightGetter level,
            BlockState state,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir) {
        if (!LightPreviewRule.movingLightEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || level != minecraft.level) {
            // Only the level currently being rendered is simulated; anything else is another pass.
            return;
        }
        int emission = ClientHeldLight.emission(minecraft);
        if (emission <= 0) {
            return;
        }
        Vec3 centre = ClientHeldLight.centre(minecraft);
        int simulated = ClientHeldLight.levelAt(minecraft, pos, emission, centre);
        if (simulated <= 0) {
            return;
        }

        int packed = cir.getReturnValueI();
        int existing = LightCoordsUtil.block(packed);
        if (simulated > existing) {
            cir.setReturnValue(LightCoordsUtil.withBlock(packed, simulated));
        }
    }
}
