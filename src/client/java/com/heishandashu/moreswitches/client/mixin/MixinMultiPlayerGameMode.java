package com.heishandashu.moreswitches.client.mixin;

import com.heishandashu.moreswitches.client.feature.ClientFeatures;
import com.heishandashu.moreswitches.drop.DropKeyRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The client's share of the switches that have to stop a vanilla call before it acts.
 *
 * <p>Firework rocket: both right-click paths are covered, because a rocket is used by both —</p>
 * <ul>
 *   <li>{@code useItem} — right-clicking into the air while gliding, which boosts the player;</li>
 *   <li>{@code useItemOn} — right-clicking a block, which places a rocket.</li>
 * </ul>
 *
 * <p>Each is cancelled at {@code HEAD}, before the click becomes a packet, so no rocket is spawned
 * client-side and none is requested from the server. Injecting at {@code RETURN} would be too late:
 * the packet is already sent inside the method.</p>
 *
 * <p>Drop key: {@code dropItem} is the single client method that throws an item away, for both the
 * plain key and the drop-the-whole-stack variant. Everything it does — removing the stack from the
 * inventory and sending the packet — happens inside the method, so it likewise has to be cancelled
 * at {@code HEAD}. Cancelling it removes nothing locally, which is exactly the intent: the item
 * stays where it is.</p>
 *
 * <p>Handler parameter types match the target methods exactly ({@code useItem} takes
 * {@link Player}, {@code useItemOn} takes {@link LocalPlayer}, {@code dropItem} takes
 * {@link LocalPlayer} and a boolean); Mixin validates this when it applies the injection.
 * {@code require = 1} makes a failed application a startup crash instead of a switch that silently
 * does nothing.</p>
 *
 * <p>This class holds no decision logic; {@link ClientFeatures} and {@link DropKeyRule} decide.</p>
 */
@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void moreswitches$blockFireworkUse(Player player, InteractionHand hand,
                                               CallbackInfoReturnable<InteractionResult> cir) {
        if (ClientFeatures.blocksItemUse(Minecraft.getInstance(), player, hand)) {
            // FAIL consumes the action without reporting success: the click is eaten and nothing
            // else in vanilla reacts to it.
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 1)
    private void moreswitches$blockFireworkPlacement(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (ClientFeatures.blocksItemUse(Minecraft.getInstance(), player, hand)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void moreswitches$blockDropItem(LocalPlayer player, boolean all, CallbackInfo cir) {
        if (DropKeyRule.blocksDrop()) {
            cir.cancel();
        }
    }
}
