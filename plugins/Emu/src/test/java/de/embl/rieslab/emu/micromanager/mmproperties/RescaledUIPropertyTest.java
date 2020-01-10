package de.embl.rieslab.emu.micromanager.mmproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import de.embl.rieslab.emu.controller.log.Logger;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.PropertyPair;
import de.embl.rieslab.emu.ui.uiproperties.RescaledUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIPropertyType;
import de.embl.rieslab.emu.utils.exceptions.AlreadyAssignedUIPropertyException;
import de.embl.rieslab.emu.utils.exceptions.IncompatibleMMProperty;

public class RescaledUIPropertyTest {


	@Test
	public void testUIPropertyType() throws AlreadyAssignedUIPropertyException {
		RescaledUIPropertyTestPanel cp = new RescaledUIPropertyTestPanel("MyPanel");

		assertEquals(UIPropertyType.RESCALED, cp.property1.getType());
		assertEquals(UIPropertyType.RESCALED, cp.property2.getType());
		assertFalse(cp.property2.setScalingFactors(1., 1.));
	}

	@Test(expected = IncompatibleMMProperty.class)
	public void testNotIntFloatPairing() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		RescaledUIPropertyTestPanel cp = new RescaledUIPropertyTestPanel("MyPanel");

		final StringMMProperty mmprop = new StringMMProperty(null, new Logger(), MMProperty.MMPropertyType.STRING, "", "") {
			
			@Override
			public String getValue() { // avoids NullPointerException
				return "";
			}
		};

