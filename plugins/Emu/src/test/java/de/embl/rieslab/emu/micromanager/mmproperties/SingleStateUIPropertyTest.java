package de.embl.rieslab.emu.micromanager.mmproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.controller.log.Logger;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.PropertyPair;
import de.embl.rieslab.emu.ui.uiproperties.SingleStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIPropertyType;
import de.embl.rieslab.emu.utils.exceptions.AlreadyAssignedUIPropertyException;
import de.embl.rieslab.emu.utils.exceptions.IncompatibleMMProperty;

public class SingleStateUIPropertyTest {

	@Test
	public void testUIPropertyType() throws AlreadyAssignedUIPropertyException {
		SingleStateUIPropertyTestPanel cp = new SingleStateUIPropertyTestPanel("MyPanel");

		assertEquals(UIPropertyType.SINGLESTATE, cp.property.getType());
	}
	
	@Test
	public void testSettingStateValue() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		SingleStateUIPropertyTestPanel cp = new SingleStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String val = "4";
		
		boolean b = cp.property.setStateValue(val);
		assertTrue(b);
		assertEquals(val, cp.property.getStateValue());
	}
	
	@Test
	public void testSettingWrongStateValue() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		SingleStateUIPropertyTestPanel cp = new SingleStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);
		
		boolean b = cp.property.setStateValue("");
		assertFalse(b);
		
		b = cp.property.setStateValue(null);
		assertFalse(b);
		
		b = cp.property.setStateValue("dsadas");
		assertFalse(b);
	}

	@Test
	public void testSettingValue() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		SingleStateUIPropertyTestPanel cp = new SingleStateUIPropertyTestPanel("MyPanel");

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

		final String val = "4";
		cp.property.setStateValue(val);
		
		boolean b = cp.property.setPropertyValue(val);
		assertTrue(b);
		assertEquals(val, cp.property.getPropertyValue());

		b = cp.property.setPropertyValue(null);
		assertFalse(b);
		assertEquals(val, cp.property.getPropertyValue());

		b = cp.property.setPropertyValue("false");
		assertFalse(b);
		assertEquals(val, cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("");
		assertFalse(b);
		assertEquals(val, cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("fdsfeg");
		assertFalse(b);
		assertEquals(val, cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("2.48");
		assertFalse(b);
		assertEquals(val, cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("5");
		assertFalse(b);
		assertEquals(val, cp.property.getPropertyValue());
	}

	@Test
	public void testSettingGenericValue() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		SingleStateUIPropertyTestPanel cp = new SingleStateUIPropertyTestPanel("MyPanel");

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

		final String val = "4";
		cp.property.setStateValue(val);
		
		boolean b = cp.property.setPropertyValue(SingleStateUIProperty.getStateLabel());
		assertTrue(b);
		assertEquals(val, cp.property.getPropertyValue());
	}
	
	private class SingleStateUIPropertyTestPanel extends ConfigurablePanel{

		private static final long serialVersionUID = 1L;
		public SingleStateUIProperty property;
		public final String PROP = "MyProp"; 
		public final String DESC = "MyDescription";
	 	
		public SingleStateUIPropertyTestPanel(String label) {
			super(label);
		}
		
		@Override
		protected void initializeProperties() {
			property = new SingleStateUIProperty(this, PROP, DESC);
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
