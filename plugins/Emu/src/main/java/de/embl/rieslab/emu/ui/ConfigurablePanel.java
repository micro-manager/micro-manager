package de.embl.rieslab.emu.ui;

import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.embl.rieslab.emu.ui.internalproperties.BoolInternalProperty;
import de.embl.rieslab.emu.ui.internalproperties.DoubleInternalProperty;
import de.embl.rieslab.emu.ui.internalproperties.IntegerInternalProperty;
import de.embl.rieslab.emu.ui.internalproperties.InternalProperty;
import de.embl.rieslab.emu.ui.internalproperties.InternalProperty.InternalPropertyType;
import de.embl.rieslab.emu.ui.uiparameters.BoolUIParameter;
import de.embl.rieslab.emu.ui.uiparameters.ColorUIParameter;
import de.embl.rieslab.emu.ui.uiparameters.DoubleUIParameter;
import de.embl.rieslab.emu.ui.uiparameters.IntegerUIParameter;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter.UIParameterType;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIPropertyType;
import de.embl.rieslab.emu.utils.exceptions.IncorrectInternalPropertyTypeException;
import de.embl.rieslab.emu.utils.exceptions.IncorrectUIParameterTypeException;
import de.embl.rieslab.emu.utils.exceptions.UnknownInternalPropertyException;
import de.embl.rieslab.emu.utils.exceptions.UnknownUIParameterException;
import de.embl.rieslab.emu.utils.exceptions.UnknownUIPropertyException;

/**
 * Building block of EMU, this abstract class extends a Swing JPanel. It holds a map of {@link de.embl.rieslab.emu.ui.uiproperties.UIProperty UIProperty},
 * {@link de.embl.rieslab.emu.ui.uiparameters.UIParameter UIParameter} and {@link de.embl.rieslab.emu.ui.internalproperties.InternalProperty InternalProperty}.
 * ConfigurablePanel subclasses should be instantiated and added to a {@link ConfigurableMainFrame}.
 * <p> 
 * Subclasses of ConfigurablePanel must implements few methods called in order to instantiate the
 * UIProperties ({@link #initializeProperties()}), UIParameters ({@link #initializeParameters()}) and InternalProperties ({@link #initializeInternalProperties()}). 
 * All JComponent instantiations should happen in the constructor. Action listeners can be added to the JComponents in {@link #addComponentListeners()}, in case the 
 * states or values of the UIProperty are necessary.  
 * <p> 
 * UIProperties are aimed at linking the state of a MMProperty with the state of one or multiple JComponenents. InternalProperties are made to allow
 * shared values between ConfigurablePanels, such that a modification to one panel can trigger a change in the other panel. Finally, UIProperties
 * should only be used for user settings, such as changing the colors of JLabels or JButtons (to describe a filter or a laser) or the text of a header. 
 * All UIProperties and UIParameters appear in the configuration wizard. The user can then map the 
 * UIProperties with a MMProperty and change the value of a UIParameter. 
 * <p>
 * In order to be added to the internal HashMap representation, UIproperties, UIParameters and InternalProperties need to be added using the following methods
 * respectively: {@link #addUIProperty(UIProperty)}, {@link #addUIParameter(UIParameter)} and {@link #addInternalProperty(InternalProperty)}. 
 * <p>
 * Modifications to the state of UIProperties and InternalProperties should not be done explicitly in the subclasses, but should be done through the 
 * abstraction methods: {@link #setUIPropertyValue(String, String)} and setInternalPropertyValue(String, ?). UIParameters should not be modified within the subclasses. 
 * Modifications of the JComponents based on UIProperties, UIParameters and InternalProperties changes take place in the subclasses 
 * implementation of {@link #propertyhasChanged(String, String)}, {@link #parameterhasChanged(String)} and {@link #internalpropertyhasChanged(String)} respectively.
 * To query the value of a UIParameter or an InternalProperty, use the methods {@code getUIParamterValue()} and
 * {@code getInternalPropertyValue()}.
 *  <p> 
 * For instance, a JToggleButton can be designed to turn on and off a laser. After declaration of the JToggleButton and addition to the panel in the constructor, 
 * an eventListener can be added to the JToggleButton. The eventListener should then call {@link #setUIPropertyValue(String, String)} to modify the corresponding
 * UIProperty with a new value being on when the JToggleButton is selected, and an off value when the JToggleButton is unselected. More details can be found in
 * tutorials and the javadocs of the different UIProperties implementations.
 * <p> 
 * Upon start up of the {@link de.embl.rieslab.emu.plugin.UIPlugin}, the {@link de.embl.rieslab.emu.controller.SystemController} will
 * pair up the UIProperties with {@link de.embl.rieslab.emu.micromanager.mmproperties.MMProperty} and the latter's values will be propagated 
 * to the ConfigurablePanel via {@link #propertyhasChanged(String, String)}. Later on, changes to a MMProperty (for instance by another ConfigurablePanel)
 * will trigger the same method. The same mechanism is at play for the InternalProperties and the UIParameters. Note that the UIParameters are only changed
 * upon start up and when the user modifies the configuration through the {@link de.embl.rieslab.emu.configuration.ui.ConfigurationWizardUI}. 
 * <p> 
 * 
 * @see de.embl.rieslab.emu.ui.uiproperties.UIProperty
 * @see de.embl.rieslab.emu.ui.uiparameters.UIParameter
 * @see de.embl.rieslab.emu.ui.internalproperties.InternalProperty
 * @see ConfigurableMainFrame
 * 
 * @author Joran Deschamps
 *
 */
