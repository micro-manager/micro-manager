package de.embl.rieslab.emu.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JPanel;

import org.junit.Test;

import de.embl.rieslab.emu.controller.log.Logger;
import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import de.embl.rieslab.emu.ui.internalproperties.IntegerInternalProperty;
import de.embl.rieslab.emu.ui.uiparameters.StringUIParameter;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;
import de.embl.rieslab.emu.ui.uiproperties.PropertyPair;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.utils.EmuUtils;
import de.embl.rieslab.emu.utils.exceptions.AlreadyAssignedUIPropertyException;
import de.embl.rieslab.emu.utils.exceptions.IncompatibleMMProperty;
import de.embl.rieslab.emu.utils.exceptions.IncorrectInternalPropertyTypeException;
import de.embl.rieslab.emu.utils.exceptions.UnknownInternalPropertyException;
import de.embl.rieslab.emu.utils.exceptions.UnknownUIParameterException;
import de.embl.rieslab.emu.utils.exceptions.UnknownUIPropertyException;
import de.embl.rieslab.emu.utils.settings.IntSetting;
import de.embl.rieslab.emu.utils.settings.Setting;

public class ConfigurableMainFrameTest {

	@Test
	public void testCollectionConfigurablePanels() throws UnknownUIParameterException, IncorrectInternalPropertyTypeException, UnknownInternalPropertyException {
		final String[] panels = {"Pane1", "Pane2", "Pane2"};
		
		final ConfigurableTestPanel cp1 = new ConfigurableTestPanel(panels[0]);

		final ConfigurableTestPanel cp2 = new ConfigurableTestPanel(panels[1]);

		final ConfigurableTestPanel cp3 = new ConfigurableTestPanel(panels[2]);
	
		ConfigurableTestFrame cf = new ConfigurableTestFrame("MyFrame") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initComponents() {
				this.add(cp1);
				
				JPanel pane = new JPanel();
				pane.add(cp2);
				
				JPanel subpane = new JPanel();
				subpane.add(cp3);
				pane.add(subpane);
				this.add(pane);
			}
		};
	
