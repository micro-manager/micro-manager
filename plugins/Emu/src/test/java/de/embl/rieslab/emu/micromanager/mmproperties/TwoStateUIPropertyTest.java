package de.embl.rieslab.emu.micromanager.mmproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.controller.log.Logger;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.PropertyPair;
import de.embl.rieslab.emu.ui.uiproperties.TwoStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIPropertyType;
import de.embl.rieslab.emu.utils.exceptions.AlreadyAssignedUIPropertyException;
import de.embl.rieslab.emu.utils.exceptions.IncompatibleMMProperty;

public class TwoStateUIPropertyTest {


	@Test
	public void testUIPropertyType() throws AlreadyAssignedUIPropertyException {
		TwoStateUIPropertyTestPanel cp = new TwoStateUIPropertyTestPanel("MyPanel");

		assertEquals(UIPropertyType.TWOSTATE, cp.property.getType());
	}
	
	@Test
	public void testSettingOnOffValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		TwoStateUIPropertyTestPanel cp = new TwoStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String offval = "4";
		final String onval = "8";
		
		boolean b = cp.property.setOffStateValue(offval);
		assertTrue(b);
		assertEquals(offval, cp.property.getOffStateValue());
		
		b = cp.property.setOnStateValue(onval);	
		assertTrue(b);
		assertEquals(onval, cp.property.getOnStateValue());
	}

	@Test
	public void testSettingWrongOnOffValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		TwoStateUIPropertyTestPanel cp = new TwoStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String offval = "4.1";
		
		boolean b = cp.property.setOffStateValue(offval); // IntegerMMProperty round up doubles to an int
		assertTrue(b);
		assertEquals(offval, cp.property.getOffStateValue());
		
		b = cp.property.setOnStateValue("");	
		assertFalse(b);
		
		b = cp.property.setOnStateValue(null);	
		assertFalse(b);
		
		b = cp.property.setOnStateValue("dfsdf");	
		assertFalse(b);
	}
	
	@Test
	public void testSettingValue() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		TwoStateUIPropertyTestPanel cp = new TwoStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
			
			@Override
			public String getStringValue() {
				return convertToString(value);
			}
			
			@Override
			public boolean setValue(String stringval, UIProperty source){
				value = convertToValue(stringval);
				return true;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		// sets on and off values
		final String offval = "4";
		final String onval = "8";
		cp.property.setOffStateValue(offval);
		cp.property.setOnStateValue(onval);	

		// sets value to onval
		boolean b  = cp.property.setPropertyValue(onval);
		assertTrue(b);
		assertEquals(onval, cp.property.getPropertyValue());

		// sets value to offval
		b = cp.property.setPropertyValue(offval);
		assertTrue(b);
		assertEquals(offval, cp.property.getPropertyValue());

		// sets value to the OnState using the generic name
		b = cp.property.setPropertyValue(TwoStateUIProperty.getOnStateLabel());
		assertTrue(b);
		assertEquals(onval, cp.property.getPropertyValue());

		// sets value to the OffState using the generic name
		b = cp.property.setPropertyValue(TwoStateUIProperty.getOffStateLabel());
		assertTrue(b);
		assertEquals(offval, cp.property.getPropertyValue());

		// sets value to the OnState using 1
		b = cp.property.setPropertyValue("1");
		assertTrue(b);
		assertEquals(onval, cp.property.getPropertyValue());

		// sets value to the OffState using 0
		b = cp.property.setPropertyValue("0");
		assertTrue(b);
		assertEquals(offval, cp.property.getPropertyValue());

		// sets value to the OnState using true
		b = cp.property.setPropertyValue("true");
		assertTrue(b);
		assertEquals(onval, cp.property.getPropertyValue());

		// sets value to the OffState using false
		b = cp.property.setPropertyValue("false");
		assertTrue(b);
		assertEquals(offval, cp.property.getPropertyValue());

		// sets value to null (should be refused and value should not change)
		b = cp.property.setPropertyValue(null);
		assertFalse(b);
		assertEquals(offval, cp.property.getPropertyValue());

		// sets value to null (should be refused and value should not change)		
		b = cp.property.setPropertyValue("");
		assertFalse(b);
		assertEquals(offval, cp.property.getPropertyValue());

		// sets value to a wrong value	
		b = cp.property.setPropertyValue("fdsfeg");
		assertFalse(b);
		assertEquals(offval, cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("2.48");
		assertFalse(b);
		assertEquals(offval, cp.property.getPropertyValue());	
	}	
	
	@Test
	public void testIsOnOffValueIntegerMMProperty() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		TwoStateUIPropertyTestPanel cp = new TwoStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
			
			@Override
			public String getStringValue() {
				return convertToString(value);
			}
			
			@Override
			public boolean setValue(String stringval, UIProperty source){
				value = convertToValue(stringval);
				return true;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		// sets on and off values
		final String offval = "4";
		final String onval = "-8";
		cp.property.setOffStateValue(offval);
		cp.property.setOnStateValue(onval);	
		
		// tests if the states value are recognized
		assertTrue(cp.property.isOnState(onval));
		assertTrue(cp.property.isOffState(offval));
		assertFalse(cp.property.isOnState(offval));
		assertFalse(cp.property.isOffState(onval));
		
		// tests that rounding works
		assertTrue(cp.property.isOnState("-8.1"));
		assertTrue(cp.property.isOffState("4.1"));
		
		// tests if wrong values are recognized as states
		assertFalse(cp.property.isOnState(""));
		assertFalse(cp.property.isOffState(""));
		assertFalse(cp.property.isOnState(null));
		assertFalse(cp.property.isOffState(null));
		assertFalse(cp.property.isOnState("4"));
		assertFalse(cp.property.isOffState("-8"));
		assertFalse(cp.property.isOnState("-89"));
		assertFalse(cp.property.isOffState("42"));
		assertFalse(cp.property.isOnState("-8f"));
		assertFalse(cp.property.isOffState("4g"));
	}

	
	@Test
	public void testIsOnOffValueFloatMMProperty() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		TwoStateUIPropertyTestPanel cp = new TwoStateUIPropertyTestPanel("MyPanel");

		final FloatMMProperty mmprop = new FloatMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Float getValue() { // avoids NullPointerException
				return (float) 0.;
			}
			
			@Override
			public String getStringValue() {
				return convertToString(value);
			}
			
			@Override
			public boolean setValue(String stringval, UIProperty source){
				value = convertToValue(stringval);
				return true;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		// sets on and off values
		final String offval = "4.145";
		final String onval = "-8.778";
		cp.property.setOffStateValue(offval);
		cp.property.setOnStateValue(onval);	
		
		// tests if the states value are recognized
		assertTrue(cp.property.isOnState(onval));
		assertTrue(cp.property.isOffState(offval));
		assertFalse(cp.property.isOnState(offval));
		assertFalse(cp.property.isOffState(onval));
		
		// tests precision
		assertTrue(cp.property.isOnState("-8.777999"));
		assertTrue(cp.property.isOffState("4.144999"));
		assertTrue(cp.property.isOnState("-8.778000"));
		assertTrue(cp.property.isOffState("4.145000"));
		assertFalse(cp.property.isOnState("-8.7779"));
		assertFalse(cp.property.isOffState("4.1449"));
		
		// tests if wrong values are recognized as states
		assertFalse(cp.property.isOnState(""));
		assertFalse(cp.property.isOffState(""));
		assertFalse(cp.property.isOnState(null));
		assertFalse(cp.property.isOffState(null));
		assertFalse(cp.property.isOnState("4"));
		assertFalse(cp.property.isOffState("-8"));
		assertFalse(cp.property.isOnState("-89"));
		assertFalse(cp.property.isOffState("42"));
		assertFalse(cp.property.isOnState("-8f"));
		assertFalse(cp.property.isOffState("4g"));
		assertFalse(cp.property.isOnState("-465.684"));
		assertFalse(cp.property.isOffState("4.548"));
	}
	

	@Test
	public void testAssigningOnAndOff01Values() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		TwoStateUIPropertyTestPanel cp = new TwoStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
			
			@Override
			public String getStringValue() {
				return convertToString(value);
			}
			
			@Override
			public boolean setValue(String stringval, UIProperty source){
				value = convertToValue(stringval);
				return true;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		// set state values opposite to accepted value to test priority given to the value
		final String offval = "1";
		final String onval = "0";
		cp.property.setOffStateValue(offval);
		cp.property.setOnStateValue(onval);	

		boolean b = cp.property.setPropertyValue("0");
		assertTrue(b);
		assertEquals(onval, cp.property.getPropertyValue());

		b = cp.property.setPropertyValue("1");
		assertTrue(b);
		assertEquals(offval, cp.property.getPropertyValue());
	}
	
	@Test
	public void testAssigningOnAndOffBooleanValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		TwoStateUIPropertyTestPanel cp = new TwoStateUIPropertyTestPanel("MyPanel");

		final StringMMProperty mmprop = new StringMMProperty(null, new Logger(), MMProperty.MMPropertyType.STRING, "", "", new String[] {"true", "false"}) {
			@Override
			public String getValue() { // avoids NullPointerException
				return "";
			}
			
			@Override
			public String getStringValue() {
				return convertToString(value);
			}
			
			@Override
			public boolean setValue(String stringval, UIProperty source){
				value = convertToValue(stringval);
				return true;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		// set state values opposite to values otherwise accepted
		final String offval = "true";
		final String onval = "false";
		cp.property.setOffStateValue(offval);
		cp.property.setOnStateValue(onval);	

		boolean b = cp.property.setPropertyValue("false");
		assertTrue(b);
		assertEquals(onval, cp.property.getPropertyValue());

		b = cp.property.setPropertyValue("true");
		assertTrue(b);
		assertEquals(offval, cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("1");
		assertTrue(b);
		assertEquals(onval, cp.property.getPropertyValue());

		b = cp.property.setPropertyValue("0");
		assertTrue(b);
		assertEquals(offval, cp.property.getPropertyValue());
	}
		
	private class TwoStateUIPropertyTestPanel extends ConfigurablePanel{

		private static final long serialVersionUID = 1L;
		public TwoStateUIProperty property;
		public final String PROP = "MyProp"; 
		public final String DESC = "MyDescription";
	 	
		public TwoStateUIPropertyTestPanel(String label) {
			super(label);
		}
		
		@Override
		protected void initializeProperties() {
			property = new TwoStateUIProperty(this, PROP, DESC);
		}
		
		@Override
		protected void initializeParameters() {}
		
		@Override
		protected void parameterhasChanged(String parameterName) {}
		
		@Override
		protected void initializeInternalProperties() {}

		@Override
		public void internalpropertyhasChanged(String propertyName) {}

		@Override
		protected void propertyhasChanged(String propertyName, String newvalue) {}

		@Override
		public void shutDown() {}

		@Override
		protected void addComponentListeners() {}
		
		@Override
		public String getDescription() {return "";}
	}
}
