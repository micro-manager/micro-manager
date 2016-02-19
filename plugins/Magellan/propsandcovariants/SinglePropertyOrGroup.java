///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package propsandcovariants;

import acq.AcquisitionEvent;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import main.Magellan;
import misc.Log;
import misc.NumberUtils;
import misc.SortFunctionObjects;
import mmcorej.CMMCore;
import mmcorej.PropertyType;
import mmcorej.StrVector;

/**
 * Adapted form MM class PropertyItem
 */
public class SinglePropertyOrGroup implements Covariant{
  
   //prefix so that groups can be read from txt files
   public static String GROUP_PREFIX = "Group: ";
   
   public String device;  // device name (label)
   public String name;    // property name
   public String value;   // property value
   public boolean readOnly = false;    // is it read-only ?
   public boolean preInit = false; // is it a pre-initialization property ?
   public String allowed[];            // the list of allowed values
   public boolean hasRange = false; // is there a range for values
   public double lowerLimit = 0.0;
   public double upperLimit = 0.0;
   public PropertyType type;
   
   public SinglePropertyOrGroup() {
      device = null;
      name = "Undefined";
      value = new String();
      allowed = new String[0];
   }
   
   public boolean isGroup() {
      return device == null;
   }
   
   @Override 
   public boolean equals(Object o) {
      SinglePropertyOrGroup prop = (SinglePropertyOrGroup) o;
      if ((device != null && prop.device == null) || (device == null && prop.device != null)) {
         return false;
      } else if (device != null && prop.device != null && !device.equals(prop.device)) {
         return false;
      }
      return name.equals(prop.name);
   }
   
   @Override
   public String toString() {
      return getName();
   }
  
   private String setValueFromCoreString(String coreValue) {
	   try {
		   if (isInteger())
			   return NumberUtils.intStringCoreToDisplay(coreValue);
		   else if (isFloat())
			   return NumberUtils.doubleStringCoreToDisplay(coreValue);
		   else
			   return coreValue;
	   } catch (Exception e) {
         Log.log(e);
		   return coreValue;
	   }
   }
   
   private String getValueInCoreFormat(String val) {
      try {
         if (isInteger()) {
            return NumberUtils.intStringDisplayToCore(val);
         } else if (isFloat()) {
            return NumberUtils.doubleStringDisplayToCore(val);
         } else {
            return val;
         }
      } catch (Exception e) {
         Log.log(e);
         return null;
      }
   }

   public void readGroupValuesFromConfig(String groupName) {
      CMMCore core = Magellan.getCore();
      name = groupName;
      type = PropertyType.String;
      allowed = core.getAvailableConfigs(groupName).toArray();
   }

   public void readFromCore(String deviceName, String propertyName,
           boolean cached) {
      CMMCore core = Magellan.getCore();
      device = deviceName;
      name = propertyName;
      try {
         readOnly = core.isPropertyReadOnly(deviceName, propertyName);
         preInit = core.isPropertyPreInit(deviceName, propertyName);
         hasRange = core.hasPropertyLimits(deviceName, propertyName);
         lowerLimit = core.getPropertyLowerLimit(deviceName, propertyName);
         upperLimit = core.getPropertyUpperLimit(deviceName, propertyName);
         type = core.getPropertyType(deviceName, propertyName);
         StrVector values = core.getAllowedPropertyValues(deviceName, propertyName);
         allowed = new String[(int) values.size()];
         for (int k = 0; k < values.size(); k++) {
            allowed[k] = setValueFromCoreString(values.get(k));
         }

         sort();

         String coreVal;
         if (cached) {
            coreVal = core.getPropertyFromCache(deviceName, propertyName);
         } else {
            coreVal = core.getProperty(deviceName, propertyName);
         }
         value = setValueFromCoreString(coreVal);
      } catch (Exception e) {
         Log.log(e);
      }
   }

   public void sort() {
      try {
         if (allowed.length > 0) {
            if (PropertyType.Float == type) {
               Arrays.sort(allowed, new SortFunctionObjects.DoubleStringComp());
            } else if (PropertyType.Integer == type) {
               Arrays.sort(allowed, new SortFunctionObjects.IntStringComp());
            } else if (PropertyType.String == type) {
               boolean allNumeric = true;
               // test that first character of every possible value is a numeral
               // if so, show user the list sorted by the numeric prefix
               for (int k = 0; k < allowed.length; k++) {
                  if ( null != allowed[k]){
                     if( 0 < allowed[k].length()){
                        if (!Character.isDigit(allowed[k].charAt(0))) {
                           allNumeric = false;
                           break;
                        }
                     }else {
                        allNumeric = false;
                        break;
                     }
                  }else {
                     allNumeric = false;
                     break;
                  }
               }

               if (allNumeric) {
                  Arrays.sort(allowed, new SortFunctionObjects.NumericPrefixStringComp());
               }
            }
         }
      } catch(Exception e){
         Log.log(e);
      }
   }

   public boolean isInteger() {
	   return type == PropertyType.Integer;
   }
   
   public boolean isFloat() {
	   return type == PropertyType.Float;
   }

   public boolean isString() {
	   return type == PropertyType.String;
   }
   
   public boolean isUndefined() {
	   return type == PropertyType.Undef;
   }
   
