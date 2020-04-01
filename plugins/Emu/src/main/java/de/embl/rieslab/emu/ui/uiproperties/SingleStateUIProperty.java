package de.embl.rieslab.emu.ui.uiproperties;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.flag.PropertyFlag;

/**
 * A UIProperty that accepts only a single state, whose value can be set by the user in the
 * configuration wizard. 
 * 
 * @author Joran Deschamps
 *
 */
public class SingleStateUIProperty extends UIProperty{

	private final static String STATE = "state";
	
	private String state_ = "";

	/**
	 * Constructor with a PropertyFlag.
	 * 
	 * @param owner ConfigurablePanel that instantiated the UIProperty
	 * @param label Name of the UIProperty
	 * @param description Description of the UIProperty
	 * @param flag Flag of the UIProperty
	 */
	public SingleStateUIProperty(ConfigurablePanel owner, String label, String description, PropertyFlag flag) {
		super(owner, label, description, flag);
	}

	/**
	 * Constructor with no PropertyFlag.
	 * 
	 * @param owner ConfigurablePanel that instantiated the UIProperty
	 * @param label Name of the UIProperty
	 * @param description Description of the UIProperty
	 */
	public SingleStateUIProperty(ConfigurablePanel owner, String label, String description) {
		super(owner, label, description);
	}

	/**
	 * Sets the value of the single state of the UIProperty.
	 * 
	 * @param stateValue New state value
	 * @return True if the value was allowed and set, false otherwise.
	 */
	public boolean setStateValue(String stateValue){
		if(isValueAllowed(stateValue)){
			state_ = stateValue;
			return true;
		}
		return false;
	}
	
	/**
	 * Returns the single allowed state value.
	 * 
	 * @return State value.
	 */
	public String getStateValue(){
		return state_;
	}
	
	/**
	 * Sets the value of the MMProperty to the only state value
	 * that is allowed.
	 * 
	 */
	@Override
	public boolean setPropertyValue(String val) {
		if (val != null && isAssigned() && (val.equals(state_) || val.equals(getStateLabel()))) {
			return getMMProperty().setValue(state_, this);
		}
		return false;
	}
	

	/**
	 * Sets the value of the assigned MMProperty to the value in the state labeled {@code newState}. 
	 * If newState is not a valid state name, then the regular setPropertyValue method is called.
	 * 
	 * @param stateName New state's name 
	 * @return True if the value was set, false otherwise.
	 */
	@Override
	public boolean setPropertyValueByState(String stateName) {
		if (isAssigned()) {
			// if it corresponds to a valid state name 
			if (getStateLabel().equals(stateName)) {
				return getMMProperty().setValue(state_, this);
			}
			
			// otherwise, call the regular method
			return setPropertyValue(stateName);
		}
		return false;
	}

	/**
	 * Sets the value of the assigned MMProperty to the property state if stateIndex equals 0. 
	 * 
	 * @param stateIndex New state's index
	 * @return True if the value was set, false otherwise.
	 */
	@Override
	public boolean setPropertyValueByStateIndex(int stateIndex) {
		if (isAssigned()) {
			if (stateIndex == 0) {
				return getMMProperty().setValue(state_, this);
			}
		}
		return false;
	}	
	
	/**
	 * Returns the name of the state.
	 * 
	 * @return State name
	 */
	public static String getStateLabel(){
		return " "+STATE;
	}
	
	/**
	 * Returns a String describing the UIPropertyType of the UIProperty.
	 * 
	 * @return UIProperty type
	 */
	public UIPropertyType getType() {
		return UIPropertyType.SINGLESTATE;
	}
}
