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
 * <p>Minecraft bakes block light into a section's vertex data when that section is meshed, and only
 * re-meshes when something marks the section dirty. {@code MixinLightCoordsUtil} changes what the
 * mesher samples, but on its own that never shows up: the surrounding sections were already meshed
 * before the light existed, so the picture stays as it was.</p>
 *
 * <p>The dirty request must go to {@code Minecraft.levelExtractor}, not to the level. In 26.3 the
 * section meshes are scheduled by the extractor; marking sections dirty through the level does not
 * reach it, which is why an earlier version of this switch rebuilt nothing.</p>
 *
 * <p>The work happens only when the light crosses a block boundary, never per frame, and covers the
 * light's section plus its neighbours — a torch reaches 13 blocks, which cannot leave that 3×3×3
 * box.</p>
 */
public final class MovingLightModule implements ClientFeature {

    /** Opt-in diagnostics: counts how far the effect got, printed every few seconds. */
    private static final String DEBUG_PROPERTY = "moreswitches.debugLight";

    private static final long REPORT_INTERVAL_MILLIS = 3_000L;

    private static int bakedMeshes;

    private static int dirtySections;

    private static long lastReport;

    private boolean active;

    /** Section the light was last seen in, or null when it was off or unavailable. */
    private SectionPos lastSection;

    /**
     * Counts one mesh that baked the simulated light.
     *
     * <p>Called from the light-coords injection. A growing number means the mesher is seeing the
     * simulated brightness; a number stuck at zero means no re-meshed section contains a block the
     * light could brighten.</p>
     */
    public static void noteBakedMesh() {
        bakedMeshes++;
    }

    @Override
    public void register(String switchId) {
        this.active = enabled(switchId);
        MoreSwitchesMod.LOGGER.info("[{}] switch '{}' starts {}", MoreSwitchesMod.MOD_ID,
                SwitchIds.MOVING_LIGHT, this.active ? "on" : "off");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
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

    private void tick(Minecraft client) {
        if (!this.active || client.level == null || client.player == null) {
            return;
        }
        Vec3 centre = ClientHeldLight.centre(client);
        int emission = ClientHeldLight.emission(client);
        if (centre == null || emission <= 0) {
            if (this.lastSection != null) {
                // The player put the light away: clear the glow that was baked in.
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
        report(emission, centre);
    }

    /**
     * Re-meshes one section and its neighbours so the baked light is recomputed.
     *
     * <p>Three by three by three, because the reach of a torch can spill into a neighbouring section
     * on every axis.</p>
     */
    private static void invalidate(Minecraft client, SectionPos section) {
        if (client.levelExtractor == null) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    client.levelExtractor.setSectionDirty(
                            section.x() + dx, section.y() + dy, section.z() + dz);
                    dirtySections++;
                }
            }
        }
    }

    private static void report(int emission, Vec3 centre) {
        if (!Boolean.getBoolean(DEBUG_PROPERTY)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastReport < REPORT_INTERVAL_MILLIS) {
            return;
        }
        lastReport = now;
        MoreSwitchesMod.LOGGER.info(
                "[light-move] emission={} centre={},{},{} dirtySections={} bakedMeshes={}",
                emission,
                String.format("%.1f", centre.x),
                String.format("%.1f", centre.y),
                String.format("%.1f", centre.z),
                dirtySections, bakedMeshes);
    }
}
