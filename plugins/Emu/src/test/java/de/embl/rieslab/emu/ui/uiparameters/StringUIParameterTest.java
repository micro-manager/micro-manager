package de.embl.rieslab.emu.ui.uiparameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import org.junit.Test;

public class StringUIParameterTest {
   @Test
   public void testStringUIParameterCreation() {
      StringParamTestPanel cp = new StringParamTestPanel("My panel");

      assertEquals(cp.defVal, cp.parameter.getStringValue());
      assertEquals(cp.defVal, cp.parameter.getValue());

      assertEquals(UIParameter.UIParameterType.STRING, cp.parameter.getType());
      assertEquals(UIParameter.getHash(cp, cp.param), cp.parameter.getHash());
      assertEquals(cp.param, cp.parameter.getLabel());
      assertEquals(cp.desc, cp.parameter.getDescription());
   }

   @Test
   public void testAreValuesSuitable() {
      StringParamTestPanel cp = new StringParamTestPanel("My panel");

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
      StringParamTestPanel cp = new StringParamTestPanel("My panel");

      String s = "2fsdj*745+$\u00A3%$6(*&) {}~'"; // U+00A3 POUND SIGN
      cp.parameter.setStringValue(s);
      assertEquals(s, cp.parameter.getStringValue());
      assertEquals(s, cp.parameter.getValue());

      s = "\u00A3$sdjdsn"; // U+00A3 POUND SIGN
      cp.parameter.setValue(s);
      assertEquals(s, cp.parameter.getStringValue());
      assertEquals(s, cp.parameter.getValue());
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullOwner() {
      new StringParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            defVal = "Like a rolling stone";

            parameter = new StringUIParameter(null, param, desc, defVal);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullLabel() {
      new StringParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            defVal = "Like a rolling stone";

            parameter = new StringUIParameter(this, null, desc, defVal);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullDescription() {
      new StringParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            defVal = "Like a rolling stone";

            parameter = new StringUIParameter(this, param, null, defVal);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullDefaultvalue() {
      new StringParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            parameter = new StringUIParameter(this, param, desc, null);
         }
      };
   }

   private class StringParamTestPanel extends ConfigurablePanel {

      private static final long serialVersionUID = -3307917280544843397L;
      public StringUIParameter parameter;
      public final String param = "MyParam";
      public final String desc = "MyDescription";
      public String defVal;

      public StringParamTestPanel(String label) {
         super(label);
      }

      @Override
      protected void initializeParameters() {
         defVal = "Like a rolling stone";

         parameter = new StringUIParameter(this, param, desc, defVal);
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
