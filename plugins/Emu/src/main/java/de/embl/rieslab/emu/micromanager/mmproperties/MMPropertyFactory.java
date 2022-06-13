package de.embl.rieslab.emu.micromanager.mmproperties;

import de.embl.rieslab.emu.controller.log.Logger;
import mmcorej.CMMCore;

/**
 * {@link MMProperty} factory.
 *
 * @author Joran Deschamps
 */
public class MMPropertyFactory {

   private final CMMCore core_;
   private final Logger logger_;

   /**
    * Constructor.
    *
    * @param core   Micro-manager CMMCore.
    * @param logger EMU logger.
    */
   public MMPropertyFactory(CMMCore core, Logger logger) {
      core_ = core;
      logger_ = logger;
   }

   /**
    * Returns an instantiated MMProperty from a device and a device property labels. The
    * method retrieves the type of property (Float, Integer or String), the limits and allowed
    * values if applicable. If the device property does not exist, it returns a null object.
    *
    * @param deviceLabel   Label of the device.
    * @param propertyLabel Label of the property.
    * @return MMProperty corresponding to the device property, null if the device property
    *     does not exist.
    */
   @SuppressWarnings("rawtypes")
   public MMProperty getNewProperty(String deviceLabel, String propertyLabel) {
      MMProperty p = null;

      try {

         // retrieves type, read only, limits and allowed values
         String type = (core_.getPropertyType(deviceLabel, propertyLabel)).toString();

         boolean readOnly = core_.isPropertyReadOnly(deviceLabel, propertyLabel);
         boolean hasLimits = core_.hasPropertyLimits(deviceLabel, propertyLabel);
         double upLimit = 0;
         double downLimit = 0;
         if (hasLimits) {
            upLimit = core_.getPropertyUpperLimit(deviceLabel, propertyLabel);
            downLimit = core_.getPropertyLowerLimit(deviceLabel, propertyLabel);
         }

         String[] allowedValues =
               core_.getAllowedPropertyValues(deviceLabel, propertyLabel).toArray();
         boolean allowedValuesIsNull = true;
         if (allowedValues.length > 0) {
            if (allowedValues[0] != null) {
               allowedValuesIsNull = false;
            }
         }

         // instantiate using the type and the constructors for read-only, limited or allowed values
         if (type.equals(MMProperty.MMPropertyType.FLOAT.toString())) {
            if (hasLimits) {
               p = new FloatMMProperty(core_, logger_, deviceLabel, propertyLabel, upLimit,
                     downLimit);
            } else if (!allowedValuesIsNull) {
               p = new FloatMMProperty(core_, logger_, deviceLabel, propertyLabel, allowedValues);
            } else {
               p = new FloatMMProperty(core_, logger_, deviceLabel, propertyLabel, readOnly);
            }
         } else if (type.equals(MMProperty.MMPropertyType.INTEGER.toString())) {
            if (hasLimits) {
               p = new IntegerMMProperty(core_, logger_, deviceLabel, propertyLabel, upLimit,
                     downLimit);
            } else if (!allowedValuesIsNull) {
               p = new IntegerMMProperty(core_, logger_, deviceLabel, propertyLabel, allowedValues);
            } else {
               p = new IntegerMMProperty(core_, logger_, deviceLabel, propertyLabel, readOnly);
            }
         } else if (type.equals(MMProperty.MMPropertyType.STRING.toString())) {
            if (!allowedValuesIsNull) {
               p = new StringMMProperty(core_, logger_, MMProperty.MMPropertyType.STRING,
                     deviceLabel, propertyLabel, allowedValues);
            } else {
               p = new StringMMProperty(core_, logger_, MMProperty.MMPropertyType.STRING,
                     deviceLabel, propertyLabel);
            }
         } else if (type.equals(MMProperty.MMPropertyType.UNDEF.toString())) {
            if (!allowedValuesIsNull) {
               p = new StringMMProperty(core_, logger_, MMProperty.MMPropertyType.UNDEF,
                     deviceLabel, propertyLabel, allowedValues);
            } else {
               p = new StringMMProperty(core_, logger_, MMProperty.MMPropertyType.UNDEF,
                     deviceLabel, propertyLabel);
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      return p;
   }
}
