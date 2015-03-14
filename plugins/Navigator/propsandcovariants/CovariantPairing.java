/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import acq.Acquisition;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.TreeMap;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Class that encapsulates and independent and a dependent covariant, and a set of covaried values between the two
 */
public class CovariantPairing {

   //list of independent values--determines order of pairings
   ArrayList<CovariantValue> independentValues_ = new ArrayList<CovariantValue>();
   //map that stores dependent values
   private IdentityHashMap<CovariantValue, CovariantValue> valueMap_ = new IdentityHashMap<CovariantValue, CovariantValue>();
   private Covariant independent_, dependent_;
   //set of acquisitions for which this pairing has been explicitly marked as inactive
   private IdentityHashMap<Acquisition, Object> excludedAcqs_; //not actually a map, but theres no IdentityHashSet
   
   public CovariantPairing(Covariant independent, Covariant dependent) {
      independent_ = independent;
      dependent_ = dependent;
      excludedAcqs_ = new IdentityHashMap<Acquisition, Object>();
   }
   
   public Covariant getIndependentCovariant() {
      return independent_;
   }
   
   public Covariant getDependentCovariant() {
      return dependent_;
   }

   /**
    * Create new pairing with arbitrary valid values
    */
   public void addNewValuePairing() {
      //Independent can be float, int, or string
      //can have limits if float or int
      //can have discrete values to choose from, or it can allow only some values 
      //(such as strings that only accept certain values)
      //Get a valid independent value
      CovariantValue newPairingIndependentValue = null;
      if (independent_.isDiscrete()) {
         //check for available value
         CovariantValue[] allowedValues = independent_.getAllowedValues();         
         for (CovariantValue v: allowedValues) {
            if (!valueMap_.containsKey(v)) {
               newPairingIndependentValue = v;
               break;
            }
         }
         if (newPairingIndependentValue == null) {
            ReportingUtils.showMessage("All independent values of covariant already taken. Must delete existing value to add new one");
            return;
         }
      } else {
         newPairingIndependentValue = independent_.getValidValue();
      }
      //get a valid dependent value       
      CovariantValue newPairingDependentValue = null;
      if (dependent_.isDiscrete()) {
         //get valid value
         newPairingDependentValue = dependent_.getAllowedValues()[0];
      } else {
         newPairingDependentValue = dependent_.getValidValue();
      }
      //add pairing
      independentValues_.add(newPairingIndependentValue);
      valueMap_.put(newPairingIndependentValue, newPairingDependentValue);
      Collections.sort(independentValues_);
   }
   
   public void deleteValuePair(int index) {
      CovariantValue val = independentValues_.remove(index);
      valueMap_.remove(val);
   }
   
   /**
    * add pairing with specifie values
    * @param independentValue
    * @param dependentValue 
    */
   public void addValuePair(CovariantValue independentValue, CovariantValue dependentValue) {
      //check validity of values
      if (independent_.isValid(independentValue) && dependent_.isValid(dependentValue)) {
         valueMap_.put(independentValue, dependentValue);
         independentValues_.add(independentValue);
         Collections.sort(independentValues_);
      } else {
         ReportingUtils.showMessage(independentValue + " and " + dependentValue + " are not a valid pairing for "
                 + independent_.getName() + " and " + dependent_.getName());
      }
   }

   /**
    * Change values of an existing pairing
    * @param propIndex
    * @param rowIndex
    * @param value 
    */
   public void setValue(int propIndex, int rowIndex, CovariantValue value) {
      if (propIndex == 0) {
         value.restrainWithinLimits(independent_);
         //new independent, exisiting dependent 
         if (!independent_.isValid(value)) {
            ReportingUtils.showMessage("Invalid value");
            throw new RuntimeException();
         }
         valueMap_.put(value, valueMap_.remove(independentValues_.get(rowIndex)));
         independentValues_.remove(rowIndex);
         independentValues_.add(rowIndex, value);
         Collections.sort(independentValues_);
      } else {
         //new dependent, existiing independent 
         value.restrainWithinLimits(dependent_);
         if (!dependent_.isValid(value)) {
            ReportingUtils.showMessage("Invalid value");
            throw new RuntimeException();
         }
         valueMap_.put(independentValues_.get(rowIndex), value);
         //no need to re sort, only dependent value changed
      }
   }

   public void setValue(int propIndex, int rowIndex, String value) {
      setValue(propIndex, rowIndex, convertToCovariantValue(value, propIndex == 0 ? independent_ : dependent_));
   }

   private CovariantValue convertToCovariantValue(String s, Covariant c) {
      try {
         if (c.getType() == CovariantType.STRING) {
            return new CovariantValue(s);
         } else if (c.getType() == CovariantType.INT) {
            return new CovariantValue(NumberUtils.displayStringToInt(s));
         } else {
            return new CovariantValue(NumberUtils.displayStringToDouble(s));
         }
      } catch (ParseException e) {
         ReportingUtils.showError("Invalid value");
         return null;
      }
   }

   public void enableForAcqusition(Acquisition acq, boolean enable) {
      if (enable) {
         excludedAcqs_.remove(acq);
      } else {
         excludedAcqs_.put(acq,null);
      }
   }

   public boolean isActiveForAcquisition(Acquisition acq) {
      return !excludedAcqs_.containsKey(acq);
   }
   
   public int getNumPairings() {
      return valueMap_.size();
   }
   
   public String getIndependentName() {
      return independent_.getName();
   }
   
   public String getDependentName() {
      return dependent_.getName();
   }

   /**
    * 
    * @param covIndex 0 for independent, 1 for dependent
    * @param valueIndex
    * @return 
    */
   CovariantValue getValue(int covIndex, int valueIndex) {
      CovariantValue indValue = independentValues_.get(valueIndex);
      return (CovariantValue) (covIndex == 0 ? indValue : valueMap_.get(indValue));
   }
  
   @Override
   public String toString() {
      return independent_.getName() + " : " + dependent_.getName();
   }
}
