package cr0s.warpdrive.block.movement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.common.Optional;
import cr0s.warpdrive.block.TileEntityAbstractEnergy;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.network.PacketHandler;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;

public class TileEntityLift extends TileEntityAbstractEnergy {
	private interface CCMethod {
		Object[] execute();
	}

	private static enum Mode {
		REDSTONE(-1),
		INACTIVE(0),
		UP(1),
		DOWN(2);

		private int code;
		private Mode(int code) {
			this.code = code;
		}
		public int getCode() {
			return code;
		}
	}
	
	private int firstUncoveredY;
	private Mode mode = Mode.INACTIVE;
	private boolean isEnabled = false;
	private boolean computerEnabled = true;
	private Mode computerMode = Mode.REDSTONE;
	
	private int tickCount = 0;

	private final Map<String, CCMethod> methodMap = new HashMap<String, CCMethod>();
	
	public TileEntityLift() {
		super();
		IC2_sinkTier = 2;
		IC2_sourceTier = 2;
		peripheralName = "warpdriveLift";
		initMethodMap();
		addMethods(methodMap.keySet().toArray(new String[7]));
	}

	private void initMethodMap() {
		CCMethod func_up = new CCMethod() { public Object[] execute() { return mode(Mode.UP); }};
		CCMethod func_down = new CCMethod() { public Object[] execute() { return mode(Mode.DOWN); }};
		CCMethod func_redstone = new CCMethod() { public Object[] execute() { return mode(Mode.REDSTONE); }};
		CCMethod func_getMode = new CCMethod() { 
			public Object[] execute() { 
				return new Object[] {computerMode.toString()}; 
			}
		};
		CCMethod func_enable = new CCMethod() { 
			public Object[] execute() { 
				computerEnabled = true; 
				return new Object[] { !computerEnabled && isEnabled };
			}
		};
		CCMethod func_disable = new CCMethod() { 
			public Object[] execute() { 
				computerEnabled = false; 
				return new Object[] { !computerEnabled && isEnabled };
			}
		};
		CCMethod func_isEnabled = new CCMethod() { 
			public Object[] execute() { 
				return new Object[] { isEnabled };
			}
		};

		methodMap.put("up", func_up);
		methodMap.put("down", func_down);
		methodMap.put("redstone", func_redstone);
		methodMap.put("getMode", func_getMode);
		methodMap.put("enable", func_enable);
		methodMap.put("disable", func_disable);
		methodMap.put("isEnabled", func_isEnabled);
	}
	
	@Override
	public void updateEntity() {
		super.updateEntity();
		
		if (worldObj.isRemote) {
			return;
		}
		
		tickCount++;
		if (tickCount >= WarpDriveConfig.LIFT_UPDATE_INTERVAL_TICKS) {
			tickCount = 0;
			
			// Switching mode
			if (  computerMode == Mode.DOWN
			  || (computerMode == Mode.REDSTONE && worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord))) {
				mode = Mode.DOWN;
			} else {
				mode = Mode.UP;
			}
			
			isEnabled = computerEnabled
				     && isPassableBlock(yCoord + 1)
				     && isPassableBlock(yCoord + 2)
				     && isPassableBlock(yCoord - 1)
				     && isPassableBlock(yCoord - 2);
			
			if (energy_getEnergyStored() < WarpDriveConfig.LIFT_ENERGY_PER_ENTITY || !isEnabled) {
				mode = Mode.INACTIVE;
				if (getBlockMetadata() != 0) {
					worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 0, 2); // disabled
				}
				return;
			}
			
