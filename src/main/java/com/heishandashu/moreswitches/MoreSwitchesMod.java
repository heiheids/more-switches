package com.heishandashu.moreswitches;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (both-side) entrypoint.
 *
 * <p>The two shipped features are client behaviours, so the server side only owns the shared
 * pieces: the log channel, the identifier helper and the settings file. Keeping the settings
 * in the common source set means a dedicated server writes the same {@code config/moreswitches.json}
 * and a pack author can inspect it without a client.</p>
 *
 * <p>Author: 黑山大叔 — https://space.bilibili.com/19428259<br>
 * Core structure inspired by https://crates.io/crates/lighty-launcher</p>
 */
public class MoreSwitchesMod implements ModInitializer {

    /** Registry namespace; also the prefix of every translation key this mod owns. */
    public static final String MOD_ID = "moreswitches";

    public static final Logger LOGGER = LoggerFactory.getLogger("MoreSwitches");

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] common init (settings: config/{}.json)", MOD_ID, MOD_ID);
    }

    /** {@code moreswitches:<path>} — the only place identifiers are built. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
