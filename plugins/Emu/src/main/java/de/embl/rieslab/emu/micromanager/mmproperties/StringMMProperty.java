package de.embl.rieslab.emu.micromanager.mmproperties;

import de.embl.rieslab.emu.controller.log.Logger;
import mmcorej.CMMCore;

/**
 * Wrapper for a Micro-manager device property with String value. The property can be
 * read-only or not or with a set of allowed values.
 *
 * @author Joran Deschamps
 */
public class StringMMProperty extends MMProperty<String> {

   /**
    * Builds a String MMProperty without limits or allowed values. The property is read-only.
    *
    * @param core          Micro-manager core.
    * @param logger        Log manager.
    * @param type          Micro-manager property type (String or Undef)
    * @param deviceLabel   Label of the parent device as defined in Micro-manager.
    * @param propertyLabel Label of the device property as defined in Micro-manager.
    */
   StringMMProperty(CMMCore core, Logger logger, MMPropertyType type, String deviceLabel,
                    String propertyLabel) {
      super(core, logger, type, deviceLabel, propertyLabel, true);
   }

   /**
    * Builds a String MMProperty with allowed values.
    *
    * @param core          Micro-manager core.
    * @param logger        Log manager.
    * @param type          Micro-manager property type ("String" or "Undef")
    * @param deviceLabel   Label of the parent device as defined in Micro-manager.
    * @param propertyLabel Label of the device property as defined in Micro-manager.
    * @param allowedValues Array of allowed values.
    */
   StringMMProperty(CMMCore core, Logger logger, MMPropertyType type, String deviceLabel,
                    String propertyLabel, String[] allowedValues) {
      super(core, logger, type, deviceLabel, propertyLabel, allowedValues);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String convertToValue(String s) {
      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String convertToValue(int s) {
      return String.valueOf(s);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String convertToValue(double s) {
      return String.valueOf(s);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String[] arrayFromStrings(String[] s) {
      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String convertToString(String val) {
      return val;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isAllowed(String val) {
      if (val == null) {
         return false;
      }

      if (isReadOnly()) {
         return false;
      } else if (hasAllowedValues()) {
         for (int i = 0; i < getAllowedValues().length; i++) {
            if (areEquals(val, getAllowedValues()[i])) {
               return true;
            }
         }
         return false;
      }
      return true;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean areEquals(String val1, String val2) {
      return val1.equals(val2);
   }
}
