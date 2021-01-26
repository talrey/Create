package com.simibubi.create.content.contraptions.components.structureMovement.bearing;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.ProperDirectionalBlock;
import com.simibubi.create.foundation.utility.DyeHelper;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.VecHelper;
import com.simibubi.create.foundation.utility.placement.IPlacementHelper;
import com.simibubi.create.foundation.utility.placement.PlacementHelpers;
import com.simibubi.create.foundation.utility.placement.PlacementOffset;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class SailBlock extends ProperDirectionalBlock {

	public static SailBlock frame(Properties properties) {
		return new SailBlock(properties, true);
	}

	public static SailBlock withCanvas(Properties properties) {
		return new SailBlock(properties, false);
	}

	private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

	private final boolean frame;

	protected SailBlock(Properties p_i48415_1_, boolean frame) {
		super(p_i48415_1_);
		this.frame = frame;
	}

	@Override
	public BlockState getStateForPlacement(BlockItemUseContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state.with(FACING, state.get(FACING).getOpposite());
	}

	@Override
	public ActionResultType onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult ray) {
		ItemStack heldItem = player.getHeldItem(hand);

		if (AllBlocks.SAIL.isIn(heldItem) || AllBlocks.SAIL_FRAME.isIn(heldItem)) {
			IPlacementHelper placementHelper = PlacementHelpers.get(placementHelperId);
			PlacementOffset offset = placementHelper.getOffset(world, state, pos, ray);

			if (!offset.isReplaceable(world))
				return ActionResultType.PASS;

			offset.placeInWorld(world, ((BlockItem) heldItem.getItem()).getBlock().getDefaultState(), player, heldItem);

			/*BlockState blockState = ((BlockItem) heldItem.getItem()).getBlock()
					.getDefaultState()
					.with(FACING, state.get(FACING));
			BlockPos offsetPos = new BlockPos(offset.getPos());
			if (!world.isRemote && world.getBlockState(offsetPos).getMaterial().isReplaceable()) {
				world.setBlockState(offsetPos, blockState);
				if (!player.isCreative())
					heldItem.shrink(1);
			}*/

			return ActionResultType.SUCCESS;
		}

		if (heldItem.getItem() instanceof ShearsItem) {
			if (!world.isRemote)
				applyDye(state, world, pos, null);
			return ActionResultType.SUCCESS;
		}

		if (frame)
			return ActionResultType.PASS;

		for (DyeColor color : DyeColor.values()) {
			if (!heldItem.getItem()
					.isIn(DyeHelper.getTagOfDye(color)))
				continue;
			if (!world.isRemote)
				applyDye(state, world, pos, color);
			return ActionResultType.SUCCESS;
		}

		return ActionResultType.PASS;
	}

	protected void applyDye(BlockState state, World world, BlockPos pos, @Nullable DyeColor color) {
		BlockState newState =
				(color == null ? AllBlocks.SAIL_FRAME : AllBlocks.DYED_SAILS[color.ordinal()]).getDefaultState()
						.with(FACING, state.get(FACING));

		// Dye the block itself
		if (state != newState) {
			world.setBlockState(pos, newState);
			return;
		}

		// Dye all adjacent
		for (Direction d : Iterate.directions) {
			if (d.getAxis() == state.get(FACING)
					.getAxis())
				continue;
			BlockPos offset = pos.offset(d);
			BlockState adjacentState = world.getBlockState(offset);
			Block block = adjacentState.getBlock();
			if (!(block instanceof SailBlock) || ((SailBlock) block).frame)
				continue;
			if (state == adjacentState)
				continue;
			world.setBlockState(offset, newState);
			return;
		}

		// Dye all the things
		List<BlockPos> frontier = new ArrayList<>();
		frontier.add(pos);
		Set<BlockPos> visited = new HashSet<>();
		int timeout = 100;
		while (!frontier.isEmpty()) {
			if (timeout-- < 0)
				break;

			BlockPos currentPos = frontier.remove(0);
			visited.add(currentPos);

			for (Direction d : Iterate.directions) {
				if (d.getAxis() == state.get(FACING)
						.getAxis())
					continue;
				BlockPos offset = currentPos.offset(d);
				if (visited.contains(offset))
					continue;
				BlockState adjacentState = world.getBlockState(offset);
				Block block = adjacentState.getBlock();
				if (!(block instanceof SailBlock) || ((SailBlock) block).frame && color != null)
					continue;
				if (state != adjacentState)
					world.setBlockState(offset, newState);
				frontier.add(offset);
				visited.add(offset);
			}
		}
	}

	@Override
	public VoxelShape getShape(BlockState state, IBlockReader p_220053_2_, BlockPos p_220053_3_, ISelectionContext p_220053_4_) {
		return (frame ? AllShapes.SAIL_FRAME : AllShapes.SAIL).get(state.get(FACING));
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, IBlockReader p_220071_2_, BlockPos p_220071_3_, ISelectionContext p_220071_4_) {
		if (frame)
			return AllShapes.SAIL_FRAME_COLLISION.get(state.get(FACING));
		return getShape(state, p_220071_2_, p_220071_3_, p_220071_4_);
	}

	@Override
	public ItemStack getPickBlock(BlockState state, RayTraceResult target, IBlockReader world, BlockPos pos, PlayerEntity player) {
		ItemStack pickBlock = super.getPickBlock(state, target, world, pos, player);
		if (pickBlock.isEmpty())
			return AllBlocks.SAIL.get()
					.getPickBlock(state, target, world, pos, player);
		return pickBlock;
	}

	public void onFallenUpon(World p_180658_1_, BlockPos p_180658_2_, Entity p_180658_3_, float p_180658_4_) {
		if (frame)
			super.onFallenUpon(p_180658_1_, p_180658_2_, p_180658_3_, p_180658_4_);
		super.onFallenUpon(p_180658_1_, p_180658_2_, p_180658_3_, 0);
	}

	public void onLanded(IBlockReader p_176216_1_, Entity p_176216_2_) {
		if (frame || p_176216_2_.bypassesLandingEffects()) {
			super.onLanded(p_176216_1_, p_176216_2_);
		} else {
			this.bounce(p_176216_2_);
		}
	}

	private void bounce(Entity p_226860_1_) {
		Vec3d vec3d = p_226860_1_.getMotion();
		if (vec3d.y < 0.0D) {
			double d0 = p_226860_1_ instanceof LivingEntity ? 1.0D : 0.8D;
			p_226860_1_.setMotion(vec3d.x, -vec3d.y * (double) 0.26F * d0, vec3d.z);
		}

	}

	@MethodsReturnNonnullByDefault
	private static class PlacementHelper implements IPlacementHelper {
		@Override
		public Predicate<ItemStack> getItemPredicate() {
			return i -> AllBlocks.SAIL.isIn(i) || AllBlocks.SAIL_FRAME.isIn(i);
		}

		@Override
		public Predicate<BlockState> getStatePredicate() {
			return s -> s.getBlock() instanceof SailBlock;
		}

		@Override
		public PlacementOffset getOffset(World world, BlockState state, BlockPos pos, BlockRayTraceResult ray) {
			List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(pos, ray.getHitVec(), state.get(SailBlock.FACING).getAxis(), dir -> world.getBlockState(pos.offset(dir)).getMaterial().isReplaceable());

			if (directions.isEmpty())
				return PlacementOffset.fail();
			else {
				return PlacementOffset.success(pos.offset(directions.get(0)), s -> s.with(FACING, state.get(FACING)));
			}
		}

		@Override
		public void renderAt(BlockPos pos, BlockState state, BlockRayTraceResult ray, PlacementOffset offset) {
			IPlacementHelper.renderArrow(VecHelper.getCenterOf(pos), VecHelper.getCenterOf(offset.getPos()), state.get(FACING));
		}
	}
}
