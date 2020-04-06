package de.embl.rieslab.emu.ui.uiproperties;

import de.embl.rieslab.emu.controller.utils.GlobalSettings;
import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.flag.PropertyFlag;
import de.embl.rieslab.emu.utils.EmuUtils;

/**
 * UIProperty that only accepts two states: ON or OFF. The value of these states are not known at compilation time and can be changed in
 * the configuration wizard.
 * 
 * @author Joran Deschamps
 *
 */
public class TwoStateUIProperty extends UIProperty{
	
	private final static String ON = " - On value";
	private final static String OFF = " - Off value";
	private String onstate_;
	private String offstate_;
	
	/**
	 * Constructor with a PropertyFlag.
	 * 
	 * @param owner ConfigurablePanel that instantiated the UIProperty
	 * @param label Name of the UIProperty
	 * @param description Description of the UIProperty
	 * @param flag Flag of the UIProperty
	 */
	public TwoStateUIProperty(ConfigurablePanel owner, String label, String description, PropertyFlag flag) {
		super(owner, label, description, flag);
	}	
	
	/**
	 * Constructor without PropertyFlag, the flag being set to NoFlag.
	 * 
	 * @param owner ConfigurablePanel that instantiated the UIProperty
	 * @param label Name of the UIProperty
	 * @param description Description of the UIProperty
	 */
	public TwoStateUIProperty(ConfigurablePanel owner, String label, String description) {
		super(owner, label, description);
	}
	
	/**
	 * Returns the static String used to refer to the TwoStateUIProperty's ON state.
	 * 
	 * @return ON state's name
	 */
	public static String getOnStateLabel(){
		return ON;
	}
	
	/**
	 * Returns the static String used to refer to the TwoStateUIProperty's OFF state.
	 * 
	 * @return OFF state's name
	 */
	public static String getOffStateLabel(){
		return OFF;
	}

	/**
	 * Sets the value of the ON state.
	 * 
	 * @param newOnValue New value of the ON state
	 * @return True if the value was correctly set, false otherwise (for instance if the value is not allowed)
	 */
	public boolean setOnStateValue(String newOnValue){
		if(isValueAllowed(newOnValue)){
			onstate_ = newOnValue;	
			return true;
		}
		
		return false;
	}
	
	/**
	 * Sets the value of the OFF state.
	 * 
	 * @param newOffvalue New value of the OFF state
	 * @return True if the value was correctly set, false otherwise (for instance if the value is not allowed)
	 */
	public boolean setOffStateValue(String newOffvalue){
		if(isValueAllowed(newOffvalue)){
			offstate_ = newOffvalue;
			return true;
		}
		return false;
	}

	/**
	 * Returns the value of the ON state.
	 * 
	 * @return ON state value
	 */
	public String getOnStateValue(){
		return onstate_;
	}
	
	/**
	 * Returns the value of the OFF state.
	 * 
	 * @return OFF state value
	 */
	public String getOffStateValue(){
		return offstate_;
	}
	
	/**
	 * Sets the value of the UIproperty to {@code newValue} if {@code newValue}
	 * is either equal to the ON state or to the OFF state, or their respective value. The 
	 * method also accepts 1/0 or true/false as state labels.
	 */
	@Override
	public boolean setPropertyValue(String newValue) {
		if(newValue != null && isAssigned()) {
			if (newValue.equals(getOnStateValue()) || newValue.equals(getOnStateLabel())) {
				return getMMProperty().setValue(getOnStateValue(), this);
			} else if(newValue.equals(getOffStateValue()) || newValue.equals(getOffStateLabel())) {
				return getMMProperty().setValue(getOffStateValue(), this);
			} else {
				if(newValue.equals("1") || newValue.equals("true")) {
					return getMMProperty().setValue(getOnStateValue(), this);
				} else if(newValue.equals("0") || newValue.equals("false")) {
					return getMMProperty().setValue(getOffStateValue(), this);
				}
			}
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
			if (getOnStateLabel().equals(stateName)) {
				return getMMProperty().setValue(getOnStateValue(), this);
			} else if(getOffStateLabel().equals(stateName)) {
				return getMMProperty().setValue(getOffStateValue(), this);
			}
			
			// otherwise, call the regular method
			return setPropertyValue(stateName);
		}
		return false;
	}

	/**
	 * Sets the value of the assigned MMProperty to the On/Off state if stateIndex equals to 1/0 respectively. 
	 * 
	 * @param stateIndex New state's index
	 * @return True if the value was set, false otherwise.
	 */
	@Override
	public boolean setPropertyValueByStateIndex(int stateIndex) {
		if (isAssigned()) {
			if (stateIndex == 0) {
				return getMMProperty().setValue(getOffStateValue(), this);
			} else if(stateIndex == 1) {
				return getMMProperty().setValue(getOnStateValue(), this);
			}
		}
		return false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public UIPropertyType getType() {
		return UIPropertyType.TWOSTATE;
	}
	
	/**
	 * Tests if {@code value} is the ON state.
	 * 
	 * @param value Value to be compared with the ON state.
	 * @return True if it is, false otherwise.
	 */
	public boolean isOnState(String value) {
		if(value != null && isAssigned()) {		
			if(getMMProperty().getType() == MMProperty.MMPropertyType.FLOAT) { // if float, then "0" decimals might be added and need to be compared. 
				if(EmuUtils.isNumeric(value)) {
					Float f = Float.parseFloat(value);
					Float onstate = Float.parseFloat(onstate_);
					return Math.abs(f-onstate) < GlobalSettings.EPSILON;
				}
				return false;
			} else if(getMMProperty().getType() == MMProperty.MMPropertyType.INTEGER) { 
				if(EmuUtils.isNumeric(value)) {
					double i = Double.parseDouble(value);
					Integer onstate = Integer.parseInt(onstate_);
					return onstate.equals((int) i);
				}
			} else {
				return value.equals(onstate_);
			}
		}
		return false;
	}	
	
	/**
	 * Tests if {@code value} is the OFF state.
	 * 
	 * @param value Value to be compared with the OFF state.
	 * @return True if it is, false otherwise.
	 */
	public boolean isOffState(String value) {
		if(value != null && isAssigned()) {		
			if(getMMProperty().getType() == MMProperty.MMPropertyType.FLOAT) { // if float, then "0" decimals might be added and need to be compared. 
				if(EmuUtils.isNumeric(value)) {
					Float f = Float.parseFloat(value);
					Float offstate = Float.parseFloat(offstate_);
					return Math.abs(f-offstate) < GlobalSettings.EPSILON;
				}
				return false;
			} else if(getMMProperty().getType() == MMProperty.MMPropertyType.INTEGER) { 
				if(EmuUtils.isNumeric(value)) {
					double i = Double.parseDouble(value);
					Integer offstate = Integer.parseInt(offstate_);
					return offstate.equals((int) i);
				}
			} else {
				return value.equals(offstate_);
			}
		}
		return false;
	}
}
