package dev.faithrunner;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Faith on Minecraft's player: each rendered frame she steps on the blocks around her and the
 * player goes where she is, looking where she looks, with her camera.
 *
 * Frames: Minecraft is Y up (+X east, +Z south), 1 block a metre; faith_ffi's host frame is Z up
 * with +Y north, so host = (x, -z, y). Headings: Minecraft's yaw is 0 facing south, growing
 * clockwise; Faith's heading is 0 facing north, clockwise: heading = yaw - 180 degrees.
 */
public final class Faith {
	private static Native lib;
	private static volatile MemorySegment handle = MemorySegment.NULL;
	private static final Arena ARENA = Arena.global();
	private static MemorySegment input;
	private static MemorySegment frame;
	private static volatile String loadError;

	/** Faith is driving the player. Read by the server-side mixins too (singleplayer, same JVM). */
	public static volatile boolean active;
	/** The player she drives (the singleplayer owner, for the server-side mixins). */
	public static volatile UUID owner;

	static boolean haveFrame;
	private static long lastNanos;
	/** Mouse look since the last step (degrees: yaw right, pitch down). */
	private static float lookYaw, lookPitch;
	/** Where we last put the player: anything else moving it (teleport, respawn) re-places her. */
	private static Vec3 lastSet;
	private static boolean wasJump, wasCrouch, wasTurn, wasMelee, soundPaused, waitingForGround;

	private Faith() {}

	/** Starts loading Faith in the background (reading Mirror's Edge takes seconds). */
	static void preload() {
		Thread t = new Thread(Faith::ensure, "faithrunner-load");
		t.setDaemon(true);
		t.start();
	}

	/** Loads faith_ffi.dll and makes Faith (once). */
	static synchronized boolean ensure() {
		if (handle.address() != 0) {
			return true;
		}
		if (loadError != null) {
			return false;
		}
		try {
			Config cfg = Config.load();
			Path dll = cfg.dll;
			if (!Files.exists(dll)) {
				throw new IllegalStateException("no faith_ffi.dll at " + dll.toAbsolutePath());
			}
			lib = new Native(dll);
			MemorySegment dir = cfg.mirrorsEdge.isEmpty() ? MemorySegment.NULL : ARENA.allocateFrom(cfg.mirrorsEdge);
			handle = (MemorySegment) lib.create.invokeExact(dir, 1.0f);
			if (handle.address() == 0) {
				throw new IllegalStateException("faith_create failed: " + Native.cString((MemorySegment) lib.lastError.invokeExact()));
			}
			// Minecraft's world is steps and slabs everywhere: Mirror's Edge's own step-up (shipped
			// switched off in the game) takes the half-block ones.
			lib.setAutoStepUp.invokeExact(handle, (byte) 1);
			// ... including slabs: a half block (0.5 m) is just over the game's 0.48 m.
			lib.setAutoStepUpMax.invokeExact(handle, 0.52f);
			input = ARENA.allocate(Native.INPUT_SIZE);
			frame = ARENA.allocate(Native.FRAME_SIZE);
			FaithRunner.LOG.info("Faith loaded ({}animated)", ((byte) lib.animated.invokeExact(handle)) != 0 ? "" : "not ");
			return true;
		} catch (Throwable t) {
			loadError = t.getMessage();
			FaithRunner.LOG.error("Faith couldn't load", t);
			return false;
		}
	}

	static String loadError() {
		return loadError;
	}

	static void toggle(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		if (active) {
			active = false;
			pauseSound(true);
			p.sendOverlayMessage(Component.literal("Faith: off"));
			return;
		}
		if (handle.address() == 0 && loadError == null) {
			p.sendOverlayMessage(Component.literal("Faith: still loading Mirror's Edge, try again in a moment"));
			return;
		}
		if (!ensure()) {
			p.sendOverlayMessage(Component.literal("Faith couldn't load: " + loadError));
			return;
		}
		owner = p.getUUID();
		WorldGather.rebuild(mc, true);
		place(p);
		active = true;
		pauseSound(false);
		lastNanos = System.nanoTime();
		p.sendOverlayMessage(Component.literal("Faith: on"));
	}

	static void pauseSound(boolean paused) {
		if (handle.address() == 0) {
			return;
		}
		try {
			lib.soundPause.invokeExact(handle, (byte) (paused ? 1 : 0));
		} catch (Throwable ignored) {
		}
	}

	/** Puts Faith where the player stands, facing their way. */
	private static void place(LocalPlayer p) {
		try {
			MemorySegment feet = ARENA.allocate(Native.VEC3);
			feet.set(JAVA_FLOAT, 0, (float) p.getX());
			feet.set(JAVA_FLOAT, 4, (float) -p.getZ());
			feet.set(JAVA_FLOAT, 8, (float) p.getY());
			lib.teleport.invokeExact(handle, feet, (float) Math.toRadians(p.getYRot() - 180.0f));
			lastSet = p.position();
			haveFrame = false;
		} catch (Throwable t) {
			FaithRunner.LOG.error("faith_teleport", t);
		}
	}

