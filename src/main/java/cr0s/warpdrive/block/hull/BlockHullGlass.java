package cr0s.warpdrive.block.hull;

import cr0s.warpdrive.data.Vector3;
import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.util.EnumFacing;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IDamageReceiver;
import cr0s.warpdrive.config.WarpDriveConfig;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nonnull;

public class BlockHullGlass extends BlockColored implements IDamageReceiver {
	private int tier;
	
	public BlockHullGlass(final String registryName, final int tier) {
		super(Material.GLASS);
		this.tier = tier;
		setHardness(WarpDriveConfig.HULL_HARDNESS[tier - 1]);
		setResistance(WarpDriveConfig.HULL_BLAST_RESISTANCE[tier - 1] * 5 / 3);
		setSoundType(SoundType.GLASS);
		setCreativeTab(WarpDrive.creativeTabWarpDrive);
		setUnlocalizedName("warpdrive.hull" + tier + ".glass.");
		setRegistryName(registryName);
		GameRegistry.register(this);
		setLightLevel(10.0F / 15.0F);
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public EnumPushReaction getMobilityFlag(IBlockState state) {
		return EnumPushReaction.BLOCK;
	}
	
	@Override
	public boolean isVisuallyOpaque() {
		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean isFullyOpaque(IBlockState state) {
		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean shouldSideBeRendered(IBlockState blockState, @Nonnull IBlockAccess blockAccess, @Nonnull BlockPos blockPos, EnumFacing side) {
		if (blockAccess.isAirBlock(blockPos)) {
			return true;
		}
		EnumFacing direction = side.getOpposite();
		IBlockState blockStateSide = blockAccess.getBlockState(blockPos);
		if (blockStateSide.getBlock() instanceof BlockGlass || blockStateSide.getBlock() instanceof BlockHullGlass) {
			return blockState.getBlock().getMetaFromState(blockState)
				!= blockStateSide.getBlock().getMetaFromState(blockStateSide);
		}
		return !blockAccess.isSideSolid(blockPos, direction, false);
	}
	
	@Override
	public float getBlockHardness(World world, BlockPos blockPos, DamageSource damageSource, int damageParameter, Vector3 damageDirection, int damageLevel) {
		// TODO: adjust hardness to damage type/color
		return WarpDriveConfig.HULL_HARDNESS[tier - 1];
	}

	@Override
	public int applyDamage(World world, BlockPos blockPos, DamageSource damageSource, int damageParameter, Vector3 damageDirection, int damageLevel) {
		if (damageLevel <= 0) {
			return 0;
		}
		if (tier == 1) {
			world.setBlockToAir(blockPos);
		} else {
			IBlockState blockState = world.getBlockState(blockPos);
			int metadata = blockState.getBlock().getMetaFromState(blockState);
			world.setBlockState(blockPos, WarpDrive.blockHulls_glass[tier - 2].getStateFromMeta(metadata), 2);
		}
		return 0;
	}
}
