package com.heishandashu.moreswitches.client.feature;

import com.heishandashu.moreswitches.MoreSwitchesMod;
import com.heishandashu.moreswitches.client.light.ClientHeldLight;
import com.heishandashu.moreswitches.config.SwitchIds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the simulated light baked into the nearby section meshes.
 *
 * <p>Minecraft bakes block light into a section's vertex data when that section is meshed, and
 * only re-meshes when something marks the section dirty. {@code MixinLightCoordsUtil} changes what
 * the mesher samples, but on its own that never shows up: the surrounding sections were already
 * meshed before the light existed, so the picture stays as it was. That is why an earlier version
 * of this switch did nothing visible.</p>
 *
 * <p>The fix is to mark the sections around the light dirty whenever the light moves to a different
 * block, so they are re-meshed and pick up the simulated brightness. The reach of a torch is 13
 * blocks, which cannot leave the light's own section and its immediate neighbours, so
 * {@code setSectionDirtyWithNeighbors} on that one section is exactly the area that needs it.</p>
 *
 * <p>This is throttled on purpose: the work only happens when the light crosses a block boundary,
 * not every frame, because a re-mesh is not free and the effect is invisible at a finer
 * granularity.</p>
 */
public final class MovingLightModule implements ClientFeature {

    private boolean active;

    /** Section the light was last seen in, or null when it was off or unavailable. */
    private SectionPos lastSection;

    @Override
    public void register(String switchId) {
        this.active = enabled(switchId);
        MoreSwitchesMod.LOGGER.info("[{}] switch '{}' starts {}", MoreSwitchesMod.MOD_ID,
                SwitchIds.MOVING_LIGHT, this.active ? "on" : "off");
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, switchId));
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (this.active == enabled) {
            return;
        }
        this.active = enabled;
        // Turning the switch off has to refresh the meshes too, otherwise the light stays baked in
        // until the player happens to walk far enough for those sections to be rebuilt.
        this.lastSection = null;
        MoreSwitchesMod.LOGGER.info("[{}] switch '{}' -> {}",
                MoreSwitchesMod.MOD_ID, SwitchIds.MOVING_LIGHT, enabled);
    }

    private void tick(Minecraft client, String switchId) {
        if (!this.active || client.level == null || client.player == null) {
            return;
        }
        Vec3 centre = ClientHeldLight.centre(client);
        int emission = ClientHeldLight.emission(client);
        if (centre == null || emission <= 0) {
            if (this.lastSection != null) {
                // The player put the light away or turned the preview off: clear the old glow.
                invalidate(client, this.lastSection);
                this.lastSection = null;
            }
            return;
        }

        SectionPos section = SectionPos.of(BlockPos.containing(centre));
        if (section.equals(this.lastSection)) {
            return;
        }
        if (this.lastSection != null) {
            invalidate(client, this.lastSection);
        }
        invalidate(client, section);
        this.lastSection = section;
    }

    /** Re-meshes one section and its neighbours so the baked light is recomputed. */
    private static void invalidate(Minecraft client, SectionPos section) {
        if (client.level == null) {
            return;
        }
        client.level.setSectionDirtyWithNeighbors(section.x(), section.y(), section.z());
    }
}
