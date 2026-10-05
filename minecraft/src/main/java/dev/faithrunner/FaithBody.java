package dev.faithrunner;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Faith's own first-person body (Mirror's Edge's arms and torso, and legs), skinned each frame by
 * faith_ffi and drawn where Minecraft draws its hand: with its own projection and a cleared depth
 * buffer, so it never clips into blocks. The same mesh the Skyrim plugin draws.
 */
public final class FaithBody {
	/** sizeof(FaithVertex), FaithPartInfo, FaithSection (faith.h). */
	private static final long VERTEX = 48, PART_INFO = 20, SECTION = 12;

	private record Section(int first, int count, int material) {}

	private record Part(int vertexCount, int[] indices, Section[] sections, RenderType[] materials, MemorySegment vertices, boolean legs) {}

	private static Part[] parts;
	private static boolean tried;
	/** Her right hand's bone, and the held item's place in it. */
	private static final MemorySegment HAND_BONE = Arena.global().allocateFrom("RightHand");
	private static final ItemStackRenderState HELD_STATE = new ItemStackRenderState();
	/**
	 * Where ItemInHandLayer holds an item, in her hand bone's space. Her hand: X along the fingers,
	 * Y out of the palm, Z to the thumb (the index finger fans that way of the little one).
	 * ItemInHandLayer's frame, after its turns: X to the back of the hand, Y the thumb's way, Z
	 * back up the arm; its origin the fist's lower front edge, 2 px ahead of and below the fist's
	 * middle, which here is 7 cm along her fingers and 2 cm into the palm.
	 */
	static final Matrix4f HELD = new Matrix4f(
		0, -1, 0, 0,
		0, 0, 1, 0,
		-1, 0, 0, 0,
		0.07f + 2 / 16f, 0.02f, 2 / 16f, 1);
	private static MethodHandle bodyParts, bodyPart, bodyIndices, bodySections, bodyTexture, bodySkin, bodyMaterialName;

	private FaithBody() {}

