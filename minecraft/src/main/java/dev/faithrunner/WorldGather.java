package dev.faithrunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WeatheringCopperBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.AABB;

/**
 * The blocks around Faith as her world, in the host frame (host = (x, -z, y)):
 * - collision: each block's boxes as triangles (full cubes only where they open onto something);
 * - stairs (straight, bottom half) as a 45 degree ramp, as Mirror's Edge's levels put collision
 *   ramps over their stairs: walked up, not stepped up one by one;
 * - ladders and vines as her ladders (climbing out at the top where there's a roof to step onto);
 * - closed wooden doors as doors she barges or kicks open (and Minecraft's door opens with them);
 * - hay bales and slime blocks as soft landings;
 * - iron bars and fences with two clear blocks under them, offered as swing poles (faith_ffi
 *   decides, as it does with Skyrim's bars).
 */
final class WorldGather {
	/** How far round her it reads (blocks), and how often (ticks), or when she's moved this far. */
	static final int RADIUS = 20, DOWN = 10, UP = 14, EVERY = 10;
	static final double MOVED = 6.0;
	/** A door she burst open stays open to her for this long (ms), whatever the block says yet. */
	static final long DOOR_GRACE = 3000;

	private static int ticks;
	private static BlockPos lastCentre;
	/** The doors in the last host fixture list (by their index in it), and doors recently burst open. */
	static final Map<Integer, BlockPos> doors = new HashMap<>();
	static final Map<BlockPos, Long> openedDoors = new HashMap<>();

	/** A fixture for faith_set_host_fixtures (FaithHostFixture). */
	record HostFixture(int kind, int flags, float[] a, float[] b, float[] n, float top) {}

	/** A candidate for faith_set_fixture_candidates (FaithFixtureCandidate). */
	record Candidate(float[] a, float[] b, float thickness) {}

	private WorldGather() {}

	static void tick(Minecraft mc) {
		if (!Faith.active || mc.player == null) {
			return;
		}
		boolean moved = lastCentre == null || lastCentre.distToCenterSqr(mc.player.position()) > MOVED * MOVED;
		if (++ticks >= EVERY || moved) {
			rebuild(mc, false);
		}
	}

	private static boolean solid(ClientLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return !s.isAir() && s.isCollisionShapeFullBlock(level, p);
	}

	private static boolean poleBlock(BlockState s) {
		Block b = s.getBlock();
		return s.is(Blocks.IRON_BARS) || b instanceof WeatheringCopperBarsBlock || b instanceof FenceBlock;
	}

	/** A bar or fence high up: two clear blocks under it. */
	private static boolean highPole(ClientLevel level, BlockPos p, BlockState s) {
		return poleBlock(s) && level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
			&& level.getBlockState(p.below(2)).getCollisionShape(level, p.below(2)).isEmpty();
	}

	private static boolean doorOpenToHer(BlockPos lower) {
		Long t = openedDoors.get(lower);
		return t != null && System.currentTimeMillis() - t < DOOR_GRACE;
	}

