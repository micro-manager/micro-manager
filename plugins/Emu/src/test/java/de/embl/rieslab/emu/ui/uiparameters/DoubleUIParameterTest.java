package de.embl.rieslab.emu.ui.uiparameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.ui.ConfigurablePanel;

public class DoubleUIParameterTest {
  @Test
  public void testDoubleUIParameterCreation() {
    DoubleParamTestPanel cp = new DoubleParamTestPanel("My panel");

    assertEquals(String.valueOf(cp.def_val), cp.parameter.getStringValue());
    assertEquals(new Double(cp.def_val), cp.parameter.getValue());

    assertEquals(UIParameter.UIParameterType.DOUBLE, cp.parameter.getType());
    assertEquals(UIParameter.getHash(cp, cp.PARAM), cp.parameter.getHash());
    assertEquals(cp.PARAM, cp.parameter.getLabel());
    assertEquals(cp.DESC, cp.parameter.getDescription());
  }

  @Test
  public void testAreValuesSuitable() {
    DoubleParamTestPanel cp = new DoubleParamTestPanel("My panel");

    // test if suitable
    assertTrue(cp.parameter.isSuitable("+4684,61"));
    assertTrue(cp.parameter.isSuitable("+4684.61"));
    assertTrue(cp.parameter.isSuitable("-4684,61"));
    assertTrue(cp.parameter.isSuitable("-4684.61"));
    assertTrue(cp.parameter.isSuitable("847.321654"));
    assertTrue(cp.parameter.isSuitable("448766"));

    assertFalse(cp.parameter.isSuitable("false"));
    assertFalse(cp.parameter.isSuitable("dfse"));
    assertFalse(cp.parameter.isSuitable(null));
    assertFalse(cp.parameter.isSuitable(""));
  }

  @Test
  public void testChangeValue() {
    DoubleParamTestPanel cp = new DoubleParamTestPanel("My panel");

    double d = 12.45;
    cp.parameter.setValue(d);
    assertEquals(String.valueOf(d), cp.parameter.getStringValue());
    assertEquals(new Double(d), cp.parameter.getValue());

    String s = String.valueOf(-85.00154);
    cp.parameter.setStringValue(s);
    assertEquals(s, cp.parameter.getStringValue());
    assertEquals(Double.valueOf(s), cp.parameter.getValue());
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullOwner() {
    new DoubleParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        def_val = -2.485;

        parameter = new DoubleUIParameter(null, PARAM, DESC, def_val);
      }
    };
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullLabel() {
    new DoubleParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        def_val = -2.485;

        parameter = new DoubleUIParameter(this, null, DESC, def_val);
      }
    };
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullDescription() {
    new DoubleParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        def_val = -2.485;

        parameter = new DoubleUIParameter(this, PARAM, null, def_val);
      }
    };
  }

  private class DoubleParamTestPanel extends ConfigurablePanel {

    private static final long serialVersionUID = -3307917280544843397L;
    public DoubleUIParameter parameter;
    public final String PARAM = "MyParam";
    public final String DESC = "MyDescription";
    public double def_val;

    public DoubleParamTestPanel(String label) {
      super(label);
    }

    @Override
    protected void initializeParameters() {
      def_val = -2.485;

      parameter = new DoubleUIParameter(this, PARAM, DESC, def_val);
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
    public String getDescription() {
      return "";
    }
  }
}
