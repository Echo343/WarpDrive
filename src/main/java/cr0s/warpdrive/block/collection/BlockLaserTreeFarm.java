package cr0s.warpdrive.block.collection;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.block.BlockAbstractContainer;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BlockLaserTreeFarm extends BlockAbstractContainer {
	public final static int ICON_IDLE = 0;
	public final static int ICON_FARMING_LOW_POWER = 1;
	public final static int ICON_FARMING_POWERED = 2;
	public final static int ICON_SCANNING_LOW_POWER = 3;
	public final static int ICON_SCANNING_POWERED = 4;
	public final static int ICON_PLANTING_LOW_POWER = 5;
	public final static int ICON_PLANTING_POWERED = 6;
	private final static int ICON_BOTTOM = 7;
	private final static int ICON_TOP = 8;
	
	public BlockLaserTreeFarm() {
		super(Material.IRON);
		setRegistryName("warpdrive.collection.LaserTreeFarm");
		GameRegistry.register(this);
	}

	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull World world, int metadata) {
		return new TileEntityLaserTreeFarm();
	}
	
	@Override
	public int quantityDropped(Random random) {
		return 1;
	}
	
	@Override
	public boolean onBlockActivated(World world, BlockPos blockPos, IBlockState blockState, EntityPlayer entityPlayer, EnumHand hand, @Nullable ItemStack itemStackHeld, EnumFacing side, float hitX, float hitY, float hitZ) {
		if (world.isRemote) {
			return false;
		}
		
		if (itemStackHeld == null) {
			TileEntity tileEntity = world.getTileEntity(blockPos);
			if (tileEntity instanceof TileEntityLaserTreeFarm) {
				WarpDrive.addChatMessage(entityPlayer, ((TileEntityLaserTreeFarm)tileEntity).getStatus());
				return true;
			}
		}
		
		return false;
	}
}