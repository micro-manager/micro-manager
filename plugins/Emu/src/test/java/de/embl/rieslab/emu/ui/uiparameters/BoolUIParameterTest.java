package de.embl.rieslab.emu.ui.uiparameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiparameters.BoolUIParameter;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;

public class BoolUIParameterTest {

  @Test
  public void testBoolUIParameterCreation() {
    BoolParamTestPanel cp = new BoolParamTestPanel("My panel");

    assertEquals("false", cp.parameter.getStringValue());
    assertFalse(cp.parameter.getValue());
    assertEquals(UIParameter.UIParameterType.BOOL, cp.parameter.getType());
    assertEquals(UIParameter.getHash(cp, cp.PARAM), cp.parameter.getHash());
    assertEquals(cp.PARAM, cp.parameter.getLabel());
    assertEquals(cp.DESC, cp.parameter.getDescription());
  }

  @Test
  public void testAreValuesSuitable() {
    BoolParamTestPanel cp = new BoolParamTestPanel("My panel");

    // test if suitable
    assertTrue(cp.parameter.isSuitable("true"));
    assertTrue(cp.parameter.isSuitable("false"));
    assertFalse(cp.parameter.isSuitable("falsesa"));
    assertFalse(cp.parameter.isSuitable("448766"));
    assertFalse(cp.parameter.isSuitable(null));
    assertFalse(cp.parameter.isSuitable(""));
  }

  @Test
  public void testChangeValue() {
    final String true_ = "true";
    final String false_ = "false";
    BoolParamTestPanel cp = new BoolParamTestPanel("My panel");

    assertEquals(false_, cp.parameter.getStringValue());

    cp.parameter.setStringValue(true_);
    assertEquals(true_, cp.parameter.getStringValue());
    assertEquals(true, cp.parameter.getValue());

    cp.parameter.setValue(false);
    assertEquals(false_, cp.parameter.getStringValue());
    assertEquals(false, cp.parameter.getValue());
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullOwner() {
    new BoolParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        parameter = new BoolUIParameter(null, PARAM, DESC, false);
      }
    };
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullLabel() {
    new BoolParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        parameter = new BoolUIParameter(this, null, DESC, false);
      }
    };
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullDescription() {
    new BoolParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        parameter = new BoolUIParameter(this, PARAM, null, false);
      }
    };
  }

  private class BoolParamTestPanel extends ConfigurablePanel {

    private static final long serialVersionUID = -3307917280544843397L;
    public BoolUIParameter parameter;
    public final String PARAM = "MyParam";
    public final String DESC = "MyDescription";

    public BoolParamTestPanel(String label) {
      super(label);
    }

    @Override
    protected void initializeParameters() {
      parameter = new BoolUIParameter(this, PARAM, DESC, false);
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