@SuppressWarnings("rawtypes")
public abstract class ConfigurablePanel extends JPanel{

	private static final long serialVersionUID = 1L;

	private HashMap<String, UIProperty> properties_; 
	private HashMap<String, UIParameter> parameters_;
	private HashMap<String, InternalProperty> internalprops_;

	private String label_;
	
	private boolean componentTriggering_ = true;
	
	/**
	 * Constructor, calls the abstract methods {@link #initializeProperties()} and {@link #initializeParameters()}, 
	 * {@link #initializeInternalProperties()}.
	 * 
	 * @param label Label of the panel.
	 */
	public ConfigurablePanel(String label){
		if(label == null) {
			throw new NullPointerException("A ConfigurablePanel label cannot be null.");
		}
		
		label_ = label;
		
		properties_ = new HashMap<String,UIProperty>();
		parameters_ = new HashMap<String,UIParameter>();
		internalprops_ = new HashMap<String, InternalProperty>();
		
		initializeProperties();
		initializeParameters();
		initializeInternalProperties();
	}

	/**
	 * Returns a hash map of the panel's UI properties indexed by their name.
	 *
	 * @see de.embl.rieslab.emu.ui.uiproperties.UIProperty
	 * 
	 * @return HashMap with the UIProperty indexed by their name. 
	 */
	protected HashMap<String, UIProperty> getUIProperties(){
		return properties_;
	}
	
	/**
	 * Returns a hash map of the panel's internal properties indexed by their names.
	 *
	 * @see de.embl.rieslab.emu.ui.internalproperties.InternalProperty
	 * 
	 * @return HashMap with the InternalProperty indexed by their names. 
	 */
	protected HashMap<String, InternalProperty> getInternalProperties(){
		return internalprops_;
	}
	
	/**
	 * Returns a hash map of the panel's {@link de.embl.rieslab.emu.ui.uiparameters.UIParameter UIParameters} indexed by their hash (({panel's name}-{parameter's name})).
	 *
	 * @see de.embl.rieslab.emu.ui.uiparameters.UIParameter
	 * 
	 * @return HashMap with the UIParameter indexed by their hash. 
	 */
	protected HashMap<String,UIParameter> getUIParameters(){
		return parameters_;
	}	
	
	/**
	 * Returns the {@link de.embl.rieslab.emu.ui.uiparameters.UIParameter UIParameter}
	 * named {@code propertyName}.
	 * 
	 * @param parameterName Name of the UIParameter
	 * @return Corresponding UIParameter, null if it doesn't exist.
	 * @throws UnknownUIParameterException Thrown if parameterName does not correspond to a known UIParameter.
	 */
	protected UIParameter getUIParameter(String parameterName) throws UnknownUIParameterException {
		if(parameterName == null) {
			throw new NullPointerException("UIParameters name cannot be null.");
		}
		
		if (parameters_.containsKey(UIParameter.getHash(this,parameterName))) {
			return parameters_.get(UIParameter.getHash(this,parameterName));
		} else {
			throw new UnknownUIParameterException(parameterName);
		}
	}

	/**
	 * Returns the {@link de.embl.rieslab.emu.ui.uiproperties.UIProperty UIProperty}
	 * named {@code propertyName}.
	 * 
	 * @param propertyName Name of the UIProperty
	 * @return Corresponding UIProperty, null if it doesn't exist.
	 * @throws UnknownUIPropertyException Thrown if propertyName does not correspond to a known UIProperty.
	 */
	protected UIProperty getUIProperty(String propertyName) throws UnknownUIPropertyException {
		if(propertyName == null) {
			throw new NullPointerException("UIProperties name cannot be null.");
		}
		if (properties_.containsKey(propertyName)) {
			return properties_.get(propertyName);
		}else {
			throw new UnknownUIPropertyException(propertyName);
		}
	}