			if (getBlockMetadata() != mode.getCode()) {
				worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, mode.getCode(), 2); // current mode
			}
			
			// Launch a beam: search non-air blocks under lift
			for (int ny = yCoord - 2; ny > 0; ny--) {
				if (!isPassableBlock(ny)) {
					firstUncoveredY = ny + 1;
					break;
				}
			}
			
			if (yCoord - firstUncoveredY >= 2) {
				if (mode == Mode.UP) {
					PacketHandler.sendBeamPacket(worldObj,
							new Vector3(xCoord + 0.5D, firstUncoveredY, zCoord + 0.5D),
							new Vector3(xCoord + 0.5D, yCoord, zCoord + 0.5D),
							0f, 1f, 0f, 40, 0, 100);
				} else if (mode == Mode.DOWN) {
					PacketHandler.sendBeamPacket(worldObj,
							new Vector3(xCoord + 0.5D, yCoord, zCoord + 0.5D),
							new Vector3(xCoord + 0.5D, firstUncoveredY, zCoord + 0.5D), 0f,
							0f, 1f, 40, 0, 100);
				}
				
				liftEntity();
			}
		}
	}
	
	private boolean isPassableBlock(int yPosition) {
		Block block = worldObj.getBlock(xCoord, yPosition, zCoord);
		return block.isAssociatedBlock(Blocks.air)
			|| worldObj.isAirBlock(xCoord, yPosition, zCoord)
			|| block.getCollisionBoundingBoxFromPool(worldObj, xCoord, yPosition, zCoord) == null;
	}
	
	private void liftEntity() {
		final double CUBE_RADIUS = 0.4;
		double xMax, zMax;
		double xMin, zMin;
		
		xMin = xCoord + 0.5 - CUBE_RADIUS;
		xMax = xCoord + 0.5 + CUBE_RADIUS;
		zMin = zCoord + 0.5 - CUBE_RADIUS;
		zMax = zCoord + 0.5 + CUBE_RADIUS;
		
		// Lift up
		if (mode == Mode.UP) {
			AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(xMin, firstUncoveredY, zMin, xMax, yCoord, zMax);
			List list = worldObj.getEntitiesWithinAABBExcludingEntity(null, aabb);
			if (list != null) {
				for (Object o : list) {
					if ( o != null
					  && o instanceof EntityLivingBase
					  && energy_consume(WarpDriveConfig.LIFT_ENERGY_PER_ENTITY, true)) {
						((EntityLivingBase) o).setPositionAndUpdate(xCoord + 0.5D, yCoord + 1.0D, zCoord + 0.5D);
						PacketHandler.sendBeamPacket(worldObj,
								new Vector3(xCoord + 0.5D, firstUncoveredY, zCoord + 0.5D),
								new Vector3(xCoord + 0.5D, yCoord, zCoord + 0.5D),
								1F, 1F, 0F, 40, 0, 100);
						worldObj.playSoundEffect(xCoord + 0.5D, yCoord, zCoord + 0.5D, "warpdrive:hilaser", 4F, 1F);
						energy_consume(WarpDriveConfig.LIFT_ENERGY_PER_ENTITY, false);
					}
				}
			}
		} else if (mode == Mode.DOWN) {
			AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(xMin,
					Math.min(firstUncoveredY + 4.0D, yCoord), zMin, xMax, yCoord + 2.0D, zMax);
			List list = worldObj.getEntitiesWithinAABBExcludingEntity(null, aabb);
			if (list != null) {
				for (Object o : list) {
					if ( o != null
					  && o instanceof EntityLivingBase
					  && energy_consume(WarpDriveConfig.LIFT_ENERGY_PER_ENTITY, true)) {
						((EntityLivingBase) o).setPositionAndUpdate(xCoord + 0.5D, firstUncoveredY, zCoord + 0.5D);
						PacketHandler.sendBeamPacket(worldObj,
								new Vector3(xCoord + 0.5D, yCoord, zCoord + 0.5D),
								new Vector3(xCoord + 0.5D, firstUncoveredY, zCoord + 0.5D), 1F, 1F, 0F, 40, 0, 100);
						worldObj.playSoundEffect(xCoord + 0.5D, yCoord, zCoord + 0.5D, "warpdrive:hilaser", 4F, 1F);
						energy_consume(WarpDriveConfig.LIFT_ENERGY_PER_ENTITY, false);
					}
				}
			}
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		if (tag.hasKey("mode")) {
			mode = Mode.valueOf(tag.getString("mode"));
		}
		if (tag.hasKey("computerEnabled")) {
			computerEnabled = tag.getBoolean("computerEnabled");
		}
		if (tag.hasKey("computerMode")) {
			computerMode = Mode.valueOf(tag.getString("computerMode"));
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		tag.setString("mode", mode.name());
		tag.setBoolean("computerEnabled", computerEnabled);
		tag.setString("computerMode", computerMode.name());
	}
	
	@Override
	public int energy_getMaxStorage() {
		return WarpDriveConfig.LIFT_MAX_ENERGY_STORED;
	}
	
	@Override
	public boolean energy_canInput(ForgeDirection from) {
		return true;
	}
	
	// OpenComputer callback methods
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] mode(Context context, Arguments arguments) {
		switch (arguments.checkString(0)) {
			case "up":
				return mode(Mode.UP);
			case "down":
				return mode(Mode.DOWN);
			default:
				return mode(Mode.REDSTONE);
		}
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] active(Context context, Arguments arguments) {
		if (arguments.count() == 1) {
			computerEnabled = arguments.checkBoolean(0);
			markDirty();
		}
		return new Object[] { !computerEnabled && isEnabled };
	}
	
	private Object[] mode(Mode mode) {
		computerMode = mode;
		markDirty();
		
		switch (computerMode) {
		case REDSTONE:
			return new Object[] { "redstone" };
		case UP:
			return new Object[] { "up" };
		case DOWN:
			return new Object[] { "down" };
		default:
			break;
		}
		return null;
	}
	
	// ComputerCraft IPeripheral methods implementation
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) {
		String methodName = getMethodName(method);
		CCMethod ccMethod = methodMap.get(methodName);
		if (ccMethod != null) {
			return ccMethod.execute();
		}
		else {
			return super.callMethod(computer, context, method, arguments);
		}
	}
}
