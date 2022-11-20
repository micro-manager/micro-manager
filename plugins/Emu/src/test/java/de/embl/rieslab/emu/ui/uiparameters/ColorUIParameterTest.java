package de.embl.rieslab.emu.ui.uiparameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.utils.ColorRepository;
import java.awt.Color;
import org.junit.Test;

public class ColorUIParameterTest {
   @Test
   public void testColorUIParameterCreation() {
      ColorParamTestPanel cp = new ColorParamTestPanel("My panel");

      assertEquals(ColorRepository.strblack, cp.parameter.getStringValue());
      assertEquals(Color.black, cp.parameter.getValue());
      assertEquals(UIParameter.UIParameterType.COLOR, cp.parameter.getType());
      assertEquals(UIParameter.getHash(cp, cp.param), cp.parameter.getHash());
      assertEquals(cp.param, cp.parameter.getLabel());
      assertEquals(cp.desc, cp.parameter.getDescription());
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullDefaultValue() {
      new ColorParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            parameter = new ColorUIParameter(this, param, desc, null);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullOwner() {
      new ColorParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            parameter = new ColorUIParameter(null, param, desc, Color.black);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testSetNullLabel() {
      new ColorParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            parameter = new ColorUIParameter(this, null, desc, Color.black);
         }
      };
   }


   @Test(expected = NullPointerException.class)
   public void testSetNullDescription() {
      new ColorParamTestPanel("My panel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeParameters() {
            parameter = new ColorUIParameter(this, param, null, Color.black);
         }
      };
   }

   @Test
   public void testAreValuesSuitable() {
      ColorParamTestPanel cp = new ColorParamTestPanel("My panel");

      // test if suitable
      String[] vals = ColorRepository.getColors();
      for (int i = 0; i < vals.length; i++) {
         assertTrue(cp.parameter.isSuitable(vals[i]));
      }

      assertFalse(cp.parameter.isSuitable("false"));
      assertFalse(cp.parameter.isSuitable("falsesa"));
      assertFalse(cp.parameter.isSuitable("448766"));
      assertFalse(cp.parameter.isSuitable(null));
      assertFalse(cp.parameter.isSuitable(""));
   }

   @Test
   public void testChangeValue() {
      ColorParamTestPanel cp = new ColorParamTestPanel("My panel");

      assertEquals(ColorRepository.strblack, cp.parameter.getStringValue());

      cp.parameter.setStringValue(ColorRepository.strbrown);
      assertEquals(ColorRepository.strbrown, cp.parameter.getStringValue());
      assertEquals(ColorRepository.brown, cp.parameter.getValue());

      cp.parameter.setValue(ColorRepository.violet);
      assertEquals(ColorRepository.strviolet, cp.parameter.getStringValue());
      assertEquals(ColorRepository.violet, cp.parameter.getValue());
   }

   private class ColorParamTestPanel extends ConfigurablePanel {

      private static final long serialVersionUID = -3307917280544843397L;
      public ColorUIParameter parameter;
      public final String param = "MyParam";
      public final String desc = "MyDescription";

      public ColorParamTestPanel(String label) {
         super(label);
      }

      @Override
      protected void initializeParameters() {
         parameter = new ColorUIParameter(this, param, desc, Color.black);
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