	/**
	 * Returns the {@link de.embl.rieslab.emu.ui.internalproperties.InternalProperty InternalProperty}
	 * named {@code propertyName}.
	 * 
	 * @param propertyName Name of the InternalProperty
	 * @return Corresponding InternalProperty.
	 * @throws UnknownInternalPropertyException Thrown if propertyName does not correspond to a known InternalProperty.
	 */
	protected InternalProperty getInternalProperty(String propertyName) throws UnknownInternalPropertyException {
		if(propertyName == null) {
			throw new NullPointerException("InternalProperties name cannot be null.");
		}
		if (internalprops_.containsKey(propertyName)) {
			return internalprops_.get(propertyName);
		} else {
			throw new UnknownInternalPropertyException(propertyName);
		}
	}

	/**
	 * Sets the UIProperty {@code propertyName} friendly name to {@code friendlyName}.
	 * 
	 * @param propertyName Property name
	 * @param friendlyName New friendly name
	 */
	protected void setUIPropertyFriendlyName(String propertyName, String friendlyName){
		if(propertyName == null) {
			throw new NullPointerException("UIProperty's name cannot be null.");
		}

		if(properties_.containsKey(propertyName)){
			properties_.get(propertyName).setFriendlyName(friendlyName);
		}
	}
	
	/**
	 * Returns the current value of the UIProperty called {@code propertyName}.
	 * 
	 * @param propertyName Name of the property
	 * @return String value of the property, null if the property doesn't exist.
	 * @throws UnknownUIPropertyException Thrown if propertyName does not correspond to a known UIProperty.
	 */
	protected String getUIPropertyValue(String propertyName) throws UnknownUIPropertyException{
		if(propertyName == null) {
			throw new NullPointerException("UIProperty's name cannot be null.");
		}
		
		if(properties_.containsKey(propertyName)){
			return properties_.get(propertyName).getPropertyValue();
		} else {
			throw new UnknownUIPropertyException(propertyName);
		}
	}
	
	/**
	 * Sets the UIProperty {@code propertyName}'s value to {@code newValue}. This method calls the 
	 * UIProperty's method to set the value, which will in turn call the corresponding MMProperty's
	 * method. Since the change will be notified to all the UIProperties listening to the MMProperty 
	 * (through {@link #triggerPropertyHasChanged(String, String)}), this method runs on an independent
	 * thread (that is, not on the EDT).
	 *  
	 * @param propertyName UIProperty's name
	 * @param newValue New value
	 */
	public void setUIPropertyValue(String propertyName, String newValue){
		
		// this should be a protected method, but in order to call it in SwingUIListeners it was set to public...
		// passing it as lambdas could be a solution
		
		if(propertyName == null) {
			throw new NullPointerException("The UIProperty's label cannot be null.");
		}
		
		if(isComponentTriggeringEnabled()) {
			// makes sure the call does NOT run on EDT
			Thread t = new Thread("Property change: " + propertyName) {
				public void run() {
					if (properties_.containsKey(propertyName)) {
						properties_.get(propertyName).setPropertyValue(newValue);
					}
				}
			};
			t.start();
		}
	}
	
	/**
	 * Sets the value of the IntegerInternalProperty called {@code propertyName} to {@code newValue}.
	 * If {@code propertyName} doesn't exist or is not an IntegerInternalProperty, nothing happens.
	 *  
	 * @param propertyName Name of the InternalProperty
	 * @param newValue New value
	 * @throws IncorrectInternalPropertyTypeException Thrown if propertyName does not correspond to an IntegerInternalProperty.
	 * @throws UnknownInternalPropertyException Thrown if propertyName does not correspond to a known InternalProperty.
	 */
	protected void setInternalPropertyValue(String propertyName, int newValue) throws IncorrectInternalPropertyTypeException, UnknownInternalPropertyException{
		if(propertyName == null) {
			throw new NullPointerException("An InternalProperty label cannot be null.");
		}
		
		// runs on EDT, is this a problem?
		if (internalprops_.containsKey(propertyName)){
			if (internalprops_.get(propertyName).getType().equals(InternalPropertyType.INTEGER)) {
				((IntegerInternalProperty) internalprops_.get(propertyName)).setInternalPropertyValue(newValue, this);
			} else {
				throw new IncorrectInternalPropertyTypeException(InternalPropertyType.INTEGER.getTypeValue(), internalprops_.get(propertyName).getType().getTypeValue());
			}
		} else {
			throw new UnknownInternalPropertyException(propertyName);
		}
	}
	
