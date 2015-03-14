package propsandcovariants;

import java.text.ParseException;
import propsandcovariants.Covariant;
import java.util.Arrays;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertyType;
import mmcorej.StrVector;
import org.micromanager.MMStudio;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.SortFunctionObjects;

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
   public String toString() {
      return getName();
   }
  
   public void setValueFromCoreString(String coreValue) {
	   try {
		   if (isInteger())
			   value = NumberUtils.intStringCoreToDisplay(coreValue);
		   else if (isFloat())
			   value = NumberUtils.doubleStringCoreToDisplay(coreValue);
		   else
			   value = coreValue;
	   } catch (Exception e) {
         ReportingUtils.logError(e);
		   value = coreValue;
	   }
   }
   
   public String getValueInCoreFormat() {
	   try {
	   if (isInteger())
		   return NumberUtils.intStringDisplayToCore(value);
	   else if (isFloat())
		   return NumberUtils.doubleStringDisplayToCore(value);
	   else
		   return value;
	   } catch (Exception e) {
           ReportingUtils.logError(e);
		   return value;
	   }
   }
   
   public void readGroupValuesFromConfig(String groupName) {
      CMMCore core = MMStudio.getInstance().getCore();
      name = groupName;
      type = PropertyType.String;
      allowed = core.getAvailableConfigs(groupName).toArray();
   }

   public void readFromCore(String deviceName, String propertyName,
           boolean cached) {
      CMMCore core = MMStudio.getInstance().getCore();
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
            allowed[k] = values.get(k);
         }

         sort();

         String coreVal;
         if (cached)
            coreVal = core.getPropertyFromCache(deviceName,propertyName);
         else
            coreVal = core.getProperty(deviceName,propertyName);
         setValueFromCoreString(coreVal);
 		 } catch (Exception e) {
			ReportingUtils.logError(e);
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
         ReportingUtils.logMessage("error sorting " + toString());
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
      try {
         CovariantValue[] vals = new CovariantValue[allowed.length];
         for (int i = 0; i < allowed.length; i++) {
            if (isInteger()) {
               vals[i] = new CovariantValue( NumberUtils.coreStringToInt(allowed[i]));
            } else if (isFloat()) {
               vals[i] = new CovariantValue( NumberUtils.coreStringToDouble(allowed[i]));
            } else {
               vals[i] = new CovariantValue(allowed[i]);
            }
            
         }
         return vals;
      } catch (ParseException e) {
         ReportingUtils.showError("Couldn't parse property value");
         throw new RuntimeException();
      }
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
   public CovariantValue getValidValue() {
      try {
         if (isGroup()) {
            return new CovariantValue(allowed[0]);
         } else if (isInteger()) {
            return new CovariantValue(NumberUtils.displayStringToInt(value));
         } else if (isFloat()) {                
            return new CovariantValue(NumberUtils.displayStringToDouble(value));
         } else {
            return new CovariantValue(value);
         }
      } catch (ParseException e) {
         ReportingUtils.showError("Error parsing property value");
         throw new RuntimeException();
      }
   }
   
}
