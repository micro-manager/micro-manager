package de.embl.rieslab.emu.ui.uiproperties;

import de.embl.rieslab.emu.micromanager.mmproperties.PresetGroupAsMMProperty;
import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.flag.NoFlag;
import de.embl.rieslab.emu.ui.uiproperties.flag.PropertyFlag;
import de.embl.rieslab.emu.utils.exceptions.AlreadyAssignedUIPropertyException;
import de.embl.rieslab.emu.utils.exceptions.IncompatibleMMProperty;

/**
 * A UIProperty acts as a link between a JComponent and a Micro-Manager device
 * property ( {@link de.embl.rieslab.emu.micromanager.mmproperties.MMProperty
 * MMProperty}). It should be instantiated in the initializeProperties() method
 * of a ConfigurablePanel and added with the addUIProperty(). The UIProperty
 * will appear in the configuration wizard of EMU. The user can then assign a
 * Micro-Manager device property to the UIProperty. While several UIProperties
 * can be allocated to a single MMProperty, each UIPropoerty can only be
 * assigned to a single MMproperty.
 * <p>
 * A user interaction with a JComponent should trigger a corresponding
 * UIProperty. The ConfigurablePanel calls {@link #setPropertyValue(String)}. In
 * turns, this will change the value of the MMProperty. All other UIProperty
 * allocated to the MMProperty will be updated. Finally, updated UIProperty will
 * call the owner ConfigurablePanel (triggerPropertyHasChanged()) to change the
 * relevant JComponents.
 * <p>
 * UIProperties can be labeled with a PropertyFlag to allow categorizing them.
 * This mechanism is not used within EMU, but can be exploited in the plugins.
 * <p>
 * 
 * @author Joran Deschamps
 * @see SingleStateUIProperty
 * @see TwoStateUIProperty
 * @see MultiStateUIProperty
 * @see RescaledUIProperty
 */
@SuppressWarnings("rawtypes")
public class UIProperty {

	private String label_;
	private String friendlyname_;
	private String description_;
	private ConfigurablePanel owner_;
	private MMProperty mmproperty_;
	private PropertyFlag flag_;
	private boolean assigned_ = false;
	
	/**
	 * Constructor with a PropertyFlag.
	 * 
	 * @param owner ConfigurablePanel that instantiated the UIProperty
	 * @param label Label of the UIProperty
	 * @param description Description of the UIProperty
	 * @param flag Flag of the UIProperty
	 */
	public UIProperty(ConfigurablePanel owner, String label, String description, PropertyFlag flag){
		if(owner == null) {
			throw new NullPointerException("ConfigurablePanel owner cannot be null.");
		}
		if(label == null) {
			throw new NullPointerException("The UIProperty label cannot be null.");
		}
		if(description == null) {
			throw new NullPointerException("The UIProperty description cannot be null.");
		}
		if(flag == null) {
			throw new NullPointerException("The UIProperty flag cannot be null.");
		}
		
		this.owner_ = owner;
		this.label_ = label;
		this.description_ = description;
		this.flag_ = flag;
		
		friendlyname_ = label;
	}	
	
	/**
	 * Constructor without PropertyFlag, the flag being set to NoFlag.
	 * 
	 * @param owner ConfigurablePanel that instantiated the UIProperty
	 * @param label Label of the UIProperty
	 * @param description Description of the UIProperty
	 */
	public UIProperty(ConfigurablePanel owner, String label, String description){
		if(owner == null) {
			throw new NullPointerException("ConfigurablePanel owner cannot be null.");
		}
		if(label == null) {
			throw new NullPointerException("The UIProperty label cannot be null.");
		}
		if(description == null) {
			throw new NullPointerException("The UIProperty description cannot be null.");
		}
		
		this.owner_ = owner;
		this.label_ = label;
		this.description_ = description;
		this.flag_ = new NoFlag();
		
		friendlyname_ = label;
	}
	
