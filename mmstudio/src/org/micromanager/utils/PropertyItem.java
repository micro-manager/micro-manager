package org.micromanager.utils;

import java.util.Arrays;

import mmcorej.CMMCore;
import mmcorej.PropertyType;
import mmcorej.StrVector;

/**
 * Property descriptor, representing MMCore data
 */
public class PropertyItem {
   public String device;  // device name (label)
   public String name;    // property name
   public String value;   // property value
   public boolean readOnly = false;    // is it read-only ?
   public boolean preInit = false; // is it a pre-initialization property ?
   public String allowed[];            // the list of allowed values
   public boolean confInclude = false; // is it included in the current configuration ?
   public boolean hasRange = false; // is there a range for values
   public double lowerLimit = 0.0;
   public double upperLimit = 0.0;
   public PropertyType type;
   
   public PropertyItem() {
      device = new String();
      name = "Undefined";
      value = new String();
      allowed = new String[0];
   }

   public PropertyItem(String _name, String _value) {
      name = _name;
      value = _value;
      allowed = new String[0];
   }

   public PropertyItem(String _name, String _value, boolean _preInit) {
      name = _name;
      value = _value;
      allowed = new String[0];
      preInit = _preInit;
   }


   public void dump() {
      ReportingUtils.logMessage("Property : " + name);
      ReportingUtils.logMessage("Property : " + value);
      ReportingUtils.logMessage("   allowed :");
      for (int i=0; i<allowed.length; i++)
         ReportingUtils.logMessage("   " + allowed[i]);
      
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

   public void readFromCore(CMMCore core, String deviceName, String propertyName) {
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
         allowed = new String[(int)values.size()];
         for (int k=0; k<values.size(); k++){
            allowed[k] = values.get(k);
         }

         sort();

         String coreVal = core.getProperty(deviceName,propertyName);
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
         ReportingUtils.logMessage("error sorting " + device + "."+ name);
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

  
}
