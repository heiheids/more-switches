package com.heishandashu.moreswitches.client;

import com.heishandashu.moreswitches.MoreSwitchesMod;
import com.heishandashu.moreswitches.client.feature.ClientFeatures;
import com.heishandashu.moreswitches.client.i18n.ClientLanguageReload;
import com.heishandashu.moreswitches.client.keybind.SettingsKeybinds;
import com.heishandashu.moreswitches.client.ui.SettingsScreen;
import com.heishandashu.moreswitches.config.SwitchIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.util.function.Consumer;

/**
 * Client wiring for NeoForge.
 *
 * <p>Responsibilities are split by file so this class stays a wiring list:</p>
 * <ul>
 *   <li>{@link ClientFeatures} — creates the feature modules and owns their state</li>
 *   <li>{@link SettingsKeybinds} — the key that opens and closes the screen</li>
 *   <li>{@link ClientLanguageReload} — reads the two language files on every resource reload</li>
 *   <li>{@link ClientTickHook} — the one tick entry point the modules subscribe through</li>
 * </ul>
 *
 * <p>NeoForge only loads this class on a physical client, so a dedicated server never sees it.</p>
 */
@EventBusSubscriber(modid = MoreSwitchesMod.MOD_ID, value = Dist.CLIENT)
public final class MoreSwitchesClient {

    /**
     * Ticks to wait before the debug auto-open fires.
     *
     * <p>Waiting lets the title screen settle, so a screenshot shows a normal screen with the
     * settings screen on top rather than a half-built one.</p>
     */
    private static final int DEBUG_OPEN_DELAY_TICKS = 80;

    /** JVM switch that opens the settings screen automatically; for verification runs only. */
    private static final String DEBUG_OPEN_PROPERTY = "moreswitches.debugOpenSettings";

    private MoreSwitchesClient() {
    }

    /** Registers the key binding with the mod event bus. */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SettingsKeybinds.OPEN_SETTINGS);
    }

    /** Reads the mod's language files whenever the client reloads resources. */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ClientLanguageReload.listener());
    }

    /** Starts the feature modules once the client is set up. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
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
     * <p>This exists so the screen can be exercised (and its rendering exceptions caught in the log)
     * without a human pressing a key. It only runs when the JVM property is set, so it can never
     * affect a normal player.</p>
     */
    private static void registerDebugAutoOpen() {
        MoreSwitchesMod.LOGGER.info("[{}] debug: the settings screen will open automatically",
                MoreSwitchesMod.MOD_ID);
        ClientTickHook.onEndTick(new Consumer<Minecraft>() {
            private int ticks;

            @Override
            public void accept(Minecraft client) {
                if (this.ticks++ < DEBUG_OPEN_DELAY_TICKS) {
                    return;
                }
                Screen current = client.screen;
                if (current instanceof SettingsScreen) {
                    return;
                }
                MoreSwitchesMod.LOGGER.info("[{}] debug: opening the settings screen (was: {})",
                        MoreSwitchesMod.MOD_ID, current == null ? "null" : current.getClass().getName());
                client.setScreen(new SettingsScreen(current));
            }
        });
    }
}
