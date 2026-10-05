package dev.faithrunner.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.faithrunner.FaithBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/** Her arms' field of view for the hand pass (HandsRendererMixin draws them there). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@ModifyExpressionValue(method = "render3dHud", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraRenderState;hudFov:F"))
	private float faithrunner$armsFov(float hudFov) {
		if (!FaithBody.drawing()) {
			return hudFov;
		}
		Minecraft mc = Minecraft.getInstance();
		float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		return FaithBody.armsFov(mc.gameRenderer.mainCamera().getFov(), aspect);
	}
}
