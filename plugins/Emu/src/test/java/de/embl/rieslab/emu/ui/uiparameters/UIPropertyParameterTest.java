package de.embl.rieslab.emu.ui.uiparameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.flag.PropertyFlag;
import org.junit.Test;

public class UIPropertyParameterTest {

   @Test
   public void testUIPropertyParameterCreation() {
      UIPropertyParamTestPanel cp = new UIPropertyParamTestPanel("My panel");

      assertEquals(UIPropertyParameter.NO_PROPERTY, cp.parameter.getStringValue());
      assertEquals(UIPropertyParameter.NO_PROPERTY, cp.parameter.getValue());

      assertEquals(cp.flag, cp.parameter.getFlag());

      assertEquals(UIParameter.UIParameterType.UIPROPERTY, cp.parameter.getType());
      assertEquals(UIParameter.getHash(cp, cp.param), cp.parameter.getHash());
      assertEquals(cp.param, cp.parameter.getLabel());
      assertEquals(cp.desc, cp.parameter.getDescription());
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
      new UIPropertyParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            flag = new PropertyFlag("Union Jack") {
            };

            parameter = new UIPropertyParameter(null, param, desc, flag);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullLabel() {
      new UIPropertyParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            flag = new PropertyFlag("Union Jack") {
            };

            parameter = new UIPropertyParameter(this, null, desc, flag);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullDescription() {
      new UIPropertyParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            flag = new PropertyFlag("Union Jack") {
            };

            parameter = new UIPropertyParameter(this, param, null, flag);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullFlag() {
      new UIPropertyParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            flag = new PropertyFlag("Union Jack") {
            };

            parameter = new UIPropertyParameter(this, param, desc, null);
         }
      };
   }

   private class UIPropertyParamTestPanel extends ConfigurablePanel {

      private static final long serialVersionUID = -3307917280544843397L;
      public UIPropertyParameter parameter;
      public final String param = "MyParam";
      public final String desc = "MyDescription";
      public PropertyFlag flag;

      public UIPropertyParamTestPanel(String label) {
         super(label);
      }

      @Override
      protected void initializeParameters() {
         flag = new PropertyFlag("Union Jack") {
         };

         parameter = new UIPropertyParameter(this, param, desc, flag);
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