	/**
	 * Sets the value of the BoolInternalProperty called {@code propertyName} to {@code newValue}.
	 * If {@code propertyName} doesn't exist or is not an BoolInternalProperty, nothing happens.
	 *  
	 * @param propertyName Name of the InternalProperty
	 * @param newValue New value
	 * @throws IncorrectInternalPropertyTypeException Thrown if propertyName does not correspond to an BoolInternalProperty.
	 * @throws UnknownInternalPropertyException Thrown if propertyName does not correspond to a known InternalProperty.
	 */
	protected void setInternalPropertyValue(String propertyName, boolean newValue) throws IncorrectInternalPropertyTypeException, UnknownInternalPropertyException{
		if(propertyName == null) {
			throw new NullPointerException("An InternalProperty's label cannot be null.");
		}
		
		// runs on EDT, is this a problem?
		if (internalprops_.containsKey(propertyName)) {
			if(internalprops_.get(propertyName).getType().equals(InternalPropertyType.BOOLEAN)) {
				((BoolInternalProperty) internalprops_.get(propertyName)).setInternalPropertyValue(newValue, this);
			} else {
				throw new IncorrectInternalPropertyTypeException(InternalPropertyType.BOOLEAN.getTypeValue(), internalprops_.get(propertyName).getType().getTypeValue());
			}
		} else {
			throw new UnknownInternalPropertyException(propertyName);
		}
	}
	
	/**
	 * Sets the value of the DoubleInternalProperty called {@code propertyName} to {@code newValue}.
	 * If {@code propertyName} doesn't exist or is not an DoubleInternalProperty, nothing happens.
	 *  
	 * @param propertyName name of the InternalProperty
	 * @param newValue New value
	 * @throws IncorrectInternalPropertyTypeException Thrown if propertyName does not correspond to an DoubleInternalProperty.
	 * @throws UnknownInternalPropertyException Thrown if propertyName does not correspond to a known InternalProperty.
	 */
	protected void setInternalPropertyValue(String propertyName, double newValue) throws IncorrectInternalPropertyTypeException, UnknownInternalPropertyException{
		if(propertyName == null) {
			throw new NullPointerException("An InternalProperty's label cannot be null.");
		}
		
		// runs on EDT, is this a problem?
		if (internalprops_.containsKey(propertyName)) {
			if( internalprops_.get(propertyName).getType().equals(InternalPropertyType.DOUBLE)) {
				((DoubleInternalProperty) internalprops_.get(propertyName)).setInternalPropertyValue(newValue, this);
			} else {
				throw new IncorrectInternalPropertyTypeException(InternalPropertyType.DOUBLE.getTypeValue(), internalprops_.get(propertyName).getType().getTypeValue());
			}
		} else {
			throw new UnknownInternalPropertyException(propertyName);
		}
	}

	/**
	 * Returns the value of the IntegerInternalProperty called {@code propertyName}.
	 * 
	 * @param propertyName Name of the property
	 * @return Value of the InternalProperty.
	 * @throws IncorrectInternalPropertyTypeException Thrown if propertyName does not correspond to an IntegerInternalProperty.
	 * @throws UnknownInternalPropertyException Thrown if propertyName does not correspond to a known InternalProperty
	 */
	protected int getIntegerInternalPropertyValue(String propertyName) throws IncorrectInternalPropertyTypeException, UnknownInternalPropertyException {
		if(propertyName == null) {
			throw new NullPointerException("An InternalProperty's label cannot be null.");
		}
		
		if(internalprops_.containsKey(propertyName)){
			if(internalprops_.get(propertyName).getType().equals(InternalPropertyType.INTEGER)) {
				return ((IntegerInternalProperty) internalprops_.get(propertyName)).getInternalPropertyValue();
			} else {
				throw new IncorrectInternalPropertyTypeException(InternalPropertyType.INTEGER.getTypeValue(), internalprops_.get(propertyName).getType().getTypeValue());
			}
		} else {
			throw new UnknownInternalPropertyException(propertyName);
		}
	}
	
