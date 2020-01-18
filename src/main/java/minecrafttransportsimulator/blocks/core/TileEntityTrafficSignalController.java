package minecrafttransportsimulator.blocks.core;

import java.util.ArrayList;
import java.util.List;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import minecrafttransportsimulator.MTS;
import minecrafttransportsimulator.packets.tileentities.PacketTrafficSignalControllerChange;
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
        return "iv_signalcntlr"; // INFO: Max length is 14 chars
    }

    /* Getter */

    @Callback(doc = "function():boolean; Returns if the primary axis is X", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] isPrimaryAxisX(Context context, Arguments args) {
        return new Object[] { orientedOnX };
    }

    @Callback(doc = "function():int; Returns what mode is currently set. (0 - DISABLED, 1 - TIMED, 2 - VEHICLE TRIGGER)", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] getMode(Context context, Arguments args) {
        return new Object[] { mode };
    }

    @Callback(doc = "function():string; Returns the name of what mode it's currently set.", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] getModeName(Context context, Arguments args) {
        return new Object[] { mode == 1 ? "TIMED": mode == 2 ? "VEHICLE_TRIGGER" : "DISABLED" };
    }

    @Callback(doc = "function():boolean; Returns if lights will blink green before switching to yellow", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] willGreenBlink(Context context, Arguments args) {
        return new Object[] { blinkingGreen };
    }

    @Callback(doc = "function():int; Returns how long is main signal in green (in ticks)", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] getGreenMainTime(Context context, Arguments args) {
        return new Object[] { greenMainTime };
    }

    @Callback(doc = "function():int; Returns how long is cross signal in green (in ticks)", direct = true)
    public Object[] getGreenCrossTime(Context context, Arguments args) {
        return new Object[] { greenCrossTime };
    }

    @Callback(doc = "function():int; Returns how long are all signals yellow (in ticks)", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] getYellowTime(Context context, Arguments args) {
        return new Object[] { yellowTime };
    }

    @Callback(doc = "function():int; Returns how long are all signals red (in ticks)", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] getAllRedTime(Context context, Arguments args) {
        return new Object[] { allRedTime };
    }

    /* Setters */

    @Callback(doc = "function(boolean):boolean; This will save all changes to the Traffic Signal Controller. You need to do this when you want to save all changes !", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] confirmChanges(Context context, Arguments args) {
        MTS.MTSNet.sendToServer(new PacketTrafficSignalControllerChange(this));
        return new Object[] { true };
    }

    @Callback(doc = "function(boolean):boolean; Set if the primary axis is X. Returns true on success", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] setPrimaryAxisX(Context context, Arguments args) {
        orientedOnX = args.isBoolean(0) && args.checkBoolean(0);
        return new Object[] { orientedOnX };
    }

    @Callback(doc = "function(int):boolean; Set signal mode. (0 - DISABLED, 1 - TIMED, 2 - VEHICLE TRIGGER)", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] setMode(Context context, Arguments args) {
        return new Object[] { args.isInteger(0) ? mode = args.checkInteger(0) : false };
    }

    @Callback(doc = "function(boolean):boolean; Set if lights should blink green before switching to yellow. Returns true on success and false if mode is DISABLED (0) or VEHICLE_TRIGGER (2)", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] shouldGreenBlink(Context context, Arguments args) {
        blinkingGreen = (args.isBoolean(0) && mode == 1) && args.checkBoolean(0);
        return new Object[] { blinkingGreen };
    }

    @Callback(doc = "function(int):boolean; Set the time main signal is Green. Returns true on success", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] setGreenMainTime(Context context, Arguments args) {
        return new Object[] { args.isInteger(0) ? greenMainTime = args.checkInteger(0) : false };
    }

    @Callback(doc = "function(int):boolean; Set the time cross signal is Green. Returns true on success", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] setGreenCrossTime(Context context, Arguments args) {
        System.out.println("setGreenCrossTime");
        return new Object[] { args.isInteger(0) ? greenCrossTime = args.checkInteger(0) : false };
    }

    @Callback(doc = "function(int):boolean; Set the time all signals are Yellow. Returns true on success", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] setYellowTime(Context context, Arguments args) {
        System.out.println("setYellowTime");
        return new Object[] { args.isInteger(0) ? yellowTime = args.checkInteger(0) : false };
    }

    @Callback(doc = "function(int):boolean; Set the time all signals are Red. Returns true on success", direct = true)
    @Optional.Method(modid = "opencomputers")
    public Object[] setAllRedTime(Context context, Arguments args) {
        System.out.println("setAllRedTime");
        return new Object[] { args.isInteger(0) ? allRedTime = args.checkInteger(0) : false };
    }
}
