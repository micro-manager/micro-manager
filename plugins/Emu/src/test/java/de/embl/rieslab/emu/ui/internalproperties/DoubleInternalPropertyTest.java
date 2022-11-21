package de.embl.rieslab.emu.ui.internalproperties;

import static org.junit.Assert.assertEquals;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import org.junit.Test;

public class DoubleInternalPropertyTest {


   @Test
   public void testDoubleInternalProperty() {
      DoubleInternalPropertyTestPanel cp = new DoubleInternalPropertyTestPanel("MyPanel");

      assertEquals(InternalProperty.InternalPropertyType.DOUBLE, cp.intprop.getType());
      assertEquals(cp.prop, cp.intprop.getLabel());
      assertEquals(cp.propval, (double) cp.intprop.getInternalPropertyValue(), 1E-20);
   }

   @Test
   public void testSetValue() {
      DoubleInternalPropertyTestPanel cp = new DoubleInternalPropertyTestPanel("MyPanel");

      double val = 568.652;
      cp.intprop.setInternalPropertyValue(val, cp);
      assertEquals(val, (double) cp.intprop.getInternalPropertyValue(), 1E-20);

      val = -47.6347;
      cp.intprop.setInternalPropertyValue(val, cp);
      assertEquals(val, (double) cp.intprop.getInternalPropertyValue(), 1E-20);
   }


   @Test(expected = NullPointerException.class)
   public void testNullValue() {
      DoubleInternalPropertyTestPanel cp = new DoubleInternalPropertyTestPanel("MyPanel");

      double val = -31.4;
      cp.intprop.setInternalPropertyValue(val, cp);

      cp.intprop.setInternalPropertyValue(null, cp);
      assertEquals(val, (double) cp.intprop.getInternalPropertyValue(), 1E-20);
   }

   @Test
   public void testNotifyListener() {
      DoubleInternalPropertyTestPanel cp = new DoubleInternalPropertyTestPanel("MyPanel") {
         private static final long serialVersionUID = 1L;

         @Override
         public void internalpropertyhasChanged(String propertyName) {
            if (propertyName.equals(prop)) {
               propval = intprop.getInternalPropertyValue();
            }
         }
      };

      double val = 48.9;
      cp.intprop.setInternalPropertyValue(val, null);

      assertEquals(val, (double) cp.propval, 1E-20);

      val = 1.58;
      cp.intprop.setInternalPropertyValue(val, null);

      assertEquals(val, (double) cp.propval, 1E-20);
   }

   private class DoubleInternalPropertyTestPanel extends ConfigurablePanel {

      private static final long serialVersionUID = 1L;
      public DoubleInternalProperty intprop;
      public final String prop = "MyProp";
      public double propval;

      public DoubleInternalPropertyTestPanel(String label) {
         super(label);
      }

      @Override
      protected void initializeProperties() {
      }

      @Override
      protected void initializeParameters() {
      }

      @Override
      protected void parameterhasChanged(String parameterName) {
      }

      @Override
      protected void initializeInternalProperties() {
         propval = 1.256;
         intprop = new DoubleInternalProperty(this, prop, propval);
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