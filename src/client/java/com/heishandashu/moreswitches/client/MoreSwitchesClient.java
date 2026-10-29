package com.heishandashu.moreswitches.client;

import com.heishandashu.moreswitches.MoreSwitchesMod;
import com.heishandashu.moreswitches.client.feature.ClientFeatures;
import com.heishandashu.moreswitches.client.i18n.ClientLanguageReload;
import com.heishandashu.moreswitches.client.keybind.SettingsKeybinds;
import com.heishandashu.moreswitches.client.ui.SettingsScreen;
import com.heishandashu.moreswitches.config.SwitchIds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client entrypoint.
 *
 * <p>Responsibilities are split by file so this class stays a wiring list:</p>
 * <ul>
 *   <li>{@link ClientFeatures} — creates the feature modules and owns their state</li>
 *   <li>{@link SettingsKeybinds} — the key that opens and closes the screen</li>
 *   <li>{@link ClientLanguageReload} — reads the two language files</li>
 *   <li>{@link com.heishandashu.moreswitches.client.mixin.MixinMultiPlayerGameMode} — the one
 *       hook the firework switch needs</li>
 * </ul>
 */
public class MoreSwitchesClient implements ClientModInitializer {

    /**
     * Ticks to wait before the debug auto-open fires.
     *
     * <p>Waiting lets the title screen settle, so the screenshot shows a normal screen with the
     * settings screen on top rather than a half-built one.</p>
     */
    private static final int DEBUG_OPEN_DELAY_TICKS = 80;

    /** JVM switch that opens the settings screen automatically; for verification runs only. */
    private static final String DEBUG_OPEN_PROPERTY = "moreswitches.debugOpenSettings";

    @Override
    public void onInitializeClient() {
        ClientLanguageReload.register();
        ClientFeatures.registerAll();
        SettingsKeybinds.register();

        MoreSwitchesMod.LOGGER.info("[{}] client ready -- {} switches registered, default key: J",
                MoreSwitchesMod.MOD_ID, SwitchIds.all().size());

        if (Boolean.getBoolean(DEBUG_OPEN_PROPERTY)) {
            registerDebugAutoOpen();
        }
    }

    /**
     * Opens the screen on its own after startup.
     *
     * <p>This exists so the screen can be exercised (and its rendering exceptions caught in the
     * log) without a human pressing a key. It only runs when the JVM property is set, so it can
     * never affect a normal player.</p>
     */
    private void registerDebugAutoOpen() {
        MoreSwitchesMod.LOGGER.info("[{}] debug: the settings screen will open automatically", MoreSwitchesMod.MOD_ID);
        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            private int ticks;

            @Override
            public void onEndTick(Minecraft client) {
                if (this.ticks++ < DEBUG_OPEN_DELAY_TICKS) {
                    return;
                }
                Screen current = client.gui.screen();
                if (current instanceof SettingsScreen) {
                    return;
                }
                MoreSwitchesMod.LOGGER.info("[{}] debug: opening the settings screen (was: {})",
                        MoreSwitchesMod.MOD_ID, current == null ? "null" : current.getClass().getName());
                client.gui.setScreen(new SettingsScreen(current));
            }
        });
    }
}