	/**
	 * Returns the value of the BoolInternalProperty called {@code propertyName}.
	 * 
	 * @param propertyName Name of the property.
	 * @return InternalPropery value.
	 * @throws IncorrectInternalPropertyTypeException Thrown if propertyName does not correspond to an BoolInternalProperty.
	 * @throws UnknownInternalPropertyException Thrown if propertyName does not correspond to a known InternalProperty.
	 */
	protected boolean getBoolInternalPropertyValue(String propertyName) throws IncorrectInternalPropertyTypeException, UnknownInternalPropertyException {
		if(propertyName == null) {
			throw new NullPointerException("An InternalProperty's label cannot be null.");
		}
		if(internalprops_.containsKey(propertyName)) {
			if(internalprops_.get(propertyName).getType().equals(InternalPropertyType.BOOLEAN)) {
				return ((BoolInternalProperty) internalprops_.get(propertyName)).getInternalPropertyValue();
			} else {
				throw new IncorrectInternalPropertyTypeException(InternalPropertyType.BOOLEAN.getTypeValue(), internalprops_.get(propertyName).getType().getTypeValue());
			}
		} else {
			throw new UnknownInternalPropertyException(propertyName);
		}
	}
	
	/**
	 * Returns the value of the DoubleInternalProperty called {@code propertyName}.
	 * 
	 * @param propertyName Name of the property
	 * @return Value of the InternalProperty.
	 * @throws IncorrectInternalPropertyTypeException Thrown if propertyName does not correspond to an DoubleInternalProperty.
	 * @throws UnknownInternalPropertyException Thrown if propertyName does not correspond to a known InternalProperty.
	 */
	protected double getDoubleInternalPropertyValue(String propertyName) throws IncorrectInternalPropertyTypeException, UnknownInternalPropertyException {
		if(propertyName == null) {
			throw new NullPointerException("An InternalProperty's label cannot be null.");
		}
		if(internalprops_.containsKey(propertyName)) {
			if(internalprops_.get(propertyName).getType().equals(InternalPropertyType.DOUBLE)) {
				return ((DoubleInternalProperty) internalprops_.get(propertyName)).getInternalPropertyValue();
			} else {
				throw new IncorrectInternalPropertyTypeException(InternalPropertyType.DOUBLE.getTypeValue(), internalprops_.get(propertyName).getType().getTypeValue());
			}
		} else {
			throw new UnknownInternalPropertyException(propertyName);
		}
	}
	
	/**
	 * Returns the value of the DoubleUIParameter called {@code parameterName}.
	 * 
	 * @param parameterName Name of the parameter
	 * @return Value of the UIParameter.
	 * @throws IncorrectUIParameterTypeException Thrown if parameterName does not correspond to a DoubleUIParameter.
	 * @throws UnknownUIParameterException Thrown if parameterName does not correspond to a known UIParameter.
	 */
	protected double getDoubleUIParameterValue(String parameterName) throws IncorrectUIParameterTypeException, UnknownUIParameterException {
		if(parameterName == null) {
			throw new NullPointerException("UIParameter's name cannot be null.");
		}
		
		if(parameters_.containsKey(UIParameter.getHash(this,parameterName))){
			if(parameters_.get(UIParameter.getHash(this,parameterName)).getType().equals(UIParameterType.DOUBLE)) {
				return ((DoubleUIParameter) parameters_.get(UIParameter.getHash(this,parameterName))).getValue();					
			} else {
				throw new IncorrectUIParameterTypeException(parameterName, UIParameter.UIParameterType.DOUBLE.toString(),
						parameters_.get(UIParameter.getHash(this, parameterName)).getType().toString());
			}
		} else {
			throw new UnknownUIParameterException(parameterName);
		}
	}

	
	/**
	 * Returns the value of the BoolUIParameter called {@code parameterName}.
	 * 
	 * @param parameterName Name of the parameter
	 * @return Value of the UIParameter.
	 * @throws IncorrectUIParameterTypeException Thrown if parameterName does not correspond to a BoolUIParameter.
	 * @throws UnknownUIParameterException Thrown if parameterName does not correspond to a known UIParameter.
	 */
	protected boolean getBoolUIParameterValue(String parameterName) throws IncorrectUIParameterTypeException, UnknownUIParameterException {
		if(parameterName == null) {
			throw new NullPointerException("UIParameter's name cannot be null.");
		}
		
		if(parameters_.containsKey(UIParameter.getHash(this,parameterName))){
			if(parameters_.get(UIParameter.getHash(this,parameterName)).getType().equals(UIParameterType.BOOL)) {
				return ((BoolUIParameter) parameters_.get(UIParameter.getHash(this,parameterName))).getValue();					
			} else {
				throw new IncorrectUIParameterTypeException(parameterName, UIParameter.UIParameterType.BOOL.toString(),
						parameters_.get(UIParameter.getHash(this, parameterName)).getType().toString());
			}
		} else {
			throw new UnknownUIParameterException(parameterName);
		}
	}

