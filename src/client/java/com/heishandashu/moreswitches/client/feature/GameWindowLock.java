package com.heishandashu.moreswitches.client.feature;

import com.heishandashu.moreswitches.MoreSwitchesMod;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * Stops the game window from being resized. Position is left alone.
 *
 * <p>Two mechanisms are combined, because the first one alone is not enough:</p>
 * <ol>
 *   <li>{@code SDL_SetWindowResizable(false)} removes {@code WS_THICKFRAME} from the native
 *       window. That style is what draws the resize border, and it is the reason the cursor turns
 *       into a double arrow over an edge.</li>
 *   <li>{@code SDL_SetWindowBordered(true)} is then called purely as a refresh. SDL's resizable
 *       setter only rewrites the style with {@code SetWindowLong} and never asks Windows to
 *       recompute the frame; without {@code SWP_FRAMECHANGED} the window keeps its old hit-test
 *       regions, so the double-arrow cursor survives and the border still looks draggable. The
 *       bordered setter is the one SDL call that does pass {@code SWP_FRAMECHANGED}, so it is used
 *       here as a way to make Windows believe the frame changed. It does not change the border
 *       state: the argument matches how the window already is.</li>
 *   <li>The minimum and maximum sizes are pinned to the size at lock time, so nothing else can
 *       resize the window either (an OS display change, a program calling SDL, or dragging an edge
 *       that still hit-tests).</li>
 * </ol>
 *
 * <p>Deliberately <b>not</b> done: the position is never written back. The player can drag the
 * window anywhere and it stays there; only its size is frozen.</p>
 */
final class GameWindowLock {

    /** Vanilla sets this as the minimum window size when it creates the window. */
    private static final int VANILLA_MIN_WIDTH = 320;
    private static final int VANILLA_MIN_HEIGHT = 240;

    /** SDL's "no maximum" value. */
    private static final int NO_MAXIMUM = 0xFFFF;

    private boolean locked;

    private long windowHandle;

    private int lockedWidth;
    private int lockedHeight;

    /** True while the switch is on and the window's size is actually frozen. */
    boolean isLocked() {
        return this.locked;
    }

    /**
     * Stops the window from being resized at its current size.
     *
     * @return true when the size is frozen; false when the window cannot be locked right now, in
     *         which case the caller leaves the switch visually off instead of pretending
     */
    boolean apply(Window window) {
        if (this.locked) {
            return true;
        }
        long handle = window.handle();
        if (handle == 0L) {
            MoreSwitchesMod.LOGGER.warn("[{}] window lock: the window has no native handle yet -- not locking",
                    MoreSwitchesMod.MOD_ID);
            return false;
        }
        IntBuffer width = null;
        MemoryStack stack = MemoryStack.stackPush();
        try {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            if (!SDLVideo.SDL_GetWindowSize(handle, w, h)) {
                MoreSwitchesMod.LOGGER.warn("[{}] window lock: could not read the window size",
                        MoreSwitchesMod.MOD_ID);
                return false;
            }
            this.lockedWidth = w.get(0);
            this.lockedHeight = h.get(0);
        } finally {
            stack.pop();
        }
        if (this.lockedWidth <= 0 || this.lockedHeight <= 0) {
            MoreSwitchesMod.LOGGER.warn("[{}] window lock: read an invalid window size {}x{}",
                    MoreSwitchesMod.MOD_ID, this.lockedWidth, this.lockedHeight);
            return false;
        }

        this.windowHandle = handle;

        if (!SDLVideo.SDL_SetWindowResizable(handle, false)) {
            MoreSwitchesMod.LOGGER.warn("[{}] window lock: SDL_SetWindowResizable(false) failed",
                    MoreSwitchesMod.MOD_ID);
        }
        // Refresh the frame so the resize border and its double-arrow cursor really go away.
        refreshFrame(handle);
        SDLVideo.SDL_SetWindowMinimumSize(handle, this.lockedWidth, this.lockedHeight);
        SDLVideo.SDL_SetWindowMaximumSize(handle, this.lockedWidth, this.lockedHeight);

        this.locked = true;
        MoreSwitchesMod.LOGGER.info("[{}] window lock on: size fixed at {}x{}, position left free",
                MoreSwitchesMod.MOD_ID, this.lockedWidth, this.lockedHeight);
        return true;
    }

    /** Gives the window back its resize border. Position was never touched, so nothing to undo. */
    void release() {
        if (!this.locked) {
            return;
        }
        long handle = this.windowHandle;
        this.locked = false;
        if (!SDLVideo.SDL_SetWindowResizable(handle, true)) {
            MoreSwitchesMod.LOGGER.warn("[{}] window lock: SDL_SetWindowResizable(true) failed",
                    MoreSwitchesMod.MOD_ID);
        }
        // Same refresh as when locking, so the resize border reappears immediately.
        refreshFrame(handle);
        SDLVideo.SDL_SetWindowMinimumSize(handle, VANILLA_MIN_WIDTH, VANILLA_MIN_HEIGHT);
        SDLVideo.SDL_SetWindowMaximumSize(handle, NO_MAXIMUM, NO_MAXIMUM);
        MoreSwitchesMod.LOGGER.info("[{}] window lock off", MoreSwitchesMod.MOD_ID);
    }

    /**
     * Makes Windows recompute the non-client area.
     *
     * <p>Passes the state the window already has, so this only triggers the frame recalculation;
     * it is not a border change. Failure is only logged, because the size lock above already
     * works without it — the only thing lost is the immediate cursor update.</p>
     */
    private void refreshFrame(long handle) {
        if (!SDLVideo.SDL_SetWindowBordered(handle, true)) {
            MoreSwitchesMod.LOGGER.warn("[{}] window lock: the frame refresh failed; "
                    + "the resize cursor may linger until the window is moved", MoreSwitchesMod.MOD_ID);
        }
    }
}
