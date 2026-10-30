package com.heishandashu.moreswitches;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for the NeoForge build.
 *
 * <p>Everything this mod ships is a client behaviour, so the constructor only owns the shared
 * pieces: the log channel, the identifier helper and the settings file location. The client
 * init lives in {@code MoreSwitchesClient}, which NeoForge only loads on a client.</p>
 *
 * <p>Author: 黑山大叔 — https://space.bilibili.com/19428259<br>
 * Core structure inspired by https://crates.io/crates/lighty-launcher</p>
 */
@Mod(MoreSwitchesMod.MOD_ID)
public final class MoreSwitchesMod {

    /** Registry namespace; also the prefix of every translation key this mod owns. */
    public static final String MOD_ID = "moreswitches";

    public static final Logger LOGGER = LoggerFactory.getLogger("MoreSwitches");

    /**
     * @param modBus the mod's own event bus, used by the initialisers that subscribe to it
     */
    public MoreSwitchesMod(IEventBus modBus) {
        LOGGER.info("[{}] common init (settings: {})", MOD_ID,
                FMLPaths.CONFIGDIR.get().resolve(MOD_ID + ".json"));
    }

    /** {@code moreswitches:<path>} — the only place identifiers are built. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
