package com.heishandashu.moreswitches.mixin;

import com.heishandashu.moreswitches.sleep.RainSleepRule;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes rain count as "dark enough to sleep".
 *
 * <p>26.3 moved the sleep prerequisite into {@link BedRule}: {@code canSleep(Level)} asks the
 * rule ({@code WHEN_DARK}) whether the bed may be used, and only then does the player actually
 * lie down. Answering {@code true} here is what lets vanilla do the rest — the bed still runs its
 * normal "enough players are sleeping" logic and still advances the clock to its own wake-up
 * marker, so the day is skipped exactly the way a night is.</p>
 *
 * <p>The injection only ever changes a false answer into a true one, and only while the switch is
 * on and it is actually raining; every other case falls straight through to vanilla. It is applied
 * on both sides because the rule is consulted by the client's prediction and by the server.</p>
 */
@Mixin(BedRule.class)
public class MixinBedRule {

    @Inject(method = "canSleep", at = @At("RETURN"), cancellable = true, require = 1)
    private void moreswitches$allowSleepInRain(Level level, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return; // already allowed (night, or a rule that always allows)
        }
        if (RainSleepRule.allowsSleep(level)) {
            cir.setReturnValue(true);
        }
    }
}
