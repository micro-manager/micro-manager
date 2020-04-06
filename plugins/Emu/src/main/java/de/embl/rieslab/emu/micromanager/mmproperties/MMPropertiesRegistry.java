package de.embl.rieslab.emu.micromanager.mmproperties;

import java.util.HashMap;
import java.util.Iterator;

import de.embl.rieslab.emu.controller.log.Logger;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.Studio;

/**
 * Class referencing the devices loaded in Micro-Manager and their device properties. 
 * 
 * @author Joran Deschamps
 *
 */
@SuppressWarnings("rawtypes")
public class MMPropertiesRegistry {

   private final Studio studio_;
	private final CMMCore core_;
	private final Logger logger_;
	private final HashMap<String, MMDevice> devices_;
	private final HashMap<String, MMProperty> properties_;
	
	/**
	 * Constructor. Calls a private initialization method to extract the devices and their properties. It ignores "COM" devices.
	 * 
	 * @param studio MM studio instance
	 * @param logger EMU logger
	 */
	public MMPropertiesRegistry(Studio studio, Logger logger){
      studio_ = studio;
		core_ = studio.getCMMCore();
		logger_ = logger;
		devices_ = new HashMap<>();
		properties_ = new HashMap<>();
		
		initialize();
	}
	
	private void initialize() {
		StrVector deviceList = core_.getLoadedDevices();
		StrVector propertyList;
		MMPropertyFactory builder = new MMPropertyFactory(core_, logger_);

		for (String device : deviceList) {
			if(! ( (device.length() >= 3) && device.substring(0, 3).equals("COM")) ){
				MMDevice dev = new MMDevice(device);
				try {
					propertyList = core_.getDevicePropertyNames(device);
					for (String property : propertyList) {
						MMProperty prop = builder.getNewProperty(device, property);
						dev.registerProperty(prop);
						properties_.put(prop.getHash(),prop);
					}
				} catch (Exception e) {
               studio_.logs().logError(e);
				}
				devices_.put(dev.getDeviceLabel(),dev);
			}
		}
	}
	
	/**
	 * Returns the property with hash {@code propertyHash} (see {@link MMProperty} for the definition of the hash).
	 * 
	 * @param propertyHash Hash of the requested property.
	 * @return Micro-manager property.
	 */
	public MMProperty getProperty(String propertyHash){
		return properties_.get(propertyHash);
	}
	
	/**
	 * Returns the map of {@link MMProperty} indexed by their hash.
	 * 
	 * @return Micro-manager properties map.
	 */
	public HashMap<String, MMProperty> getProperties(){
		return properties_;
	}
	
	/**
	 * Returns the device with label {@code deviceLabel}.
	 * 
	 * @param deviceLabel Label of the device
	 * @return Micro-manager device or null if the device does not exists.
	 */
	public MMDevice getDevice(String deviceLabel){
		return devices_.get(deviceLabel);
	}
	
	/**
	 * Returns the map of {@link MMDevice} indexed by their label.
	 * 
	 * @return Micro-manager devices map.
	 */
	public HashMap<String, MMDevice> getDevices(){
		return devices_;
	}
	
	/**
	 * Returns the names of the devices in a String array. 
	 * 
	 * @return Array of device names
	 */
	public String[] getDevicesList(){
		String[] s = new String[devices_.size()];
		devices_.keySet().toArray(s);
		return s;
	}
	
	/**
	 * Adds a device to the map of devices.
	 * 
	 * @param device Device to be added.
	 */
	public void addMMDevice(MMDevice device){
		if(device.getProperties().size() > 0){
			devices_.put(device.getDeviceLabel(),device);
			properties_.putAll(device.getProperties());
		}
	}
	
	/**
	 * Checks if {@code hash} corresponds to a known Micro-manager device property.
	 * 
	 * @param hash Hash to be tested
	 * @return True if the hash corresponds to a device property, false otherwise.
	 */
	public boolean isProperty(String hash){
		return properties_.containsKey(hash);
	}

	/**
	 * Clears all Micro-manager device property listeners (which are of the class UIProperty). 
	 * Called during reloading of the system by the {@link de.embl.rieslab.emu.controller.SystemController}.
	 * 
	 */
	public void clearAllListeners(){
		Iterator<String> it = properties_.keySet().iterator();
		while(it.hasNext()){
			properties_.get(it.next()).clearAllListeners();
		}
	}
}
