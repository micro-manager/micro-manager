package de.embl.rieslab.emu.ui.uiparameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.ui.ConfigurablePanel;

public class ComboUIParameterTest {
  @Test
  public void testComboUIParameterCreation() {
    ComboParamTestPanel cp = new ComboParamTestPanel("My panel");

    assertEquals(cp.vals[cp.def_val], cp.parameter.getStringValue());
    assertEquals(cp.vals[cp.def_val], cp.parameter.getValue());

    assertEquals(UIParameter.UIParameterType.COMBO, cp.parameter.getType());
    assertEquals(UIParameter.getHash(cp, cp.PARAM), cp.parameter.getHash());
    assertEquals(cp.PARAM, cp.parameter.getLabel());
    assertEquals(cp.DESC, cp.parameter.getDescription());
  }

  @Test
  public void testAreValuesSuitable() {
    ComboParamTestPanel cp = new ComboParamTestPanel("My panel");

    // test if suitable
    for (int i = 0; i < cp.vals.length; i++) assertTrue(cp.parameter.isSuitable(cp.vals[i]));

    assertFalse(cp.parameter.isSuitable("false"));
    assertFalse(cp.parameter.isSuitable("falsesa"));
    assertFalse(cp.parameter.isSuitable("448766"));
    assertFalse(cp.parameter.isSuitable(null));
    assertFalse(cp.parameter.isSuitable(""));
  }

  @Test
  public void testChangeValue() {
    ComboParamTestPanel cp = new ComboParamTestPanel("My panel");

    assertEquals(cp.vals[cp.def_val], cp.parameter.getStringValue());

    for (int i = 0; i < cp.vals.length; i++) {
      cp.parameter.setStringValue(cp.vals[i]);
      assertEquals(cp.vals[i], cp.parameter.getStringValue());
      assertEquals(cp.vals[i], cp.parameter.getValue());
    }
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullDefaultValue() {
    new ComboParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        vals = new String[] {"154.652", "fdsfs", "true784"};
        def_val = 1;

        parameter = new ComboUIParameter(this, PARAM, DESC, null, def_val);
      }
    };
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullOwner() {
    new ComboParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        vals = new String[] {"154.652", "fdsfs", "true784"};
        def_val = 1;

        parameter = new ComboUIParameter(null, PARAM, DESC, vals, def_val);
      }
    };
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullLabel() {
    new ComboParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        vals = new String[] {"154.652", "fdsfs", "true784"};
        def_val = 1;

        parameter = new ComboUIParameter(this, null, DESC, vals, def_val);
      }
    };
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullDescription() {
    new ComboParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        vals = new String[] {"154.652", "fdsfs", "true784"};
        def_val = 1;

        parameter = new ComboUIParameter(this, PARAM, null, vals, def_val);
      }
    };
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullValueInAllowedArray() {
    new ComboParamTestPanel("My panel") {
      private static final long serialVersionUID = 1L;

      @Override
      protected void initializeParameters() {
        vals = new String[] {"154.652", null, "true784"};
        def_val = 1;

        parameter = new ComboUIParameter(this, DESC, null, vals, def_val);
      }
    };
  }

  private class ComboParamTestPanel extends ConfigurablePanel {

    private static final long serialVersionUID = -3307917280544843397L;
    public ComboUIParameter parameter;
    public final String PARAM = "MyParam";
    public final String DESC = "MyDescription";
    public String[] vals;
    public int def_val;

    public ComboParamTestPanel(String label) {
      super(label);
    }

    @Override
    protected void initializeParameters() {
      vals = new String[] {"154.652", "fdsfs", "true784"};
      def_val = 1;

      parameter = new ComboUIParameter(this, PARAM, DESC, vals, def_val);
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
