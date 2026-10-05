package dev.faithrunner;

import com.mojang.blaze3d.platform.InputConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Faith Runner: Mirror's Edge's movement, camera and animation on Minecraft's player. */
public class FaithRunner implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("faithrunner");

	static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("faithrunner", "main"));
	// Minecraft 26's keys are SDL scancodes.
	static final KeyMapping TOGGLE = new KeyMapping("key.faithrunner.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, CATEGORY);
	static final KeyMapping TURN = new KeyMapping("key.faithrunner.turn", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY);
	static final KeyMapping MELEE = new KeyMapping("key.faithrunner.melee", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY);

	@Override
	public void onInitializeClient() {
		KeyMappingHelper.registerKeyMapping(TOGGLE);
		KeyMappingHelper.registerKeyMapping(TURN);
		KeyMappingHelper.registerKeyMapping(MELEE);
		Faith.preload();
		// Development check: apply every mixin now (a broken one otherwise only shows on joining a world).
		if (Boolean.getBoolean("faithrunner.audit")) {
			org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
			LOG.info("mixin audit done");
		}
		// Her legs go in with the world's own geometry (its depth hides them behind blocks).
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			if (FaithBody.drawing()) {
				FaithBody.submitLegs(ctx.submitNodeCollector(), ctx.poseStack());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (TOGGLE.consumeClick()) {
				Faith.toggle(mc);
			}
			if (mc.player == null && Faith.active) {
				Faith.active = false;
			}
			WorldGather.tick(mc);
		});
	}
}
