package minecrafttransportsimulator.blocks.core;

import java.util.ArrayList;
import java.util.List;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Optional;

@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "opencomputers")
public class TileEntityTrafficSignalController extends TileEntityBase implements SimpleComponent {
	public boolean orientedOnX;
    // 0 - Disabled lights, 1 - Time Delay, 2 - Vehicle Trigger
	public int mode;
    // If the green light should blink before changing to yellow
    public boolean blinkingGreen;
	public int greenMainTime;
	public int greenCrossTime;
	public int yellowTime;
	public int allRedTime;
	public final List<BlockPos> trafficSignalLocations = new ArrayList<BlockPos>();
	
	public byte operationIndex;
	public long timeOperationStarted;	
	
	@Override
    public void readFromNBT(NBTTagCompound tagCompound){
        super.readFromNBT(tagCompound);
        this.orientedOnX = tagCompound.getBoolean("orientedOnX");
        this.mode = tagCompound.getInteger("mode");
        this.blinkingGreen = tagCompound.getBoolean("blinkingGreen");
        this.greenMainTime = tagCompound.getInteger("greenMainTime");
        this.greenCrossTime = tagCompound.getInteger("greenCrossTime");
        this.yellowTime = tagCompound.getInteger("yellowTime");
        this.allRedTime = tagCompound.getInteger("allRedTime");
        
        trafficSignalLocations.clear();
        for(byte i=0; i<tagCompound.getInteger("trafficSignalCount"); ++i){
        	int[] posArray = tagCompound.getIntArray("trafficSignalLocation" + i);
        	trafficSignalLocations.add(new BlockPos(posArray[0], posArray[1], posArray[2]));
        }
    }
    
	@Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound){
        super.writeToNBT(tagCompound);
        tagCompound.setBoolean("orientedOnX", this.orientedOnX);
        tagCompound.setInteger("mode", this.mode);
        tagCompound.setBoolean("blinkingGreen", this.blinkingGreen);
        tagCompound.setInteger("greenMainTime", this.greenMainTime);
        tagCompound.setInteger("greenCrossTime", this.greenCrossTime);
        tagCompound.setInteger("yellowTime", this.yellowTime);
        tagCompound.setInteger("allRedTime", this.allRedTime);
        
        //Save all pos data to NBT.
        for(byte i=0; i<trafficSignalLocations.size(); ++i){
        	BlockPos trafficSignalPos = trafficSignalLocations.get(i);
	    	tagCompound.setIntArray("trafficSignalLocation" + i, new int[]{trafficSignalPos.getX(), trafficSignalPos.getY(), trafficSignalPos.getZ()});
        }
        tagCompound.setInteger("trafficSignalCount", trafficSignalLocations.size());
        return tagCompound;
    }

    @Override
    public void validate() {
        super.validate();
    }

    @Override
    public String getComponentName() {
        return "tsc";
    }

    /* Getter */

    @Callback//(doc = "function():boolean; Returns if the primary axis is X", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] isPrimaryAxisX(Context context, Arguments args) {
        return new Object[] { orientedOnX };
    }

    @Callback//(doc = "function():int; Returns in what mode it currently is", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] getMode(Context context, Arguments args) {
        return new Object[] { mode };
    }

    @Callback//(doc = "function():boolean; Returns if lights will blink green before switching to yellow", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] willGreenBlink(Context context, Arguments args) {
        return new Object[] { blinkingGreen };
    }

    @Callback
    @Optional.Method(modid = "opencomputers")
    public Object[] getGreenMainTime(Context context, Arguments args) {
        return new Object[] { greenMainTime };
    }

    @Callback
    @Optional.Method(modid = "opencomputers")
    public Object[] getGreenCrossTime(Context context, Arguments args) {
        return new Object[] { greenCrossTime };
    }

    @Callback
    @Optional.Method(modid = "opencomputers")
    public Object[] getYellowTime(Context context, Arguments args) {
        return new Object[] { yellowTime };
    }

    @Callback
    @Optional.Method(modid = "opencomputers")
    public Object[] getAllRedTime(Context context, Arguments args) {
        return new Object[] { allRedTime };
    }

    /* Setters */

    @Callback//(doc = "function(boolean):boolean; Set if the primary axis is X. Returns true on success", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] setPrimaryAxisX(Context context, Arguments args) {
        return new Object[] { args.isBoolean(0) ? orientedOnX = args.checkBoolean(0) : false };
    }

    @Callback//(doc = "function(int):boolean; Set in what mode it currently will be", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] setMode(Context context, Arguments args) {
        return new Object[] { args.isInteger(0) ? mode = args.checkInteger(0) : false };
    }

    @Callback//(doc = "function(boolean):boolean; Set if lights will blink green before switching to yellow. Returns true on success", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] shouldGreenBlink(Context context, Arguments args) {
        return new Object[] { args.isBoolean(0) ? blinkingGreen = args.checkBoolean(0) : false };
    }

    @Callback
    @Optional.Method(modid = "opencomputers")
    public Object[] setGreenMainTime(Context context, Arguments args) {
        return new Object[] { args.isInteger(0) ? greenMainTime = args.checkInteger(0) : false };
    }

    @Callback
    @Optional.Method(modid = "opencomputers")
    public Object[] setGreenCrossTime(Context context, Arguments args) {
        return new Object[] { args.isInteger(0) ? greenCrossTime = args.checkInteger(0) : false };
    }

    @Callback
    @Optional.Method(modid = "opencomputers")
    public Object[] setYellowTime(Context context, Arguments args) {
        return new Object[] { args.isInteger(0) ? yellowTime = args.checkInteger(0) : false };
    }

    @Callback
    @Optional.Method(modid = "opencomputers")
    public Object[] setAllRedTime(Context context, Arguments args) {
        return new Object[] { args.isInteger(0) ? allRedTime = args.checkInteger(0) : false };
    }
}
