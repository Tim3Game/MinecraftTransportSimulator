package minecrafttransportsimulator.vehicles.parts;

import minecrafttransportsimulator.jsondefs.JSONPart;
import minecrafttransportsimulator.jsondefs.JSONVehicle.VehiclePart;
import minecrafttransportsimulator.systems.RotationSystem;
import minecrafttransportsimulator.vehicles.main.EntityVehicleE_Powered;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public abstract class APartGroundEffector extends APart<EntityVehicleE_Powered>{
	protected final BlockPos[] lastBlocksModified;
	protected final BlockPos[] affectedBlocks;
	
	public APartGroundEffector(EntityVehicleE_Powered vehicle, VehiclePart packVehicleDef, JSONPart definition, NBTTagCompound dataTag){
		super(vehicle, packVehicleDef, definition, dataTag);
		lastBlocksModified = new BlockPos[definition.effector.blocksWide];
		affectedBlocks = new BlockPos[definition.effector.blocksWide];
	}
	
	@Override
	public void updatePart(){
		super.updatePart();
		int startingIndex = -definition.effector.blocksWide/2;
		for(int i=0; i<definition.effector.blocksWide; ++i){
			int xOffset = startingIndex + i;
			if(effectIsBelowPart()){
				affectedBlocks[i] = new BlockPos(RotationSystem.getRotatedPoint(new Vec3d(xOffset, 0, 0), vehicle.rotationPitch, vehicle.rotationYaw, vehicle.rotationRoll).add(partPos)).down();
			}else{
				affectedBlocks[i] = new BlockPos(RotationSystem.getRotatedPoint(new Vec3d(xOffset, 0, 0), vehicle.rotationPitch, vehicle.rotationYaw, vehicle.rotationRoll).add(partPos));
			}
		}
		
		for(byte i=0; i<affectedBlocks.length; ++i){
			if(!affectedBlocks[i].equals(lastBlocksModified[i])){
				performEffectsAt(affectedBlocks[i]);
				lastBlocksModified[i] = affectedBlocks[i];
			}
		}
	}
	
	@Override
	public NBTTagCompound getPartNBTTag(){
		return new NBTTagCompound(); 
	}
	
	@Override
	public float getWidth(){
		return 1.0F;
	}

	@Override
	public float getHeight(){
		return 1.0F;
	}
	
	protected abstract void performEffectsAt(BlockPos pos);
	
	protected abstract boolean effectIsBelowPart();
}