	/**
	 * Returns the value of the ColorUIParameter called {@code parameterName}. 
	 * 
	 * @param parameterName Name of the parameter
	 * @return Value of the UIParameter.
	 * @throws IncorrectUIParameterTypeException Thrown if parameterName does not correspond to a ColorUIParameter.
	 * @throws UnknownUIParameterException Thrown if parameterName does not correspond to a known UIParameter.
	 */
	protected Color getColorUIParameterValue(String parameterName) throws IncorrectUIParameterTypeException, UnknownUIParameterException {
		if(parameterName == null) {
			throw new NullPointerException("UIParameter's name cannot be null.");
		}
		
		if(parameters_.containsKey(UIParameter.getHash(this,parameterName))){
			if(parameters_.get(UIParameter.getHash(this,parameterName)).getType().equals(UIParameterType.COLOR)) {
				return ((ColorUIParameter) parameters_.get(UIParameter.getHash(this,parameterName))).getValue();					
			} else {
				throw new IncorrectUIParameterTypeException(parameterName, UIParameter.UIParameterType.COLOR.toString(),
						parameters_.get(UIParameter.getHash(this, parameterName)).getType().toString());
			}
		} else {
			throw new UnknownUIParameterException(parameterName);
		}
	}


	/**
	 * Returns the value of the IntegerUIParameter called {@code parameterName}. 
	 * 
	 * @param parameterName Name of the parameter
	 * @return Value of the UIParameter.
	 * @throws IncorrectUIParameterTypeException Thrown if parameterName does not correspond to a IntegerUIParameter.
	 * @throws UnknownUIParameterException Thrown if parameterName does not correspond to a known UIParameter.
	 */
	protected int getIntegerUIParameterValue(String parameterName) throws IncorrectUIParameterTypeException, UnknownUIParameterException {
		if(parameterName == null) {
			throw new NullPointerException("UIParameter's name cannot be null.");
		}
		
		if(parameters_.containsKey(UIParameter.getHash(this,parameterName))){
			if(parameters_.get(UIParameter.getHash(this,parameterName)).getType().equals(UIParameterType.INTEGER)) {
				return ((IntegerUIParameter) parameters_.get(UIParameter.getHash(this,parameterName))).getValue();					
			} else {
				throw new IncorrectUIParameterTypeException(parameterName, UIParameter.UIParameterType.INTEGER.toString(),
						parameters_.get(UIParameter.getHash(this, parameterName)).getType().toString());
			}
		} else {
			throw new UnknownUIParameterException(parameterName);
		}
	}

	/**
	 * Returns the value of the StringUIParameter called {@code parameterName}. 
	 * 
	 * @param parameterName Name of the parameter
	 * @return Value of the UIParameter.
	 * @throws UnknownUIParameterException Thrown if parameterName does not correspond to a known UIParameter.
	 */
	protected String getStringUIParameterValue(String parameterName) throws UnknownUIParameterException {
		if (parameterName == null) {
			throw new NullPointerException("UIParameter's name cannot be null.");
		}

		if (parameters_.containsKey(UIParameter.getHash(this, parameterName))) {
			return parameters_.get(UIParameter.getHash(this, parameterName)).getStringValue();			
		} else {
			throw new UnknownUIParameterException(parameterName);
		}
	}

	/**
	 * Adds a {@link de.embl.rieslab.emu.ui.uiproperties.UIProperty} to the internal HashMap
	 * using the UI property's name.
	 * 
	 * @param uiproperty UIProperty to add
	 */
	protected void addUIProperty(UIProperty uiproperty){
		if(uiproperty == null) {
			throw new NullPointerException("Null UIProperties are not allowed.");
		}
		
		properties_.put(uiproperty.getPropertyLabel(),uiproperty);
	}	

	/**
	 * Adds a {@link de.embl.rieslab.emu.ui.uiparameters.UIParameter} to the internal HashMap
	 * using the UIParameter's hash.
	 * 
	 * @param uiparameter UIParameter to add
	 */
	protected void addUIParameter(UIParameter uiparameter){
		if(uiparameter == null) {
			throw new NullPointerException("Null UIParameters are not allowed.");
		}
		parameters_.put(uiparameter.getHash(),uiparameter);
	}
	
	/**
	 * Adds a {@link de.embl.rieslab.emu.ui.internalproperties.InternalProperty} to the internal HashMap
	 * using the InternalProperty name.
	 * 
	 * @param internalproperty InternalProperty to add
	 */
	protected void addInternalProperty(InternalProperty internalproperty){
		if(internalproperty == null) {
			throw new NullPointerException("Null InternalProperties are not allowed.");
		}
		internalprops_.put(internalproperty.getLabel(),internalproperty);
	}
	
