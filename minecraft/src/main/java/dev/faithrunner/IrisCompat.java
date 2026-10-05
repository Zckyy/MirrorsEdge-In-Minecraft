package dev.faithrunner;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/** What Iris is doing, when it's there (its classes are touched only then). */
public final class IrisCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("iris");

	private IrisCompat() {}

	/** Iris's own hand pass is drawing (its model-view is identity, not the camera's rotation). */
	public static boolean handPass() {
		return LOADED && Hands.active();
	}

	/** In Iris's hand pass: whether this pass is the one that draws `stack` (solid or translucent). */
	public static boolean passDraws(ItemStack stack) {
		return Hands.solid() != Hands.translucent(stack);
	}

	private static final class Hands {
		static boolean active() {
			return net.irisshaders.iris.pathways.HandRenderer.INSTANCE.isActive();
		}

		static boolean solid() {
			return net.irisshaders.iris.pathways.HandRenderer.INSTANCE.isRenderingSolid();
		}

		static boolean translucent(ItemStack stack) {
			return net.irisshaders.iris.pathways.HandRenderer.INSTANCE.isHandTranslucent(stack);
		}
	}
}
