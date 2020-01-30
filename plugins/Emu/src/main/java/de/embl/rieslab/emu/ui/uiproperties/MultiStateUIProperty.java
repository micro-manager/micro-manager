package de.embl.rieslab.emu.ui.uiproperties;

import de.embl.rieslab.emu.controller.utils.GlobalSettings;
import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.flag.PropertyFlag;
import de.embl.rieslab.emu.utils.EmuUtils;

/**
 * A UIProperty with multiple allowed states, whose values are unknown at compilation time. Upon instantiation
 * the number of states is set, while the user can change the values of each state in the configuration wizard.
 * 
 * @author Joran Deschamps
 *
 */
public class MultiStateUIProperty extends UIProperty{

	public final static String STATE = " state ";
	
	private String[] states_;
	private String[] statenames_;
	
	/**
	 * Constructor with a PropertyFlag.
	 * 
	 * @param owner ConfigurablePanel that instantiated the UIProperty
	 * @param label Name of the UIProperty
	 * @param description Description of the UIProperty
	 * @param flag Flag of the UIProperty
	 * @param size Number of allowed states
	 */
	public MultiStateUIProperty(ConfigurablePanel owner, String label, String description, PropertyFlag flag, int size) {
		super(owner, label, description, flag);

		states_ = new String[size];
		statenames_ = new String[size];
		for(int i=0;i<size;i++){
			states_[i] = "";
			statenames_[i] = getConfigurationStateLabel(i);
		}
	}	
	/**
	 * Constructor without a PropertyFlag defining the number of states.
	 * 
	 * @param owner ConfigurablePanel that instantiated the UIProperty
	 * @param label Name of the UIProperty
	 * @param description Description of the UIProperty
	 * @param size Number of allowed states
	 */
	public MultiStateUIProperty(ConfigurablePanel owner, String label, String description, int size) {
		super(owner, label, description);

		if(size <= 0) {
			throw new IllegalArgumentException("The number of state of a MultiStateUIProperty cannot be negative or zero.");
		}
		
		states_ = new String[size];
		statenames_ = new String[size];
		for(int i=0;i<size;i++){
			states_[i] = "";
			statenames_[i] = getConfigurationStateLabel(i);
		}
	}

	/**
	 * Sets values for the states of the UI property. If the array of values is too long, then the supernumerary values are ignored. 
	 * If the array is too short, then the corresponding states are modified while the other ones are left unchanged.
	 * 
	 * @param vals Array of values.
	 * @return True if some values were set, false otherwise.
	 */
	public boolean setStateValues(String[] vals){
		if(vals == null){
			return false;
		}
		
		for(int i=0;i<vals.length;i++){
			if(vals[i] == null || !isValueAllowed(vals[i])){
				return false;
			}
		}
		
		if(vals.length == states_.length){
			states_ = vals;
		} else if (vals.length > states_.length){
			for(int i=0; i<states_.length;i++){
				states_[i] = vals[i];
			}
		} else {
			for(int i=0; i<vals.length;i++){
				states_[i] = vals[i];
			}
		}
		return true;
	}
	
	/**
	 * Gives names to the states. If stateNames has less entries than the number of states, then only
	 * the corresponding states will be updated. If it has more entries, then the supernumerary entries
	 * will be ignored.
	 * 
	 * @param stateNames State names
	 * @return True if some names were set, false otherwise.
	 */
	public boolean setStateNames(String[] stateNames){
		for(String s: stateNames) {
			if(s == null) {
				throw new NullPointerException("State names cannot be null.");
			} else if(s.equals("")) {
				throw new IllegalArgumentException("State names cannot be empty.");
			}
		}
		
		if(stateNames.length == statenames_.length){
			statenames_ = stateNames;
			return true;
		} else if (stateNames.length > statenames_.length){
			for(int i=0; i<statenames_.length;i++){
				statenames_[i] = stateNames[i];
			}
			return true;
		} else {
			for(int i=0; i<stateNames.length;i++){
				statenames_[i] = stateNames[i];
			}
			return true;
		}
	}
	
	/**
	 * Returns the number of states.
	 * 
	 * @return Number of states.
	 */
	public int getNumberOfStates(){
		return states_.length;
	}
	