	static void setWorld(float[] tris, java.util.List<WorldGather.HostFixture> fixtures, java.util.List<WorldGather.Candidate> candidates) {
		if (handle.address() == 0) {
			return;
		}
		try (Arena a = Arena.ofConfined()) {
			MemorySegment fx = a.allocate(Math.max(1, fixtures.size()) * Native.HOST_FIXTURE);
			for (int i = 0; i < fixtures.size(); i++) {
				WorldGather.HostFixture f = fixtures.get(i);
				long o = i * Native.HOST_FIXTURE;
				fx.set(JAVA_INT, o, f.kind());
				fx.set(JAVA_INT, o + 4, f.flags());
				for (int k = 0; k < 3; k++) {
					fx.set(JAVA_FLOAT, o + 8 + k * 4, f.a()[k]);
					fx.set(JAVA_FLOAT, o + 20 + k * 4, f.b()[k]);
					fx.set(JAVA_FLOAT, o + 32 + k * 4, f.n()[k]);
				}
				fx.set(JAVA_FLOAT, o + 44, f.top());
			}
			lib.setHostFixtures.invokeExact(handle, fx, fixtures.size());
			MemorySegment cs = a.allocate(Math.max(1, candidates.size()) * Native.CANDIDATE);
			for (int i = 0; i < candidates.size(); i++) {
				WorldGather.Candidate c = candidates.get(i);
				long o = i * Native.CANDIDATE;
				for (int k = 0; k < 3; k++) {
					cs.set(JAVA_FLOAT, o + k * 4, c.a()[k]);
					cs.set(JAVA_FLOAT, o + 12 + k * 4, c.b()[k]);
				}
				cs.set(JAVA_FLOAT, o + 24, c.thickness());
				cs.set(JAVA_INT, o + 28, 1);
			}
			lib.setCandidates.invokeExact(handle, cs, candidates.size());
			MemorySegment seg = a.allocateFrom(JAVA_FLOAT, tris);
			lib.setWorld.invokeExact(handle, seg, tris.length / 9);
		} catch (Throwable t) {
			FaithRunner.LOG.error("faith_set_world", t);
		}
	}

