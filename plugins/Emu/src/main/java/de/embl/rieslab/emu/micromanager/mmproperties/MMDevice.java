package de.embl.rieslab.emu.micromanager.mmproperties;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Class representing a device loaded in Micro-Manager. It holds a map for the different 
 * device properties. The class is used to sort the properties in the UI configuration Wizard
 * (PropertiesTable).
 * 
 * @author Joran Deschamps
 *
 */
public class MMDevice {

	@SuppressWarnings("rawtypes")
	private HashMap<String, MMProperty> properties_;
	private String label_;
	
	/**
	 * Creates a device with label {@code deviceLabel}.
	 * 
	 * @param deviceLabel Label of the device
	 */
	@SuppressWarnings("rawtypes")
	public MMDevice(String deviceLabel){
		this.label_ = deviceLabel;
		properties_ = new HashMap<String,MMProperty>();
	}
	
	/**
	 * Adds the property {@code p} to the properties map. If a property with the same hash already exists,
	 * then nothing happens.
	 * 
	 * @param p MMproperty to register
	 */
	public void registerProperty(@SuppressWarnings("rawtypes") MMProperty p){
		if(!hasHashProperty(p.getMMPropertyLabel())){
			properties_.put(p.getHash(), p);
		}
	}

	/**
	 * Checks if the properties map already contains the a property indexed by {@code propertyHash}.
	 * 
	 * @param propertyHash Hash of the property.
	 * @return True if the property hash is contained in the properties map, false otherwise.
	 */
	public boolean hasHashProperty(String propertyHash){
		return properties_.containsKey(propertyHash);
	}

	/**
	 * Same as {@link #hasHashProperty(String)}, albeit with the property label instead of the hash.
	 * 
	 * @param propertyLabel Label of the property.
	 * @return True if a property with the same label exists in the map, false otherwise.
	 */
	public boolean hasLabelProperty(String propertyLabel){
		Iterator<String> keys = properties_.keySet().iterator();
		while(keys.hasNext()){
			String prop = keys.next();
			if(properties_.get(prop).getMMPropertyLabel().equals(propertyLabel)){
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Returns the hash of the property with label {@code propertyLabel}. If the property is not found,
	 * the method returns null.
	 * 
	 * @param propertyLabel Label of the property.
	 * @return Hash of the corresponding property, null if the property is unknown.
	 */
	public String getHashFromLabel(String propertyLabel){
		Iterator<String> keys = properties_.keySet().iterator();
		while(keys.hasNext()){
			String prop = keys.next();
			if(properties_.get(prop).getMMPropertyLabel().equals(propertyLabel)){
				return prop;
			}
		}
		return null;
	}
	
	/**
	 * Returns the label of the device.
	 * 
	 * @return Device label.
	 */
	public String getDeviceLabel(){
		return label_;
	}

	/**
	 * Returns a String array containing the hashes of all known properties.
	 * 
	 * @return Property hashes.
	 */
	public String[] getPropertyHashes(){
		String[] str = properties_.keySet().toArray(new String[properties_.size()]); 
		Arrays.sort(str);
		return str;
	}
	
	/**
	 * Returns a String array containing the labels of all known properties.
	 * 
	 * @return Property labels.
	 */
	public String[] getPropertyLabels(){
		String[] str = new String[properties_.size()];

		Iterator<String> keys = properties_.keySet().iterator();
		int count = 0;
		while(keys.hasNext()){
			String prop = keys.next();
			str[count] = properties_.get(prop).getMMPropertyLabel();
			count ++;
		}
		
		Arrays.sort(str); 
		return str;
	}
	
	/**
	 * Returns the properties map. Each MMProperty is indexed by its hash.
	 * 
	 * @return Properties map.
	 */
	@SuppressWarnings("rawtypes")
	public HashMap<String, MMProperty> getProperties(){
		return properties_;
	}
	
}