	/**
	 * Returns the index of the state corresponding to the value val. If the value is not amongst 
	 * the states, returns 0.
	 * 
	 * @param val The state value.
	 * @return The index corresponding to the value.
	 */
	public int getStateIndex(String val){
		for(int i=0;i<states_.length;i++){
			if(isEqual(states_[i],val)){
				return i;
			}
		}
		return 0;
	}
	
	/**
	 * Returns the value of the state in position pos.
	 * 
	 * @param pos Position of the state.
	 * @return Value of the state.
	 */
	public String getStateValue(int pos){
		if(pos >= 0 && pos<states_.length){
			return states_[pos];
		}
		return "";
	}
	
	/**
	 * Returns the array of values.
	 * 
	 * @return State array.
	 */
	public String[] getStateValues(){
		return states_;
	}
	
	/**
	 * Returns the name of the state in position pos.
	 * 
	 * @param pos Position of the state.
	 * @return Name of the state.
	 */
	public String getStateName(int pos){
		if(pos >= 0 && pos<states_.length){
			return statenames_[pos];
		}
		return "";
	}
	
	/**
	 * Returns the name of the state corresponding to value. The first
	 * state will be returned if the value does not correspond to any state.
	 * 
	 * @param value Value
	 * @return Name of the state, or the first state if value does not correspond to any state.
	 */
	public String getStateNameFromValue(String value){
		for(int i=0;i<states_.length;i++){
			if(isEqual(states_[i],value)){
				return statenames_[i];
			}
		}	
		return statenames_[0];
	}
	
	
	/**
	 * Sets the value of the MMProperty to {@code val} if {@code val}
	 * equals either one of the state values or one of the state names.
	 * 
	 */
	@Override
	public boolean setPropertyValue(String val) {
		if (isAssigned()) {
			// checks if it corresponds to a valid state
			for (int i = 0; i < states_.length; i++) {
				if (isEqual(states_[i],val)) {
					return getMMProperty().setValue(val, this);
				}
			}
			// or if it corresponds to a valid state name 
			for (int i = 0; i < statenames_.length; i++) {
				if (statenames_[i].equals(val)) {
					return getMMProperty().setValue(states_[i], this);
				}
			}
			// otherwise, accept indices
			if (EmuUtils.isInteger(val)) {
				int v = Integer.parseInt(val);
				if (v >= 0 && v < states_.length) {
					return getMMProperty().setValue(states_[v], this);
				}

			}
		}
		return false;
	}
	
	/**
	 * Returns the generic name for state i.
	 * 
	 * @param i State number
	 * @return Generic name
	 */
	public static String getConfigurationStateLabel(int i){
		return STATE+i;
	}
	
	/**
	 * Returns the generic state name for String search and comparison: ".*"+STATE+"\\d+".
	 * 
	 * @return generic state name
	 */
	public static String getGenericStateName(){
		return ".*"+STATE+"\\d+";
	}
		
	/**
	 * Returns the names of the states.
	 * 
	 * @return State names
	 */
	public String[] getStatesName(){
		return statenames_;
	}
	
	/**
	 * Returns a String describing the UIPropertyType of the UIProperty.
	 * 
	 * @return UIProperty type
	 */
	public UIPropertyType getType() {
		return UIPropertyType.MULTISTATE;
	}
	
	private boolean isEqual(String stateval, String valToCompare) {
		if(valToCompare != null && isAssigned()) {
			if(EmuUtils.isNumeric(valToCompare) && getMMProperty().getType() == MMProperty.MMPropertyType.FLOAT) {
				Float state = Float.parseFloat(stateval);
				Float val = Float.parseFloat(valToCompare);
				return Math.abs(state-val) < GlobalSettings.EPSILON;
			} else if(EmuUtils.isNumeric(valToCompare) && getMMProperty().getType() == MMProperty.MMPropertyType.INTEGER) {
				double state = Double.parseDouble(valToCompare);
				Integer val = Integer.parseInt(stateval);
				return val.equals((int) state);
			} else {
				return stateval.equals(valToCompare);
			}
		}		
		return false;
	}
}