	/**
	 * Returns the UIProperty's label
	 * 
	 * @return Label of the UIProperty
	 */
	public String getPropertyLabel(){
		return label_;
	}
	
	/**
	 * Returns the UIProperty's description, this description is used in the Help window
	 * of the configuration wizard.
	 * 
	 * @return Description of the UIProperty
	 */
	public String getDescription(){
		return description_;
	}
	
	/**
	 * Assigns the UIProperty to a MMProperty. A UIProperty can only be assigned once and to
	 * a single MMProperty. This method will throw an AlreadyAssignedUIPropertyException if
	 * there is an attempt to assign it to a second MMProperty.
	 * 
	 * @param prop MMproperty to assign the UIProperty to.
	 * @return True if the property was allocated successfully, false otherwise.
	 * @throws AlreadyAssignedUIPropertyException Exception thrown when the UIProperty has already been assigned
	 * @throws IncompatibleMMProperty exception thrown when the MMProperty is not compatible with the UIProperty.
	 */
	public boolean assignProperty(MMProperty prop) throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty{
		if(prop == null) {
			throw new NullPointerException();
		} else if(!assigned_ && isCompatibleMMProperty(prop)){
			mmproperty_ = prop;
			assigned_ = true;
			return true;
		} else if(assigned_){
			throw new AlreadyAssignedUIPropertyException(label_);
		} else if(!isCompatibleMMProperty(prop)){
			throw new IncompatibleMMProperty(prop.getHash(),label_);
		}
		return false;
	}
	
	/**
	 * Checks if the MMProperty is compatible with the UIProperty.
	 * 
	 * @param prop MMProperty
	 * @return True if it is, false otherwise.
	 */
	public boolean isCompatibleMMProperty(MMProperty prop) {
		return true;
	}
		
	/**
	 * Checks if the UIProperty has been assigned or not.
	 * 
	 * @return True if the UIProperty is assigned to a MMProperty, false otherwise.
	 */
	public boolean isAssigned(){
		return assigned_;
	}
	
	/**
	 * Notifies its ConfigurablePanel owner that the MMProperty it is assigned to has changed its
	 * value.
	 * 
	 * @param value New value of the MMProperty
	 */
	public void mmPropertyHasChanged(String value){
		owner_.triggerPropertyHasChanged(label_,value);
	}
	
	/**
	 * Returns the value of the MMProperty it is assigned to.
	 * 
	 * @return Value of the MMProperty or an empty String if the UIProperty has not been assigned to a MMProperty
	 */
	public String getPropertyValue() {
		if (assigned_) {
			return mmproperty_.getStringValue();
		}
		return "";
	}
	
	/**
	 * Sets the value of the assigned MMProperty to {@code newValue}.
	 * 
	 * @param newValue New value
	 * @return True if the value was set, false otherwise.
	 */
	public boolean setPropertyValue(String newValue){
		if(assigned_){
			return mmproperty_.setValue(newValue, this);
		}  
		return false;
	}

	/**
	 * Sets the value of the assigned MMProperty to {@code newState}. For a pure UIProperty, this method is equivalent
	 * to setPropertyValue (subclasses might have a different implementation).
	 * 
	 * @param newState New state 
	 * @return True if the value was set, false otherwise.
	 */
	public boolean setPropertyValueByState(String newState) {
		return mmproperty_.setValue(newState, this);
	}
	
	/**
	 * Sets the value of the assigned MMProperty to {@code newState}. For a pure UIProperty, this method is equivalent
	 * to setPropertyValue (subclasses might have a different implementation).
	 * 
	 * @param stateIndex New state 
	 * @return True if the value was set, false otherwise.
	 */
	public boolean setPropertyValueByStateIndex(int stateIndex) {
		return mmproperty_.setValue(String.valueOf(stateIndex), this);
	}
	
	/**
	 * Returns the assigned MMProperty.
	 * 
	 * @return The assigned MMProperty, null if the UIProperty has not been assigned
	 */
	protected MMProperty getMMProperty(){
		return mmproperty_;
	}
	
