package dev.faithrunner;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Her attacks on Minecraft's mobs: before each step the mobs around her are her targets
 * (faith_set_targets); after it, what landed (faith_melee_hits) hurts them on the singleplayer
 * server. Which attacks land, and how hard, is Mirror's Edge's.
 */
final class FaithMelee {
	/** The jump kick's reach (TargetingMaxDistance 800 uu x 3), the farthest any attack looks. */
	private static final double REACH = 24.0;
	private static final int MAX_TARGETS = 64, MAX_HITS = 16;
	/**
	 * Mirror's Edge's hit points to Minecraft's: its cops have 100, a zombie 20. A punch (33.5)
	 * takes a third of a zombie, as it does a cop; a full-speed jump kick (100) drops one.
	 */
	private static final float DAMAGE_SCALE = 0.2f;
	/** The blow's knockback (blocks a tick): at least a punch's, at most a launch's. */
	private static final double MIN_KNOCK = 0.3, MAX_KNOCK = 1.0;

	private FaithMelee() {}

	/** The living mobs within her reach, as her targets (by their entity ids). */
	static void setTargets(Minecraft mc, LocalPlayer p) {
		List<Entity> near = mc.level.getEntities(p, new AABB(p.position(), p.position()).inflate(REACH), FaithMelee::hittable);
		int n = Math.min(near.size(), MAX_TARGETS);
		try (Arena a = Arena.ofConfined()) {
			MemorySegment ts = a.allocate(Math.max(1, n) * Native.TARGET);
			for (int i = 0; i < n; i++) {
				Entity e = near.get(i);
				long o = i * Native.TARGET;
				Vec3 c = e.getBoundingBox().getCenter();
				float yaw = (float) Math.toRadians(((LivingEntity) e).yBodyRot);
				ts.set(JAVA_INT, o, e.getId());
				// Host frame: (x, -z, y).
				ts.set(JAVA_FLOAT, o + 4, (float) c.x);
				ts.set(JAVA_FLOAT, o + 8, (float) -c.z);
				ts.set(JAVA_FLOAT, o + 12, (float) c.y);
				ts.set(JAVA_FLOAT, o + 16, e.getBbWidth() * 0.5f);
				ts.set(JAVA_FLOAT, o + 20, e.getBbHeight() * 0.5f);
				ts.set(JAVA_FLOAT, o + 24, e.getEyeHeight() - e.getBbHeight() * 0.5f);
				// Facing (-sin yaw, cos yaw) in Minecraft's x, z.
				ts.set(JAVA_FLOAT, o + 28, (float) -Math.sin(yaw));
				ts.set(JAVA_FLOAT, o + 32, (float) -Math.cos(yaw));
				ts.set(JAVA_FLOAT, o + 36, 0f);
			}
			Faith.lib().setTargets.invokeExact(Faith.handle(), ts, n);
		} catch (Throwable t) {
			FaithRunner.LOG.error("faith_set_targets", t);
		}
	}

	private static boolean hittable(Entity e) {
		return e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof ArmorStand) && e.isAlive() && !e.isSpectator();
	}

	private record Hit(int target, float damage, double mx, double mz) {}

	/** What landed in the last step: hurt and knocked back on the singleplayer server. */
	static void applyHits(Minecraft mc) {
		Hit[] hits;
		try (Arena a = Arena.ofConfined()) {
			MemorySegment out = a.allocate(MAX_HITS * Native.HIT);
			int n = (int) Faith.lib().meleeHits.invokeExact(Faith.handle(), out, MAX_HITS);
			if (n == 0) {
				return;
			}
			hits = new Hit[n];
			for (int i = 0; i < n; i++) {
				long o = i * Native.HIT;
				// Momentum host (x, y, z) -> Minecraft (x, z = -y); m/s.
				hits[i] = new Hit(out.get(JAVA_INT, o), out.get(JAVA_FLOAT, o + 4), out.get(JAVA_FLOAT, o + 8), -out.get(JAVA_FLOAT, o + 12));
			}
		} catch (Throwable t) {
			FaithRunner.LOG.error("faith_melee_hits", t);
			return;
		}
		var server = mc.getSingleplayerServer();
		if (server == null || mc.level == null) {
			return;
		}
		var dimension = mc.level.dimension();
		var owner = Faith.owner;
		server.execute(() -> {
			var level = server.getLevel(dimension);
			var player = server.getPlayerList().getPlayer(owner);
			if (level == null || player == null) {
				return;
			}
			for (Hit h : hits) {
				if (!(level.getEntity(h.target()) instanceof LivingEntity mob) || !mob.isAlive()) {
					continue;
				}
				// Her combos come faster than Minecraft's half-second of invulnerability: each blow lands.
				mob.setInvulnerableTime(0);
				var source = level.damageSources().playerAttack(player);
				float damage = h.damage() * DAMAGE_SCALE;
				mob.hurtServer(level, source, damage);
				// The blow's momentum (m/s, so blocks a tick / 20), the way it was thrown.
				double len = Math.hypot(h.mx(), h.mz());
				if (len > 1e-4) {
					double strength = Math.clamp(len / 20.0, MIN_KNOCK, MAX_KNOCK);
					mob.knockback(strength, -h.mx() / len, -h.mz() / len, source, damage);
				}
			}
		});
	}
}
