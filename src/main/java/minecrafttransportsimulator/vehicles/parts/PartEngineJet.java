package minecrafttransportsimulator.vehicles.parts;

import java.util.List;

import minecrafttransportsimulator.dataclasses.DamageSources.DamageSourceJet;
import minecrafttransportsimulator.jsondefs.JSONPart;
import minecrafttransportsimulator.jsondefs.JSONVehicle.VehiclePart;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.vehicles.main.EntityVehicleG_Plane;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

public class PartEngineJet extends APartEngine<EntityVehicleG_Plane>{

	public PartEngineJet(EntityVehicleG_Plane vehicle, VehiclePart packVehicleDef, JSONPart definition, NBTTagCompound dataTag){
		super(vehicle, packVehicleDef, definition, dataTag);
	}
	
	@Override
	public void updatePart(){
		super.updatePart();
		if(state.running){
			double engineTargetRPM = vehicle.throttle/100F*(definition.engine.maxRPM - engineStartRPM*1.25 - hours*10) + engineStartRPM*1.25;
			double engineRPMDifference = engineTargetRPM - RPM;
			//This is governed by the core, so use the bypass ratio and air density to calculate how fast this thing spools up.
			//Smaller cores and higher altitudes will cause spool times to increase due to lack of airflow to push.
			RPM += definition.engine.fuelConsumption*engineRPMDifference/(10 + definition.engine.gearRatios[0])/vehicle.airDensity;
		}else if(!state.esOn){
			RPM = Math.max(RPM + (vehicle.velocity - 0.0254*250*RPM/60/20)*15 - 10, 0);
		}
		
		if(!vehicle.world.isRemote){
			if(RPM >= 5000){
				//Check for entities in front of the jet, and damage them if they are.
				List<EntityLivingBase> collidedEntites = vehicle.world.getEntitiesWithinAABB(EntityLivingBase.class, this.getAABBWithOffset(vehicle.headingVec).expand(-0.25F, -0.25F, -0.25F));
				if(!collidedEntites.isEmpty()){
					Entity attacker = null;
					for(Entity passenger : vehicle.getPassengers()){
						if(vehicle.getSeatForRider(passenger).isController){
							attacker = passenger;
							break;
						}
					}
					for(int i=0; i < collidedEntites.size(); ++i){
						if(!vehicle.equals(collidedEntites.get(i).getRidingEntity())){
							collidedEntites.get(i).attackEntityFrom(new DamageSourceJet(attacker, true), (float) (ConfigSystem.configObject.damage.jetDamageFactor.value*RPM/1000F));
						}
					}
				}
				
				//Check for entities behind the jet, and damage them with fire if they are.
				collidedEntites = vehicle.world.getEntitiesWithinAABB(EntityLivingBase.class, this.getAABBWithOffset(vehicle.headingVec.scale(-1.0D)).expand(0.25F, 0.25F, 0.25F));
				if(!collidedEntites.isEmpty()){
					Entity attacker = null;
					for(Entity passenger : vehicle.getPassengers()){
						if(vehicle.getSeatForRider(passenger).isController){
							attacker = passenger;
							break;
						}
					}
					for(int i=0; i < collidedEntites.size(); ++i){
						if(!vehicle.equals(collidedEntites.get(i).getRidingEntity())){
							collidedEntites.get(i).attackEntityFrom(new DamageSourceJet(attacker, false), (float) (ConfigSystem.configObject.damage.jetDamageFactor.value*RPM/2000F));
							collidedEntites.get(i).setFire(5);
						}
					}
				}
			}
		}
		
		engineRotationLast = engineRotation;
		engineRotation += RPM*1200D/360D;
		engineDriveshaftRotationLast = engineDriveshaftRotation;
		engineDriveshaftRotation += RPM*1200D/360D;
	}
	
	@Override
	public double getForceOutput(){
		//Propellers max out at about 25 force, so use that to determine this force.
		if(state.running){
			//First we need the air density (sea level 1.225) so we know how much air we are moving.
			//We then multiply that by the RPM and the fuel consumption to get the raw power produced
			//by the core of the engine.  This is speed-independent as the core will ALWAYS accelerate air.
			//Note that due to a lack of jet physics formulas available, this is "hacky math".
			double safeRPMFactor = RPM/getSafeRPMFromMax(definition.engine.maxRPM);
			double coreContribution = Math.max(10*vehicle.airDensity*definition.engine.fuelConsumption*safeRPMFactor - definition.engine.gearRatios[0], 0);
			//The fan portion is calculated similarly to how propellers are calculated.
			//This takes into account the air density, and relative speed of the engine versus the fan's desired speed.
			//Again, this is "hacky math", as for some reason there's no data on fan pitches.
			//In this case, however, we don't care about the fuelConsumption as that's only used by the core.
			double fanVelocityFactor = (0.0254*250*RPM/60/20 - vehicle.velocity)/200D;
			double fanContribution = 10*vehicle.airDensity*safeRPMFactor*fanVelocityFactor*definition.engine.gearRatios[0];
			return vehicle.reverseThrust ? -(coreContribution + fanContribution) : coreContribution + fanContribution;
		}else{
			return 0;
		}
	}
}
