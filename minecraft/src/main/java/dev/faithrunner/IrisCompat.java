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

	/**
	 * Her normal and specular maps to Iris, as a resource pack's would be: Iris asks a texture's
	 * loader for them when a shader pack first samples it. (A pack reads them only in its LabPBR
	 * mode, e.g. Complementary's "RP Support: labPBR".)
	 */
	static void registerPbr() {
		if (LOADED) {
			Pbr.register();
		}
	}

	private static final class Pbr {
		static void register() {
			net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry.INSTANCE.register(FaithTexture.class,
				(net.irisshaders.iris.pbr.loader.PBRTextureLoader<FaithTexture>) (texture, resources, maps) -> {
					var normal = texture.newNormal();
					if (normal != null) {
						maps.acceptNormalTexture(normal);
					}
					var specular = texture.newSpecular();
					if (specular != null) {
						maps.acceptSpecularTexture(specular);
					}
				});
		}
	}

	/**
	 * While the level draws, Iris gives every quad its face's normal (right for Minecraft's boxes).
	 * Her triangles go in as quads, so that would flat-shade her: off while hers are written.
	 * Returns what restoreNormals puts back.
	 */
	public static boolean keepNormals() {
		if (!LOADED) {
			return false;
		}
		boolean was = Normals.get();
		Normals.set(false);
		return was;
	}

	public static void restoreNormals(boolean was) {
		if (LOADED) {
			Normals.set(was);
		}
	}

	private static final class Normals {
		static boolean get() {
			return net.irisshaders.iris.vertices.ImmediateState.isRenderingLevel;
		}

		static void set(boolean recalculate) {
			net.irisshaders.iris.vertices.ImmediateState.isRenderingLevel = recalculate;
		}
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
