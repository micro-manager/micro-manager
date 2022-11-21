package de.embl.rieslab.emu.ui.uiproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.uiproperties.flag.PropertyFlag;
import de.embl.rieslab.emu.utils.exceptions.AlreadyAssignedUIPropertyException;
import de.embl.rieslab.emu.utils.exceptions.IncompatibleMMProperty;
import org.junit.Test;

public class UIPropertyTest {

   @Test
   public void testUIProperty() {
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel");

      assertEquals(cp.prop, cp.property.getPropertyLabel());
      assertEquals(cp.desc, cp.property.getDescription());
      assertFalse(cp.property.isAssigned());
   }

   @Test(expected = NullPointerException.class)
   public void testUIPropertyNullPairing()
         throws AlreadyAssignedUIPropertyException, IncompatibleMMProperty {
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel");

      cp.property.assignProperty(null);
      assertTrue(cp.property.isAssigned());
   }

   @Test(expected = NullPointerException.class)
   public void testUIPropertyNullOwner() throws AlreadyAssignedUIPropertyException {
      @SuppressWarnings("unused")
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel") {

         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeProperties() {
            property = new UIProperty(null, prop, desc);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testUIPropertyNullLabel() throws AlreadyAssignedUIPropertyException {
      @SuppressWarnings("unused")
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel") {

         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeProperties() {
            property = new UIProperty(this, null, desc);
         }
      };
   }

   @Test(expected = NullPointerException.class)
   public void testUIPropertyNullDescription() throws AlreadyAssignedUIPropertyException {
      @SuppressWarnings("unused")
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel") {

         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeProperties() {
            property = new UIProperty(this, prop, null);
         }
      };
   }

   @Test
   public void testUIPropertyFlag() {
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel") {

         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeProperties() {
            flag = new PropertyFlag("MyFlag") {
            };
            property = new UIProperty(this, prop, desc, flag);
         }
      };

      assertEquals(cp.flag, cp.property.getFlag());
   }

   @Test(expected = NullPointerException.class)
   public void testNullPropertyFlag() {
      @SuppressWarnings("unused")
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel") {

         private static final long serialVersionUID = 1L;

         @Override
         protected void initializeProperties() {
            property = new UIProperty(this, prop, desc, null);
         }
      };
   }

   @Test
   public void testUIPropertyFriendlyName() {
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel");

      final String s = "MyFriendlyName";
      cp.property.setFriendlyName(s);
      assertEquals(s, cp.property.getFriendlyName());
   }

   @Test(expected = NullPointerException.class)
   public void testUIPropertyNullFriendlyName() {
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel");

      cp.property.setFriendlyName(null);
   }

   @Test
   public void testUIPropertyNotification() {
      UIPropertyTestPanel cp = new UIPropertyTestPanel("MyPanel") {
         private static final long serialVersionUID = 1L;

         @Override
         protected void propertyhasChanged(String propertyName, String newvalue) {
            if (propertyName.equals(prop)) {
               value = newvalue;
            }
         }
      };

      String s = "NewValue";
      cp.property.mmPropertyHasChanged(s);

      // waits to let the other thread finish
      try {
         Thread.sleep(20);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }

      assertEquals(s, cp.value);
   }

   private class UIPropertyTestPanel extends ConfigurablePanel {

      private static final long serialVersionUID = 1L;
      public UIProperty property;
      public final String prop = "MyProp";
      public final String desc = "MyDescription";
      public PropertyFlag flag;
      public String value = "";

      public UIPropertyTestPanel(String label) {
         super(label);
      }

      @Override
      protected void initializeProperties() {
         property = new UIProperty(this, prop, desc);
      }

      @Override
      protected void initializeParameters() {
      }

      @Override
      protected void parameterhasChanged(String parameterName) {
      }

      @Override
      protected void initializeInternalProperties() {
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
