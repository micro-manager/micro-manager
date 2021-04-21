package de.embl.rieslab.emu.ui.internalproperties;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.embl.rieslab.emu.ui.ConfigurablePanel;

public class BoolInternalPropertyTest {

  @Test
  public void testBoolInternalProperty() {
    BoolInternalPropertyTestPanel cp = new BoolInternalPropertyTestPanel("MyPanel");

    assertEquals(InternalProperty.InternalPropertyType.BOOLEAN, cp.intprop.getType());
    assertEquals(cp.PROP, cp.intprop.getLabel());
    assertEquals(cp.propval, cp.intprop.getInternalPropertyValue());
  }

  @Test
  public void testSetValue() {
    BoolInternalPropertyTestPanel cp = new BoolInternalPropertyTestPanel("MyPanel");

    boolean val = true;
    cp.intprop.setInternalPropertyValue(val, cp);
    assertEquals(val, cp.intprop.getInternalPropertyValue());

    val = false;
    cp.intprop.setInternalPropertyValue(val, cp);
    assertEquals(val, cp.intprop.getInternalPropertyValue());
  }

  @Test(expected = NullPointerException.class)
  public void testNullValue() {
    BoolInternalPropertyTestPanel cp = new BoolInternalPropertyTestPanel("MyPanel");

    boolean val = true;
    cp.intprop.setInternalPropertyValue(val, cp);

    cp.intprop.setInternalPropertyValue(null, cp);
    assertEquals(val, cp.intprop.getInternalPropertyValue());
  }

  @Test
  public void testNotifyListener() {
    BoolInternalPropertyTestPanel cp =
        new BoolInternalPropertyTestPanel("MyPanel") {
          private static final long serialVersionUID = 1L;

          @Override
          public void internalpropertyhasChanged(String propertyName) {
            if (propertyName.equals(PROP)) propval = intprop.getInternalPropertyValue();
          }
        };

    boolean val = true;
    cp.intprop.setInternalPropertyValue(val, null);

    assertEquals(val, cp.propval);

    val = false;
    cp.intprop.setInternalPropertyValue(val, null);

    assertEquals(val, cp.propval);
  }

  private class BoolInternalPropertyTestPanel extends ConfigurablePanel {

    private static final long serialVersionUID = 1L;
    public BoolInternalProperty intprop;
    public final String PROP = "MyProp";
    public boolean propval;

    public BoolInternalPropertyTestPanel(String label) {
      super(label);
    }

    @Override
    protected void initializeProperties() {}

    @Override
    protected void initializeParameters() {}

    @Override
    protected void parameterhasChanged(String parameterName) {}

    @Override
    protected void initializeInternalProperties() {
      propval = false;
      intprop = new BoolInternalProperty(this, PROP, propval);
    }

    @Override
    public void internalpropertyhasChanged(String propertyName) {}

    @Override
    protected void propertyhasChanged(String propertyName, String newvalue) {}

    @Override
    public void shutDown() {}

    @Override
    protected void addComponentListeners() {}

    @Override
    public String getDescription() {
      return "";
    }
  }
}