	/**
	 * Updates the ConfigurablePanel for all UI properties by calling {@link #triggerPropertyHasChanged(String, String)} 
	 * for each UIProperty.
	 */
	protected void updateAllProperties(){
		Iterator<String> it = properties_.keySet().iterator();
		String prop;
		while(it.hasNext()){
			prop = it.next();
			triggerPropertyHasChanged(prop,properties_.get(prop).getPropertyValue());
		}
	}	
	
	/**
	 * Updates the ConfigurablePanel for all UI parameters by calling {@link #triggerParameterHasChanged(String)} 
	 * for each UIParameter.
	 */
	protected void updateAllParameters(){
		Iterator<String> it = parameters_.keySet().iterator();
		while(it.hasNext()){
			triggerParameterHasChanged(parameters_.get(it.next()).getLabel());
		}
	}
	
	/**
	 * Returns this ConfigurablePanel's label.
	 * 
	 * @return This panel's label.
	 */
	public String getPanelLabel(){
		return label_;
	}

	/**
	 * Substitute the parameter indexed by {@code paramHash} with {@code uiParameter}. This is used to resolve collisions
	 * between two parameters with same hash: their respective ConfigurablePanel owners then share the same UIParameter.
	 * 
	 * @param uiParameter UIParameter to substitute.
	 */
	public void substituteParameter(UIParameter uiParameter) {
		final String hash = uiParameter.getHash();
		if(parameters_.containsKey(hash) && parameters_.get(hash).getType() == uiParameter.getType()) {
			parameters_.put(hash, uiParameter);
		}
	}
	
	/**
	 * Substitutes the InternalProperty with same name and type than {@code internalProperty} with it. If no such 
	 * InternalProperty exists, then this method does nothing.  
	 * 
	 * @param internalProperty InternalProperty to substitute with an existing one.
	 */
	public void substituteInternalProperty(InternalProperty internalProperty) {
		if(internalprops_.containsKey(internalProperty.getLabel())) {
			if(getInternalPropertyType(internalProperty.getLabel()).equals(internalProperty.getType())) {
				internalprops_.put(internalProperty.getLabel(), internalProperty);
			}
		}
	}
	
	/**
	 * Returns the InternalPropertyType of the InternalProperty called {@code propertyName}. If there is no such
	 * InternalProperty, returns InternalPropertyType.NONE.
	 * 
	 * @param propertyName Name of the InternalProperty
	 * @return The corresponding InternalPropertyType, InternalPropertyType.NONE if there is no such InternalProperty.
	 */
	public InternalPropertyType getInternalPropertyType(String propertyName) {
		if(propertyName == null) {
			throw new NullPointerException("An InternalProperty cannot have a null label.");
		}
		
		if(internalprops_.containsKey(propertyName)) {
			return internalprops_.get(propertyName).getType();
		}
		return InternalPropertyType.NONE;
	}

	/**
	 * Returns the UIPropertyType of the UIProperty called {@code propertyName}. If there is no such
	 * UIProperty, returns UIPropertyType.NONE.
	 * 
	 * @param propertyName Name of the UIProperty
	 * @return The corresponding UIPropertyType, UIPropertyType.NONE if there is no such UIProperty.
	 */
	public UIPropertyType getUIPropertyType(String propertyName) {
		if(propertyName == null) {
			throw new NullPointerException("A UIProperty cannot have a null label.");
		}
		if(properties_.containsKey(propertyName)) {
			return properties_.get(propertyName).getType();
		}
		return UIPropertyType.NONE;
	}

	/**
	 * Returns the UIParameterType of the UIParameter called {@code parameterName}. If there is no such
	 * UIParameter, returns UIParameterType.NONE.
	 * 
	 * @param parameterName Name of the UIParameter
	 * @return The corresponding UIParameterType, UIParameterType.NONE if there is no such UIParameter.
	 */
	public UIParameterType getUIParameterType(String parameterName) {
		if(parameterName == null) {
			throw new NullPointerException("A UIParameter cannot have a null label.");
		}
		if(parameters_.containsKey(UIParameter.getHash(this, parameterName))) {
			return parameters_.get(UIParameter.getHash(this, parameterName)).getType();
		}
		return UIParameterType.NONE;
	}
	
	/**
	 * Checks if the component triggering is enabled. The change in permission is done through {@link #turnOffComponentTriggering()}
	 * and {@link #turnOnComponentTriggering()}.
	 * 
	 * @return true if the component triggering is on, false if it is off.
	 */
	private boolean isComponentTriggeringEnabled(){
		return componentTriggering_;
	}