	private static boolean init(MemorySegment faith, Native lib) {
		if (tried) {
			return parts != null;
		}
		tried = true;
		try {
			var linker = java.lang.foreign.Linker.nativeLinker();
			var sym = java.lang.foreign.SymbolLookup.libraryLookup(lib.path, Arena.global());
			bodyParts = linker.downcallHandle(sym.find("faith_body_parts").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS));
			bodyPart = linker.downcallHandle(sym.find("faith_body_part").orElseThrow(), FunctionDescriptor.of(JAVA_BYTE, ADDRESS, JAVA_INT, ADDRESS));
			bodyIndices = linker.downcallHandle(sym.find("faith_body_indices").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
			bodySections = linker.downcallHandle(sym.find("faith_body_sections").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
			bodyTexture = linker.downcallHandle(sym.find("faith_body_texture").orElseThrow(),
				FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
			bodySkin = linker.downcallHandle(sym.find("faith_body_skin").orElseThrow(), FunctionDescriptor.of(JAVA_BYTE, ADDRESS, JAVA_INT, ADDRESS));
			bodyMaterialName = linker.downcallHandle(sym.find("faith_body_material_name").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

			int n = (int) bodyParts.invokeExact(faith);
			if (n == 0) {
				FaithRunner.LOG.info("Faith's body: none (no Mirror's Edge)");
				return false;
			}
			Arena a = Arena.global();
			Part[] out = new Part[n];
			for (int p = 0; p < n; p++) {
				MemorySegment info = a.allocate(PART_INFO);
				if ((byte) bodyPart.invokeExact(faith, p, info) == 0) {
					continue;
				}
				int vc = info.get(JAVA_INT, 0), ic = info.get(JAVA_INT, 4), sc = info.get(JAVA_INT, 8), mc = info.get(JAVA_INT, 12);
				MemorySegment idx = ((MemorySegment) bodyIndices.invokeExact(faith, p)).reinterpret(ic * 4L);
				int[] indices = idx.toArray(JAVA_INT);
				MemorySegment sec = ((MemorySegment) bodySections.invokeExact(faith, p)).reinterpret(sc * SECTION);
				Section[] sections = new Section[sc];
				for (int s = 0; s < sc; s++) {
					sections[s] = new Section(sec.get(JAVA_INT, s * SECTION), sec.get(JAVA_INT, s * SECTION + 4), sec.get(JAVA_INT, s * SECTION + 8));
				}
				RenderType[] materials = new RenderType[mc];
				for (int m = 0; m < mc; m++) {
					materials[m] = texture(faith, p, m, a);
				}
				StringBuilder secs = new StringBuilder();
				for (Section s : sections) {
					secs.append(s.material).append(materials.length > s.material && materials[s.material] != null ? "" : "(no texture)").append(" x").append(s.count / 3).append(' ');
				}
				FaithRunner.LOG.info("Faith's body part {}: {} vertices, {} materials, sections: {}", p, vc, mc, secs);
				out[p] = new Part(vc, indices, sections, materials, a.allocate(vc * VERTEX, 16), info.get(JAVA_BYTE, 16) != 0);
			}
			parts = out;
			FaithRunner.LOG.info("Faith's body: {} parts", n);
			return true;
		} catch (Throwable t) {
			FaithRunner.LOG.error("Faith's body couldn't load", t);
			return false;
		}
	}

	/**
	 * A material's colour texture, uploaded to Minecraft, as an entity render type. It carries the
	 * material's normal and specular maps for a shader pack (Iris's PBR textures, IrisCompat).
	 */
	private static RenderType texture(MemorySegment faith, int part, int material, Arena a) throws Throwable {
		// Opaque: Mirror's Edge's colour maps don't carry coverage in alpha, and the pipeline cuts
		// out anything under 0.1.
		NativeImage colour = image(faith, part, material, 0, a, abgr -> abgr | 0xFF000000);
		if (colour == null) {
			return null;
		}
		String name = Native.cString((MemorySegment) bodyMaterialName.invokeExact(faith, part, material));
		boolean skin = name.toLowerCase(java.util.Locale.ROOT).contains("skin");
		Identifier id = Identifier.fromNamespaceAndPath("faithrunner", "body/p" + part + "m" + material);
		NativeImage n = image(faith, part, material, 1, a, FaithBody::labNormal);
		NativeImage s = image(faith, part, material, 2, a, abgr -> labSpecular(abgr, skin));
		FaithTexture texture = new FaithTexture("Faith " + id, colour, n, s);
		// Iris learns which texture a GPU texture is when its getTexture() is first asked for (as
		// a resource pack's are on loading): drawing asks only for the view, so ask once here.
		texture.getTexture();
		Minecraft.getInstance().getTextureManager().register(id, texture);
		FaithRunner.LOG.info("Faith's material {} ({}): normal map {}, specular map {}", id, name, n != null, s != null);
		return RenderTypes.entityCutout(id);
	}

	/** One of a material's maps (`kind` 0 colour, 1 normal, 2 specular), each pixel through `pixel` (ABGR). */
	private static NativeImage image(MemorySegment faith, int part, int material, int kind, Arena a, java.util.function.IntUnaryOperator pixel) throws Throwable {
		MemorySegment w = a.allocate(JAVA_INT), h = a.allocate(JAVA_INT);
		MemorySegment px = (MemorySegment) bodyTexture.invokeExact(faith, part, material, kind, w, h);
		if (px.address() == 0) {
			return null;
		}
		int width = w.get(JAVA_INT, 0), height = h.get(JAVA_INT, 0);
		MemorySegment rgba = px.reinterpret((long) width * height * 4);
		NativeImage image = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				// RGBA bytes in memory are ABGR as a little-endian int: what setPixelABGR stores.
				int abgr = rgba.get(JAVA_INT.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN), ((long) y * width + x) * 4);
				image.setPixelABGR(x, y, pixel.applyAsInt(abgr));
			}
		}
		return image;
	}

	/**
	 * Mirror's Edge's tangent-space normal map (RGB = XYZ, Unreal's green-down, as LabPBR's) as
	 * LabPBR's: X and Y kept (the pack works out Z), no ambient occlusion in blue (255), no height
	 * in alpha (255: flat, so no parallax).
	 */
	static int labNormal(int abgr) {
		return 0xFFFF0000 | (abgr & 0x0000FFFF);
	}

	/**
	 * Mirror's Edge's specular map is a grey intensity (how much highlight, 0..255). As LabPBR's:
	 * red is perceptual smoothness, from matte (0.15) where it has none to a soft sheen (0.50)
	 * where it's brightest; green is F0, 10 (0.04, a dielectric: skin, cloth, leather); blue,
	 * subsurface scattering for skin (190) and none elsewhere; alpha 255, no emission.
	 */
	static int labSpecular(int abgr, boolean skin) {
		int intensity = ((abgr & 0xFF) + (abgr >> 8 & 0xFF) + (abgr >> 16 & 0xFF)) / 3;
		int smoothness = Math.round(255 * (0.15f + 0.35f * intensity / 255f));
		int sss = skin ? 190 : 0;
		return 0xFF000000 | sss << 16 | 10 << 8 | smoothness;
	}

