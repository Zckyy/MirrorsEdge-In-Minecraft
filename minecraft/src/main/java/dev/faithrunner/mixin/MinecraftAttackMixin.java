package dev.faithrunner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.faithrunner.Faith;
import net.minecraft.client.Minecraft;

/**
 * While Faith has the player, left click is her attack (FaithRunner.MELEE): Minecraft's own
 * (hitting with the arm, breaking blocks) is off. Off, or handed off to Minecraft, it's back.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void faithrunner$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (Faith.driving()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void faithrunner$noMining(boolean down, CallbackInfo ci) {
		if (Faith.driving()) {
			ci.cancel();
		}
	}
}