	/**
	 * Turns off component triggering. See {@link #isComponentTriggeringEnabled()}.
	 */
	private void turnOffComponentTriggering(){
		componentTriggering_ = false;
	}
	
	/**
	 * Turns on component triggering. See {@link #isComponentTriggeringEnabled()}.
	 */
	private void turnOnComponentTriggering(){
		componentTriggering_ = true;
	}

	/**
	 * Calls {@link #propertyhasChanged(String, String)} on the EDT. This allows the extension classes of
	 * ConfigurablePanel to change the state of a JComponent depending on the value {@code newValue} of 
	 * the UIProperty {@code propertyName} (itself linked to a MMProperty). Since a UIProperty does not hold the value of the corresponding
	 * MMProperty, it is passed as a parameter.
	 * 
	 * @param propertyName Name of the property
	 * @param newValue New value of the property
	 */
	public void triggerPropertyHasChanged(final String propertyName, final String newValue){
		// Makes sure that the updating runs on EDT
		if (SwingUtilities.isEventDispatchThread()) {
			turnOffComponentTriggering();
			propertyhasChanged(propertyName, newValue);
			turnOnComponentTriggering();
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					turnOffComponentTriggering();
					propertyhasChanged(propertyName, newValue);
					turnOnComponentTriggering();
				}
			});
		}
	}
	
	/**
	 * Calls {@link #parameterhasChanged(String)} on the EDT. This allows the subclasses of
	 * ConfigurablePanel to adjust its components based on the value of the UIParameter {@code parameterName}.
	 * 
	 * @param parameterName Name of the parameter
	 */
	public void triggerParameterHasChanged(final String parameterName){
		// Makes sure that the updating runs on EDT
		if (SwingUtilities.isEventDispatchThread()) {
			parameterhasChanged(parameterName);
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					parameterhasChanged(parameterName);
				}
			});
		}
	}

	/**
	 * In this method, the subclasses must create their UIproperty and add them to the map of properties using 
	 * {@link #addUIProperty(UIProperty)}. The method is called in the constructor of a ConfigurablePanel.  
	 */
	protected abstract void initializeProperties();
		
	/**
	 * Notifies the ConfigurablePanel subclass that the MMProperty linked to its UIProperty {@code propertyName}
	 * has changed in value. This function is called on the EDT and allows the subclass to change the states of
	 * its JComponents based on the new value of the UIProperty.
	 * 
	 * @param propertyName Name of the Property whose value has changed.
	 * @param newvalue New value of the UIProperty
	 */
	protected abstract void propertyhasChanged(String propertyName, String newvalue);
		
	/**
	 * In this method, the subclasses can add Swing action listeners to its JComponents. Since the method is called after loading 
	 * a configuration, the values of the UIProperty and UIParameters are known and can be used with the static methods of 
	 * {@link de.embl.rieslab.emu.ui.swinglisteners.SwingUIListeners}.  
	 */
	protected abstract void addComponentListeners();
	
	/**
	 * In this method, the subclasses must create their InternalProperties and add them to the map of internal properties 
	 * using {@link #addInternalProperty(InternalProperty)}. The method is called in the constructor of a ConfigurablePanel.  
	 */
	protected abstract void initializeInternalProperties();
		
	/**
	 * Method called when an internal property's value has been changed. This allows the ConfigurablePanel
	 * subclasses to react to the change of vale.
	 * 
	 * @param propertyName Name of the internal property
	 */
	public abstract void internalpropertyhasChanged(String propertyName);
	
	/**
	 * In this method, the subclasses must create their UIparameter and add them to the map of properties using 
	 * {@link #addUIParameter(UIParameter)}. The method is called in the constructor of a ConfigurablePanel.  
	 */
	protected abstract void initializeParameters();
	
	/**
	 * Notifies the ConfigurablePanel subclass that the UIParameter {@code parameterName} value has changed.
	 * This method is called upon loading of the UI and whenever the user changes the configuration.
	 * 
	 * @param parameterName Name of the UIParameter
	 */
	protected abstract void parameterhasChanged(String parameterName);

	/**
	 * Returns the description of the ConfigurablePanel. 
	 * 
	 * @return Description of the ConfigurablePanel.
	 */
	public abstract String getDescription();
	
	/**
	 * Allows the ConfigurablePanel subclass to shut down all processes, e.g. SwingWorkers. This method is called 
	 * when EMU is closing.
	 */
	public abstract void shutDown();
}
