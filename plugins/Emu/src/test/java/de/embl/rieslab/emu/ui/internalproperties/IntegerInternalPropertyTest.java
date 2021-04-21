package de.embl.rieslab.emu.ui.internalproperties;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.embl.rieslab.emu.ui.ConfigurablePanel;

public class IntegerInternalPropertyTest {

  @Test
  public void testIntInternalProperty() {
    IntInternalPropertyTestPanel cp = new IntInternalPropertyTestPanel("MyPanel");

    assertEquals(InternalProperty.InternalPropertyType.INTEGER, cp.intprop.getType());
    assertEquals(cp.PROP, cp.intprop.getLabel());
    assertEquals(cp.propval, (int) cp.intprop.getInternalPropertyValue());
  }

  @Test
  public void testSetValue() {
    IntInternalPropertyTestPanel cp = new IntInternalPropertyTestPanel("MyPanel");

    int val = -99;
    cp.intprop.setInternalPropertyValue(val, cp);
    assertEquals(val, (int) cp.intprop.getInternalPropertyValue());

    val = 85;
    cp.intprop.setInternalPropertyValue(val, cp);
    assertEquals(val, (int) cp.intprop.getInternalPropertyValue());
  }

  @Test(expected = NullPointerException.class)
  public void testNullValue() {
    IntInternalPropertyTestPanel cp = new IntInternalPropertyTestPanel("MyPanel");

    int val = 21;
    cp.intprop.setInternalPropertyValue(val, cp);

    cp.intprop.setInternalPropertyValue(null, cp);
    assertEquals(val, (int) cp.intprop.getInternalPropertyValue());
  }

  @Test
  public void testNotifyListener() {
    IntInternalPropertyTestPanel cp =
        new IntInternalPropertyTestPanel("MyPanel") {
          private static final long serialVersionUID = 1L;

          @Override
          public void internalpropertyhasChanged(String propertyName) {
            if (propertyName.equals(PROP)) propval = intprop.getInternalPropertyValue();
          }
        };

    int val = 5;
    cp.intprop.setInternalPropertyValue(val, null);

    assertEquals(val, (int) cp.propval);

    val = -48;
    cp.intprop.setInternalPropertyValue(val, null);

    assertEquals(val, (int) cp.propval);
  }

  private class IntInternalPropertyTestPanel extends ConfigurablePanel {

    private static final long serialVersionUID = 1L;
    public IntegerInternalProperty intprop;
    public final String PROP = "MyProp";
    public int propval;

    public IntInternalPropertyTestPanel(String label) {
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
      propval = 4;
      intprop = new IntegerInternalProperty(this, PROP, propval);
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