	/**
	 * Her arms and torso, in Minecraft's hand pass (its own projection, cleared depth: they never
	 * clip into blocks). The hand pass's model view is the camera's view rotation, so her camera's
	 * rotation in the pose takes camera space back to it.
	 */
	public static void submitArms(SubmitNodeCollector collector) {
		submit(collector, cameraPose(), false);
	}

	/**
	 * Camera space into the hand pass's: Minecraft's hand pass is in the camera's rotation, Iris's
	 * (model-view identity) in camera space already.
	 */
	private static PoseStack cameraPose() {
		PoseStack pose = new PoseStack();
		if (!IrisCompat.handPass()) {
			pose.mulPose(new Matrix4f().rotation(Minecraft.getInstance().gameRenderer.mainCamera().rotation()));
		}
		return pose;
	}

	/**
	 * What she holds, in her right hand: the item as Minecraft draws it in a body's hand (its
	 * third-person transform, as ItemInHandLayer places it), in a frame built from her hand bone.
	 */
	public static void submitHeld(SubmitNodeCollector collector, net.minecraft.world.item.ItemStack stack) {
		MemorySegment faith = Faith.handle();
		Minecraft mc0 = Minecraft.getInstance();
		if (faith == null || stack == null || stack.isEmpty() || mc0.player == null) {
			return;
		}
		HELD_STATE.clear();
		mc0.getItemModelResolver().updateForLiving(HELD_STATE, stack, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, mc0.player);
		ItemStackRenderState item = HELD_STATE;
		MemorySegment x = Arena.ofAuto().allocate(Native.XFORM);
		try {
			if ((byte) Faith.lib().bodyBone.invokeExact(faith, HAND_BONE, x) == 0) {
				return;
			}
		} catch (Throwable t) {
			return;
		}
		Quaternionf rot = new Quaternionf(x.get(JAVA_FLOAT, 0), x.get(JAVA_FLOAT, 4), x.get(JAVA_FLOAT, 8), x.get(JAVA_FLOAT, 12));
		Vector3f at = new Vector3f(x.get(JAVA_FLOAT, 16), x.get(JAVA_FLOAT, 20), x.get(JAVA_FLOAT, 24));
		PoseStack pose = cameraPose();
		pose.mulPose(new Matrix4f().translation(at).rotate(rot).mul(HELD));
		item.submit(pose, collector, lightAt(Minecraft.getInstance(), at), OverlayTexture.NO_OVERLAY, 0);
	}

	/**
	 * Her legs, in the world (its depth: walls and floors hide them, as Mirror's Edge draws them).
	 * The world pass is relative to the camera's position; her camera's rotation takes camera
	 * space into it.
	 */
	public static void submitLegs(SubmitNodeCollector collector, PoseStack world) {
		world.pushPose();
		world.mulPose(new Matrix4f().rotation(Minecraft.getInstance().gameRenderer.mainCamera().rotation()));
		submit(collector, world, true);
		world.popPose();
	}

	/**
	 * Her whole body, arms and legs, into a shadow pass whose pose is relative to `shadowCam` (as
	 * that pass draws entities: at their position less it).
	 */
	public static void submitShadow(SubmitNodeCollector collector, PoseStack shadow, double camX, double camY, double camZ) {
		var cam = Minecraft.getInstance().gameRenderer.mainCamera();
		var p = cam.position();
		shadow.pushPose();
		shadow.translate(p.x - camX, p.y - camY, p.z - camZ);
		shadow.mulPose(new Matrix4f().rotation(cam.rotation()));
		submit(collector, shadow, true);
		submit(collector, shadow, false);
		shadow.popPose();
	}

	/** The light (packed block and sky light) at a camera-space point. */
	private static int lightAt(Minecraft mc, Vector3f camSpace) {
		if (mc.level == null) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
		var cam = mc.gameRenderer.mainCamera();
		Vector3f w = new Vector3f(camSpace).rotate(cam.rotation());
		var p = cam.position();
		return LightCoordsUtil.getLightCoords(mc.level, BlockPos.containing(p.x + w.x, p.y + w.y, p.z + w.z));
	}

