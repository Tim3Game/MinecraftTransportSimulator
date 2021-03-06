package minecrafttransportsimulator.vehicles.parts;

import minecrafttransportsimulator.jsondefs.JSONPart;
import minecrafttransportsimulator.jsondefs.JSONVehicle.VehiclePart;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Air;
import minecrafttransportsimulator.vehicles.main.EntityVehicleG_Blimp;
import net.minecraft.nbt.NBTTagCompound;

public class PartEngineAircraft extends APartEngine<EntityVehicleF_Air>{
	public PartPropeller propeller;

	public PartEngineAircraft(EntityVehicleF_Air vehicle, VehiclePart packVehicleDef, JSONPart definition, NBTTagCompound dataTag){
		super(vehicle, packVehicleDef, definition, dataTag);
	}
	
	@Override
	public void updatePart(){
		super.updatePart();
		if(propeller == null){
			if(!childParts.isEmpty()){
				propeller = (PartPropeller) childParts.get(0);
			}
		}else{
			if(childParts.isEmpty()){
				propeller = null;
			}
		}
		if(state.running){
			double engineTargetRPM = vehicle.throttle/100F*(definition.engine.maxRPM - engineStartRPM*1.25 - hours) + engineStartRPM*1.25;
			double engineRPMDifference = engineTargetRPM - RPM;
			if(propeller != null){
				double propellerForcePenalty = (propeller.definition.propeller.diameter - 75)/(50*this.definition.engine.fuelConsumption - 15);
				double propellerFeedback = (0.0254*Math.abs(propeller.currentPitch)*RPM*definition.engine.gearRatios[0]/60/20 - vehicle.velocity + propellerForcePenalty)*50;
				//PropellerFeedback can't make an engine stall, but hours can.
				if(RPM + engineRPMDifference/10 > engineStallRPM && RPM + engineRPMDifference/10 - propellerFeedback < engineStallRPM){
					RPM = engineStallRPM;
				}else{
					RPM += engineRPMDifference/10 - propellerFeedback;	
				}				
			}else{
				RPM += engineRPMDifference/10;
			}
		}else{
			if(propeller != null){
				RPM = Math.max(RPM + (vehicle.velocity - 0.0254*Math.abs(propeller.currentPitch)*RPM*definition.engine.gearRatios[0]/60/20)*15 - 10, 0);
			}else{
				RPM = Math.max(RPM - 10, 0);
			}
		}
		
		engineRotationLast = engineRotation;
		engineRotation += 360D*RPM/1200D;
		engineDriveshaftRotationLast = engineDriveshaftRotation;
		engineDriveshaftRotation += 360D*RPM/1200D*definition.engine.gearRatios[0];
	}
	
	@Override
	public double getForceOutput(){
		if(propeller != null && Math.abs(propeller.currentPitch) > 20 && state.running){
			//Get what the pitch velocity of the propeller would be at the current velocity.
			double currentPitchVelocity = vehicle.velocity*20D;
			//Get the effective pitch velocity of the propeller at the current RPM.
			double effectivePitchVelocity = 0.0254D*propeller.currentPitch*RPM*definition.engine.gearRatios[0]/60D;
			//Multiply by a factor to get the true effective pitch velocity.  This is slightly higher than ideal.
			effectivePitchVelocity *= (1D*propeller.currentPitch/propeller.definition.propeller.diameter + 0.2D)/(1D*propeller.currentPitch/propeller.definition.propeller.diameter);
			if(effectivePitchVelocity != 0){
				//Get the angle of attack of the propeller.
				double angleOfAttack = Math.abs(effectivePitchVelocity - currentPitchVelocity);
				double thrust = vehicle.airDensity*Math.PI*Math.pow(0.0254*propeller.definition.propeller.diameter/2D, 2)*
						(effectivePitchVelocity*effectivePitchVelocity - effectivePitchVelocity*currentPitchVelocity)*
						Math.pow(propeller.definition.propeller.diameter/2D/Math.abs(propeller.currentPitch) + propeller.definition.propeller.numberBlades/1000D, 1.5)/400D;
				//If the angle of attack is greater than 35, sap power off the propeller for stalling.
				if(angleOfAttack > 35){
					thrust *= 35/angleOfAttack;
				}
				//Get the correct sign of the force, taking engine systems into account.
				if(vehicle instanceof EntityVehicleG_Blimp && vehicle.reverseThrust){
					thrust *= -Math.signum(effectivePitchVelocity);
				}else{
					thrust *= Math.signum(effectivePitchVelocity);
				}
				return thrust;
			}
		}
		return 0;
	}
}
