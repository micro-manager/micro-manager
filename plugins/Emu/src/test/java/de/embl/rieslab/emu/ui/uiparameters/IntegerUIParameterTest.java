package de.embl.rieslab.emu.ui.uiparameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import org.junit.Test;

public class IntegerUIParameterTest {
   @Test
   public void testIntegerUIParameterCreation() {
      IntegerParamTestPanel cp = new IntegerParamTestPanel("My panel");

      assertEquals(String.valueOf(cp.defVal), cp.parameter.getStringValue());
      assertEquals(new Integer(cp.defVal), cp.parameter.getValue());

      assertEquals(UIParameter.UIParameterType.INTEGER, cp.parameter.getType());
      assertEquals(UIParameter.getHash(cp, cp.param), cp.parameter.getHash());
      assertEquals(cp.param, cp.parameter.getLabel());
      assertEquals(cp.desc, cp.parameter.getDescription());
   }

   @Test
   public void testAreValuesSuitable() {
      IntegerParamTestPanel cp = new IntegerParamTestPanel("My panel");

      // test if suitable
      assertTrue(cp.parameter.isSuitable("-645"));
      assertTrue(cp.parameter.isSuitable("+74"));
      assertTrue(cp.parameter.isSuitable("0"));

      assertFalse(cp.parameter.isSuitable("+4684,61"));
      assertFalse(cp.parameter.isSuitable("+4684.61"));
      assertFalse(cp.parameter.isSuitable("-4684,61"));
      assertFalse(cp.parameter.isSuitable("false"));
      assertFalse(cp.parameter.isSuitable("dfse"));
      assertFalse(cp.parameter.isSuitable(null));
      assertFalse(cp.parameter.isSuitable(""));
   }

   @Test
   public void testChangeValue() {
      IntegerParamTestPanel cp = new IntegerParamTestPanel("My panel");

      int d = -5;
      cp.parameter.setValue(d);
      assertEquals(String.valueOf(d), cp.parameter.getStringValue());
      assertEquals(new Integer(d), cp.parameter.getValue());

      String s = String.valueOf(96);
      cp.parameter.setStringValue(s);
      assertEquals(s, cp.parameter.getStringValue());
      assertEquals(Integer.valueOf(s), cp.parameter.getValue());
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullOwner() {
      new IntegerParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            defVal = 42;

            parameter = new IntegerUIParameter(null, param, desc, defVal);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullLabel() {
      new IntegerParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            defVal = 42;

            parameter = new IntegerUIParameter(this, null, desc, defVal);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullDescription() {
      new IntegerParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            defVal = 42;

            parameter = new IntegerUIParameter(this, param, null, defVal);
         }
      };
   }


   private class IntegerParamTestPanel extends ConfigurablePanel {

      private static final long serialVersionUID = -3307917280544843397L;
      public IntegerUIParameter parameter;
      public final String param = "MyParam";
      public final String desc = "MyDescription";
      public int defVal;

      public IntegerParamTestPanel(String label) {
         super(label);
      }

      @Override
      protected void initializeParameters() {
         defVal = 42;

         parameter = new IntegerUIParameter(this, param, desc, defVal);
      }

      @Override
      protected void parameterhasChanged(String parameterName) {
      }

      @Override
      protected void initializeInternalProperties() {
      }

      @Override
      protected void initializeProperties() {
      }

      @Override
      public void internalpropertyhasChanged(String propertyName) {
      }

      @Override
      protected void propertyhasChanged(String propertyName, String newvalue) {
      }

      @Override
      public void shutDown() {
      }

      @Override
      protected void addComponentListeners() {
      }

      @Override
      public String getDescription() {
         return "";
      }
   }
}