	private static void submit(SubmitNodeCollector collector, PoseStack pose, boolean legs) {
		MemorySegment faith = Faith.handle();
		Native lib = Faith.lib();
		if (faith == null || !init(faith, lib)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		var cam = mc.gameRenderer.mainCamera();
		Quaternionf camRot = new Quaternionf(cam.rotation());
		var camPos = cam.position();
		for (int p = 0; p < parts.length; p++) {
			Part part = parts[p];
			if (part == null || part.legs != legs) {
				continue;
			}
			try {
				if ((byte) bodySkin.invokeExact(faith, p, part.vertices) == 0) {
					continue;
				}
			} catch (Throwable t) {
				continue;
			}
			MemorySegment v = part.vertices;
			// The light where each vertex is, by block: a hand in the sun, a foot in the shade.
			int[] light = new int[part.vertexCount];
			java.util.HashMap<Long, Integer> byBlock = new java.util.HashMap<>();
			Vector3f w = new Vector3f();
			for (int i = 0; i < part.vertexCount; i++) {
				long o = i * VERTEX;
				w.set(v.get(JAVA_FLOAT, o), v.get(JAVA_FLOAT, o + 4), v.get(JAVA_FLOAT, o + 8)).rotate(camRot);
				long key = BlockPos.asLong((int) Math.floor(camPos.x + w.x), (int) Math.floor(camPos.y + w.y), (int) Math.floor(camPos.z + w.z));
				light[i] = byBlock.computeIfAbsent(key, k -> mc.level == null ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.getLightCoords(mc.level, BlockPos.of(k)));
			}
			for (Section s : part.sections) {
				RenderType type = s.material < part.materials.length ? part.materials[s.material] : null;
				if (type == null) {
					continue;
				}
				int[] idx = part.indices;
				collector.submitCustomGeometry(pose, type, (at, buf) -> {
					// Her normals are smooth, per vertex: Iris mustn't flatten them to each face's.
					boolean was = IrisCompat.keepNormals();
					try {
						int last = idx[s.first];
						for (int i = s.first; i + 2 < s.first + s.count; i += 3) {
							// The pipeline draws quads: each triangle as one with its last corner twice.
							vertex(buf, at, v, idx[i], light[idx[i]]);
							vertex(buf, at, v, idx[i + 1], light[idx[i + 1]]);
							vertex(buf, at, v, idx[i + 2], light[idx[i + 2]]);
							vertex(buf, at, v, idx[i + 2], light[idx[i + 2]]);
							last = idx[i + 2];
						}
						// Iris finishes a quad when the next vertex starts: an empty one (one point
						// four times) last, so her last triangle is finished in here, and the one
						// finished out there draws nothing.
						for (int k = 0; k < 4; k++) {
							vertex(buf, at, v, last, light[last]);
						}
					} finally {
						IrisCompat.restoreNormals(was);
					}
				});
			}
		}
	}

	private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose at, MemorySegment v, int i, int light) {
		long o = i * VERTEX;
		buf.addVertex(at, v.get(JAVA_FLOAT, o), v.get(JAVA_FLOAT, o + 4), v.get(JAVA_FLOAT, o + 8))
			.setColor(0xFFFFFFFF)
			.setUv(v.get(JAVA_FLOAT, o + 40), v.get(JAVA_FLOAT, o + 44))
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(at, v.get(JAVA_FLOAT, o + 12), v.get(JAVA_FLOAT, o + 16), v.get(JAVA_FLOAT, o + 20));
	}

	/** Her arms' field of view (vertical, degrees): Mirror's Edge's 100 degrees across, blending to
	 * the world's when she looks down so her legs line up with it (the Skyrim plugin's ArmsFov). */
	public static float armsFov(float worldVerticalDeg, float aspect) {
		double worldV = Math.toRadians(worldVerticalDeg);
		double worldH = 2 * Math.atan(Math.tan(worldV / 2) * aspect);
		double t = Math.max(0, Math.min(1, (-Faith.pitch() - 0.35) / 0.55));
		t = t * t * (3 - 2 * t);
		double h = Math.toRadians(100) * (1 - t) + worldH * t;
		return (float) Math.toDegrees(2 * Math.atan(Math.tan(h / 2) / aspect));
	}

	/** For the tests: each part's camera-space bounds this frame. */
	public static String bounds() {
		if (parts == null) {
			return "no parts";
		}
		StringBuilder b = new StringBuilder();
		for (Part part : parts) {
			if (part == null) {
				continue;
			}
			float[] lo = { 1e9f, 1e9f, 1e9f }, hi = { -1e9f, -1e9f, -1e9f };
			for (int i = 0; i < part.vertexCount; i++) {
				for (int k = 0; k < 3; k++) {
					float c = part.vertices.get(JAVA_FLOAT, i * VERTEX + k * 4);
					lo[k] = Math.min(lo[k], c);
					hi[k] = Math.max(hi[k], c);
				}
			}
			b.append(String.format("[%d verts, x %.2f..%.2f y %.2f..%.2f z %.2f..%.2f] ", part.vertexCount, lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]));
		}
		return b.toString();
	}

	public static boolean drawing() {
		return Faith.drivesCamera() && Faith.animated();
	}
}
