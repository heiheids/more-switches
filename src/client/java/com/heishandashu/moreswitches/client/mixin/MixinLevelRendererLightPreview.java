package com.heishandashu.moreswitches.client.mixin;

import com.heishandashu.moreswitches.client.light.ClientHeldLight;
import com.heishandashu.moreswitches.light.LightFalloff;
import com.heishandashu.moreswitches.light.LightPreviewRule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the light-level numbers of the blocks a held light source would illuminate.
 *
 * <p>Injected after the block outline is submitted, so the numbers are queued into the same
 * world-space pass and depth-test against the terrain. Each number is drawn with
 * {@code submitText} rather than a name tag: a name tag would come with its own background plate
 * and white text, while the requested look is plain cyan digits.</p>
 *
 * <p>Only blocks inside the simulated source's reach get a number, and only where the simulated
 * level would actually beat the world's own light — a spot that is already brighter gains nothing,
 * so labelling it would be misleading. That keeps the marked area exactly equal to the area a real
 * torch would change.</p>
 *
 * <p>Nothing here writes to the level. The numbers come from the same read-only simulation the
 * moving-light switch uses.</p>
 */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public class MixinLevelRendererLightPreview {

    /** Cyan, opaque. */
    private static final int CYAN = 0xFF00E5E5;

    /** Full-bright packed light, so the numbers stay readable in dark areas. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Lifts the digits just clear of the block's top face. */
    private static final double TOP_OFFSET = 1.02D;

    @Inject(method = "submitBlockOutline", at = @At("RETURN"), require = 1)
    private void moreswitches$drawLightNumbers(PoseStack poseStack,
                                               SubmitNodeCollector collector,
                                               LevelRenderState renderState,
                                               CallbackInfo cir) {
        if (!LightPreviewRule.previewEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        int emission = ClientHeldLight.emission(minecraft);
        if (emission <= 0) {
            return;
        }
        Vec3 centre = ClientHeldLight.centre(minecraft);
        if (centre == null) {
            return;
        }
        CameraRenderState camera = renderState.cameraRenderState;
        if (camera == null || camera.pos == null || camera.orientation == null) {
            return;
        }

        int reach = LightFalloff.reach(emission);
        BlockPos origin = BlockPos.containing(centre);
        // Only walk the cube the light can reach: at torch level that is 27 blocks per axis, not
        // anything near the render distance.
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!minecraft.level.isLoaded(pos) || !isTopFaceVisible(minecraft, pos)) {
                        continue;
                    }
                    int simulated = ClientHeldLight.levelAt(minecraft, pos, emission, centre);
                    if (simulated <= 0) {
                        continue;
                    }
                    // Skip blocks the world already lights at least as brightly: the torch adds
                    // nothing there, so a number would be a lie.
                    int existing = minecraft.level.getBrightness(LightLayer.BLOCK, pos);
                    if (existing >= simulated) {
                        continue;
                    }
                    submitNumber(poseStack, collector, camera, pos, simulated);
                }
            }
        }
    }

    /**
     * Whether this block has open space above it, i.e. a top face a player could actually see.
     *
     * <p>Stops the numbers from being drawn inside solid rock, which would both look wrong and
     * cost time for blocks nobody can see.</p>
     */
    private static boolean isTopFaceVisible(Minecraft minecraft, BlockPos pos) {
        return minecraft.level.getBlockState(pos.above()).isAir();
    }

    /** Queues one cyan number, centred over the block's top face and turned to face the camera. */
    private static void submitNumber(PoseStack poseStack, SubmitNodeCollector collector,
                                     CameraRenderState camera, BlockPos pos, int level) {
        poseStack.pushPose();
        try {
            poseStack.translate(pos.getX() + 0.5D, pos.getY() + TOP_OFFSET, pos.getZ() + 0.5D);
            // Face the camera the same way name tags do, then centre the glyph on this spot.
            poseStack.rotate(new Quaternionf().rotateY((float) Math.toRadians(-camera.yRot))
                    .rotateX((float) Math.toRadians(camera.xRot)));
            poseStack.scale(0.025F, -0.025F, 0.025F);

            String text = Integer.toString(level);
            float width = Minecraft.getInstance().font.width(text) / 2.0F;
            collector.submitText(poseStack, -width, 0.0F,
                    net.minecraft.util.FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY),
                    false, Font.DisplayMode.NORMAL, FULL_BRIGHT, CYAN, 0, 0);
        } finally {
            poseStack.popPose();
        }
    }
}
