package dev.faithrunner;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * One of her materials' colour maps, carrying its normal and specular maps (LabPBR, see
 * FaithBody.labNormal / labSpecular) for a shader pack under Iris to read. Null where Mirror's Edge
 * has none.
 */
public final class FaithTexture extends DynamicTexture {
	private final String label;
	private final NativeImage normal, specular;

	FaithTexture(String label, NativeImage colour, NativeImage normal, NativeImage specular) {
		super(() -> label, colour);
		this.label = label;
		this.normal = normal;
		this.specular = specular;
	}

	/**
	 * A new texture of the normal map, or null. New each time: Iris owns what it's given and closes
	 * it when its PBR textures reload (a resource or shader pack change), then asks again.
	 */
	DynamicTexture newNormal() {
		return upload(normal, " normal");
	}

	/** A new texture of the specular map, or null (new each time, as newNormal). */
	DynamicTexture newSpecular() {
		return upload(specular, " specular");
	}

	private DynamicTexture upload(NativeImage image, String kind) {
		if (image == null) {
			return null;
		}
		NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), false);
		copy.copyFrom(image);
		return new DynamicTexture(() -> label + kind, copy);
	}

	@Override
	public void close() {
		super.close();
		if (normal != null) {
			normal.close();
		}
		if (specular != null) {
			specular.close();
		}
	}
}
