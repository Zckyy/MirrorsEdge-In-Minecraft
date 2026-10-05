package dev.faithrunner.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.faithrunner.FaithBody;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/**
 * Her shadow under shaders: Iris's shadow pass draws the local player's body (Steve) from the
 * sun; in its place, Faith's.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class IrisShadowMixin {
	@Shadow
	@Final
	private LevelRenderState levelRenderState;
	@Shadow
	@Final
	private SubmitNodeStorage submitNodeStorage;

	@Inject(method = "renderEntities", at = @At("HEAD"), require = 0)
	private void faithrunner$faith(LevelRendererAccessor levelRenderer, EntityRenderDispatcher dispatcher, PoseStack pose, float partialTicks,
		Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Integer> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (!FaithBody.drawing() || mc.player == null) {
			return;
		}
		int id = mc.player.getId();
		levelRenderState.entityRenderStates.removeIf(s -> s instanceof AvatarRenderState a && a.id == id);
		FaithBody.submitShadow(submitNodeStorage, pose, camX, camY, camZ);
	}
}
