package dev.faithrunner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.faithrunner.Faith;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/** Singleplayer: Minecraft's fall damage doesn't land on top of Mirror's Edge's landings. */
@Mixin(Player.class)
public abstract class PlayerFallMixin {
	@Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
	private void faithrunner$fall(double fallDistance, float damageModifier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
		if (Faith.driving() && (Object) this instanceof ServerPlayer sp && sp.getUUID().equals(Faith.owner)) {
			cir.setReturnValue(false);
		}
	}
}