	/**
	 * Returns the ConfigurablePanel that owns this UIProperty.
	 * @return Owner ConfigurablePanel.
	 */
	protected ConfigurablePanel getOwner() {
		return owner_;
	}
	
	/**
	 * Returns the UIProperty's friendly name.
	 *
	 * @return Friendly name, or the UIProperty's label if the friendly name is null
	 */
	public String getFriendlyName(){
		if(friendlyname_ == null){
			return label_;
		}
		return friendlyname_;
	}
	
	/**
	 * Sets the UIProperty's friendly name.
	 * 
	 * @param newFriendlyName New friendly name
	 */
	public void setFriendlyName(String newFriendlyName){
		if(newFriendlyName != null) {
			friendlyname_ = newFriendlyName;
		} else {
			throw new NullPointerException("Null friendly names are not allowed.");
		}
	}

	/**
	 * Checks if the MMProperty assigned to this UIProperty is read-only.
	 * 
	 * @return True if the MMProperty is read-only, false otherwise. If the UIProperty
	 * is not assigned, returns true.
	 */
	public boolean isMMPropertyReadOnly(){
		if(assigned_){
			return mmproperty_.isReadOnly();
		}
		return true;
	}
	
	/**
	 * Checks if the MMProperty assigned to this UIProperty has allowed values.
	 * 
	 * @return True if it does, false otherwise. If the UIProperty is not assigned, then returns false.
	 */
	public boolean hasMMPropertyAllowedValues(){
		if(assigned_){
			return mmproperty_.hasAllowedValues();
		}
		return false;
	}
	
	/**
	 * Checks if the MMProperty assigned to this UIProperty has limits.
	 * 
	 * @return True if it does, false otherwise. If the UIProperty is not assigned, then returns false.
	 */
	public boolean hasMMPropertyLimits(){
		if(assigned_){
			return mmproperty_.hasLimits();
		}
		return false;
	}
	
	/**
	 * Returns the allowed values of the assigned MMProperty.
	 * 
	 * @return Allowed values or null if the MMProperty does not have any or if the UIProperty is not assigned.
	 */
	public String[] getAllowedValues(){
		if(hasMMPropertyAllowedValues()){
			return mmproperty_.getStringAllowedValues() ;
		}
		return null;
	}
	
	/**
	 * Returns the limits of the assigned MMProperty.
	 * 
	 * @return Limits or null if the MMProperty does not have any or if the UIProperty is not assigned.
	 */
	public String[] getLimits(){
		if(hasMMPropertyLimits()){
			String[] lim = {mmproperty_.getStringMin(),mmproperty_.getStringMax()};
			return lim;
		}
		return null;
	}
	
	/**
	 * Returns the PropertyFlag of this UIProperty.
	 * 
	 * @return PropertyFlag of the UIProperty
	 */
	public PropertyFlag getFlag(){
		return flag_;
	}
	
	/**
	 * Checks if the String {@code val} is an allowed value for the MMProperty.
	 * 
	 * @param val Value to test
	 * @return True if the value is allowed, false otherwise. If the UIProperty is not assigned, returns true
	 */
	public boolean isValueAllowed(String val){
		if(assigned_){
			return mmproperty_.isStringAllowed(val);
		}
		return false;
	}
	
	/**
	 * Checks if the assigned MMProperty is a Configuration group from Micro-manager.
	 * 
	 * @return True if it is, false otherwise or if the UIProperty is not assigned.
	 */
	public boolean isConfigGroupMMProperty() {
		if(assigned_) {
			return (mmproperty_ instanceof PresetGroupAsMMProperty);
		}
		return false;
	}
	
	/**
	 * Returns a String describing the UIPropertyType of the UIProperty.
	 * 
	 * @return UIProperty type
	 */
	public UIPropertyType getType() {
		return UIPropertyType.UIPROPERTY;
	}
	
}
