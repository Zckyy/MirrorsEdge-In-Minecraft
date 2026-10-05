package dev.faithrunner;

import net.minecraft.client.Minecraft;

/** For the client game tests. */
public final class FaithTesting {
	private FaithTesting() {}

	public static boolean loaded() {
		return Faith.handle() != null || Faith.loadError() != null;
	}

	public static void toggle(Minecraft mc) {
		Faith.toggle(mc);
	}

	/** Holds crouch (or lets go), as the sneak key would. */
	public static void crouch(boolean held) {
		Faith.testCrouch = held;
	}

	/** Turns her view (degrees: right, down), as the mouse would. */
	public static void turn(float yaw, float pitch) {
		Faith.addLook(yaw / 0.15, pitch / 0.15);
	}

	public static String body() {
		return FaithBody.bounds() + " pitch " + Faith.pitch() + " cam " + Faith.cameraPos();
	}

	/** One line of where she and the camera are. */
	public static String where(Minecraft mc) {
		var c = mc.gameRenderer.mainCamera().position();
		var p = mc.player.position();
		return String.format("%s feet (%.2f %.2f %.2f) cam (%.2f %.2f %.2f)", Faith.stateName(), p.x, p.y, p.z, c.x, c.y, c.z);
	}

	/** Bones in camera space: position and where their local axes point. */
	public static String bones(String... names) {
		StringBuilder b = new StringBuilder();
		try (java.lang.foreign.Arena a = java.lang.foreign.Arena.ofConfined()) {
			for (String n : names) {
				java.lang.foreign.MemorySegment x = a.allocate(32);
				byte ok = (byte) Faith.lib().bodyBone.invokeExact(Faith.handle(), a.allocateFrom(n), x);
				if (ok == 0) {
					b.append(n).append(": none; ");
					continue;
				}
				org.joml.Quaternionf q = new org.joml.Quaternionf(x.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0), x.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 4),
					x.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 8), x.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 12));
				org.joml.Vector3f ax = new org.joml.Vector3f(1, 0, 0).rotate(q), ay = new org.joml.Vector3f(0, 1, 0).rotate(q), az = new org.joml.Vector3f(0, 0, 1).rotate(q);
				b.append(String.format("%s pos (%.2f %.2f %.2f) X(%.2f %.2f %.2f) Y(%.2f %.2f %.2f) Z(%.2f %.2f %.2f); ", n,
					x.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 16), x.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 20), x.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 24),
					ax.x, ax.y, ax.z, ay.x, ay.y, ay.z, az.x, az.y, az.z));
			}
		} catch (Throwable t) {
			b.append(t);
		}
		return b.toString();
	}

	public static net.minecraft.client.KeyMapping meleeKey() {
		return FaithRunner.MELEE;
	}

	/** Who has the player: "off", "faith", or Minecraft and why ("minecraft: swimming"). */
	public static String mode() {
		return !Faith.active ? "off" : Faith.handedOff ? "minecraft: " + Faith.handOffReason : "faith";
	}

	public static String state() {
		return Faith.stateName() + (Faith.loadError() != null ? " (load error: " + Faith.loadError() + ")" : "");
	}

	/** What her feet and hands touch now, as given to her step sounds: "feet/hands". */
	public static String surfaces(Minecraft mc) {
		int[] s = FaithSurfaces.of(mc.player, Faith.stateName());
		return s[0] + "/" + s[1];
	}
}