		assertFalse(cp.property1.isCompatibleMMProperty(mmprop));
		PropertyPair.pair(cp.property1, mmprop);
	}


	@Test(expected = IncompatibleMMProperty.class)
	public void testNoLimitIntPairing() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		RescaledUIPropertyTestPanel cp = new RescaledUIPropertyTestPanel("MyPanel");

		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", false) {
			
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};

		assertFalse(cp.property1.isCompatibleMMProperty(mmprop));
		PropertyPair.pair(cp.property1, mmprop);
	}

	@Test(expected = IncompatibleMMProperty.class)
	public void testNoLimitFloatPairing() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		RescaledUIPropertyTestPanel cp = new RescaledUIPropertyTestPanel("MyPanel");

		final FloatMMProperty mmprop = new FloatMMProperty(null, new Logger(), "", "", false) {
			
			@Override
			public Float getValue() { // avoids NullPointerException
				return (float) 0.;
			}
		};

		assertFalse(cp.property1.isCompatibleMMProperty(mmprop));
		PropertyPair.pair(cp.property1, mmprop);
	}
	

	@Test
	public void testIntegerMMProp() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		RescaledUIPropertyTestPanel cp = new RescaledUIPropertyTestPanel("MyPanel");

		final int min1 = -14;
		final int max1 = 46;
		final int max2 = 255;
		final int min2 = 0;
		AtomicInteger reporter1 = new AtomicInteger(1);
		AtomicInteger reporter2 = new AtomicInteger(1);
		final IntegerMMProperty mmprop1 = new IntegerMMProperty(null, new Logger(), "", "", max1, min1) {
			@Override
			public boolean setValue(String stringval, UIProperty source){
				reporter1.set(Integer.valueOf(stringval));
				return true;
			}
			
			@Override
			public Integer getValue() { // avoids NullPointerException
				return reporter1.get();
			}
		};
		final IntegerMMProperty mmprop2 = new IntegerMMProperty(null, new Logger(), "", "", max2, min2) {
			@Override
			public boolean setValue(String stringval, UIProperty source){
				reporter2.set(Integer.valueOf(stringval));
				return true;
			}
			
			@Override
			public Integer getValue() { // avoids NullPointerException
				return reporter2.get();
			}
		};

		PropertyPair.pair(cp.property1, mmprop1);
		PropertyPair.pair(cp.property2, mmprop2);

		double slope1 = -4;
		double slope2 = max2/100.;
		double offset1 = 2;
		double offset2 = 0;

		// set scaling factors
		assertTrue(cp.property1.setScalingFactors(slope1, offset1));
		assertTrue(cp.property1.haveSlopeOffsetBeenSet());
		assertEquals(slope1, cp.property1.getSlope(), 0.0001);
		assertEquals(offset1, cp.property1.getOffset(), 0.0001);
		
		assertTrue(cp.property2.setScalingFactors(slope2, offset2));
		assertTrue(cp.property2.haveSlopeOffsetBeenSet());
		assertEquals(slope2, cp.property2.getSlope(), 0.0001);
		assertEquals(offset2, cp.property2.getOffset(), 0.0001);
		
		// set values
		int minval1 = (int) ((max1-offset1)/slope1);
		int maxval1 = (int) ((min1-offset1)/slope1);
		assertTrue(cp.property1.setPropertyValue(String.valueOf(minval1)));
		assertEquals(max1, reporter1.get());
		assertTrue(cp.property1.setPropertyValue(String.valueOf(maxval1)));
		assertEquals(min1, reporter1.get());
		assertTrue(cp.property1.setPropertyValue(String.valueOf(0)));
		assertEquals((int) offset1, reporter1.get());
		
		// set values with percentages
		assertTrue(cp.property2.setPropertyValue(String.valueOf(0)));
		assertEquals(min2, reporter2.get());
		assertTrue(cp.property2.setPropertyValue(String.valueOf(100)));
		assertEquals(max2, reporter2.get());
		assertTrue(cp.property2.setPropertyValue(String.valueOf(60)));
		assertEquals(153, reporter2.get());		
	}

	@Test
	public void testFloatMMProp() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		RescaledUIPropertyTestPanel cp = new RescaledUIPropertyTestPanel("MyPanel");

		final double min1 = -14.15;
		final double max1 = 46.78;
		final double max2 = 255.;
		final double min2 = 0.;

		final FloatMMProperty mmprop1 = new FloatMMProperty(null, new Logger(), "", "", max1, min1) {
			public Float val;
			
			@Override
			public boolean setValue(String stringval, UIProperty source){
				val = new Float(stringval);
				return true;
			}
			
			@Override
			public Float getValue() { // avoids NullPointerException
				return val;
			}
		};
		final FloatMMProperty mmprop2 = new FloatMMProperty(null, new Logger(), "", "", max2, min2) {
			public Float val;
			
			@Override
			public boolean setValue(String stringval, UIProperty source){
				val = new Float(stringval);
				return true;
			}
			
			@Override
			public Float getValue() { // avoids NullPointerException
				return val;
			}
		};

		PropertyPair.pair(cp.property1, mmprop1);
		PropertyPair.pair(cp.property2, mmprop2);

		double slope1 = -4.78;
		double slope2 = max2/100.;
		double offset1 = 2.64;
		double offset2 = 0;

		// set scaling factors
		assertTrue(cp.property1.setScalingFactors(slope1, offset1));
		assertTrue(cp.property1.haveSlopeOffsetBeenSet());
		assertEquals(slope1, cp.property1.getSlope(), 0.0001);
		assertEquals(offset1, cp.property1.getOffset(), 0.0001);
		
		assertTrue(cp.property2.setScalingFactors(slope2, offset2));
		assertTrue(cp.property2.haveSlopeOffsetBeenSet());
		assertEquals(slope2, cp.property2.getSlope(), 0.0001);
		assertEquals(offset2, cp.property2.getOffset(), 0.0001);
		
		// set values
		double minval1 = ((max1-offset1)/slope1);
		double maxval1 = ((min1-offset1)/slope1);
		assertTrue(cp.property1.setPropertyValue(String.valueOf(minval1)));
		assertEquals(max1, mmprop1.getValue(), 0.00001);
		assertTrue(cp.property1.setPropertyValue(String.valueOf(maxval1)));
		assertEquals(min1,mmprop1.getValue(), 0.00001);
		assertTrue(cp.property1.setPropertyValue(String.valueOf(0)));
		assertEquals(offset1, mmprop1.getValue(), 0.00001);
		
		// set values with percentages
		assertTrue(cp.property2.setPropertyValue(String.valueOf(0)));
		assertEquals(min2, mmprop2.getValue(), 0.00001);
		assertTrue(cp.property2.setPropertyValue(String.valueOf(100)));
		assertEquals(max2, mmprop2.getValue(), 0.00001);
		assertTrue(cp.property2.setPropertyValue(String.valueOf(60)));
		assertEquals(153., mmprop2.getValue(), 0.00001);		
	}

	@Test
	public void testNotifyIntegerMMProp() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty, InterruptedException {
		RescaledUIPropertyTestPanel cp = new RescaledUIPropertyTestPanel("MyPanel");

		final int min = -14;
		final int max = 46;
		final IntegerMMProperty mmprop = new IntegerMMProperty(null, new Logger(), "", "", max, min) {
			@Override
			public boolean setValue(String stringval, UIProperty source){
				return true;
			}
			
			@Override
			public Integer getValue() { // avoids NullPointerException
				return 0;
			}
		};

		PropertyPair.pair(cp.property1, mmprop);

		double slope = -4;
		double offset = 2;

		// set scaling factors
		assertTrue(cp.property1.setScalingFactors(slope, offset));
		assertTrue(cp.property1.haveSlopeOffsetBeenSet());
		assertEquals(slope, cp.property1.getSlope(), 0.0001);
		assertEquals(offset, cp.property1.getOffset(), 0.0001);
		
		// notify new value
		int expectedMax = (int) ((max-offset)/slope);  
		mmprop.notifyListeners(null, String.valueOf(max));
		Thread.sleep(20);
		assertEquals(expectedMax, cp.intVal);
		
		int expectedMin = (int) ((min-offset)/slope);  
		mmprop.notifyListeners(null, String.valueOf(min));
		Thread.sleep(20);
		assertEquals(expectedMin, cp.intVal);
	}

	@Test
	public void testFloatIntegerMMProp() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty, InterruptedException {
		RescaledUIPropertyTestPanel cp = new RescaledUIPropertyTestPanel("MyPanel");

		final double min = -14.784;
		final double max = 46.44;
		final FloatMMProperty mmprop = new FloatMMProperty(null, new Logger(), "", "", max, min) {
			
			@Override
			public boolean setValue(String stringval, UIProperty source){
				return true;
			}
			
			@Override
			public Float getValue() { // avoids NullPointerException
				return (float) 0.;
			}
		};

		PropertyPair.pair(cp.property2, mmprop);

		double slope = -4.5641;
		double offset = 2.874;

		// set scaling factors
		assertTrue(cp.property2.setScalingFactors(slope, offset));
		assertTrue(cp.property2.haveSlopeOffsetBeenSet());
		assertEquals(slope, cp.property2.getSlope(), 0.0001);
		assertEquals(offset, cp.property2.getOffset(), 0.0001);
		
		// notify new value
		double expectedMax = ((max-offset)/slope);  
		mmprop.notifyListeners(null, String.valueOf(max));
		Thread.sleep(20);
		assertEquals(expectedMax, cp.floatVal, 0.0001);
		
		double expectedMin = ((min-offset)/slope);  
		mmprop.notifyListeners(null, String.valueOf(min));
		Thread.sleep(20);
		assertEquals(expectedMin, cp.floatVal, 0.0001);
	}

	@Test
	public void testWrongValues() throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
		RescaledUIPropertyTestPanel cp = new RescaledUIPropertyTestPanel("MyPanel");

		final double min = -12;
		final double max = 47;
		AtomicInteger reporter1 = new AtomicInteger();
		final IntegerMMProperty mmprop1 = new IntegerMMProperty(null, new Logger(), "", "", max, min) {
			@Override
			public Integer getValue() { // avoids NullPointerException
				return reporter1.get();
			}
		};

		PropertyPair.pair(cp.property1, mmprop1);

		assertFalse(cp.property1.setScalingFactors(Double.NaN, 1.2));
		assertFalse(cp.property1.setScalingFactors(2.3, Double.NEGATIVE_INFINITY));
		assertFalse(cp.property1.setScalingFactors(2.3, Double.POSITIVE_INFINITY));
		assertFalse(cp.property1.setScalingFactors(0., 1.5));
		assertFalse(cp.property1.setScalingFactors(0.0, 1.5));
	}
	
	private class RescaledUIPropertyTestPanel extends ConfigurablePanel{

		private static final long serialVersionUID = 1L;
		public RescaledUIProperty property1, property2;
		public final String PROP1 = "MyProp1"; 
		public final String DESC1 = "MyDescription1";
		public final String PROP2 = "MyProp2"; 
		public final String DESC2 = "MyDescription2";
		public int intVal;
		public float floatVal;  
	 	
		public RescaledUIPropertyTestPanel(String label) {
			super(label);

			intVal = -1000;
			floatVal = (float) -1000.;
		}
		
		@Override
		protected void initializeProperties() {
			property1 = new RescaledUIProperty(this, PROP1, DESC1);
			property2 = new RescaledUIProperty(this, PROP2, DESC2);
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
		protected void propertyhasChanged(String propertyName, String newvalue) {
			if(propertyName.equals(PROP1)) {
				intVal = Integer.parseInt(newvalue);
			} else if(propertyName.equals(PROP2)) {
				floatVal = Float.parseFloat(newvalue);
			}
		}

		@Override
		public void shutDown() {}

		@Override
		protected void addComponentListeners() {}
		
		@Override
		public String getDescription() {return "";}
	}
}
