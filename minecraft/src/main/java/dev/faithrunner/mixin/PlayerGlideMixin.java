package dev.faithrunner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.faithrunner.Faith;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Jump is hers in the air too (wallrun and wallclimb kicks, ledge and pole jumps): with an elytra on,
 * the jump opens it only while she's falling free. Once it's open, Minecraft has the player.
 */
@Mixin(Player.class)
public abstract class PlayerGlideMixin {
	@Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
	private void faithrunner$glide(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof LocalPlayer && !Faith.mayGlide()) {
			cir.setReturnValue(false);
		}
	}
}
