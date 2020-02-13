package de.embl.rieslab.emu.ui.uiproperties;

import de.embl.rieslab.emu.micromanager.mmproperties.FloatMMProperty;
import de.embl.rieslab.emu.micromanager.mmproperties.IntegerMMProperty;
import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.flag.PropertyFlag;
import de.embl.rieslab.emu.utils.EmuUtils;

/**
 * A RescaledUIProperty is only compatible with IntegerMMProperty and FloatMMProperty. Once the scaling factors have been set, any value
 * set using {@link #setPropertyValue(String)} will be scaled to {@code slope*value+offset}. Inversely, any update from the MMProperty will
 * be scaled to {@code (value-offset)/slope}.
 * 
 * @author Joran Deschamps
 *
 */
public class RescaledUIProperty extends UIProperty{
	
	private double slope_ = 1., offset_ = 0., rescaledMin_, rescaledMax_;
	private boolean limitsSet_ = false;
	
	public RescaledUIProperty(ConfigurablePanel owner, String label, String description) {
		super(owner, label, description);
	}

	public RescaledUIProperty(ConfigurablePanel owner, String label, String description, PropertyFlag flag) {
		super(owner, label, description, flag);
	}
	
	public RescaledUIProperty(ConfigurablePanel owner, String label, String description, double defaultSlope, double defaultOffset) {
		super(owner, label, description);
		
		slope_ = defaultSlope;
		offset_ = defaultOffset;
	}
	
	public RescaledUIProperty(ConfigurablePanel owner, String label, String description, double defaultSlope, double defaultOffset, PropertyFlag flag) {
		super(owner, label, description, flag);
		
		slope_ = defaultSlope;
		offset_ = defaultOffset;
	}

	@Override
	public boolean isCompatibleMMProperty(@SuppressWarnings("rawtypes") MMProperty prop) {
		if(prop.getType() == MMProperty.MMPropertyType.FLOAT 
				|| prop.getType() == MMProperty.MMPropertyType.INTEGER) {
			if(prop.hasLimits()) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Sets the scaling factors applied to scale the values submitted to the UIProperty to the limits of the MMProperty.
	 * If the slope or the offset are not finite, the values will be refused. The slope cannot be zero.
	 * 
	 * @param slope Slope
	 * @param offset Offset
	 * @return True if the slope and offset were set, false otherwise.
	 */
	public boolean setScalingFactors(double slope, double offset) {
		if(isAssigned() && Double.isFinite(slope) && Double.isFinite(offset) && Double.compare(slope, 0) != 0) {			
			if(getMMProperty().getType() == MMProperty.MMPropertyType.FLOAT) {
				slope_ = slope;
				offset_ = offset;
				
				Float max = ((FloatMMProperty) getMMProperty()).getMax();
				Float min = ((FloatMMProperty) getMMProperty()).getMin();

				double rescaledMax = (max-offset)/slope;
				double rescaledMin = (min-offset)/slope;
				
				if(slope_ > 0) {
					rescaledMax_ = rescaledMax;
					rescaledMin_ = rescaledMin;
				} else {
					rescaledMax_ = rescaledMin;
					rescaledMin_ = rescaledMax;
				}
				limitsSet_ = true;
				return true;
			} else { // MMProperty is of Integer type
				slope_ = slope;
				offset_ = offset;
				
				Integer max = ((IntegerMMProperty) getMMProperty()).getMax();
				Integer min = ((IntegerMMProperty) getMMProperty()).getMin();

				double rescaledMax = (max-offset)/slope;
				double rescaledMin = (min-offset)/slope;

				if(slope_ > 0) {
					rescaledMax_ = rescaledMax;
					rescaledMin_ = rescaledMin;
				} else {
					rescaledMax_ = rescaledMin;
					rescaledMin_ = rescaledMax;
				}
				limitsSet_ = true;
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean setPropertyValue(String newValue){
		if(isAssigned()){
			if(!limitsSet_) {
				return getMMProperty().setValue(newValue, this);
			} else if(getMMProperty().getType() == MMProperty.MMPropertyType.FLOAT && EmuUtils.isFloat(newValue)) {
				Float val = new Float(newValue);
				
				if(val.compareTo((float) rescaledMin_) >= 0 && val.compareTo((float) rescaledMax_) <= 0) {
					Float rescaledValue = new Float(val*slope_+offset_);
					
					return getMMProperty().setValue(rescaledValue.toString(), this);
				}
			} else if(getMMProperty().getType() == MMProperty.MMPropertyType.INTEGER && EmuUtils.isInteger(newValue)){
				Integer val = new Integer(newValue);
				
				if(val.compareTo((int) rescaledMin_) >= 0 && val.compareTo((int) rescaledMax_) <= 0) {
					Integer rescaledValue = new Integer(EmuUtils.roundToInt(slope_*val+offset_));
					return getMMProperty().setValue(rescaledValue.toString(), this);
				}
			}
		}  
		return false;
	}
	
	@Override
	public String getPropertyValue() {
		if (isAssigned()) {
			String value = getMMProperty().getStringValue();

			if(!limitsSet_) {
				return value;
			} else if(getMMProperty().getType() == MMProperty.MMPropertyType.FLOAT && EmuUtils.isFloat(value)) {
				return getScaledDownValue(new Float(value));
			} else if(getMMProperty().getType() == MMProperty.MMPropertyType.INTEGER && EmuUtils.isInteger(value)) {
				return getScaledDownValue(new Integer(value));
			}
		}
		return "";
	}
	
	@Override
	public void mmPropertyHasChanged(String value){
		if(limitsSet_) {
			if(getMMProperty().getType() == MMProperty.MMPropertyType.FLOAT && EmuUtils.isFloat(value)) {
				Float val = new Float(value);
				getOwner().triggerPropertyHasChanged(getPropertyLabel(),getScaledDownValue(val));
			} else if(getMMProperty().getType() == MMProperty.MMPropertyType.INTEGER && EmuUtils.isInteger(value)){
				Integer val = new Integer(value);
				getOwner().triggerPropertyHasChanged(getPropertyLabel(),getScaledDownValue(val));
			}
		} else {
			getOwner().triggerPropertyHasChanged(getPropertyLabel(),value);
		}
	}

	protected String getScaledDownValue(Float f) {
		Float rescaledValue = new Float((f-offset_)/slope_);
		return rescaledValue.toString();
	}
	
	protected String getScaledDownValue(Integer i) {
		Integer rescaledValue = new Integer((int) ((i-offset_)/slope_));
		return rescaledValue.toString();
	}
	
	public double getSlope() {
		return slope_;
	}
	
	public double getOffset() {
		return offset_;
	}
	
	public boolean haveSlopeOffsetBeenSet() {
		return limitsSet_;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public UIPropertyType getType() {
		return UIPropertyType.RESCALED;
	}

	public static String getOffsetLabel() {
		return " offset";
	}
	
	public static String getSlopeLabel() {
		return " slope";
	}
}
