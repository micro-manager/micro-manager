package de.embl.rieslab.emu.ui.uiparameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.flag.PropertyFlag;

public class UIPropertyParameterTest {

	@Test
	public void testUIPropertyParameterCreation() {
		UIPropertyParamTestPanel cp = new UIPropertyParamTestPanel("My panel");

		assertEquals(UIPropertyParameter.NO_PROPERTY, cp.parameter.getStringValue());		
		assertEquals(UIPropertyParameter.NO_PROPERTY, cp.parameter.getValue());
		
		assertEquals(cp.flag, cp.parameter.getFlag());
		
		assertEquals(UIParameter.UIParameterType.UIPROPERTY, cp.parameter.getType());
		assertEquals(UIParameter.getHash(cp, cp.PARAM), cp.parameter.getHash());
		assertEquals(cp.PARAM, cp.parameter.getLabel());
		assertEquals(cp.DESC, cp.parameter.getDescription());
	}

	@Test
	public void testAreValuesSuitable() {
		UIPropertyParamTestPanel cp = new UIPropertyParamTestPanel("My panel");

		// test if suitable
		assertTrue(cp.parameter.isSuitable("false"));
		assertTrue(cp.parameter.isSuitable("fdesggiojo"));
		assertTrue(cp.parameter.isSuitable("()9[]54@#'#~"));
		assertTrue(cp.parameter.isSuitable("448766"));
		assertTrue(cp.parameter.isSuitable(""));
		
		assertFalse(cp.parameter.isSuitable(null));
	}
	
	@Test
	public void testChangeValue() {
		UIPropertyParamTestPanel cp = new UIPropertyParamTestPanel("My panel");

		String s = "2fsdj*745+$\u00A3%$6(*&) {}~'";
		cp.parameter.setStringValue(s);
		assertEquals(s, cp.parameter.getStringValue());	
		assertEquals(s, cp.parameter.getValue());	

		s = "\u00A3$sdjdsn";
		cp.parameter.setValue(s);
		assertEquals(s, cp.parameter.getStringValue());	
		assertEquals(s, cp.parameter.getValue());	
	}
	
	@Test(expected = NullPointerException.class) 
	public void testSetNullOwner() {
		new UIPropertyParamTestPanel("My panel") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeParameters() {
				flag = new PropertyFlag("Union Jack") {};
				
				parameter = new UIPropertyParameter(null, PARAM, DESC, flag);
			}
		};
	}
	
	@Test(expected = NullPointerException.class) 
	public void testSetNullLabel() {
		new UIPropertyParamTestPanel("My panel") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeParameters() {
				flag = new PropertyFlag("Union Jack") {};
				
				parameter = new UIPropertyParameter(this, null, DESC, flag);
			}
		};
	}

	@Test(expected = NullPointerException.class) 
	public void testSetNullDescription() {
		new UIPropertyParamTestPanel("My panel") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeParameters() {
				flag = new PropertyFlag("Union Jack") {};
				
				parameter = new UIPropertyParameter(this, PARAM, null, flag);
			}
		};
	}

	@Test(expected = NullPointerException.class) 
	public void testSetNullFlag() {
		new UIPropertyParamTestPanel("My panel") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeParameters() {
				flag = new PropertyFlag("Union Jack") {};
				
				parameter = new UIPropertyParameter(this, PARAM, DESC, null);
			}
		};
	}
	
	private class UIPropertyParamTestPanel extends ConfigurablePanel{

		private static final long serialVersionUID = -3307917280544843397L;
		public UIPropertyParameter parameter;
		public final String PARAM = "MyParam"; 
		public final String DESC = "MyDescription"; 
		public PropertyFlag flag;
		
		public UIPropertyParamTestPanel(String label) {
			super(label);
		}

		@Override
		protected void initializeParameters() {
			flag = new PropertyFlag("Union Jack") {};
			
			parameter = new UIPropertyParameter(this, PARAM, DESC, flag);
		}
		
		@Override
		protected void parameterhasChanged(String parameterName) {}
		
		@Override
		protected void initializeInternalProperties() {}

		@Override
		protected void initializeProperties() {}

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