   private CovariantValue convertValueToCovariantValue(String val) {
     try {
      //only valid for props, not groups
      if (isGroup()) {
         throw new RuntimeException();
      } else if (isInteger()) {
         return new CovariantValue(NumberUtils.displayStringToInt(val));
      } else if (isFloat()) {
         return new CovariantValue(NumberUtils.displayStringToDouble(val));
      } else {
         return new CovariantValue(val);
      }
     } catch (ParseException e ) {
        Log.log("Problem parsing property value");
        throw new RuntimeException();
     }
   }

   /////////////////////////////////////////////////////////////////////////
   //////////////////Covariant interface methods////////////////////////////
   /////////////////////////////////////////////////////////////////////////
   @Override
   public String getName() {
      if (isGroup()) {
         return GROUP_PREFIX + name;
      } else {
         return device + "-" + name;
      }
   }
   
   @Override
   public String getAbbreviatedName() {
      if (isGroup()) {
         return name;
      } else {
         return device + "-" + name;
      }
   }

   @Override
   public boolean isValid(CovariantValue potentialValue) {
      //check type
      if (getType() != potentialValue.getType()) {
         return false;
      }
      if (potentialValue.getType() == CovariantType.DOUBLE) {
         if (hasLimits()) {
            if (potentialValue.doubleValue() < lowerLimit || potentialValue.doubleValue() > upperLimit) {
               return false;
            }
         }
      } else if (potentialValue.getType() == CovariantType.INT) {     
         if (hasLimits()) {
            if (potentialValue.intValue() < lowerLimit || potentialValue.doubleValue() > upperLimit) {
               return false;
            }
         }
      } else if (potentialValue.getType() == CovariantType.STRING) {
         if (allowed.length == 0) {
            return true; //we don't know
         }
         boolean allowedVal = false;
         for(String s : allowed) {
            if (s.equals(potentialValue.stringValue())) {
               allowedVal = true;
            }
         }
         if (!allowedVal) {
            return false;
         }
      }
      return true;
   }

   @Override
   public CovariantValue[] getAllowedValues() {
      CovariantValue[] vals = new CovariantValue[allowed.length];
      for (int i = 0; i < allowed.length; i++) {
         if (isGroup()) {
            vals[i] = new CovariantValue(allowed[i]);
         } else {
            vals[i] = convertValueToCovariantValue(allowed[i]);
         }
      }
      return vals;
   }

   @Override
   public boolean isDiscrete() {
      return allowed.length != 0;
   }

   @Override
   public boolean hasLimits() {
      return hasRange;
   }

   @Override
   public CovariantValue getLowerLimit() {
      return new CovariantValue(isInteger() ? (int) lowerLimit  : lowerLimit);
   }

   @Override
   public CovariantValue getUpperLimit() {
      return new CovariantValue(isInteger() ? (int) upperLimit : upperLimit);
   }

   @Override
   public CovariantType getType() {
      if (isInteger()) {
         return CovariantType.INT;
      } else if (isFloat()) {
         return CovariantType.DOUBLE;
      } else {
         return CovariantType.STRING;
      }
   }

   @Override
   public CovariantValue getValidValue(List<CovariantValue> values) {
      //this function is called in non discrete cases (so don't worry about groups)
      if (values == null) {
         //no values already taken so just return the one we have
         return convertValueToCovariantValue(value);         
      } else {
         //return a valid value thats not already in the list
         if (isInteger()) {
            int intVal = (int) lowerLimit;
            while (true) {
               if (!values.contains(new CovariantValue(intVal))) {
                  return new CovariantValue(intVal);
               }
               intVal++;
               if (hasRange && intVal > upperLimit) {
                  return null; //all values taken
               }
            }            
         } else if (isFloat()) {
            double dVal = (int) lowerLimit;
            while (true) {
               if (!values.contains(new CovariantValue(dVal))) {
                  return new CovariantValue(dVal);
               }
               dVal++;
               if (hasRange && dVal > upperLimit) {
                  //all int values taken, just try random shit until one works
                  dVal = Math.random();
                  if (!values.contains(new CovariantValue(dVal))) {
                     return new CovariantValue(dVal);
                  }
               }
            }
         } else {
            //its some String property without allowed values
            String base = "Independent value";
            int i = 0;
            while (true) {
               String val = base + "_" + i;
               if (!values.contains(new CovariantValue(val))) {
                  return new CovariantValue(val);
               }
               i++;
            }
         }         
      }
   }

   @Override
   public CovariantValue getCurrentValue(AcquisitionEvent evt, CovariantPairing pair) {
      try {
         CMMCore core = Magellan.getCore();
         if (isGroup()) {
            return new CovariantValue(core.getCurrentConfig(name));
         }
         String coreVal;
         coreVal = core.getProperty(device, name);
         return convertValueToCovariantValue(coreVal);
      } catch (Exception ex) {
         Log.log("Couldn't get " + name + " from core");
         throw new RuntimeException();
      }
   }

   @Override
    public void updateHardwareToValue(CovariantValue dVal) throws Exception {
        CMMCore core = Magellan.getCore();
        if (isGroup()) {
            core.setConfig(name, dVal.stringValue());
        } else {
            try {
                core.setProperty(device, name, dVal.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
