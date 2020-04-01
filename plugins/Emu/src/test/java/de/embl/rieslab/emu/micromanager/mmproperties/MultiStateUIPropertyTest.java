package de.embl.rieslab.emu.micromanager.mmproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.controller.log.Logger;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.MultiStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.PropertyPair;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIPropertyType;
import de.embl.rieslab.emu.utils.exceptions.AlreadyAssignedUIPropertyException;
import de.embl.rieslab.emu.utils.exceptions.IncompatibleMMProperty;

public class MultiStateUIPropertyTest {


	@Test
	public void testUIPropertyType() throws AlreadyAssignedUIPropertyException {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		assertEquals(UIPropertyType.MULTISTATE, cp.property.getType());
	}
	
	@Test
	public void testNumberOfStates() {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		assertEquals(cp.nStates, cp.property.getNumberOfStates());
	}

	@Test (expected = IllegalArgumentException.class)
	public void testNumberNoState() {
		@SuppressWarnings("unused")
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeProperties() {
				nStates = 0;
				property = new MultiStateUIProperty(this, PROP, DESC, nStates);
			}
		};
	}

	@Test (expected = IllegalArgumentException.class)
	public void testNumbernegativeState() {
		@SuppressWarnings("unused")
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeProperties() {
				nStates = -1;
				property = new MultiStateUIProperty(this, PROP, DESC, nStates);
			}
		};
	}

	@Test
	public void testSettingStateValueNames() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] names = {"MyVal1", "MyVal2", "GrateValue", "NotAGoddValue"};
		boolean b  = cp.property.setStateNames(names);
		assertTrue(b);
		
		String[] names_b = cp.property.getStatesName();
		assertEquals(names.length, names_b.length);
		
		for(int i=0;i<names.length;i++) {
			assertEquals(names[i], names_b[i]);
			assertEquals(names[i], cp.property.getStateName(i));
		}
	}
	
	@Test
	public void testSettingTooManyStateValueNames() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] names = {"MyVal1", "MyVal2", "GrateValue", "NotAGoddValue", "What about this one?", "Idk"};
		boolean b = cp.property.setStateNames(names);
		assertTrue(b);
		
		String[] names_b = cp.property.getStatesName();		
		for(int i=0;i<cp.property.getNumberOfStates();i++)
			assertEquals(names[i], names_b[i]);
	}
	
	@Test
	public void testSettingTooFewStateValueNames() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] names = {"MyVal1", "MyVal2"};
		boolean b = cp.property.setStateNames(names);
		assertTrue(b);
		
		String[] names_b = cp.property.getStatesName();		
		for(int i=0;i<names.length;i++)
			assertEquals(names[i], names_b[i]);
		
		for(int i=names.length;i<cp.property.getNumberOfStates();i++)
			assertEquals(MultiStateUIProperty.getConfigurationStateLabel(i), names_b[i]);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testSettingWrongStateValueNames() {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final String[] names = {"", "fds", "dfsd", "dsgrg"};
		cp.property.setStateNames(names);
	}
	
	@Test (expected = NullPointerException.class)
	public void testSettingNullStateValueNames() {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final String[] names = {"sdasd", "fds", null, "dsgrg"};
		cp.property.setStateNames(names);
	}

	@Test
	public void testSettingStateValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] vals = {"54", "124", "987", "1"};
		boolean b = cp.property.setStateValues(vals);
		assertTrue(b);
		
		String[] states_b = cp.property.getStateValues();
		assertEquals(vals.length, states_b.length);
		
		for(int i=0;i<vals.length;i++)
			assertEquals(vals[i], states_b[i]);
	}

	@Test
	public void testSettingNullStateValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] vals = {"54", "124", null, "1"};
		boolean b = cp.property.setStateValues(vals);
		assertFalse(b);
	}
	
	@Test
	public void testSettingEmptyStateValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] vals = {"54", "124", "", "1"};
		boolean b = cp.property.setStateValues(vals);
		assertFalse(b);
	}
	
	@Test
	public void testSettingWrongStateValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] vals = {"54", "124", "grd", "1"};
		boolean b = cp.property.setStateValues(vals);
		assertFalse(b);
	}

	@Test
	public void testStateValuesAndNamesIntegerMMProp() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] vals = {"54", "124", "987", "1"};
		final String[] vals_rounding = {"54.1", "124.2", "987.3", "1.4"};
		final String[] names = {"Val1", "Val2", "Val3", "Val4"};
		cp.property.setStateValues(vals);
		cp.property.setStateNames(names);
		
		for(int i=0;i<vals.length;i++) {
			// actual vals
			assertEquals(names[i],cp.property.getStateNameFromValue(vals[i]));
			
			// check that rounding works
			assertEquals(names[i],cp.property.getStateNameFromValue(vals_rounding[i]));
		}
	}

	@Test
	public void testStateValuesAndNamesFloatMMProp() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final FloatMMProperty mmprop = new FloatMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Float getValue() { // avoids NullPointerException
				return (float) 0.;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] vals = {"54.154", "124.784", "987.548", "1.484"};
		final String[] vals_prec = {"54.153999", "124.783999", "987.547999", "1.483999"};
		final String[] vals_prec_wrong = {"54.1539", "124.7839", "987.5479", "1.4839"};
		final String[] vals_zeros = {"54.154000", "124.784000", "987.548000", "1.484000"};
		final String[] names = {"Val1", "Val2", "Val3", "Val4"};
		cp.property.setStateValues(vals);
		cp.property.setStateNames(names);
		
		for(int i=0;i<vals.length;i++) {
			// with actual vals
			assertEquals(names[i],cp.property.getStateNameFromValue(vals[i]));

			// with precision from SystemConstants
			assertEquals(names[i],cp.property.getStateNameFromValue(vals_prec[i]));

			// with precision lower than from SystemConstants, it should always give the first state
			assertEquals(names[0],cp.property.getStateNameFromValue(vals_prec_wrong[i]));
			
			// with zeros
			assertEquals(names[i],cp.property.getStateNameFromValue(vals_zeros[i]));
		}
	}

	
	@Test
	public void testStatePositions() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};
		
		PropertyPair.pair(cp.property, mmprop);

		final String[] vals = {"54", "124", "987", "1"};
		cp.property.setStateValues(vals);
		
		for(int i=0;i<vals.length;i++)
			assertEquals(i,cp.property.getStateIndex(vals[i]));
	}

	@Test
	public void testSetValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

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

		final String[] vals = {"54", "124", "987", "1"};
		final String[] names = {"Val1", "Val2", "Val3", "Val4"};
		cp.property.setStateValues(vals);
		cp.property.setStateNames(names);
		
		for(String v: vals) {
			boolean b = cp.property.setPropertyValue(v);
			assertTrue(b);
			assertEquals(v, cp.property.getPropertyValue());
		}
	}

	@Test
	public void testSetValuesFromName() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

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

		final String[] vals = {"54", "124", "987", "1"};
		final String[] names = {"Val1", "Val2", "Val3", "Val4"};
		cp.property.setStateValues(vals);
		cp.property.setStateNames(names);
		
		for(int i=0;i<vals.length;i++) {
			boolean b = cp.property.setPropertyValue(names[i]);
			assertTrue(b);
			assertEquals(vals[i], cp.property.getPropertyValue());
		}
		
		for(int i=0;i<vals.length;i++) {
			boolean b = cp.property.setPropertyValueByState(names[i]);
			assertTrue(b);
			assertEquals(vals[i], cp.property.getPropertyValue());
		}
	}

	@Test
	public void testSetValuesFromPosition() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

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

		final String[] vals = {"54", "124", "987", "13"};
		cp.property.setStateValues(vals);
		
		for(int i=0;i<vals.length;i++) {
			boolean b = cp.property.setPropertyValue(String.valueOf(i));
			assertTrue(b);
			assertEquals(vals[i], cp.property.getPropertyValue());
		}
		
		for(int i=0;i<vals.length;i++) {
			boolean b = cp.property.setPropertyValueByStateIndex(i);
			assertTrue(b);
			assertEquals(vals[i], cp.property.getPropertyValue());
		}
	}

	@Test
	public void testSetValuesPriority() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

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

		final String[] vals = {"32", "2", "124", "0"};
		final String[] names = {"Val1", "Val2", "Val3", "32"};
		cp.property.setStateValues(vals);
		cp.property.setStateNames(names);

		boolean b = cp.property.setPropertyValue("2");
		assertTrue(b);
		assertEquals(vals[1], cp.property.getPropertyValue());

		b = cp.property.setPropertyValue("0");
		assertTrue(b);
		assertEquals(vals[3], cp.property.getPropertyValue());

		b = cp.property.setPropertyValue("32");
		assertTrue(b);
		assertEquals(vals[0], cp.property.getPropertyValue());

		b = cp.property.setPropertyValue("Val2");
		assertTrue(b);
		assertEquals(vals[1], cp.property.getPropertyValue());
	}

	@Test
	public void testSetValuesPriorityWithAmbiguity() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

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

		final String[] vals = {"3", "1", "2", "0"};
		final String[] names = {"0", "1", "2", "3"};
		cp.property.setStateValues(vals);
		cp.property.setStateNames(names);

		boolean b = cp.property.setPropertyValue("3");
		assertTrue(b);
		assertEquals(vals[0], cp.property.getPropertyValue()); // takes state value in priority
		
		b = cp.property.setPropertyValue("2");
		assertTrue(b);
		assertEquals(vals[2], cp.property.getPropertyValue());
	}	
	
	@Test
	public void testSetValuesByStatePriorityWithAmbiguity() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

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

		final String[] vals = {"1", "3", "2", "0"};
		final String[] names = {"3", "1", "0", "2"};
		cp.property.setStateValues(vals);
		cp.property.setStateNames(names);

		boolean b;
		
		// by state value
		for(int i=0;i<vals.length;i++) {
			b = cp.property.setPropertyValue(vals[i]);
			assertTrue(b);
			assertEquals(vals[i], cp.property.getPropertyValue());
		}
		
		// by state index
		for(int i=0;i<vals.length;i++) {
			b = cp.property.setPropertyValueByStateIndex(i);
			assertTrue(b);
			assertEquals(vals[i], cp.property.getPropertyValue());
		}
		
		// by state name
		for(int i=0;i<vals.length;i++) {
			b = cp.property.setPropertyValueByState(names[i]);
			assertTrue(b);
			assertEquals(vals[i], cp.property.getPropertyValue());
		}
	}

	@Test
	public void testWrongValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		MultiStateUIPropertyTestPanel cp = new MultiStateUIPropertyTestPanel("MyPanel");

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

		final String[] vals = {"32", "2", "124", "0"};
		final String[] names = {"Val1", "Val2", "Val3", "Val4"};
		cp.property.setStateValues(vals);
		cp.property.setStateNames(names);

		cp.property.setPropertyValue(vals[0]);
		
		boolean b = cp.property.setPropertyValue("");
		assertFalse(b);
		assertEquals(vals[0], cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("415");
		assertFalse(b);
		assertEquals(vals[0], cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("4.56");
		assertFalse(b);
		assertEquals(vals[0], cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue(null);
		assertFalse(b);
		assertEquals(vals[0], cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("true");
		assertFalse(b);
		assertEquals(vals[0], cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue("fdsf");
		assertFalse(b);
		assertEquals(vals[0], cp.property.getPropertyValue());
		
		b = cp.property.setPropertyValue(names[3]);
		assertTrue(b);
		assertEquals(vals[3], cp.property.getPropertyValue());

		// by state indices
		b = cp.property.setPropertyValueByStateIndex(5);
		assertFalse(b);
		assertEquals(vals[3], cp.property.getPropertyValue());
		b = cp.property.setPropertyValueByStateIndex(-1);
		assertFalse(b);
		assertEquals(vals[3], cp.property.getPropertyValue());
		
		// by state names
		b = cp.property.setPropertyValueByState(null);
		assertFalse(b);
		assertEquals(vals[3], cp.property.getPropertyValue());
		b = cp.property.setPropertyValueByState("");
		assertFalse(b);
		assertEquals(vals[3], cp.property.getPropertyValue());
		b = cp.property.setPropertyValueByState("dsfs");
		assertFalse(b);
		assertEquals(vals[3], cp.property.getPropertyValue());
	}
	
	private class MultiStateUIPropertyTestPanel extends ConfigurablePanel{

		private static final long serialVersionUID = 1L;
		public MultiStateUIProperty property;
		public final String PROP = "MyProp"; 
		public final String DESC = "MyDescription";
		public int nStates;
	 	
		public MultiStateUIPropertyTestPanel(String label) {
			super(label);
		}
		
		@Override
		protected void initializeProperties() {
			nStates = 4;
			property = new MultiStateUIProperty(this, PROP, DESC, nStates);
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
