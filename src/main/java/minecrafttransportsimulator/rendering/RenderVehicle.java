package minecrafttransportsimulator.rendering;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import minecrafttransportsimulator.MTS;
import minecrafttransportsimulator.baseclasses.VehicleAxisAlignedBB;
import minecrafttransportsimulator.dataclasses.MTSControls.Controls;
import minecrafttransportsimulator.dataclasses.PackPartObject;
import minecrafttransportsimulator.dataclasses.PackVehicleObject.PackControl;
import minecrafttransportsimulator.dataclasses.PackVehicleObject.PackDisplayText;
import minecrafttransportsimulator.dataclasses.PackVehicleObject.PackInstrument;
import minecrafttransportsimulator.dataclasses.PackVehicleObject.PackPart;
import minecrafttransportsimulator.dataclasses.PackVehicleObject.PackRotatableModelObject;
import minecrafttransportsimulator.dataclasses.PackVehicleObject.PackTranslatableModelObject;
import minecrafttransportsimulator.items.parts.AItemPart;
import minecrafttransportsimulator.systems.ClientEventSystem;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.OBJParserSystem;
import minecrafttransportsimulator.systems.PackParserSystem;
import minecrafttransportsimulator.systems.RotationSystem;
import minecrafttransportsimulator.systems.VehicleEffectsSystem.FXPart;
import minecrafttransportsimulator.systems.VehicleSoundSystem;
import minecrafttransportsimulator.vehicles.main.EntityVehicleE_Powered;
import minecrafttransportsimulator.vehicles.main.EntityVehicleE_Powered.LightTypes;
import minecrafttransportsimulator.vehicles.main.EntityVehicleE_Powered.VehicleInstrument;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Air;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Ground;
import minecrafttransportsimulator.vehicles.main.EntityVehicleG_Plane;
import minecrafttransportsimulator.vehicles.parts.APart;
import minecrafttransportsimulator.vehicles.parts.APartEngine;
import minecrafttransportsimulator.vehicles.parts.APartEngineGeared;
import minecrafttransportsimulator.vehicles.parts.APartGun;
import minecrafttransportsimulator.vehicles.parts.PartEngineAircraft;
import minecrafttransportsimulator.vehicles.parts.PartGroundDeviceTread;
import minecrafttransportsimulator.vehicles.parts.PartPropeller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**Main render class for all vehicles.  Renders the vehicle, along with all parts.
 * As entities don't render above 255 well due to the new chunk visibility system, 
 * this code is called both from the regular render loop and manually from
 * {@link ClientEventSystem#on(RenderWorldLastEvent)}.
 *
 * @author don_bruce
 */
public final class RenderVehicle extends Render<EntityVehicleE_Powered>{
	private static final Minecraft minecraft = Minecraft.getMinecraft();
	
	//VEHICLE MAPS.  Maps are keyed by JSON name.
	private static final Map<String, Integer> vehicleDisplayLists = new HashMap<String, Integer>();
	private static final Map<String, List<RotatablePart>> vehicleRotatableLists = new HashMap<String, List<RotatablePart>>();
	private static final Map<String, List<TranslatablePart>> vehicleTranslatableLists = new HashMap<String, List<TranslatablePart>>();
	private static final Map<String, List<LightPart>> vehicleLightLists = new HashMap<String, List<LightPart>>();
	private static final Map<String, List<WindowPart>> vehicleWindowLists = new HashMap<String, List<WindowPart>>();
	private static final Map<String, List<Float[]>> treadDeltas = new HashMap<String, List<Float[]>>();
	private static final Map<String, List<Double[]>> treadAltDeltas = new HashMap<String, List<Double[]>>();
	
	//PART MAPS.  Maps are keyed by the part model location.
	private static final Map<ResourceLocation, Integer> partDisplayLists = new HashMap<ResourceLocation, Integer>();
	private static final Map<ResourceLocation, List<RotatablePart>> partRotatableLists = new HashMap<ResourceLocation, List<RotatablePart>>();
	private static final Map<ResourceLocation, List<LightPart>> partLightLists = new HashMap<ResourceLocation, List<LightPart>>();
	
	//COMMON MAPS.  Keyed by either vehicle name or part name.
	private static final Map<String, ResourceLocation> textureMap = new HashMap<String, ResourceLocation>();
	
	//Maps to check last render times for each vehicle.
	private static final Map<EntityVehicleE_Powered, Byte> lastRenderPass = new HashMap<EntityVehicleE_Powered, Byte>();
	private static final Map<EntityVehicleE_Powered, Long> lastRenderTick = new HashMap<EntityVehicleE_Powered, Long>();
	private static final Map<EntityVehicleE_Powered, Float> lastRenderPartial = new HashMap<EntityVehicleE_Powered, Float>();
	
	//Additional maps to handle shaders compatibility.
	private static boolean shadersDetected = false;
	private static final Map<EntityVehicleE_Powered, Boolean> renderedShaderShadow = new HashMap<EntityVehicleE_Powered, Boolean>();
	private static final Map<EntityVehicleE_Powered, Boolean> renderedShaderModel = new HashMap<EntityVehicleE_Powered, Boolean>();
	
	//Constants for built-in textures.
	private static final ResourceLocation vanillaGlassTexture = new ResourceLocation("minecraft", "textures/blocks/glass.png");
	private static final ResourceLocation lensFlareTexture = new ResourceLocation(MTS.MODID, "textures/rendering/lensflare.png");
	private static final ResourceLocation lightTexture = new ResourceLocation(MTS.MODID, "textures/rendering/light.png");
	private static final ResourceLocation lightBeamTexture = new ResourceLocation(MTS.MODID, "textures/rendering/lightbeam.png");
	
	public RenderVehicle(RenderManager renderManager){
		super(renderManager);
	}
	
	/**Used to clear out the rendering caches in dev mode to allow the re-loading of models.**/
	public static void clearCaches(){
		for(Integer index : vehicleDisplayLists.values()){
			GL11.glDeleteLists(index, 1);
		}
		vehicleDisplayLists.clear();
		vehicleRotatableLists.clear();
		vehicleTranslatableLists.clear();
		vehicleLightLists.clear();
		vehicleWindowLists.clear();
		for(Integer index : partDisplayLists.values()){
			GL11.glDeleteLists(index, 1);
		}
		partDisplayLists.clear();
		partRotatableLists.clear();
		partLightLists.clear();
		treadDeltas.clear();
	}
	
	/**Returns the currently cached texture for the vehicle.  Static for use in other functions.**/
	public static ResourceLocation getTextureForVehicle(EntityVehicleE_Powered entity){
		return textureMap.get(entity.vehicleName);
	}
	
	@Override
	protected ResourceLocation getEntityTexture(EntityVehicleE_Powered entity){
		return null;
	}
	
	@Override
	public void doRender(EntityVehicleE_Powered vehicle, double x, double y, double z, float entityYaw, float partialTicks){
		boolean didRender = false;
		if(vehicle.pack != null){ 
			if(lastRenderPass.containsKey(vehicle)){
				//Did we render this tick?
				if(lastRenderTick.get(vehicle) == vehicle.world.getTotalWorldTime() && lastRenderPartial.get(vehicle) == partialTicks){
					//If we rendered last on a pass of 0 or 1 this tick, don't re-render some things.
					//This prevents double-rendering in pass 0 and pass -1 from the event system.
					if(lastRenderPass.get(vehicle) != -1 && MinecraftForgeClient.getRenderPass() == -1){
						//If we have shaders, make sure we don't call this if we really haven't rendered the model.
						if(!shadersDetected || renderedShaderModel.get(vehicle)){
							render(vehicle, Minecraft.getMinecraft().player, partialTicks, true);
							didRender = true;
						}
					}
				}
			}
			
			//If we didn't render the vehicle previously in any ticks do so now.
			//This normally gets called when we get two consecutive renders from events in pass -1,
			//but it also can happen if shaders are present and we only render the vehicle shadow.
			if(!didRender){
				render(vehicle, Minecraft.getMinecraft().player, partialTicks, false);
			}

			//If we previously rendered on pass 0 without rendering on pass -1, it means shaders are present.
			//Set bit to detect these buggers and keep vehicles from disappearing.
			//Allso init map entries to prevent nulls from hitting logic checks.
			if(!shadersDetected && lastRenderPass.containsKey(vehicle) && lastRenderPass.get(vehicle) == 1 && MinecraftForgeClient.getRenderPass() == 0){
				shadersDetected = true;
				renderedShaderShadow.put(vehicle, false);
				renderedShaderModel.put(vehicle, false);
			}
			
			//Update maps.
			lastRenderPass.put(vehicle, (byte) MinecraftForgeClient.getRenderPass());
			lastRenderTick.put(vehicle, vehicle.world.getTotalWorldTime());
			lastRenderPartial.put(vehicle, partialTicks);
			
			//If we are in pass 1, and shaders are detected, let the system know one render has been completed.
			//This will first be the shadow, and second be the model.
			if(shadersDetected){
				if(MinecraftForgeClient.getRenderPass() == 1){
					if(!renderedShaderShadow.containsKey(vehicle) || renderedShaderShadow.get(vehicle)){
						renderedShaderModel.put(vehicle, true);
					}else{
						renderedShaderShadow.put(vehicle, true);
					}
				}else if(MinecraftForgeClient.getRenderPass() == -1){
					renderedShaderShadow.put(vehicle, false);
					renderedShaderModel.put(vehicle, false);
				}
			}
		}
	}
	
	public static boolean doesVehicleHaveLight(EntityVehicleE_Powered vehicle, LightTypes light){
		for(LightPart lightPart : vehicleLightLists.get(vehicle.vehicleJSONName)){
			if(lightPart.type.equals(light)){
				return true;
			}
		}
		return false;
	}
	
	private static void render(EntityVehicleE_Powered vehicle, EntityPlayer playerRendering, float partialTicks, boolean wasRenderedPrior){
		//Get position and rotation.
		Entity renderViewEntity = minecraft.getRenderViewEntity();
		double playerX = renderViewEntity.lastTickPosX + (renderViewEntity.posX - renderViewEntity.lastTickPosX) * (double)partialTicks;
		double playerY = renderViewEntity.lastTickPosY + (renderViewEntity.posY - renderViewEntity.lastTickPosY) * (double)partialTicks;
		double playerZ = renderViewEntity.lastTickPosZ + (renderViewEntity.posZ - renderViewEntity.lastTickPosZ) * (double)partialTicks;
        double thisX = vehicle.lastTickPosX + (vehicle.posX - vehicle.lastTickPosX) * (double)partialTicks;
        double thisY = vehicle.lastTickPosY + (vehicle.posY - vehicle.lastTickPosY) * (double)partialTicks;
        double thisZ = vehicle.lastTickPosZ + (vehicle.posZ - vehicle.lastTickPosZ) * (double)partialTicks;
		double rotateYaw = -vehicle.rotationYaw + (vehicle.rotationYaw - vehicle.prevRotationYaw)*(double)(1 - partialTicks);
        double rotatePitch = vehicle.rotationPitch - (vehicle.rotationPitch - vehicle.prevRotationPitch)*(double)(1 - partialTicks);
        double rotateRoll = vehicle.rotationRoll - (vehicle.rotationRoll - vehicle.prevRotationRoll)*(double)(1 - partialTicks);
       
        //Set up lighting.
        int lightVar = vehicle.getBrightnessForRender();
        minecraft.entityRenderer.enableLightmap();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightVar%65536, lightVar/65536);
        RenderHelper.enableStandardItemLighting();
        
		//Bind texture.  Adds new element to cache if needed.
		if(!textureMap.containsKey(vehicle.vehicleName)){
			textureMap.put(vehicle.vehicleName, new ResourceLocation(vehicle.vehicleName.substring(0, vehicle.vehicleName.indexOf(':')), "textures/vehicles/" + vehicle.vehicleName.substring(vehicle.vehicleName.indexOf(':') + 1) + ".png"));
		}
		minecraft.getTextureManager().bindTexture(textureMap.get(vehicle.vehicleName));

		//Render all the model parts except windows.
		//Those need to be rendered after the player if the player is rendered manually.
        GL11.glPushMatrix();
        GL11.glTranslated(thisX - playerX, thisY - playerY, thisZ - playerZ);
		if(MinecraftForgeClient.getRenderPass() != 1 && !wasRenderedPrior){
			GL11.glPushMatrix();
			GL11.glShadeModel(GL11.GL_SMOOTH);
	        GL11.glRotated(rotateYaw, 0, 1, 0);
	        GL11.glRotated(rotatePitch, 1, 0, 0);
	        GL11.glRotated(rotateRoll, 0, 0, 1);
			renderMainModel(vehicle, partialTicks);
			renderParts(vehicle, partialTicks);
			GL11.glEnable(GL11.GL_NORMALIZE);
			renderWindows(vehicle, partialTicks);
			renderTextMarkings(vehicle);
			renderInstrumentsAndControls(vehicle);
			GL11.glDisable(GL11.GL_NORMALIZE);
			if(Minecraft.getMinecraft().getRenderManager().isDebugBoundingBox()){
				renderBoundingBoxes(vehicle);
			}
			GL11.glShadeModel(GL11.GL_FLAT);
			GL11.glPopMatrix();
		}
		
		//Check to see if we need to manually render riders.
		//MC culls rendering above build height depending on the direction the player is looking.
 		//Due to inconsistent culling based on view angle, this can lead to double-renders.
 		//Better than not rendering at all I suppose.
		if(MinecraftForgeClient.getRenderPass() != 1 && !wasRenderedPrior){
			for(Entity passenger : vehicle.getPassengers()){
				if(!(minecraft.player.equals(passenger) && minecraft.gameSettings.thirdPersonView == 0) && passenger.posY > passenger.world.getHeight()){
		        	 GL11.glPushMatrix();
		        	 GL11.glTranslated(passenger.posX - vehicle.posX, passenger.posY - vehicle.posY, passenger.posZ - vehicle.posZ);
		        	 Minecraft.getMinecraft().getRenderManager().renderEntityStatic(passenger, partialTicks, false);
		        	 GL11.glPopMatrix();
		         }
			}
		}
		
		//Lights and beacons get rendered in two passes.
		//The first renders the cases and bulbs, the second renders the beams and effects.
		//Make sure the light list is populated here before we try to render this, as loading de-syncs can leave it null.
		if(vehicleLightLists.get(vehicle.vehicleJSONName) != null){
			float sunLight = vehicle.world.getSunBrightness(0)*vehicle.world.getLightBrightness(vehicle.getPosition());
			float blockLight = vehicle.world.getLightFromNeighborsFor(EnumSkyBlock.BLOCK, vehicle.getPosition())/15F;
			float electricFactor = (float) Math.min(vehicle.electricPower > 2 ? (vehicle.electricPower-2)/6F : 0, 1);
			float lightBrightness = (float) Math.min((1 - Math.max(sunLight, blockLight))*electricFactor, 1);

			GL11.glPushMatrix();
			GL11.glEnable(GL11.GL_NORMALIZE);
	        GL11.glRotated(rotateYaw, 0, 1, 0);
	        GL11.glRotated(rotatePitch, 1, 0, 0);
	        GL11.glRotated(rotateRoll, 0, 0, 1);
	        renderLights(vehicle, sunLight, blockLight, lightBrightness, electricFactor, wasRenderedPrior, partialTicks);
			GL11.glDisable(GL11.GL_NORMALIZE);
			GL11.glPopMatrix();
			
			//Return all states to normal.
			minecraft.entityRenderer.enableLightmap();
			GL11.glEnable(GL11.GL_LIGHTING);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glDepthMask(true);
			GL11.glColor4f(1, 1, 1, 1);
		}
		
		//Render holograms for missing parts if applicable.
		if(MinecraftForgeClient.getRenderPass() != 0){
			renderPartBoxes(vehicle);
		}
		
		//Make sure lightmaps are set correctly.
		if(MinecraftForgeClient.getRenderPass() == -1){
			RenderHelper.disableStandardItemLighting();
			minecraft.entityRenderer.disableLightmap();
		}else{
			RenderHelper.enableStandardItemLighting();
			minecraft.entityRenderer.enableLightmap();
		}
		GL11.glPopMatrix();
		
		//Update SFX, but only once per render cycle.
		if(MinecraftForgeClient.getRenderPass() == -1){
			VehicleSoundSystem.updateVehicleSounds(vehicle, partialTicks);
			for(APart part : vehicle.getVehicleParts()){
				if(part instanceof FXPart){
					((FXPart) part).spawnParticles();
				}
			}
		}
	}
	
	private static void renderMainModel(EntityVehicleE_Powered vehicle, float partialTicks){
		GL11.glPushMatrix();
		//Normally we use the pack name, but since all displaylists
		//are the same for all models, this is more appropriate.
		if(vehicleDisplayLists.containsKey(vehicle.vehicleJSONName)){
			GL11.glCallList(vehicleDisplayLists.get(vehicle.vehicleJSONName));
			
			//The display list only renders static parts.  We need to render dynamic ones manually.
			//If this is a window, don't render it as that gets done all at once later.
			for(RotatablePart rotatable : vehicleRotatableLists.get(vehicle.vehicleJSONName)){
				if(!rotatable.name.contains("window")){
					GL11.glPushMatrix();
					if(rotatable.name.contains("%")){
						for(TranslatablePart translatable : vehicleTranslatableLists.get(vehicle.vehicleJSONName)){
							if(translatable.name.equals(rotatable.name)){
								translateModelObject(vehicle, translatable, partialTicks);
								break;
							}
						}
					}
					rotateModelObject(vehicle, rotatable, partialTicks);
					GL11.glBegin(GL11.GL_TRIANGLES);
					for(Float[] vertex : rotatable.vertices){
						GL11.glTexCoord2f(vertex[3], vertex[4]);
						GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
						GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
					}
					GL11.glEnd();
					GL11.glPopMatrix();
				}
			}
			for(TranslatablePart translatable : vehicleTranslatableLists.get(vehicle.vehicleJSONName)){
				if(!translatable.name.contains("window") && !translatable.name.contains("$")){
					GL11.glPushMatrix();
					translateModelObject(vehicle, translatable, partialTicks);
					GL11.glBegin(GL11.GL_TRIANGLES);
					for(Float[] vertex : translatable.vertices){
						GL11.glTexCoord2f(vertex[3], vertex[4]);
						GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
						GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
					}
					GL11.glEnd();
					GL11.glPopMatrix();
				}
			}
		}else{
			List<RotatablePart> rotatableParts = new ArrayList<RotatablePart>();
			List<TranslatablePart> translatableParts = new ArrayList<TranslatablePart>();
			List<LightPart> lightParts = new ArrayList<LightPart>();
			List<WindowPart> windows = new ArrayList<WindowPart>();
			Map<String, Float[][]> parsedModel = OBJParserSystem.parseOBJModel(vehicle.vehicleName.substring(0, vehicle.vehicleName.indexOf(':')), "objmodels/vehicles/" + vehicle.vehicleJSONName + ".obj");
			int displayListIndex = GL11.glGenLists(1);
			GL11.glNewList(displayListIndex, GL11.GL_COMPILE);
			GL11.glBegin(GL11.GL_TRIANGLES);
			for(Entry<String, Float[][]> entry : parsedModel.entrySet()){
				//Don't add rotatable model parts or windows to the display list.
				//Those go in separate maps, with windows going into both a rotatable and window mapping.
				//Do add lights, as they will be rendered both as part of the model and with special things.
				boolean shouldShapeBeInDL = true;
				if(entry.getKey().contains("$")){
					rotatableParts.add(new RotatablePart(entry.getKey(), entry.getValue(), vehicle.pack.rendering.rotatableModelObjects));
					shouldShapeBeInDL = false;
				}
				if(entry.getKey().contains("%")){
					translatableParts.add(new TranslatablePart(entry.getKey(), entry.getValue(), vehicle.pack.rendering.translatableModelObjects));
					shouldShapeBeInDL = false;
				}
				if(entry.getKey().contains("&")){
					lightParts.add(new LightPart(entry.getKey(), entry.getValue()));
				}
				if(entry.getKey().toLowerCase().contains("window")){
					windows.add(new WindowPart(entry.getKey(), entry.getValue()));
					shouldShapeBeInDL = false;
				}
				if(shouldShapeBeInDL){
					for(Float[] vertex : entry.getValue()){
						GL11.glTexCoord2f(vertex[3], vertex[4]);
						GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
						GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
					}
				}
			}
			GL11.glEnd();
			GL11.glEndList();
			
			//Now finalize the maps.
			vehicleRotatableLists.put(vehicle.vehicleJSONName, rotatableParts);
			vehicleTranslatableLists.put(vehicle.vehicleJSONName, translatableParts);
			vehicleLightLists.put(vehicle.vehicleJSONName, lightParts);
			vehicleWindowLists.put(vehicle.vehicleJSONName, windows);
			vehicleDisplayLists.put(vehicle.vehicleJSONName, displayListIndex);
		}
		GL11.glPopMatrix();
	}
	
	private static void rotateModelObject(EntityVehicleE_Powered vehicle, RotatablePart rotatable, float partialTicks){
		for(byte i=0; i<rotatable.rotationVariables.length; ++i){
			float rotation = getRotationAngleForModelVariable(vehicle, rotatable.rotationVariables[i], partialTicks);
			if(rotation != 0){
				GL11.glTranslated(rotatable.rotationPoints[i].x, rotatable.rotationPoints[i].y, rotatable.rotationPoints[i].z);
				GL11.glRotated(rotation*rotatable.rotationMagnitudes[i], rotatable.rotationAxis[i].x, rotatable.rotationAxis[i].y, rotatable.rotationAxis[i].z);
				GL11.glTranslated(-rotatable.rotationPoints[i].x, -rotatable.rotationPoints[i].y, -rotatable.rotationPoints[i].z);
			}
		}
	}
	
	private static float getRotationAngleForModelVariable(EntityVehicleE_Powered vehicle, String variable, float partialTicks){
		switch(variable){
			case("cycle"): return vehicle.world.getTotalWorldTime()%20;
			case("door"): return vehicle.parkingBrakeAngle*2;
			case("hood"): return vehicle.getEngineByNumber((byte) 0) == null ? 60 : 0;
			case("throttle"): return vehicle.throttle/4F;
			case("brake"): return vehicle.brakeOn ? 25 : 0;
			case("p_brake"): return vehicle.parkingBrakeAngle;
			case("horn"): return vehicle.hornOn ? 30 : 0;
			case("trailer"): return ((EntityVehicleF_Ground) vehicle).towingAngle;
			case("hookup"): return ((EntityVehicleF_Ground) vehicle).towedByVehicle != null ? ((EntityVehicleF_Ground) vehicle).towedByVehicle.towingAngle : 0;
			case("gearshift"): return vehicle.getEngineByNumber((byte) 0) != null ? (((APartEngineGeared) vehicle.getEngineByNumber((byte) 0))).getGearshiftRotation() : 0;
			case("gearshift_hvertical"): return vehicle.getEngineByNumber((byte) 0) != null ? (((APartEngineGeared) vehicle.getEngineByNumber((byte) 0))).getGearshiftPosition_Vertical() : 0;
			case("gearshift_hhorizontal"): return vehicle.getEngineByNumber((byte) 0) != null ? (((APartEngineGeared) vehicle.getEngineByNumber((byte) 0))).getGearshiftPosition_Horizontal() : 0;
			case("engine"): return (float) (vehicle.getEngineByNumber((byte) 0) != null ? ((APartEngine) vehicle.getEngineByNumber((byte) 0)).getEngineRotation(partialTicks) : 0);
			case("driveshaft"): return getDriveshaftValue(vehicle, partialTicks, (byte) 0);
			case("driveshaft_sin"): return (float) (1 + Math.cos(Math.toRadians(getDriveshaftValue(vehicle, partialTicks, (byte) 0) + 180F)))/2F;
			case("driveshaft_sin_offset"): return (float) Math.sin(Math.toRadians(getDriveshaftValue(vehicle, partialTicks, (byte) 0) + 180F));
			case("steeringwheel"): return vehicle.getSteerAngle();
			
			case("aileron"): return ((EntityVehicleF_Air) vehicle).aileronAngle/10F;
			case("elevator"): return ((EntityVehicleF_Air) vehicle).elevatorAngle/10F;
			case("rudder"): return ((EntityVehicleF_Air) vehicle).rudderAngle/10F;
			case("flap"): return ((EntityVehicleG_Plane) vehicle).flapCurrentAngle/10F;
			case("trim_aileron"): return ((EntityVehicleF_Air) vehicle).aileronTrim/10F;
			case("trim_elevator"): return ((EntityVehicleF_Air) vehicle).elevatorTrim/10F;
			case("trim_rudder"): return ((EntityVehicleF_Air) vehicle).rudderTrim/10F;
			case("reverser"): return ((EntityVehicleF_Air) vehicle).reversePercent/1F;
		}
		
		//If we aren't a static variable, we are a part-specific variable.
		//Take off the part following the _, and check for that instead.
		byte index = (byte) (Byte.valueOf(variable.substring(variable.lastIndexOf('_') + 1)) - 1);
		switch(variable.substring(0, variable.lastIndexOf('_'))){
			case("magneto"): return vehicle.getEngineByNumber(index) != null ? (vehicle.getEngineByNumber(index).state.magnetoOn ? 30 : 0) : 0;
			case("starter"): return vehicle.getEngineByNumber(index) != null ? (vehicle.getEngineByNumber(index).state.esOn ? 30 : 0) : 0;
			case("pitch"): return vehicle.getEngineByNumber(index) != null ? (vehicle.getEngineByNumber(index).childParts.get(0) instanceof PartEngineAircraft ? ((PartEngineAircraft) vehicle.getEngineByNumber(index).childParts.get(0)).propeller.currentPitch : 0) : 0;
			case("engine"): return (float) (vehicle.getEngineByNumber(index) != null ? vehicle.getEngineByNumber(index).getEngineRotation(partialTicks) : 0);
			case("driveshaft"): return getDriveshaftValue(vehicle, partialTicks, index);
			default: return 0;
		}
	}
	
	private static void translateModelObject(EntityVehicleE_Powered vehicle, TranslatablePart translatable, float partialTicks){
		for(byte i=0; i<translatable.translationVariables.length; ++i){
			float translation = getTranslationLengthForModelVariable(vehicle, translatable.translationVariables[i], partialTicks);
			if(translation != 0){
				float translationMagnitude = translation*translatable.translationMagnitudes[i];
				GL11.glTranslated(translationMagnitude*translatable.translationAxis[i].x, translationMagnitude*translatable.translationAxis[i].y, translationMagnitude*translatable.translationAxis[i].z);
			}
		}
	}
	
	private static float getTranslationLengthForModelVariable(EntityVehicleE_Powered vehicle, String variable, float partialTicks){
		switch(variable){
			case("door"): return vehicle.parkingBrakeAngle/30;
			case("throttle"): return vehicle.throttle/100F;
			case("brake"): return vehicle.brakeOn ? 1 : 0;
			case("p_brake"): return vehicle.parkingBrakeAngle/30;
			case("horn"): return vehicle.hornOn ? 1 : 0;
			case("trailer"): return ((EntityVehicleF_Ground) vehicle).towingAngle/30F;
			case("hookup"): return ((EntityVehicleF_Ground) vehicle).towedByVehicle != null ? ((EntityVehicleF_Ground) vehicle).towedByVehicle.towingAngle/30F : 0;
			case("gearshift"): return vehicle.getEngineByNumber((byte) 0) != null ? (((APartEngineGeared) vehicle.getEngineByNumber((byte) 0))).getGearshiftRotation()/5F : 0;
			case("engine_sin"): return (float) (vehicle.getEngineByNumber((byte) 0) != null ? (1 + Math.cos(Math.toRadians(((APartEngine) vehicle.getEngineByNumber((byte) 0)).getEngineRotation(partialTicks) + 180F)))/2F : 0);
			case("driveshaft_sin"): return (float) (1 + Math.cos(Math.toRadians(getDriveshaftValue(vehicle, partialTicks, (byte) 0) + 180F)))/2F;
			case("driveshaft_sin_offset"): return (float) Math.sin(Math.toRadians(getDriveshaftValue(vehicle, partialTicks, (byte) 0)));
			case("steeringwheel"): return vehicle.getSteerAngle()/35F;
			
			case("aileron"): return ((EntityVehicleF_Air) vehicle).aileronAngle/350F;
			case("elevator"): return ((EntityVehicleF_Air) vehicle).elevatorAngle/350F;
			case("rudder"): return ((EntityVehicleF_Air) vehicle).rudderAngle/350F;
			case("flap"): return ((EntityVehicleG_Plane) vehicle).flapCurrentAngle/350F;
			case("trim_aileron"): return ((EntityVehicleF_Air) vehicle).aileronTrim/350F;
			case("trim_elevator"): return ((EntityVehicleF_Air) vehicle).elevatorTrim/350F;
			case("trim_rudder"): return ((EntityVehicleF_Air) vehicle).rudderTrim/350F;
			case("reverser"): return ((EntityVehicleF_Air) vehicle).reversePercent/100F;
		}
		
		//If we aren't a static variable, we are a part-specific variable.
		//Take off the part following the _, and check for that instead.
		byte index = (byte) (Byte.valueOf(variable.substring(variable.lastIndexOf('_') + 1)) - 1);
		switch(variable.substring(0, variable.lastIndexOf('_'))){
			case("magneto"): return vehicle.getEngineByNumber(index) != null ? (vehicle.getEngineByNumber(index).state.magnetoOn ? 1 : 0) : 0;
			case("starter"): return vehicle.getEngineByNumber(index) != null ? (vehicle.getEngineByNumber(index).state.esOn ? 1 : 0) : 0;
			default: return 0;
		}
	}
	
	private static float getDriveshaftValue(EntityVehicleE_Powered vehicle, float partialTicks, byte engineNumber){
		if(vehicle.getEngineByNumber(engineNumber) != null){
			return (float) (vehicle.getEngineByNumber(engineNumber).getDriveshaftRotation(partialTicks)%360);
		}else{
			return 0;
		}		
	}
	
	private static void renderParts(EntityVehicleE_Powered vehicle, float partialTicks){
		for(APart part : vehicle.getVehicleParts()){
			ResourceLocation partModelLocation = part.getModelLocation();
			if(partModelLocation == null){
				continue;
			}else if(!partDisplayLists.containsKey(partModelLocation)){
				List<RotatablePart> rotatableParts = new ArrayList<RotatablePart>();
    			List<LightPart> lightParts = new ArrayList<LightPart>();
				Map<String, Float[][]> parsedModel = OBJParserSystem.parseOBJModel(partModelLocation.getResourceDomain(), partModelLocation.getResourcePath());
    			int displayListIndex = GL11.glGenLists(1);
    			GL11.glNewList(displayListIndex, GL11.GL_COMPILE);
    			GL11.glBegin(GL11.GL_TRIANGLES);
    			for(Entry<String, Float[][]> entry : parsedModel.entrySet()){
    				boolean shouldShapeBeInDL = true;
    				if(entry.getKey().contains("$")){
    					rotatableParts.add(new RotatablePart(entry.getKey(), entry.getValue(), part.pack.rendering.rotatableModelObjects));
    					shouldShapeBeInDL = false;
    				}
    				if(entry.getKey().contains("&")){
    					lightParts.add(new LightPart(entry.getKey(), entry.getValue()));
    				}
    				if(shouldShapeBeInDL){
    					for(Float[] vertex : entry.getValue()){
    						GL11.glTexCoord2f(vertex[3], vertex[4]);
    						GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
    						GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
    					}
    				}
    			}
    			GL11.glEnd();
    			GL11.glEndList();
    			partDisplayLists.put(partModelLocation, displayListIndex);
    			partRotatableLists.put(partModelLocation, rotatableParts);
    			partLightLists.put(partModelLocation, lightParts);
    		}else{
    			//If we aren't using the vehicle texture, bind the texture for this part.
    			if(!part.pack.general.useVehicleTexture){
    				if(!textureMap.containsKey(part.partName)){
        				textureMap.put(part.partName, part.getTextureLocation());
        			}
    				minecraft.getTextureManager().bindTexture(textureMap.get(part.partName));
    			}
    			
    			//If we are a tread, do the tread-specific render.
        		//Otherwise render like all other parts.
        		if(part instanceof PartGroundDeviceTread){
        			doTreadRender((PartGroundDeviceTread) part, vehicle.getPackDefForLocation(part.offset.x, part.offset.y, part.offset.z), partialTicks, partDisplayLists.get(partModelLocation));
        		}else{
	    			//Rotate and translate the part prior to rendering the displayList.
	    			//Note that if the part's parent has a rotation, use that to transform
	    			//the translation to match that rotation.  Needed for things like
	    			//tank turrets with seats or guns.
	    			Vec3d actionRotation = part.getActionRotation(partialTicks);
	    			GL11.glPushMatrix();
	    			if(part.parentPart != null && !part.parentPart.getActionRotation(partialTicks).equals(Vec3d.ZERO)){
	    				Vec3d parentActionRotation = part.parentPart.getActionRotation(partialTicks);
	    				Vec3d partRelativeOffset = part.offset.subtract(part.parentPart.offset);
	    				Vec3d partTranslationOffset = part.parentPart.offset.add(RotationSystem.getRotatedPoint(partRelativeOffset, (float) parentActionRotation.x, (float) parentActionRotation.y, (float) parentActionRotation.z));
	    				GL11.glTranslated(partTranslationOffset.x, partTranslationOffset.y, partTranslationOffset.z);
	    				rotatePart(part, parentActionRotation.add(actionRotation), true);
	    			}else{
	    				GL11.glTranslated(part.offset.x, part.offset.y, part.offset.z);
	    				rotatePart(part, actionRotation, true);
	    			}
	        		GL11.glCallList(partDisplayLists.get(partModelLocation));
	    			
	    			//The display list only renders static parts.  We need to render dynamic ones manually.
	    			for(RotatablePart rotatable : partRotatableLists.get(partModelLocation)){
	    				GL11.glPushMatrix();
	    				rotatePartObject(part, rotatable, partialTicks);
	    				GL11.glBegin(GL11.GL_TRIANGLES);
	    				for(Float[] vertex : rotatable.vertices){
	    					GL11.glTexCoord2f(vertex[3], vertex[4]);
	    					GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
	    					GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
	    				}
	    				GL11.glEnd();
	    				GL11.glPopMatrix();
	    			}
	    			
	    			GL11.glCullFace(GL11.GL_BACK);
	    			GL11.glPopMatrix();
    			}
    		}
        }
	}
	
	private static void rotatePartObject(APart part, RotatablePart rotatable, float partialTicks){
		for(byte i=0; i<rotatable.rotationVariables.length; ++i){
			float rotation = getRotationAngleForPartVariable(part, rotatable.rotationVariables[i], partialTicks);
			if(rotation != 0){
				GL11.glTranslated(rotatable.rotationPoints[i].x, rotatable.rotationPoints[i].y, rotatable.rotationPoints[i].z);
				GL11.glRotated(rotation*rotatable.rotationMagnitudes[i], rotatable.rotationAxis[i].x, rotatable.rotationAxis[i].y, rotatable.rotationAxis[i].z);
				GL11.glTranslated(-rotatable.rotationPoints[i].x, -rotatable.rotationPoints[i].y, -rotatable.rotationPoints[i].z);
			}
		}
	}
	
	private static float getRotationAngleForPartVariable(APart part, String variable, float partialTicks){
		if(part instanceof APartEngine){
			APartEngine engine = (APartEngine) part;
			switch(variable){
				case("engine"): return (float) engine.getEngineRotation(partialTicks);
				case("driveshaft"): return (float) engine.getDriveshaftRotation(partialTicks);
			}
		}else if(part instanceof PartPropeller){
			if(variable.equals("propellerpitch")){
				return (float) Math.toDegrees(Math.atan(((PartPropeller) part).currentPitch / (((PartPropeller) part).pack.propeller.diameter*0.75D*Math.PI)));
			}
		}else if(part instanceof APartGun){
			switch(variable){
				case("gun_pitch"): return ((APartGun) part).currentPitch;
				case("gun_yaw"): return ((APartGun) part).currentYaw;
			}
		}
		switch(variable){
			case("door"): return part.vehicle.parkingBrakeAngle*2;
			case("steeringwheel"): return part.vehicle.getSteerAngle();
		}
		
		//If we are an engine-specific variable, get the engine now.
		//Otherwise, get the 1st engine.
		APartEngine engine = (APartEngine) (part.parentPart instanceof APartEngine ? part.parentPart : part.vehicle.getEngineByNumber((byte) 0));
		switch(variable){
			case("engine"): return (float) (engine != null ? engine.getEngineRotation(partialTicks) : 0);
			case("driveshaft"): return (float) (engine != null ? engine.getDriveshaftRotation(partialTicks) : 0);
			case("hood"): return engine == null ? 60 : 0;
			default: return 0;
		}
	}
	
	private static void rotatePart(APart part, Vec3d actionRotation, boolean cullface){
		if(part.turnsWithSteer){
			if(part.offset.z >= 0){
				GL11.glRotatef(part.vehicle.getSteerAngle(), 0, 1, 0);
			}else{
				GL11.glRotatef(-part.vehicle.getSteerAngle(), 0, 1, 0);
			}
		}
		
		if(((part.offset.x < 0 && !part.inverseMirroring) || (part.offset.x > 0 && part.inverseMirroring)) && !part.disableMirroring){
			GL11.glScalef(-1.0F, 1.0F, 1.0F);
			if(cullface){
				GL11.glCullFace(GL11.GL_FRONT);
			}
		}
		
		GL11.glRotated(part.partRotation.x, 1, 0, 0);
		GL11.glRotated(part.partRotation.y, 0, 1, 0);
		GL11.glRotated(part.partRotation.z, 0, 0, 1);

		GL11.glRotated(actionRotation.x, 1, 0, 0);
		GL11.glRotated(-actionRotation.y, 0, 1, 0);
		GL11.glRotated(actionRotation.z, 0, 0, 1);
	}
	
	private static void doTreadRender(PartGroundDeviceTread treadPart, PackPart partDef, float partialTicks, int displayListIndex){
		List<Float[]> deltas = treadDeltas.get(treadPart.vehicle.vehicleJSONName);
		if(Keyboard.isKeyDown(Keyboard.KEY_HOME)){
			treadDeltas.remove(treadPart.vehicle.vehicleJSONName);
			return;
		}
		if(deltas == null){
			//If we don't have the deltas, calculate them based on the points of the rollers on the vehicle.
			//We use a helper class here to make life easy, as we have a LOT of points to calculate.
			//In all cases, an angle of 0 implies the tread is facing down to the ground.
			class RollerHelper{
				final double yPos;
				final double zPos;
				final double radius;
				final double circumference;
				
				double startY;
				double startZ;
				double startAngle;
				double endY;
				double endZ;
				double endAngle;
				
				public RollerHelper(RotatablePart roller){
					//Get the radius and center of the roller.
					//To do this we get the min and max Y and Z points.
					double minY = 999;
					double maxY = -999;
					double minZ = 999;
					double maxZ = -999;
					for(Float[] point : roller.vertices){
						minY = Math.min(minY, point[1]);
						maxY = Math.max(maxY, point[1]);
						minZ = Math.min(minZ, point[2]);
						maxZ = Math.max(maxZ, point[2]);
					}
					radius = (maxZ - minZ)/2D;
					circumference = 2*Math.PI*radius;
					yPos = minY + (maxY - minY)/2D;
					zPos = minZ + (maxZ - minZ)/2D;
				}
				
				public void calculateEndpoints(RollerHelper nextRoller){
					//Calculates the end point of this roller and the
					//start point of the passed-in roller using trigonometry.
					double gamma = -Math.atan((zPos - nextRoller.zPos)/(yPos - nextRoller.yPos));
					double beta = Math.asin((radius - nextRoller.radius)/Math.hypot(nextRoller.yPos - yPos, nextRoller.zPos - zPos));
					double alpha = gamma - beta;
					endY = yPos + radius*Math.cos(Math.PI/2D - alpha);
					endZ = zPos + radius*Math.sin(Math.PI/2D - alpha);
					endAngle = Math.toDegrees(Math.atan2(endY - yPos, endZ - zPos));
					//System.out.format("C1:%f C2:%f R:%f\n", endY - yPos, endZ - zPos, Math.atan2(endY - yPos, endZ - zPos)); 
					nextRoller.startY = nextRoller.yPos + nextRoller.radius*Math.cos(Math.PI/2D - alpha);
					nextRoller.startZ = nextRoller.zPos + nextRoller.radius*Math.sin(Math.PI/2D - alpha);
					nextRoller.startAngle = Math.toDegrees(Math.atan2(nextRoller.startY - nextRoller.yPos, nextRoller.startZ - nextRoller.zPos));
					System.out.format("Y1:%f Z1:%f Y2:%f Z2:%f A1:%f A2:%f\n", endY, endZ, nextRoller.startY, nextRoller.startZ, endAngle, nextRoller.startAngle);
				}
			}
			

			//Search through rotatable parts on the vehicle and grab the rollers.
			Map<Integer, RollerHelper> parsedRollers = new HashMap<Integer, RollerHelper>();
			for(RotatablePart rotatable : vehicleRotatableLists.get(treadPart.vehicle.vehicleJSONName)){
				if(rotatable.name.contains("roller")){
					parsedRollers.put(Integer.valueOf(rotatable.name.substring(rotatable.name.lastIndexOf('_') + 1)), new RollerHelper(rotatable));
				}
			}
			
			//Now that we have all the rollers, we can start calculating points.
			//First calculate the endpoints on the rollers by calling the calculation method.
			//We also transfer the rollers to an ordered array for convenience later.
			RollerHelper[] rollers = new RollerHelper[parsedRollers.size()];
			for(int i=0; i<parsedRollers.size(); ++ i){
				if(i < parsedRollers.size() - 1){
					parsedRollers.get(i).calculateEndpoints(parsedRollers.get(i + 1));
				}else{
					parsedRollers.get(i).calculateEndpoints(parsedRollers.get(0));
				}
				rollers[i] = parsedRollers.get(i);
				//System.out.format("I:%d Y:%f Z:%f A1:%f A2:%f\n", i, rollers[i].yPos, rollers[i].zPos, rollers[i].startAngle, rollers[i].endAngle);
			}
			
			//Now that the endpoints are set, we can calculate the path.
			//Do this by following the start and end points at small increments.
			List<Double[]> points = new ArrayList<Double[]>();
			double deltaDist = treadPart.pack.tread.spacing;
			double straightPathLength = 0;
			for(int i=0; i<rollers.length; ++i){
				RollerHelper roller = rollers[i];
				//Follow the curve of the roller from the start and end point.
				double rollerPathLength = 2*Math.PI*roller.radius*Math.abs(roller.endAngle - roller.startAngle)/360D;
				double currentAngle = roller.startAngle;
				while(rollerPathLength > deltaDist){
					//If we have any remaining linear path from a prior operation, we
					//need to offset our first point on the roller path to account for it.
					//If we don't we'll have bad spacing at the rollers.
					if(straightPathLength > 0){
						double pathLength = deltaDist - straightPathLength;
						double anglePercent = pathLength/roller.circumference;
						currentAngle += 360D*anglePercent;
						rollerPathLength -= pathLength;
						straightPathLength = 0;
					}
					
					//Set the point based on the current angle on this path.
					double yPoint = roller.yPos + roller.radius*Math.cos(Math.toRadians(currentAngle));
					double zPoint = roller.zPos + roller.radius*Math.sin(Math.toRadians(currentAngle));
					//points.add(new Double[]{yPoint, zPoint, currentAngle});
					
					//Decrement the path length and increment the rotation angle.
					rollerPathLength -= deltaDist;
					currentAngle += 360D*deltaDist/roller.circumference;
				}
				
				//Done following roller.  Now go from the end of the roller to the start of the next roller.
				//If we are on the last roller, we need to get the first roller to complete the loop.
				RollerHelper nextRoller = i == rollers.length - 1 ? rollers[0] : rollers[i + 1];
				straightPathLength = Math.hypot(nextRoller.startY - roller.endY, nextRoller.startZ - roller.endZ);
				double deltaY = (nextRoller.startY - roller.endY)/straightPathLength;
				double deltaZ = (nextRoller.startZ - roller.endZ)/straightPathLength;
				
				//If we have any leftover roller path, account for it here to keep spacing consistent.
				//Need to do this after delta normalization or we'll get slight errors.
				double currentY = roller.endY + deltaY*rollerPathLength/deltaDist;
				double currentZ = roller.endZ + deltaZ*rollerPathLength/deltaDist;
				while(straightPathLength > deltaDist){
					//Set the point to the current YZ coord on this path.
					points.add(new Double[]{currentY, currentZ, roller.endAngle});
					
					//Decrement the path length and add to the YZ points.
					straightPathLength -= deltaDist;
					currentY += deltaY*deltaDist;
					currentZ += deltaZ*deltaDist;
				}
			}
			
			//Add the start point as the final end point and deltas to their map.
			points.add(points.get(0));
			treadAltDeltas.put(treadPart.vehicle.vehicleJSONName, points);
			

			
			
			
			

			
			
			//First calculate the total distance the treads need to be rendered.
			float totalDistance = 0;
			float lastY = partDef.treadYPoints[0];
			float lastZ = partDef.treadZPoints[0];
			for(byte i=1; i<partDef.treadYPoints.length; ++i){
				totalDistance += Math.hypot((partDef.treadYPoints[i] - lastY), (partDef.treadYPoints[i] - lastZ));
				lastY = partDef.treadYPoints[i];
				lastZ = partDef.treadZPoints[i];
			}
			
			//Now that we have the total distance, generate a set of points for the path.
			//These points should be as far apart as the spacing parameter.
			deltas = new ArrayList<Float[]>();
			final float spacing = treadPart.pack.tread.spacing;
			byte pointIndex = 0;
			float currentY = partDef.treadYPoints[pointIndex];
			float currentZ = partDef.treadZPoints[pointIndex];
			float nextY = partDef.treadYPoints[pointIndex + 1];
			float nextZ = partDef.treadZPoints[pointIndex + 1];
			float deltaYBeforeSegment = 0;
			float deltaZBeforeSegment = 0;
			float deltaBeforeSegment = 0;
			float segmentDeltaY = (nextY - currentY);
			float segmentDeltaZ = (nextZ - currentZ);
			float segmentDeltaTotal = (float) Math.hypot(segmentDeltaY, segmentDeltaZ);
			float angle = partDef.treadAngles[pointIndex];
			float currentAngle = 0;
			
			//Keep moving along the sets of points, making another set of evenly-spaced points.
			//This set of points will be used for rendering.
			while(totalDistance > 0){
				//If we are further than the delta between points, go to the next one.
				//Set the points to the next index set and increment delta and angle.
				while(deltaBeforeSegment + segmentDeltaTotal < spacing){
					++pointIndex;
					//If we run out of points go back to the start of the point set.
					//If we are out again, exit the loop.
					if(pointIndex + 1 == partDef.treadYPoints.length){
						currentY = partDef.treadYPoints[pointIndex];
						currentZ = partDef.treadZPoints[pointIndex];
						nextY = partDef.treadYPoints[0];
						nextZ = partDef.treadZPoints[0];
						//Ensure we rotate the angle by the correct amount for the joint.
						//It's possible that we will add a negative angle here due to going from something like 270 to 0.
						//This will cause a -270 rotation rather than the +30 we want.
						float angleToAdd = partDef.treadAngles[0] - partDef.treadAngles[pointIndex];
						while(angleToAdd < 0){
							angleToAdd += 360; 
						}
						angle += angleToAdd;
					}else if(pointIndex + 1 > partDef.treadYPoints.length){
						break;
					}else{
						currentY = partDef.treadYPoints[pointIndex];
						currentZ = partDef.treadZPoints[pointIndex];
						nextY = partDef.treadYPoints[pointIndex + 1];
						nextZ = partDef.treadZPoints[pointIndex + 1];
						angle += partDef.treadAngles[pointIndex] - partDef.treadAngles[pointIndex - 1];
					}
					
					//Update deltas.
					deltaBeforeSegment += segmentDeltaTotal;
					deltaYBeforeSegment += segmentDeltaY;
					deltaZBeforeSegment += segmentDeltaZ;
					segmentDeltaY = nextY - currentY;
					segmentDeltaZ = nextZ - currentZ;
					segmentDeltaTotal = (float) Math.hypot(segmentDeltaY, segmentDeltaZ);
				}
				
				//If we have enough distance for a segment, make one.
				//Otherwise add the end distance and set the total to 0.
				if(deltaBeforeSegment + segmentDeltaTotal >= spacing){
					//We are now at a point where the distance between the current point and the next point
					//are greater than the inter-point distance.  Use the slope of these two points to make a delta.
					//If we have any delta before the point, make sure we take that into account when getting the new point.
					float segmentPercentage = (spacing - deltaBeforeSegment)/segmentDeltaTotal;
					float segmentY = deltaYBeforeSegment + segmentDeltaY*segmentPercentage;
					float segmentZ = deltaZBeforeSegment + segmentDeltaZ*segmentPercentage;
					
					//Normally we could add the point now, but since OpenGL rotation changes the coordinate system
					//we need to correct for that here.  Use trigonometry to rotate the segment before adding it.
					currentAngle += angle;
					float correctedZ = (float) (Math.cos(Math.toRadians(currentAngle))*segmentZ - Math.sin(Math.toRadians(currentAngle))*segmentY);
					float correctedY = (float) (Math.sin(Math.toRadians(currentAngle))*segmentZ + Math.cos(Math.toRadians(currentAngle))*segmentY);
					deltas.add(new Float[]{correctedY, correctedZ, angle});
					//Decrement distance traveled off the variables.
					totalDistance -= spacing;
					segmentDeltaTotal -= spacing;
					segmentDeltaY -= segmentDeltaY*segmentPercentage;
					segmentDeltaZ -= segmentDeltaZ*segmentPercentage;
					deltaBeforeSegment = 0;
					deltaYBeforeSegment = 0;
					deltaZBeforeSegment = 0;
					angle = 0;
				}else{
					//If we have half or more a link left, make an extra one before exiting.
					if(deltaBeforeSegment + segmentDeltaTotal > spacing/2F){
						deltas.add(deltas.get(deltas.size() - 1));
					}
					totalDistance = 0;
				}
			}
			//Add the finalized delta list to the map.
			treadDeltas.put(treadPart.vehicle.vehicleJSONName, deltas);
		}
		
		
		float treadMovementPercentage = (float) ((treadPart.angularPosition + treadPart.angularVelocity*partialTicks)*treadPart.getHeight()/Math.PI%treadPart.pack.tread.spacing/treadPart.pack.tread.spacing);
		if(Keyboard.isKeyDown(Keyboard.KEY_END)){
			GL11.glPushMatrix();
			//First translate to the initial point.
			GL11.glTranslated(treadPart.offset.x, treadPart.offset.y + partDef.treadYPoints[0], treadPart.offset.z + partDef.treadZPoints[0]);
			//Next use the deltas to get the amount needed to translate and rotate each link.
			for(Float[] point : deltas){
				if(point[2] != 0){
					GL11.glRotatef(point[2], 1, 0, 0);
					GL11.glTranslatef(0, point[0]*treadMovementPercentage, point[1]*treadMovementPercentage);
					GL11.glRotatef(-point[2]*(1 - treadMovementPercentage), 1, 0, 0);
					GL11.glCallList(displayListIndex);
					GL11.glRotatef(point[2]*(1 - treadMovementPercentage), 1, 0, 0);
					GL11.glTranslatef(0, point[0]*(1 - treadMovementPercentage), point[1]*( 1 - treadMovementPercentage));
				}else{
					GL11.glTranslatef(0, point[0]*treadMovementPercentage, point[1]*treadMovementPercentage);
					GL11.glCallList(displayListIndex);
					GL11.glTranslatef(0, point[0]*(1 - treadMovementPercentage), point[1]*( 1 - treadMovementPercentage));
				}
				
			}
			GL11.glPopMatrix();
		}
		
		//Render the treads along their points.
		GL11.glPushMatrix();
		GL11.glTranslated(treadPart.offset.x, 0, 0);
		//For the first point, we translate to the point itself.
		//For each remaining point, we only translate the delta of the point.
		List<Double[]> altPoints = treadAltDeltas.get(treadPart.vehicle.vehicleJSONName);
		Double[] point = altPoints.get(0);
		Double[] nextPoint;
		double yDelta = point[0];
		double zDelta = point[1];
		for(int i=0; i<altPoints.size(); ++i){
			//Get current points.
			point = altPoints.get(i);
			nextPoint = i == altPoints.size() - 1 ? altPoints.get(0) : altPoints.get(i + 1);
			if(i == treadPart.vehicle.ticksExisted%altPoints.size()){
				GL11.glColor3f(1.0F, 0.0F, 0.0F);
				GL11.glDisable(GL11.GL_TEXTURE_2D);
				GL11.glDisable(GL11.GL_LIGHTING);
			}else{
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				GL11.glEnable(GL11.GL_LIGHTING);
				GL11.glColor3f(1.0F, 1.0F, 1.0F);
			}
			//If there's no rotation to the point, don't do rotation.
			//That's an expensive operation due to sin and cos operations.
			if(point[2] == 0.000001){
			//if(point[2] != 0){
				//We can't use a running rotation here as we'll end up translating in the rotated
				//coordinate system.  To combat this, we translate like normal, but then push a
				//stack and rotate prior to rendering.  This keeps us from having to do another
				//rotation to get the old coordinate system back.
				GL11.glPushMatrix();
				GL11.glTranslated(0, yDelta*treadMovementPercentage, zDelta*treadMovementPercentage);
				GL11.glRotated(point[2]*treadMovementPercentage, 1, 0, 0);
				GL11.glCallList(displayListIndex);
				GL11.glPopMatrix();
				GL11.glTranslated(0, yDelta, zDelta);
			}else{
				//Translate to the current position of the tread based on the percent it has moved.
				//This is determined by partial ticks and actual tread position.
				//Once there, render the tread.  Then translate the remainder of the way to prepare
				//to render the next tread.
				GL11.glTranslated(0, yDelta*treadMovementPercentage, zDelta*treadMovementPercentage);
				GL11.glCallList(displayListIndex);
				GL11.glTranslated(0, yDelta*(1 - treadMovementPercentage), zDelta*( 1 - treadMovementPercentage));
			}
			
			//Increment deltas.
			yDelta = nextPoint[0] - point[0];
			zDelta = nextPoint[1] - point[1];
		}
		GL11.glPopMatrix();
	}
	
	private static void renderWindows(EntityVehicleE_Powered vehicle, float partialTicks){
		minecraft.getTextureManager().bindTexture(vanillaGlassTexture);
		//Iterate through all windows.
		for(byte i=0; i<vehicleWindowLists.get(vehicle.vehicleJSONName).size(); ++i){
			if(i >= vehicle.brokenWindows){
				GL11.glPushMatrix();
				//This is a window or set of windows.  Like the model, it will be triangle-based.
				//However, windows may be rotatable or translatable.  Check this before continuing.
				WindowPart window = vehicleWindowLists.get(vehicle.vehicleJSONName).get(i);
				for(RotatablePart rotatable : vehicleRotatableLists.get(vehicle.vehicleJSONName)){
					if(rotatable.name.equals(window.name)){
						rotateModelObject(vehicle, rotatable, partialTicks);
					}
				}
				for(TranslatablePart translatable : vehicleTranslatableLists.get(vehicle.vehicleJSONName)){
					if(translatable.name.equals(window.name)){
						translateModelObject(vehicle, translatable, partialTicks);
					}
				}
				GL11.glBegin(GL11.GL_TRIANGLES);
				for(Float[] vertex : window.vertices){
					GL11.glTexCoord2f(vertex[3], vertex[4]);
					GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
					GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
				}
				if(ConfigSystem.getBooleanConfig("InnerWindows")){
					for(int j=window.vertices.length-1; j >= 0; --j){
						Float[] vertex = window.vertices[j];
						GL11.glTexCoord2f(vertex[3], vertex[4]);
						GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
						GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);	
					}
				}
				GL11.glEnd();
				GL11.glPopMatrix();
			}
		}
	}
	
	private static void renderTextMarkings(EntityVehicleE_Powered vehicle){
		if(vehicle.pack.rendering.textLighted && RenderInstruments.lightsOn(vehicle)){
			GL11.glDisable(GL11.GL_LIGHTING);
			minecraft.entityRenderer.disableLightmap();
		}
		for(PackDisplayText text : vehicle.pack.rendering.textMarkings){
			GL11.glPushMatrix();
			GL11.glTranslatef(text.pos[0], text.pos[1], text.pos[2]);
			GL11.glScalef(1F/16F, 1F/16F, 1F/16F);
			//First rotate 180 along the X-axis to get us rendering right-side up.
			GL11.glRotatef(180F, 1, 0, 0);
			
			//Next, apply rotations.  Only doing so if they exist.
			//Apply the Y-rotation first as it will always be used and allows for correct X-rotations.
			if(text.rot[1] != 0){
				GL11.glRotatef(-text.rot[1], 0, 1, 0);
			}
			if(text.rot[0] != 0){
				GL11.glRotatef(text.rot[0], 1, 0, 0);
			}
			if(text.rot[2] != 0){
				GL11.glRotatef(text.rot[2], 0, 0, 1);
			}
			
			//Finally, scale and render the text.
			GL11.glScalef(text.scale, text.scale, text.scale);
			RenderHelper.disableStandardItemLighting();
			minecraft.fontRenderer.drawString(vehicle.displayText, -minecraft.fontRenderer.getStringWidth(vehicle.displayText)/2, 0, Color.decode(text.color).getRGB());
			GL11.glPopMatrix();
		}
		GL11.glColor3f(1.0F, 1.0F, 1.0F);
		RenderHelper.enableStandardItemLighting();
		if(vehicle.pack.rendering.textLighted){
			GL11.glEnable(GL11.GL_LIGHTING);
			minecraft.entityRenderer.enableLightmap();
		}
	}
	
	private static void renderLights(EntityVehicleE_Powered vehicle, float sunLight, float blockLight, float lightBrightness, float electricFactor, boolean wasRenderedPrior, float partialTicks){
		List<LightPart> vehicleLights = vehicleLightLists.get(vehicle.vehicleJSONName);
		Map<Integer, APart> lightIndexToParts = new HashMap<Integer, APart>();
		List<LightPart> allLights = new ArrayList<LightPart>();
		allLights.addAll(vehicleLights);
		for(APart part : vehicle.getVehicleParts()){
			if(partLightLists.containsKey(part.getModelLocation())){
				for(LightPart partLight : partLightLists.get(part.getModelLocation())){
					lightIndexToParts.put(allLights.size(), part);
					allLights.add(partLight);
				}
			}
		}
		
		for(int lightIndex=0; lightIndex<allLights.size(); ++lightIndex){
			LightPart light = allLights.get(lightIndex);
			boolean lightSwitchOn = vehicle.isLightOn(light.type);
			//Fun with bit shifting!  20 bits make up the light on index here, so align to a 20 tick cycle.
			boolean lightActuallyOn = lightSwitchOn && ((light.flashBits >> vehicle.world.getTotalWorldTime()%20) & 1) > 0;
			//Used to make the cases of the lights full brightness.  Used when lights are brighter than the surroundings.
			boolean overrideCaseBrightness = lightBrightness > Math.max(sunLight, blockLight) && lightActuallyOn;
			
			GL11.glPushMatrix();
			//This light may be rotatable.  Check this before continuing.
			//It could rotate based on a vehicle rotation variable, or a part rotation.
			if(vehicleLights.contains(light)){
				for(RotatablePart rotatable : vehicleRotatableLists.get(vehicle.vehicleJSONName)){
					if(rotatable.name.equals(light.name)){
						rotateModelObject(vehicle, rotatable, partialTicks);
					}
				}
			}else{
				APart part = lightIndexToParts.get(lightIndex);
				GL11.glTranslated(part.offset.x, part.offset.y, part.offset.z);
				rotatePart(part, part.getActionRotation(partialTicks), false);
				for(RotatablePart rotatable : partRotatableLists.get(part.getModelLocation())){
					if(rotatable.name.equals(light.name)){
						rotatePartObject(part, rotatable, partialTicks);
					}
				}
			}

			if(MinecraftForgeClient.getRenderPass() != 1 && !wasRenderedPrior){
				GL11.glPushMatrix();
				if(overrideCaseBrightness){
					GL11.glDisable(GL11.GL_LIGHTING);
					minecraft.entityRenderer.disableLightmap();
				}else{
					GL11.glEnable(GL11.GL_LIGHTING);
					minecraft.entityRenderer.enableLightmap();
				}
				GL11.glDisable(GL11.GL_BLEND);
				//Cover rendering.
				if(light.renderCover){
					minecraft.getTextureManager().bindTexture(vanillaGlassTexture);
					GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
					GL11.glBegin(GL11.GL_TRIANGLES);
					for(Float[] vertex : light.vertices){
						//Add a slight translation and scaling to the light coords based on the normals to make the lens cover.
						//Also modify the cover size to ensure the whole cover is a single glass square.
						GL11.glTexCoord2f(vertex[3], vertex[4]);
						GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
						GL11.glVertex3f(vertex[0]+vertex[5]*0.0003F, vertex[1]+vertex[6]*0.0003F, vertex[2]+vertex[7]*0.0003F);	
					}
					GL11.glEnd();
				}
				
				//Light rendering.
				if(lightActuallyOn && light.renderColor){
					GL11.glDisable(GL11.GL_LIGHTING);
					GL11.glEnable(GL11.GL_BLEND);
					minecraft.getTextureManager().bindTexture(lightTexture);
					GL11.glColor4f(light.color.getRed()/255F, light.color.getGreen()/255F, light.color.getBlue()/255F, electricFactor);
					GL11.glBegin(GL11.GL_TRIANGLES);
					for(Float[] vertex : light.vertices){
						//Add a slight translation and scaling to the light coords based on the normals to make the light.
						GL11.glTexCoord2f(vertex[3], vertex[4]);
						GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
						GL11.glVertex3f(vertex[0]+vertex[5]*0.0001F, vertex[1]+vertex[6]*0.0001F, vertex[2]+vertex[7]*0.0001F);	
					}
					GL11.glEnd();
					GL11.glDisable(GL11.GL_BLEND);
					GL11.glEnable(GL11.GL_LIGHTING);
				}
				GL11.glPopMatrix();
			}
			
			//Lens flare.
			if(lightActuallyOn && lightBrightness > 0 && MinecraftForgeClient.getRenderPass() != 0 && !wasRenderedPrior && light.renderFlare){
				for(byte i=0; i<light.centerPoints.length; ++i){
					GL11.glPushMatrix();
					GL11.glEnable(GL11.GL_BLEND);
					GL11.glDisable(GL11.GL_LIGHTING);
					minecraft.entityRenderer.disableLightmap();
					minecraft.getTextureManager().bindTexture(lensFlareTexture);
					GL11.glColor4f(light.color.getRed()/255F, light.color.getGreen()/255F, light.color.getBlue()/255F, lightBrightness);
					GL11.glBegin(GL11.GL_TRIANGLES);
					for(byte j=0; j<6; ++j){
						Float[] vertex = light.vertices[((short) i)*6+j];
						//Add a slight translation to the light size to make the flare move off it.
						//Then apply scaling factor to make the flare larger than the light.
						GL11.glTexCoord2f(vertex[3], vertex[4]);
						GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
						GL11.glVertex3d(vertex[0]+vertex[5]*0.0002F + (vertex[0] - light.centerPoints[i].x)*(2 + light.size[i]*0.25F), 
								vertex[1]+vertex[6]*0.0002F + (vertex[1] - light.centerPoints[i].y)*(2 + light.size[i]*0.25F), 
								vertex[2]+vertex[7]*0.0002F + (vertex[2] - light.centerPoints[i].z)*(2 + light.size[i]*0.25F));	
					}
					GL11.glEnd();
					GL11.glPopMatrix();
				}
			}
			
			//Render beam if light has one.
			if(lightActuallyOn && lightBrightness > 0 && light.type.hasBeam && MinecraftForgeClient.getRenderPass() == -1){
				GL11.glPushMatrix();
		    	GL11.glDisable(GL11.GL_LIGHTING);
		    	GL11.glEnable(GL11.GL_BLEND);
		    	minecraft.entityRenderer.disableLightmap();
				minecraft.getTextureManager().bindTexture(lightBeamTexture);
		    	GL11.glColor4f(1, 1, 1, Math.min(vehicle.electricPower > 4 ? 1.0F : 0, lightBrightness/2F));
		    	//Allows making things brighter by using alpha blending.
		    	GL11.glDepthMask(false);
		    	GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_SRC_ALPHA);
				
				//As we can have more than one light per definition, we will only render 6 vertices at a time.
				//Use the center point arrays for this; normals are the same for all 6 vertex sets so use whichever.
				for(byte i=0; i<light.centerPoints.length; ++i){
					GL11.glPushMatrix();
					//Translate light to the center of the cone beam.
					GL11.glTranslated(light.centerPoints[i].x - light.vertices[i*6][5]*0.15F, light.centerPoints[i].y - light.vertices[i*6][6]*0.15F, light.centerPoints[i].z - light.vertices[i*6][7]*0.15F);
					//Rotate beam to the normal face.
					GL11.glRotatef((float) Math.toDegrees(Math.atan2(light.vertices[i*6][6], light.vertices[i*6][5])), 0, 0, 1);
					GL11.glRotatef((float) Math.toDegrees(Math.acos(light.vertices[i*6][7])), 0, 1, 0);
					//Now draw the beam
					GL11.glDepthMask(false);
					for(byte j=0; j<=2; ++j){
			    		drawLightCone(light.size[i], false);
			    	}
					drawLightCone(light.size[i], true);
					GL11.glPopMatrix();
				}
		    	GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		    	GL11.glDepthMask(true);
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glEnable(GL11.GL_LIGHTING);
				GL11.glPopMatrix();
			}
			GL11.glPopMatrix();
		}
	}
	
    private static void drawLightCone(double radius, boolean reverse){
		GL11.glBegin(GL11.GL_TRIANGLE_FAN);
		GL11.glTexCoord2f(0, 0);
		GL11.glVertex3d(0, 0, 0);
    	if(reverse){
    		for(float theta=0; theta < 2*Math.PI + 0.1; theta += 2F*Math.PI/40F){
    			GL11.glTexCoord2f(theta, 1);
    			GL11.glVertex3d(radius*Math.cos(theta), radius*Math.sin(theta), radius*3F);
    		}
    	}else{
    		for(float theta=(float) (2*Math.PI); theta>=0 - 0.1; theta -= 2F*Math.PI/40F){
    			GL11.glTexCoord2f(theta, 1);
    			GL11.glVertex3d(radius*Math.cos(theta), radius*Math.sin(theta), radius*3F);
    		}
    	}
    	GL11.glEnd();
    }
	
	private static void renderInstrumentsAndControls(EntityVehicleE_Powered vehicle){
		GL11.glPushMatrix();
		for(byte i=0; i<vehicle.pack.motorized.instruments.size(); ++i){
			PackInstrument packInstrument = vehicle.pack.motorized.instruments.get(i);
			GL11.glPushMatrix();
			GL11.glTranslatef(packInstrument.pos[0], packInstrument.pos[1], packInstrument.pos[2]);
			GL11.glRotatef(packInstrument.rot[0], 1, 0, 0);
			GL11.glRotatef(packInstrument.rot[1], 0, 1, 0);
			GL11.glRotatef(packInstrument.rot[2], 0, 0, 1);
			GL11.glScalef(packInstrument.scale/16F, packInstrument.scale/16F, packInstrument.scale/16F);
			VehicleInstrument instrument = vehicle.getInstrumentInfoInSlot(i);
			if(instrument != null){
				RenderInstruments.drawInstrument(vehicle, instrument, false, packInstrument.optionalEngineNumber);
			}
			GL11.glPopMatrix();
		}
		for(byte i=0; i<vehicle.pack.motorized.controls.size(); ++i){
			PackControl packControl = vehicle.pack.motorized.controls.get(i);
			GL11.glPushMatrix();
			GL11.glTranslatef(packControl.pos[0], packControl.pos[1], packControl.pos[2]);
			GL11.glScalef(1F/16F/16F, 1F/16F/16F, 1F/16F/16F);
			for(Controls control : Controls.values()){
				if(control.name().toLowerCase().equals(packControl.controlName)){
					RenderControls.drawControl(vehicle, control, false);
				}
			}
			GL11.glPopMatrix();
		}
		GL11.glPopMatrix();
	}
	
	private static void renderBoundingBoxes(EntityVehicleE_Powered vehicle){
		GL11.glPushMatrix();
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glColor3f(0.0F, 0.0F, 0.0F);
		GL11.glLineWidth(3.0F);
		for(VehicleAxisAlignedBB box : vehicle.getCurrentCollisionBoxes()){
			GL11.glBegin(GL11.GL_LINES);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y - box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y - box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y - box.height/2F, box.rel.z + box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y - box.height/2F, box.rel.z + box.width/2F);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y + box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y + box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y + box.height/2F, box.rel.z + box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y + box.height/2F, box.rel.z + box.width/2F);
			
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y - box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y - box.height/2F, box.rel.z + box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y - box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y - box.height/2F, box.rel.z + box.width/2F);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y + box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y + box.height/2F, box.rel.z + box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y + box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y + box.height/2F, box.rel.z + box.width/2F);
			
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y - box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y + box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y - box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y + box.height/2F, box.rel.z - box.width/2F);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y - box.height/2F, box.rel.z + box.width/2F);
			GL11.glVertex3d(box.rel.x - box.width/2F, box.rel.y + box.height/2F, box.rel.z + box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y - box.height/2F, box.rel.z + box.width/2F);
			GL11.glVertex3d(box.rel.x + box.width/2F, box.rel.y + box.height/2F, box.rel.z + box.width/2F);
			GL11.glEnd();
		}
		GL11.glLineWidth(1.0F);
		GL11.glColor3f(1.0F, 1.0F, 1.0F);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glPopMatrix();
	}
	
	private static void renderPartBoxes(EntityVehicleE_Powered vehicle){
		EntityPlayer player = minecraft.player;
		ItemStack heldStack = player.getHeldItemMainhand();
		if(heldStack != null){
			if(heldStack.getItem() instanceof AItemPart){
				AItemPart heldItem = (AItemPart) heldStack.getItem();
				for(Entry<Vec3d, PackPart> packPartEntry : vehicle.getAllPossiblePackParts().entrySet()){
					boolean isPresent = false;
					boolean isHoldingPart = false;
					boolean isPartValid = false;
					
					if(vehicle.getPartAtLocation(packPartEntry.getKey().x, packPartEntry.getKey().y, packPartEntry.getKey().z) != null){
						isPresent = true;
					}
					
					PackPartObject heldItemPack = PackParserSystem.getPartPack(heldItem.partName);
					if(packPartEntry.getValue().types.contains(PackParserSystem.getPartPack(heldItem.partName).general.type)){
						isHoldingPart = true;
						if(heldItem.isPartValidForPackDef(packPartEntry.getValue())){
							isPartValid = true;
						}
					}
							
					if(!isPresent && isHoldingPart){
						Vec3d offset = RotationSystem.getRotatedPoint(packPartEntry.getKey(), vehicle.rotationPitch, vehicle.rotationYaw, vehicle.rotationRoll);
						//If we are a custom part, use the custom hitbox.  Otherwise use the regular one.
						AxisAlignedBB box;
						if(packPartEntry.getValue().types.contains("custom") && heldItemPack.general.type.equals("custom")){
							box = new AxisAlignedBB((float) (offset.x) - heldItemPack.custom.width/2F, (float) (offset.y) - heldItemPack.custom.height/2F, (float) (offset.z) - heldItemPack.custom.width/2F, (float) (offset.x) + heldItemPack.custom.width/2F, (float) (offset.y) + heldItemPack.custom.height/2F, (float) (offset.z) + heldItemPack.custom.width/2F);		
						}else{
							box = new AxisAlignedBB((float) (offset.x) - 0.375F, (float) (offset.y) - 0.5F, (float) (offset.z) - 0.375F, (float) (offset.x) + 0.375F, (float) (offset.y) + 1.25F, (float) (offset.z) + 0.375F);
						}
						
						GL11.glPushMatrix();
						GL11.glDisable(GL11.GL_TEXTURE_2D);
						GL11.glDisable(GL11.GL_LIGHTING);
						GL11.glEnable(GL11.GL_BLEND);
						if(isPartValid){
							GL11.glColor4f(0, 1, 0, 0.25F);
						}else{
							GL11.glColor4f(1, 0, 0, 0.25F);
						}
						GL11.glBegin(GL11.GL_QUADS);
						
						GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
						GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
						GL11.glVertex3d(box.minX, box.minY, box.maxZ);
						GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
						
						GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
						GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
						GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
						GL11.glVertex3d(box.maxX, box.minY, box.minZ);
						
						GL11.glVertex3d(box.maxX, box.minY, box.minZ);
						GL11.glVertex3d(box.minX, box.minY, box.minZ);
						GL11.glVertex3d(box.minX, box.maxY, box.minZ);
						GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
						
						GL11.glVertex3d(box.minX, box.minY, box.minZ);
						GL11.glVertex3d(box.minX, box.minY, box.maxZ);
						GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
						GL11.glVertex3d(box.minX, box.maxY, box.minZ);
						
						GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
						GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
						GL11.glVertex3d(box.minX, box.maxY, box.minZ);
						GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
						
						GL11.glVertex3d(box.minX, box.minY, box.maxZ);
						GL11.glVertex3d(box.minX, box.minY, box.minZ);
						GL11.glVertex3d(box.maxX, box.minY, box.minZ);
						GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
						GL11.glEnd();

						GL11.glColor4f(1, 1, 1, 1);
						GL11.glDisable(GL11.GL_BLEND);
						GL11.glEnable(GL11.GL_LIGHTING);
						GL11.glEnable(GL11.GL_TEXTURE_2D);
						GL11.glPopMatrix();
					}
				}
			}
		}
	}
	
	private static final class RotatablePart{
		private final String name;
		private final Float[][] vertices;
		
		private final Vec3d[] rotationPoints;
		private final Vec3d[] rotationAxis;
		private final float[] rotationMagnitudes;
		private final String[] rotationVariables;
		
		private RotatablePart(String name, Float[][] vertices, List<PackRotatableModelObject> rotatableModelObjects){
			this.name = name.toLowerCase();
			this.vertices = vertices;
			this.rotationPoints = getRotationPoints(name, rotatableModelObjects);
			
			Vec3d rotationAxisTemp[] = getRotationAxis(name, rotatableModelObjects);
			this.rotationAxis = new Vec3d[rotationAxisTemp.length];
			this.rotationMagnitudes = new float[rotationAxisTemp.length];
			for(byte i=0; i<rotationAxisTemp.length; ++i){
				rotationAxis[i] = rotationAxisTemp[i].normalize();
				rotationMagnitudes[i] = (float) rotationAxisTemp[i].lengthVector();
			}
			this.rotationVariables = getRotationVariables(name, rotatableModelObjects);
		}
		
		private static Vec3d[] getRotationPoints(String name, List<PackRotatableModelObject> rotatableModelObjects){
			List<Vec3d> rotationPoints = new ArrayList<Vec3d>();
			for(PackRotatableModelObject rotatable : rotatableModelObjects){
				if(rotatable.partName.equals(name)){
					if(rotatable.rotationPoint != null){
						rotationPoints.add(new Vec3d(rotatable.rotationPoint[0], rotatable.rotationPoint[1], rotatable.rotationPoint[2]));
					}
				}
			}
			return rotationPoints.toArray(new Vec3d[rotationPoints.size()]);
		}
		
		private static Vec3d[] getRotationAxis(String name, List<PackRotatableModelObject> rotatableModelObjects){
			List<Vec3d> rotationAxis = new ArrayList<Vec3d>();
			for(PackRotatableModelObject rotatable : rotatableModelObjects){
				if(rotatable.partName.equals(name)){
					if(rotatable.rotationAxis != null){
						rotationAxis.add(new Vec3d(rotatable.rotationAxis[0], rotatable.rotationAxis[1], rotatable.rotationAxis[2]));
					}
				}
			}
			return rotationAxis.toArray(new Vec3d[rotationAxis.size()]);
		}
		
		private static String[] getRotationVariables(String name, List<PackRotatableModelObject> rotatableModelObjects){
			List<String> rotationVariables = new ArrayList<String>();
			for(PackRotatableModelObject rotatable : rotatableModelObjects){
				if(rotatable.partName.equals(name)){
					if(rotatable.partName != null){
						rotationVariables.add(rotatable.rotationVariable.toLowerCase());
					}
				}
			}
			return rotationVariables.toArray(new String[rotationVariables.size()]);
		}
	}
	
	private static final class TranslatablePart{
		private final String name;
		private final Float[][] vertices;
		
		private final Vec3d[] translationAxis;
		private final float[] translationMagnitudes;
		private final String[] translationVariables;
		
		private TranslatablePart(String name, Float[][] vertices, List<PackTranslatableModelObject> translatableModelObjects){
			this.name = name.toLowerCase();
			this.vertices = vertices;
			
			Vec3d translationAxisTemp[] = getRotationAxis(name, translatableModelObjects);
			this.translationAxis = new Vec3d[translationAxisTemp.length];
			this.translationMagnitudes = new float[translationAxisTemp.length];
			for(byte i=0; i<translationAxisTemp.length; ++i){
				translationAxis[i] = translationAxisTemp[i].normalize();
				translationMagnitudes[i] = (float) translationAxisTemp[i].lengthVector();
			}
			this.translationVariables = getRotationVariables(name, translatableModelObjects);
		}
		
		private static Vec3d[] getRotationAxis(String name, List<PackTranslatableModelObject> translatableModelObjects){
			List<Vec3d> translationAxis = new ArrayList<Vec3d>();
			for(PackTranslatableModelObject translatable : translatableModelObjects){
				if(translatable.partName.equals(name)){
					if(translatable.translationAxis != null){
						translationAxis.add(new Vec3d(translatable.translationAxis[0], translatable.translationAxis[1], translatable.translationAxis[2]));
					}
				}
			}
			return translationAxis.toArray(new Vec3d[translationAxis.size()]);
		}
		
		private static String[] getRotationVariables(String name, List<PackTranslatableModelObject> translatableModelObjects){
			List<String> translationVariables = new ArrayList<String>();
			for(PackTranslatableModelObject translatable : translatableModelObjects){
				if(translatable.partName.equals(name)){
					if(translatable.partName != null){
						translationVariables.add(translatable.translationVariable.toLowerCase());
					}
				}
			}
			return translationVariables.toArray(new String[translationVariables.size()]);
		}
	}
	
	private static final class WindowPart{
		private final String name;
		private final Float[][] vertices;
		
		private WindowPart(String name, Float[][] vertices){
			this.name = name.toLowerCase();
			this.vertices = vertices;
		}
	}
	
	private static final class LightPart{
		private final String name;
		private final LightTypes type;
		private final Float[][] vertices;
		private final Vec3d[] centerPoints;
		private final Float[] size;
		private final Color color;
		private final int flashBits;
		private final boolean renderFlare;
		private final boolean renderColor;
		private final boolean renderCover;
		
		private LightPart(String name, Float[][] masterVertices){
			this.name = name.toLowerCase();
			this.type = getTypeFromName(name);
			this.vertices = new Float[masterVertices.length][];
			this.centerPoints = new Vec3d[masterVertices.length/6];
			this.size = new Float[masterVertices.length/6];
			
			for(short i=0; i<centerPoints.length; ++i){
				double minX = 999;
				double maxX = -999;
				double minY = 999;
				double maxY = -999;
				double minZ = 999;
				double maxZ = -999;
				for(byte j=0; j<6; ++j){
					Float[] masterVertex = masterVertices[i*6 + j];
					minX = Math.min(masterVertex[0], minX);
					maxX = Math.max(masterVertex[0], maxX);
					minY = Math.min(masterVertex[1], minY);
					maxY = Math.max(masterVertex[1], maxY);
					minZ = Math.min(masterVertex[2], minZ);
					maxZ = Math.max(masterVertex[2], maxZ);
					
					Float[] newVertex = new Float[masterVertex.length];
					newVertex[0] = masterVertex[0];
					newVertex[1] = masterVertex[1];
					newVertex[2] = masterVertex[2];
					//Adjust UV point here to change this to glass coords.
					switch(j){
						case(0): newVertex[3] = 0.0F; newVertex[4] = 0.0F; break;
						case(1): newVertex[3] = 0.0F; newVertex[4] = 1.0F; break;
						case(2): newVertex[3] = 1.0F; newVertex[4] = 1.0F; break;
						case(3): newVertex[3] = 1.0F; newVertex[4] = 1.0F; break;
						case(4): newVertex[3] = 1.0F; newVertex[4] = 0.0F; break;
						case(5): newVertex[3] = 0.0F; newVertex[4] = 0.0F; break;
					}
					newVertex[5] = masterVertex[5];
					newVertex[6] = masterVertex[6];
					newVertex[7] = masterVertex[7];
					
					this.vertices[((short) i)*6 + j] = newVertex;
				}
				this.centerPoints[i] = new Vec3d(minX + (maxX - minX)/2D, minY + (maxY - minY)/2D, minZ + (maxZ - minZ)/2D);
				this.size[i] = (float) Math.max(Math.max(maxX - minX, maxZ - minZ), maxY - minY)*32F;
			}
			//Lights are in the format of "&NAME_XXXXXX_YYYYY_ZZZ"
			//Where NAME is what switch it goes to.
			//XXXXXX is the color.
			//YYYYY is the blink rate.
			//ZZZ is the light type.  The first bit renders the flare, the second the color, and the third the cover.
			this.color = Color.decode("0x" + name.substring(name.indexOf('_') + 1, name.indexOf('_') + 7));
			this.flashBits = Integer.decode("0x" + name.substring(name.indexOf('_', name.indexOf('_') + 7) + 1, name.lastIndexOf('_')));
			this.renderFlare = Integer.valueOf(name.substring(name.length() - 3, name.length() - 2)) > 0;
			this.renderColor = Integer.valueOf(name.substring(name.length() - 2, name.length() - 1)) > 0;
			this.renderCover = Integer.valueOf(name.substring(name.length() - 1)) > 0;
		}
		
		private LightTypes getTypeFromName(String lightName){
			for(LightTypes light : LightTypes.values()){
				if(lightName.toLowerCase().contains(light.name().toLowerCase())){
					return light;
				}
			}
			return null;
		}
	}
}
