package dev.faithrunner;

import java.util.Set;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What her feet and hands touch, for her step sounds (faith_set_surfaces): the Minecraft block
 * as one of Mirror's Edge's surface sets. Her feet are on the floor, or on the wall in a wallrun
 * or wallclimb; her hands on the ladder, pipe or bar she holds, or the wall or ledge in front.
 */
final class FaithSurfaces {
	/** faith.h's surfaces. */
	static final int CONCRETE = 0, WOOD = 1, METAL = 2, GRATING = 3, LADDER = 4, CHAIN_LINK = 5, PIPE = 6, AIRDUCT = 7,
		CARDBOARD = 8, WATER = 9, GLASS = 10;

	/** Her moves with her feet on a wall. */
	private static final Set<String> FEET_ON_WALL = Set.of("Wallrun", "Wallclimb", "Wallclimb (turned)");
	/** Her moves hanging from a pole, line or bar: metal there is a pipe in her hands. */
	private static final Set<String> HANGING = Set.of("Swing", "Swing jump", "Zipline");
	/** Heights (above her feet) her hands reach for: chest, head, a ledge above her. */
	private static final double[] HAND_HEIGHTS = {1.2, 1.7, 2.2};

	private FaithSurfaces() {}

	/** Feet and hands for her now (in `state`, her move's name). */
	static int[] of(LocalPlayer p, String state) {
		Level level = p.level();
		int feet = feet(level, p, state);
		int hands = hands(level, p, state);
		if (hands < 0) {
			hands = feet;
		}
		return new int[] {feet, hands};
	}

	private static int feet(Level level, LocalPlayer p, String state) {
		BlockPos at = p.blockPosition();
		if (FEET_ON_WALL.contains(state)) {
			int wall = around(level, p, state, 0.3);
			if (wall >= 0) {
				return wall;
			}
		}
		// Wading: the water's what she splashes through.
		if (level.getFluidState(at).is(FluidTags.WATER)) {
			return WATER;
		}
		// What she stands in (a slab, carpet, a ladder she's on), else the block under her.
		BlockState in = level.getBlockState(at);
		if (!in.isAir() && !in.getCollisionShape(level, at).isEmpty() || in.is(BlockTags.CLIMBABLE)) {
			return surface(in, state);
		}
		BlockState below = level.getBlockState(at.below());
		if (below.isAir()) {
			// Her feet a hair into the floor: the block below that.
			below = level.getBlockState(at.below(2));
		}
		return surface(below, state);
	}

	/** Her hands: the block she holds (her own block, on a ladder or pipe), else the nearest in front or beside her. -1: nothing. */
	private static int hands(Level level, LocalPlayer p, String state) {
		for (double h : HAND_HEIGHTS) {
			BlockPos at = BlockPos.containing(p.getX(), p.getY() + h, p.getZ());
			BlockState in = level.getBlockState(at);
			if (!in.isAir() && !in.is(Blocks.WATER)) {
				return surface(in, state);
			}
		}
		for (double h : HAND_HEIGHTS) {
			int s = around(level, p, state, h);
			if (s >= 0) {
				return s;
			}
		}
		return -1;
	}

	/** The nearest block at `height` above her feet: ahead, then left, right, behind. -1: none. */
	private static int around(Level level, LocalPlayer p, String state, double height) {
		Direction ahead = p.getDirection();
		Direction[] order = {ahead, ahead.getCounterClockWise(), ahead.getClockWise(), ahead.getOpposite()};
		double reach = p.getBbWidth() * 0.5 + 0.45;
		for (Direction d : order) {
			BlockPos at = BlockPos.containing(p.getX() + d.getStepX() * reach, p.getY() + height, p.getZ() + d.getStepZ() * reach);
			BlockState s = level.getBlockState(at);
			if (!s.isAir() && !s.is(Blocks.WATER)) {
				return surface(s, state);
			}
		}
		return -1;
	}

	/** One of Mirror's Edge's surfaces for a block (`state`: her move's name). */
	static int surface(BlockState s, String state) {
		if (s.is(Blocks.WATER)) {
			return WATER;
		}
		SoundType t = s.getSoundType();
		// Glass first: panes are bars to Minecraft.
		if (t == SoundType.GLASS) {
			return GLASS;
		}
		// Mirror's Edge's ladders are metal; a wooden one's rungs sound much the same.
		if (s.getBlock() instanceof LadderBlock) {
			return LADDER;
		}
		// Drainpipes and ziplines.
		if (s.getBlock() instanceof ChainBlock || s.getBlock() instanceof RodBlock) {
			return PIPE;
		}
		// Bars: a fence to climb over, a pole to swing on.
		if (s.getBlock() instanceof IronBarsBlock) {
			return HANGING.contains(state) ? PIPE : CHAIN_LINK;
		}
		if (t == SoundType.COPPER_GRATE) {
			return AIRDUCT;
		}
		boolean metal = t == SoundType.METAL || t == SoundType.IRON || t == SoundType.COPPER || t == SoundType.COPPER_BULB || t == SoundType.NETHERITE_BLOCK
			|| t == SoundType.ANVIL || t == SoundType.HEAVY_CORE || t == SoundType.LANTERN || t == SoundType.VAULT || t == SoundType.TRIAL_SPAWNER;
		if (metal && s.getBlock() instanceof TrapDoorBlock) {
			return GRATING;
		}
		if (metal) {
			return HANGING.contains(state) ? PIPE : METAL;
		}
		if (s.is(BlockTags.WOOL) || s.is(BlockTags.WOOL_CARPETS) || s.is(Blocks.HAY_BLOCK)) {
			return CARDBOARD;
		}
		if (t == SoundType.WOOD || t == SoundType.CHERRY_WOOD || t == SoundType.BAMBOO_WOOD || t == SoundType.NETHER_WOOD || t == SoundType.SCAFFOLDING
			|| t == SoundType.BAMBOO || t == SoundType.CHISELED_BOOKSHELF || t == SoundType.SHELF || t == SoundType.LADDER) {
			return WOOD;
		}
		if (t == SoundType.WOOL || t == SoundType.MOSS_CARPET || t == SoundType.SPONGE || t == SoundType.WET_SPONGE) {
			return CARDBOARD;
		}
		// Stone, dirt, sand, everything else: concrete, as the game's default.
		return CONCRETE;
	}
}