	/** Doors she burst open this step: Minecraft's open with them (on the singleplayer server). */
	private static void openDoors(Minecraft mc) {
		int[] opened;
		try (Arena a = Arena.ofConfined()) {
			MemorySegment out = a.allocate(JAVA_INT, 8);
			int n = (int) lib.doorsOpened.invokeExact(handle, out, 8);
			if (n == 0) {
				return;
			}
			opened = new int[n];
			for (int i = 0; i < n; i++) {
				opened[i] = out.getAtIndex(JAVA_INT, i);
			}
		} catch (Throwable t) {
			return;
		}
		var server = mc.getSingleplayerServer();
		for (int i : opened) {
			net.minecraft.core.BlockPos pos = WorldGather.doors.get(i);
			if (pos == null) {
				continue;
			}
			WorldGather.openedDoors.put(pos, System.currentTimeMillis());
			if (server == null || mc.level == null) {
				continue;
			}
			var dimension = mc.level.dimension();
			server.execute(() -> {
				var level = server.getLevel(dimension);
				if (level == null) {
					return;
				}
				var state = level.getBlockState(pos);
				if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door) {
					door.setOpen(null, level, state, pos, true);
				}
			});
		}
	}

	/** Mouse look for the player while Faith has it (Entity.turn's degrees). */
	public static void addLook(double xo, double yo) {
		lookYaw += (float) xo * 0.15f;
		lookPitch += (float) yo * 0.15f;
	}

	/** Once a rendered frame, before the camera is placed. */
	public static void frame() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1e9f, 0.1f);
		lastNanos = now;
		if (!active || p == null || mc.level == null) {
			return;
		}
		// Her sounds play outside Minecraft's: hold them with the pause menu.
		if (mc.isPaused() != soundPaused) {
			soundPaused = mc.isPaused();
			pauseSound(soundPaused);
		}
		if (mc.isPaused()) {
			lookYaw = lookPitch = 0;
			return;
		}
		// Moved by something else (a teleport, a respawn, the server putting her back): from there.
		if (lastSet == null || p.position().distanceTo(lastSet) > 1.5) {
			if (lastSet != null) {
				FaithRunner.LOG.info("player moved by something else ({} -> {}): Faith follows", lastSet, p.position());
			}
			place(p);
			WorldGather.rebuild(mc, true);
		}
		// Where the ground hasn't loaded yet (just teleported, respawned, through a portal), she has
		// nothing to stand on: held where she is until it has, then her world is read again.
		if (!mc.level.getChunkSource().hasChunk(p.getBlockX() >> 4, p.getBlockZ() >> 4)) {
			waitingForGround = true;
			lookYaw = lookPitch = 0;
			return;
		}
		if (waitingForGround) {
			waitingForGround = false;
			WorldGather.rebuild(mc, true);
		}
		var o = mc.options;
		boolean jump = o.keyJump.isDown(), crouch = o.keyShift.isDown();
		boolean turn = FaithRunner.TURN.isDown(), melee = FaithRunner.MELEE.isDown();
		float fwd = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
		float strafe = (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0);
		boolean typing = mc.gui.screen() != null;
		if (typing) {
			fwd = strafe = 0;
			jump = crouch = turn = melee = false;
		}
		input.set(JAVA_FLOAT, 0, strafe);
		input.set(JAVA_FLOAT, 4, fwd);
		input.set(JAVA_FLOAT, 8, (float) Math.toRadians(lookYaw));
		input.set(JAVA_FLOAT, 12, (float) Math.toRadians(-lookPitch));
		input.set(JAVA_BYTE, 16, (byte) (jump && !wasJump ? 1 : 0));
		input.set(JAVA_BYTE, 17, (byte) (jump ? 1 : 0));
		input.set(JAVA_BYTE, 18, (byte) (crouch && !wasCrouch ? 1 : 0));
		input.set(JAVA_BYTE, 19, (byte) (crouch ? 1 : 0));
		input.set(JAVA_BYTE, 20, (byte) (turn && !wasTurn ? 1 : 0));
		input.set(JAVA_BYTE, 21, (byte) (melee && !wasMelee ? 1 : 0));
		input.set(JAVA_BYTE, 22, (byte) 0);
		input.set(JAVA_BYTE, 23, (byte) 0);
		wasJump = jump;
		wasCrouch = crouch;
		wasTurn = turn;
		wasMelee = melee;
		lookYaw = lookPitch = 0;
		try {
			lib.step.invokeExact(handle, dt, input, frame);
		} catch (Throwable t) {
			FaithRunner.LOG.error("faith_step", t);
			active = false;
			return;
		}
		haveFrame = true;
		openDoors(mc);
		// The player goes where she is, looking where she looks.
		Vec3 feet = mc(Native.F_FEET);
		p.setPos(feet.x, feet.y, feet.z);
		p.xo = feet.x;
		p.yo = feet.y;
		p.zo = feet.z;
		float yaw = (float) Math.toDegrees(Native.f(frame, Native.F_HEADING)) + 180.0f;
		float pitch = (float) -Math.toDegrees(Native.f(frame, Native.F_PITCH));
		p.setYRot(yaw);
		p.setXRot(Math.max(-90, Math.min(90, pitch)));
		p.yRotO = yaw;
		p.xRotO = p.getXRot();
		p.setYHeadRot(yaw);
		Vec3 vel = mc(Native.F_VELOCITY);
		// Minecraft's velocity is per tick.
		p.setDeltaMovement(vel.scale(1.0 / 20.0));
		p.setOnGround(frame.get(JAVA_BYTE, Native.F_ON_GROUND) != 0);
		p.fallDistance = 0;
		lastSet = p.position();
	}

	/** Host-frame point at `off` in the frame, in Minecraft's frame. */
	private static Vec3 mc(long off) {
		return new Vec3(Native.f(frame, off), Native.f(frame, off + 8), -Native.f(frame, off + 4));
	}

	private static Vector3f mcDir(long off) {
		return new Vector3f(Native.f(frame, off), Native.f(frame, off + 8), -Native.f(frame, off + 4));
	}

	public static Vec3 cameraPos() {
		return mc(Native.F_CAM_POS);
	}

	/** Her camera's orientation, as Minecraft's Camera.rotation (local -Z forward, +Y up, +X right). */
	public static Quaternionf cameraRotation() {
		Vector3f right = mcDir(Native.F_CAM_RIGHT), up = mcDir(Native.F_CAM_UP), fwd = mcDir(Native.F_CAM_FORWARD);
		Matrix3f m = new Matrix3f(right.x, right.y, right.z, up.x, up.y, up.z, -fwd.x, -fwd.y, -fwd.z);
		return new Quaternionf().setFromNormalized(m);
	}

	public static boolean drivesCamera() {
		Minecraft mc = Minecraft.getInstance();
		return active && haveFrame && mc.options.getCameraType().isFirstPerson();
	}

	static MemorySegment handle() {
		return handle.address() == 0 ? null : handle;
	}

	static Native lib() {
		return lib;
	}

	/** Her look's pitch (radians, up positive) this frame. */
	static float pitch() {
		return haveFrame ? Native.f(frame, Native.F_PITCH) : 0;
	}

	static boolean animated() {
		return haveFrame && frame.get(JAVA_BYTE, Native.F_ANIMATED) != 0;
	}

	static String stateName() {
		if (handle.address() == 0) {
			return "";
		}
		try {
			return Native.cString((MemorySegment) lib.stateName.invokeExact(handle));
		} catch (Throwable t) {
			return "";
		}
	}
}
