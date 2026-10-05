package dev.faithrunner.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.faithrunner.FaithBody;
import dev.faithrunner.IrisCompat;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;

/**
 * Faith's arms, and what she holds, in place of Minecraft's hand: here, where every way of
 * drawing the hand comes through (Minecraft's own, and shader mods' passes).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HandsRendererMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void faithrunner$faith(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (FaithBody.drawing()) {
			// Iris draws the hand twice, solid then (if what's held is) translucent: her arms in the
			// first, what she holds in its own.
			boolean iris = IrisCompat.handPass();
			if (!iris || IrisCompat.passDraws(net.minecraft.world.item.ItemStack.EMPTY)) {
				FaithBody.submitArms(collector);
			}
			if (!iris || IrisCompat.passDraws(state.mainHandItem)) {
				FaithBody.submitHeld(collector, state.mainHandItem);
			}
			ci.cancel();
		}
	}
}