		// check that all ConfigurableFrame are found
		ArrayList<ConfigurablePanel> panelist = cf.getConfigurablePanels();
		assertEquals(panels.length, panelist.size());
		assertTrue(panelist.contains(cp1));
		assertTrue(panelist.contains(cp2));
		assertTrue(panelist.contains(cp3));
	}

	@Test
	public void testCollectionUIProperties(){
		final String[] panels = {"Pane1", "Pane2", "Pane2"};
		
		// no collision between UIProperties name
		final String[] props1 = {"Pane1-Prop1", "Pane1-Prop2", "Pane1-Prop3"};
		final String[] props2 = {"Pane2-Prop1", "Pane2-Prop2"};
		final String[] props3 = {"Pane3-Prop1", "Pane3-Prop2", "Pane3-Prop3", "Pane3-Prop4"}; 
		
		final ConfigurableTestPanel cp1 = new ConfigurableTestPanel(panels[0]) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeProperties() {
				for(int i=0; i<props1.length;i++) {
					this.addUIProperty(new UIProperty(this, props1[i], ""));
				}
			}
		};

		final ConfigurableTestPanel cp2 = new ConfigurableTestPanel(panels[1]) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeProperties() {
				for(int i=0; i<props2.length;i++) {
					this.addUIProperty(new UIProperty(this, props2[i], ""));
				}
			}
		};

		final ConfigurableTestPanel cp3 = new ConfigurableTestPanel(panels[2]) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeProperties() {
				for(int i=0; i<props3.length;i++) {
					this.addUIProperty(new UIProperty(this, props3[i], ""));
				}
			}
		};
	
		ConfigurableTestFrame cf = new ConfigurableTestFrame("MyFrame") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initComponents() {
				this.add(cp1);
				
				JPanel pane = new JPanel();
				pane.add(cp2);
				
				JPanel subpane = new JPanel();
				subpane.add(cp3);
				pane.add(subpane);
				this.add(pane);
			}
		};
		
		// check that all UIProperties are found
		HashMap<String, UIProperty> proplist = cf.getUIProperties();
		int nprop = props1.length+props2.length+props3.length;
		assertEquals(nprop, proplist.size());

		Iterator<String> it = proplist.keySet().iterator();
		while(it.hasNext()) {
			String s = it.next();
			
			boolean b = false;
			for(String sp: props1) {
				if(sp.equals(s)) {
					b = true;
				}
			}
			for(String sp: props2) {
				if(sp.equals(s)) {
					b = true;
				}
			}
			for(String sp: props3) {
				if(sp.equals(s)) {
					b = true;
				}
			}
			assertTrue(b);
		}
	}
	
	@Test
	public void testCollectionUIParameters() throws UnknownUIParameterException {
		final String[] panels = {"Pane1", "Pane2", "Pane2"};
		
		// since panels[1] and panels[2] have the same name, then params2[1] and params3[0] will collide
 		final String[] params1 = {"Pane1-Param1"};
		final String[] params2 = {"Pane2-Param1", "Pane2-Param2"};
		final String[] params3 = {"Pane2-Param2"};
		
		
		final ConfigurableTestPanel cp1 = new ConfigurableTestPanel(panels[0]) {

			private static final long serialVersionUID = 1L;
			@Override
			protected void initializeParameters() {
				for(int i=0; i<params1.length;i++) {
					this.addUIParameter(new StringUIParameter(this, params1[i], "", ""));
				}
			}
		};

		final ConfigurableTestPanel cp2 = new ConfigurableTestPanel(panels[1]) {

			private static final long serialVersionUID = 1L;
			
			@Override
			protected void initializeParameters() {
				for(int i=0; i<params2.length;i++) {
					this.addUIParameter(new StringUIParameter(this, params2[i], "", ""));
				}
			}			
		};

		final ConfigurableTestPanel cp3 = new ConfigurableTestPanel(panels[2]) {

			private static final long serialVersionUID = 1L;
			@Override
			protected void initializeParameters() {
				for(int i=0; i<params3.length;i++) {
					this.addUIParameter(new StringUIParameter(this, params3[i], "", ""));
				}
			}
		};
	
		ConfigurableTestFrame cf = new ConfigurableTestFrame("MyFrame") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initComponents() {
				this.add(cp1);
				
				JPanel pane = new JPanel();
				pane.add(cp2);
				
				JPanel subpane = new JPanel();
				subpane.add(cp3);
				pane.add(subpane);
				this.add(pane);
			}
		};
	
		// check that all ConfigurableFrame are found
		ArrayList<ConfigurablePanel> panelist = cf.getConfigurablePanels();
		
		// check that all UIParameters are found (params2[1] and params3[0])
		int nparams = params1.length+params2.length+params3.length-1; // since two UIParameter collide
		HashMap<String, UIParameter> paramlist = cf.getUIParameters();
		assertEquals(nparams, paramlist.size());

		Iterator<String> it = paramlist.keySet().iterator();
		while(it.hasNext()) {
			String s = paramlist.get(it.next()).getLabel();
			
			boolean b = false;
			for(String sp: params1) {
				if(sp.equals(s)) {
					b = true;
				}
			}
			for(String sp: params2) {
				if(sp.equals(s)) {
					b = true;
				}
			}
			for(String sp: params3) {
				if(sp.equals(s)) {
					b = true;
				}
			}
			assertTrue(b);
		}
		
		// check that the collision is solved by linking the UIParameter to both ConfigurablePanel
		String collidedparam = panels[1]+" - "+params2[1];
		UIParameter<?> param = paramlist.get(collidedparam);
		final String newval = "MyNewValue";
		param.setStringValue(newval);
		
		for(ConfigurablePanel cp: panelist) {
			if(cp.getPanelLabel().equals(panels[1])) { // they have the same label anyway
				assertEquals(newval, cp.getUIParameter(params2[1]).getStringValue()); // prop is known with same label as well
			}
		}
	}
	
	@Test
	public void testCollectionInternalProperties() throws IncorrectInternalPropertyTypeException, UnknownInternalPropertyException {
		final String[] panels = {"Pane1", "Pane2", "Pane2"};
	
		final String[] intprops = {"IntProp1", "IntProp2"};
		
		final ConfigurableTestPanel cp1 = new ConfigurableTestPanel(panels[0]) {

			private static final long serialVersionUID = 1L;
			
			@Override
			protected void initializeInternalProperties() {
				this.addInternalProperty(new IntegerInternalProperty(this, intprops[0], 1));
			}
		};

		final ConfigurableTestPanel cp2 = new ConfigurableTestPanel(panels[1]) {

			private static final long serialVersionUID = 1L;
			
			@Override
			protected void initializeInternalProperties() {
				this.addInternalProperty(new IntegerInternalProperty(this, intprops[0], 1));
				this.addInternalProperty(new IntegerInternalProperty(this, intprops[1], 1));
			}
		};

		final ConfigurableTestPanel cp3 = new ConfigurableTestPanel(panels[2]) {

			private static final long serialVersionUID = 1L;
			
			@Override
			protected void initializeInternalProperties() {
				this.addInternalProperty(new IntegerInternalProperty(this, intprops[1], 1));
			}
		};
	
		ConfigurableTestFrame cf = new ConfigurableTestFrame("MyFrame") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initComponents() {
				this.add(cp1);
				
				JPanel pane = new JPanel();
				pane.add(cp2);
				
				JPanel subpane = new JPanel();
				subpane.add(cp3);
				pane.add(subpane);
				this.add(pane);
			}
		};
		
		// check that the InternalProperties have been linked
		assertEquals(1, cp1.getIntegerInternalPropertyValue(intprops[0]));
		assertEquals(1, cp2.getIntegerInternalPropertyValue(intprops[0]));
		assertEquals(1, cp2.getIntegerInternalPropertyValue(intprops[1]));
		assertEquals(1, cp3.getIntegerInternalPropertyValue(intprops[1]));
		
		final int p = 84;
		cp1.setInternalPropertyValue(intprops[0], p);
		assertEquals(p, cp1.getIntegerInternalPropertyValue(intprops[0]));
		assertEquals(p, cp2.getIntegerInternalPropertyValue(intprops[0]));
		assertEquals(1, cp2.getIntegerInternalPropertyValue(intprops[1]));
		assertEquals(1, cp3.getIntegerInternalPropertyValue(intprops[1]));
		
		final int q = -91;
		cp3.setInternalPropertyValue(intprops[1], q);
		assertEquals(p, cp1.getIntegerInternalPropertyValue(intprops[0]));
		assertEquals(p, cp2.getIntegerInternalPropertyValue(intprops[0]));
		assertEquals(q, cp2.getIntegerInternalPropertyValue(intprops[1]));
		assertEquals(q, cp3.getIntegerInternalPropertyValue(intprops[1]));
	}
	

	
	@Test
	public void testUpdateAll() throws UnknownUIParameterException, IncorrectInternalPropertyTypeException, UnknownInternalPropertyException, AlreadyAssignedUIPropertyException, UnknownUIPropertyException, IncompatibleMMProperty {
		final String[] panels = {"Pane1", "Pane2"};
		
		// no collision between UIProperties name
		final String props1 = "Pane1-Prop1";
		final String props2 = "Pane2-Prop1";
		
		// since panels[1] and panels[2] have the same name, then params2[1] and params3[0] will collide
 		final String params1 = "Pane1-Param1";
		final String params2 = "Pane2-Param1";

		final AtomicInteger reporter1 = new AtomicInteger(0);
		final AtomicInteger reporter2 = new AtomicInteger(0);
		final AtomicInteger reporter3 = new AtomicInteger(0);
		final AtomicInteger reporter4 = new AtomicInteger(0);

		final int val1 = 15;
		final int val2 = -65;
		final int val3 = 645;
		final int val4 = -874;
		
		final ConfigurableTestPanel cp1 = new ConfigurableTestPanel(panels[0]) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeProperties() {
				this.addUIProperty(new UIProperty(this, props1, ""));
			}
			
			@Override
			protected void initializeParameters() {
				this.addUIParameter(new StringUIParameter(this, params1, "", ""));
			}

			@Override
			protected void parameterhasChanged(String parameterName) {
				if(parameterName.equals(params1)) {
					reporter1.set(val1);
				}
			}

			@Override
			protected void propertyhasChanged(String propertyName, String newvalue) {
				if(propertyName.equals(props1) && EmuUtils.isInteger(newvalue)) {
					reporter2.set(Integer.valueOf(newvalue));
				}
			}
		};

		final ConfigurableTestPanel cp2 = new ConfigurableTestPanel(panels[1]) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeProperties() {
				this.addUIProperty(new UIProperty(this, props2, ""));
			}
			
			@Override
			protected void initializeParameters() {
				this.addUIParameter(new StringUIParameter(this, params2, "", ""));
			}
			
			@Override
			protected void parameterhasChanged(String parameterName) {
				if(parameterName.equals(params2)) {
					reporter3.set(val2);
				}
			}

			@Override
			protected void propertyhasChanged(String propertyName, String newvalue) {
				if(propertyName.equals(props2) && EmuUtils.isInteger(newvalue)) {
					reporter4.set(Integer.valueOf(newvalue));
				}
			}
		};

		ConfigurableTestFrame cf = new ConfigurableTestFrame("MyFrame") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initComponents() {
				this.add(cp1);
				
				JPanel pane = new JPanel();
				pane.add(cp2);
				
				this.add(pane);
			}
		};

		TestableMMProperty mmprop1 = new TestableMMProperty("MyProp1");
		TestableMMProperty mmprop2 = new TestableMMProperty("MyProp2");
		
		mmprop1.setValue(String.valueOf(val3), null);
		mmprop2.setValue(String.valueOf(val4), null);
		
		PropertyPair.pair(cp1.getUIProperty(props1), mmprop1);
		PropertyPair.pair(cp2.getUIProperty(props2), mmprop2);
				
		assertEquals(0, reporter1.intValue());
		assertEquals(0, reporter2.intValue());
		assertEquals(0, reporter3.intValue());
		assertEquals(0, reporter4.intValue());
		
		// calls updateAll
		cf.updateAllConfigurablePanels();
		
		// waits to let the other threads finish
		try {
			Thread.sleep(20);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		assertEquals(val1, reporter1.intValue());
		assertEquals(val3, reporter2.intValue());
		assertEquals(val2, reporter3.intValue());
		assertEquals(val4, reporter4.intValue());
	}
	
	@Test
	public void testUpdateAllProperties() throws AlreadyAssignedUIPropertyException, UnknownUIPropertyException, IncompatibleMMProperty {
		final String[] panels = {"Pane1", "Pane2"};
		
		// no collision between UIProperties name
		final String props1 = "Pane1-Prop1";
		final String props2 = "Pane2-Prop1";
		
		final AtomicInteger reporter2 = new AtomicInteger(0);
		final AtomicInteger reporter4 = new AtomicInteger(0);

		final int val3 = 645;
		final int val4 = -874;
		
		final ConfigurableTestPanel cp1 = new ConfigurableTestPanel(panels[0]) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeProperties() {
				this.addUIProperty(new UIProperty(this, props1, ""));
			}
			
			@Override
			protected void propertyhasChanged(String propertyName, String newvalue) {
				if(propertyName.equals(props1) && EmuUtils.isInteger(newvalue)) {
					reporter2.set(Integer.valueOf(newvalue));
				}
			}
		};

		final ConfigurableTestPanel cp2 = new ConfigurableTestPanel(panels[1]) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initializeProperties() {
				this.addUIProperty(new UIProperty(this, props2, ""));
			}
			
			@Override
			protected void propertyhasChanged(String propertyName, String newvalue) {
				if(propertyName.equals(props2) && EmuUtils.isInteger(newvalue)) {
					reporter4.set(Integer.valueOf(newvalue));
				}
			}
		};

		ConfigurableTestFrame cf = new ConfigurableTestFrame("MyFrame") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initComponents() {
				this.add(cp1);
				
				JPanel pane = new JPanel();
				pane.add(cp2);
				
				this.add(pane);
			}
		};

		TestableMMProperty mmprop1 = new TestableMMProperty("MyProp1");
		TestableMMProperty mmprop2 = new TestableMMProperty("MyProp2");
		
		mmprop1.setValue(String.valueOf(val3), null);
		mmprop2.setValue(String.valueOf(val4), null);
		
		PropertyPair.pair(cp1.getUIProperty(props1), mmprop1);
		PropertyPair.pair(cp2.getUIProperty(props2), mmprop2);
				
		assertEquals(0, reporter2.intValue());
		assertEquals(0, reporter4.intValue());
		
		// calls updateAll
		cf.updateAllConfigurablePanels();
		
		// waits to let the other threads finish
		try {
			Thread.sleep(20);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		assertEquals(val3, reporter2.intValue());
		assertEquals(val4, reporter4.intValue());
	}

	@Test
	public void testAddAllListeners() {
		final String[] panels = {"Pane1", "Pane2"};

		final AtomicInteger reporter1 = new AtomicInteger(0);
		final AtomicInteger reporter2 = new AtomicInteger(0);
		
		final int val1 = 15;
		final int val2 = -65;
		
		final ConfigurableTestPanel cp1 = new ConfigurableTestPanel(panels[0]) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void addComponentListeners() {
				reporter1.set(val1);
			}
		};

		final ConfigurableTestPanel cp2 = new ConfigurableTestPanel(panels[1]) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void addComponentListeners() {
				reporter2.set(val2);
			}
		};

		ConfigurableTestFrame cf = new ConfigurableTestFrame("MyFrame") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void initComponents() {
				this.add(cp1);
				
				JPanel pane = new JPanel();
				pane.add(cp2);
				
				this.add(pane);
			}
		};
				
		assertEquals(0, reporter1.intValue());
		assertEquals(0, reporter2.intValue());
		
		// calls addAllListeners
		cf.addAllListeners();

		assertEquals(val1, reporter1.intValue());
		assertEquals(val2, reporter2.intValue());
	}

	@Test
	public void testPluginSettings() {
		final AtomicInteger reporter1 = new AtomicInteger(0);
		final int val1 = 15;
		final String PLUGIN_SETTING = "MySetting";

		assertEquals(0, reporter1.intValue());
		
		TreeMap<String, String> settings = new TreeMap<String, String>();
		settings.put(PLUGIN_SETTING, String.valueOf(val1));
		
		ConfigurableTestFrame cf = new ConfigurableTestFrame("MyFrame", settings) {

			private static final long serialVersionUID = 1L;
			
			@Override
			protected void initComponents() {
				reporter1.set(((IntSetting) this.getCurrentPluginSettings().get(PLUGIN_SETTING)).getValue());
			}
			
			@Override
			public HashMap<String, Setting> getDefaultPluginSettings() {
				HashMap<String, Setting> t = new HashMap<String, Setting>();
				t.put(PLUGIN_SETTING, new IntSetting(PLUGIN_SETTING, "", reporter1.intValue()));
				return t;
			}
		};

		// the value of reporter1 should have changed
		assertEquals(val1, reporter1.intValue());
	}
	
	@Test
	public void testNullPluginSettings() {
		ConfigurableTestFrame cf = new ConfigurableTestFrame("MyFrame",null) {

			private static final long serialVersionUID = 1L;
			
			@Override
			public HashMap<String, Setting> getDefaultPluginSettings() {
				return null;
			}
		};
		
		assertEquals(0,cf.getCurrentPluginSettings().size());
	}
	
	private class ConfigurableTestFrame extends ConfigurableMainFrame{

		private static final long serialVersionUID = 1L;

		public ConfigurableTestFrame(String title) {
			super(title, null, new TreeMap<String, String>());
		}

		public ConfigurableTestFrame(String title, TreeMap<String, String> pluginSettings) {
			super(title, null, pluginSettings);
		}

		@Override
		protected void initComponents() {}
		
		@Override
		public HashMap<String, Setting> getDefaultPluginSettings() {
			return new HashMap<String, Setting>();
		}

		@Override
		protected String getPluginInfo() {
			return "";
		}
	}
	
	private class ConfigurableTestPanel extends ConfigurablePanel{

		private static final long serialVersionUID = 1L;
	 	
		public ConfigurableTestPanel(String label) {
			super(label);
		}
		
		@Override
		protected void initializeProperties() {}
		
		@Override
		protected void initializeParameters() {}
		
		@Override
		protected void initializeInternalProperties() {}
		
		@Override
		protected void parameterhasChanged(String parameterName) {}

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
	
	public class TestableMMProperty extends MMProperty<String> {

		public static final String DEV = "MyDevice";
		public static final String DEFVAL = "default";
		
		public TestableMMProperty(String propname) {
			super(null, new Logger(), MMProperty.MMPropertyType.STRING, DEV, propname, false);
			this.value = DEFVAL;
		}

		@Override
		protected String convertToValue(String s) {
			return s;
		}

		@Override
		protected String convertToValue(int i) {
			return String.valueOf(i);
		}

		@Override
		protected String convertToValue(double d) {
			return String.valueOf(d);
		}

		@Override
		protected String[] arrayFromStrings(String[] s) {
			return s;
		}

		@Override
		protected String convertToString(String val) {
			return val;
		}

		@Override
		protected boolean areEquals(String val1, String val2) {
			return val1.equals(val2);
		}

		@Override
		protected boolean isAllowed(String val) {
			return true;
		}

		@Override
		public String getValue() {
			return this.value;
		}

		@Override
		public String getStringValue() {
			return this.value;
		}

		@Override
		public boolean setValue(String stringval, UIProperty source) {
			value = stringval;
			notifyListeners(source, stringval);
			return true;
		}
	};
}