	static void rebuild(Minecraft mc, boolean force) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return;
		}
		ticks = 0;
		openedDoors.values().removeIf(t -> System.currentTimeMillis() - t > DOOR_GRACE);
		BlockPos c = mc.player.blockPosition();
		lastCentre = c;
		FloatList out = new FloatList();
		List<HostFixture> fixtures = new ArrayList<>();
		List<Candidate> candidates = new ArrayList<>();
		doors.clear();
		Set<Long> laddersDone = new HashSet<>();
		Set<Long> polesDone = new HashSet<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		for (int x = c.getX() - RADIUS; x <= c.getX() + RADIUS; x++) {
			for (int z = c.getZ() - RADIUS; z <= c.getZ() + RADIUS; z++) {
				for (int y = c.getY() - DOWN; y <= c.getY() + UP; y++) {
					pos.set(x, y, z);
					BlockState s = level.getBlockState(pos);
					if (s.isAir()) {
						continue;
					}
					Block block = s.getBlock();
					// Doors: a closed wooden one is her door (solid until she bursts it).
					if (block instanceof DoorBlock && DoorBlock.isWoodenDoor(s)) {
						BlockPos lower = s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.immutable() : pos.below().immutable();
						if (doorOpenToHer(lower)) {
							continue;
						}
						if (!s.getValue(DoorBlock.OPEN)) {
							if (pos.equals(lower)) {
								door(level, lower, s, fixtures);
							}
							continue;
						}
					}
					if ((block instanceof LadderBlock || block instanceof VineBlock) && !laddersDone.contains(pos.asLong())) {
						ladder(level, pos.immutable(), s, fixtures, laddersDone);
					}
					if (s.is(Blocks.HAY_BLOCK) || s.is(Blocks.SLIME_BLOCK)) {
						fixtures.add(new HostFixture(2, 0, host(x, y, z), host(x + 1, y + 1, z + 1), new float[3], 0));
					}
					if (highPole(level, pos, s)) {
						if (!polesDone.contains(pos.asLong())) {
							pole(level, pos.immutable(), candidates, polesDone);
						}
						continue;
					}
					if (block instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM && s.getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT) {
						ramp(out, x, y, z, s.getValue(StairBlock.FACING));
						continue;
					}
					if (s.isCollisionShapeFullBlock(level, pos)) {
						for (Direction d : Direction.values()) {
							n.setWithOffset(pos, d);
							if (solid(level, n)) {
								continue;
							}
							face(out, x, y, z, x + 1, y + 1, z + 1, d);
						}
						continue;
					}
					for (AABB b : s.getCollisionShape(level, pos).toAabbs()) {
						box(out, x + b.minX, y + b.minY, z + b.minZ, x + b.maxX, y + b.maxY, z + b.maxZ);
					}
				}
			}
		}
		Faith.setWorld(out.toArray(), fixtures, candidates);
	}

	/** A closed wooden door: its panel, both halves high. */
	private static void door(ClientLevel level, BlockPos lower, BlockState s, List<HostFixture> fixtures) {
		List<AABB> boxes = s.getCollisionShape(level, lower).toAabbs();
		if (boxes.isEmpty()) {
			return;
		}
		AABB b = boxes.get(0);
		Direction f = s.getValue(DoorBlock.FACING);
		int x = lower.getX(), y = lower.getY(), z = lower.getZ();
		float[] lo = host(x + b.minX, y, z + b.maxZ), hi = host(x + b.maxX, y + 2, z + b.minZ);
		doors.put(fixtures.size(), lower);
		fixtures.add(new HostFixture(1, 0, lo, hi, new float[] { f.getStepX(), -f.getStepZ(), 0 }, 0));
	}

	/** Which way a ladder or a (one-sided) vine faces out from its wall, or null. */
	private static Direction outward(BlockState s) {
		if (s.getBlock() instanceof LadderBlock) {
			return s.getValue(LadderBlock.FACING);
		}
		if (s.getBlock() instanceof VineBlock) {
			Direction found = null;
			for (Direction d : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
				if (s.getValue(VineBlock.getPropertyForFace(d))) {
					if (found != null) {
						return null;
					}
					found = d.getOpposite();
				}
			}
			return found;
		}
		return null;
	}

	/** A run of ladder (or vine) blocks up a wall, from its bottom. */
	private static void ladder(ClientLevel level, BlockPos start, BlockState s, List<HostFixture> fixtures, Set<Long> done) {
		Direction out = outward(s);
		if (out == null) {
			done.add(start.asLong());
			return;
		}
		BlockPos bottom = start;
		while (outward(level.getBlockState(bottom.below())) == out) {
			bottom = bottom.below();
		}
		BlockPos top = start;
		while (outward(level.getBlockState(top.above())) == out) {
			top = top.above();
		}
		for (BlockPos p = bottom; p.getY() <= top.getY(); p = p.above()) {
			done.add(p.asLong());
		}
		// Climbing out over the top: the wall ends there, with room on it.
		BlockPos behind = top.relative(out.getOpposite());
		boolean exit = solid(level, behind) && level.getBlockState(behind.above()).getCollisionShape(level, behind.above()).isEmpty()
			&& level.getBlockState(behind.above(2)).getCollisionShape(level, behind.above(2)).isEmpty();
		double fx = bottom.getX() + 0.5 - out.getStepX() * 0.5, fz = bottom.getZ() + 0.5 - out.getStepZ() * 0.5;
		fixtures.add(new HostFixture(0, exit ? 2 : 0, host(fx, bottom.getY(), fz), new float[3], new float[] { out.getStepX(), -out.getStepZ(), 0 },
			top.getY() + 1));
	}

	/** A straight run of high bars or fence along x or z, as a swing pole candidate. */
	private static void pole(ClientLevel level, BlockPos start, List<Candidate> out, Set<Long> done) {
		for (Direction along : new Direction[] { Direction.EAST, Direction.SOUTH }) {
			BlockPos a = start, b = start;
			while (highPole(level, a.relative(along.getOpposite()), level.getBlockState(a.relative(along.getOpposite())))) {
				a = a.relative(along.getOpposite());
			}
			while (highPole(level, b.relative(along), level.getBlockState(b.relative(along)))) {
				b = b.relative(along);
			}
			if (a.equals(b) && along == Direction.EAST) {
				continue;
			}
			for (BlockPos p = a; ; p = p.relative(along)) {
				done.add(p.asLong());
				if (p.equals(b)) {
					break;
				}
			}
			double y = start.getY() + 0.5;
			float[] pa = host(a.getX() + 0.5 - along.getStepX() * 0.5, y, a.getZ() + 0.5 - along.getStepZ() * 0.5);
			float[] pb = host(b.getX() + 0.5 + along.getStepX() * 0.5, y, b.getZ() + 0.5 + along.getStepZ() * 0.5);
			out.add(new Candidate(pa, pb, 0.06f));
			return;
		}
	}

	/** A bottom-half straight stair as a ramp rising towards `facing` (Minecraft frame). */
	private static void ramp(FloatList out, int x, int y, int z, Direction facing) {
		// Corners: low edge on the side away from `facing`, high edge on it.
		double[][] low, high;
		switch (facing) {
			case NORTH -> { low = new double[][] { { x, y, z + 1 }, { x + 1, y, z + 1 } }; high = new double[][] { { x, y + 1, z }, { x + 1, y + 1, z } }; }
			case SOUTH -> { low = new double[][] { { x + 1, y, z }, { x, y, z } }; high = new double[][] { { x + 1, y + 1, z + 1 }, { x, y + 1, z + 1 } }; }
			case WEST -> { low = new double[][] { { x + 1, y, z + 1 }, { x + 1, y, z } }; high = new double[][] { { x, y + 1, z + 1 }, { x, y + 1, z } }; }
			default -> { low = new double[][] { { x, y, z }, { x, y, z + 1 } }; high = new double[][] { { x + 1, y + 1, z }, { x + 1, y + 1, z + 1 } }; }
		}
		double[] hb0 = { high[0][0], y, high[0][2] }, hb1 = { high[1][0], y, high[1][2] };
		// The slope, the tall back, the two side triangles, the bottom.
		tri(out, low[0], low[1], high[1]);
		tri(out, low[0], high[1], high[0]);
		tri(out, hb0, high[0], high[1]);
		tri(out, hb0, high[1], hb1);
		tri(out, low[0], high[0], hb0);
		tri(out, low[1], hb1, high[1]);
		tri(out, low[0], hb0, hb1);
		tri(out, low[0], hb1, low[1]);
	}

	private static void tri(FloatList out, double[] a, double[] b, double[] c) {
		vert(out, a);
		vert(out, b);
		vert(out, c);
	}

	private static float[] host(double x, double y, double z) {
		return new float[] { (float) x, (float) -z, (float) y };
	}

	private static void box(FloatList out, double x0, double y0, double z0, double x1, double y1, double z1) {
		for (Direction d : Direction.values()) {
			face(out, x0, y0, z0, x1, y1, z1, d);
		}
	}

	/** One face of the box (Minecraft frame) as two triangles in the host frame. */
	private static void face(FloatList out, double x0, double y0, double z0, double x1, double y1, double z1, Direction d) {
		double[][] q = switch (d) {
			case DOWN -> new double[][] { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 } };
			case UP -> new double[][] { { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 } };
			case NORTH -> new double[][] { { x0, y0, z0 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y0, z0 } };
			case SOUTH -> new double[][] { { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 } };
			case WEST -> new double[][] { { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 } };
			case EAST -> new double[][] { { x1, y0, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x1, y0, z1 } };
		};
		tri(out, q[0], q[1], q[2]);
		tri(out, q[0], q[2], q[3]);
	}

	private static void vert(FloatList out, double[] v) {
		out.add((float) v[0]);
		out.add((float) -v[2]);
		out.add((float) v[1]);
	}

	/** A growable float array. */
	private static final class FloatList {
		float[] a = new float[1 << 16];
		int n;

		void add(float v) {
			if (n == a.length) {
				a = java.util.Arrays.copyOf(a, a.length * 2);
			}
			a[n++] = v;
		}

		float[] toArray() {
			return java.util.Arrays.copyOf(a, n);
		}
	}
}
