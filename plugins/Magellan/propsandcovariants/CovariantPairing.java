/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import acq.Acquisition;
import acq.AcquisitionEvent;
import java.text.ParseException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.TreeMap;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Class that encapsulates and independent and a dependent covariant, and a set of covaried values between the two
 */
public class CovariantPairing {

   //list of independent values--determines order of pairings
   LinkedList<CovariantValue> independentValues_ = new LinkedList<CovariantValue>();
   //map that stores dependent values
   private TreeMap<CovariantValue, CovariantValue> valueMap_ = new TreeMap<CovariantValue, CovariantValue>();
   private Covariant independent_, dependent_;
   //set of acquisitions for which this pairing has been explicitly marked as inactive
   private IdentityHashMap<Acquisition, Object> excludedAcqs_; //not actually a map, but theres no IdentityHashSet
   private PolynomialSplineFunction interpolant_;
   private LinearInterpolator interpolator_;
   
   public CovariantPairing(Covariant independent, Covariant dependent) {
      independent_ = independent;
      dependent_ = dependent;
      excludedAcqs_ = new IdentityHashMap<Acquisition, Object>();
   }
   
   
   public void updateHardwareBasedOnPairing(AcquisitionEvent event) throws Exception {
      CovariantValue dVal = getDependentValue(event);
      dependent_.updateHardwareToValue(dVal);
   }
   
   /**
    * Get the interpolated dependent value based on the the current state
    * or hardware related to independent
    * @return Appropriate dependent CovariantValue or null if none is defined
    */
   private CovariantValue getDependentValue(AcquisitionEvent evt) throws Exception {
      //get the value of the independent based on state of hardware
      CovariantValue iVal = independent_.getCurrentValue(evt);
      if (independent_.isDiscrete() || independent_.getType() == CovariantType.STRING) {
         //if independent is discrete, dependent value is whatever is defined
         //for the current value of independent, or null if no mapping is defined
         //for the current value of independent
         int index = independentValues_.indexOf(iVal);
         if (index == -1) {
            return null;
         }
         return valueMap_.get(independentValues_.get(index));
      } else {
         //indpendent value is not discrete or String 
         //if its an int or double this means you should be able to interpolate (I think...)
         //this interpolator does ot extrapolate, so check range and set to nearest neighbor if needed
         double interpolatedVal;
         double indVal = independent_.getType() == CovariantType.INT ? iVal.intValue() : iVal.doubleValue();
         double indLowLimit = independent_.getType() == CovariantType.INT ? independentValues_.getFirst().intValue() : independentValues_.getFirst().doubleValue();
         double indHighLimit = independent_.getType() == CovariantType.INT ? independentValues_.getLast().intValue() : independentValues_.getLast().doubleValue();
         if (indVal <= indLowLimit) {
            interpolatedVal = dependent_.getType() == CovariantType.INT ? valueMap_.get(independentValues_.getFirst()).intValue() :
                    valueMap_.get(independentValues_.getFirst()).doubleValue();                                    
         } else if (indVal >= indHighLimit) {
             interpolatedVal = dependent_.getType() == CovariantType.INT ? valueMap_.get(independentValues_.getLast()).intValue() :
                    valueMap_.get(independentValues_.getLast()).doubleValue();                      
         } else {
            interpolatedVal  = interpolant_.value(indVal);
         }
         //convert back to int if needed
         if (dependent_.getType() == CovariantType.INT) {
            return new CovariantValue((int) Math.round(interpolatedVal));
         } else {
            return new CovariantValue(interpolatedVal);
         }
      }
   }
   
   private void updateInterpolant() {
      if (!independent_.isDiscrete() && independent_.getType() != CovariantType.STRING && independentValues_.size() >= 2) {
         if (interpolator_ == null) {
            interpolator_ = new LinearInterpolator();
         }
         double[] xVals =  new double[independentValues_.size()];
         double[] yVals =  new double[independentValues_.size()];
         for (int i = 0; i < xVals.length; i++ ) {
            //set x value
            if (independent_.getType() == CovariantType.DOUBLE) {
               xVals[i] = independentValues_.get(i).doubleValue();
            } else {
               xVals[i] = independentValues_.get(i).intValue();
            }
            //set y value
            if (dependent_.getType() == CovariantType.DOUBLE) {
               yVals[i] = valueMap_.get(independentValues_.get(i)).doubleValue();
            } else {               
               yVals[i] = valueMap_.get(independentValues_.get(i)).intValue();
            }
         }
         interpolant_ = interpolator_.interpolate(xVals, yVals);
      }      
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
         newPairingIndependentValue = independent_.getValidValue(independentValues_);
         if (newPairingIndependentValue == null) {
            ReportingUtils.showMessage("All independent values of covariant already taken. Must delete existing value to add new one");
            return;
         }
      }
      //get a valid dependent value       
      CovariantValue newPairingDependentValue = null;
      if (dependent_.isDiscrete()) {
         //get valid value
         newPairingDependentValue = dependent_.getAllowedValues()[0];
      } else {
         newPairingDependentValue = dependent_.getValidValue(null);
      }
      //add pairing
      independentValues_.add(newPairingIndependentValue);
      valueMap_.put(newPairingIndependentValue, newPairingDependentValue);
      Collections.sort(independentValues_);
      updateInterpolant();
   }
   
   public void deleteValuePair(int index) {
      CovariantValue val = independentValues_.remove(index);
      valueMap_.remove(val);
      updateInterpolant();
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
         updateInterpolant();
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
      updateInterpolant();
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
   
   public String getIndependentName(boolean abbreviated) {
      return abbreviated ? independent_.getAbbreviatedName() : independent_.getName();
   }
   
   public String getDependentName(boolean abbreviated) {
      return abbreviated ? dependent_.getAbbreviatedName() : dependent_.getName();
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
      return independent_.getAbbreviatedName() + " : " + dependent_.getAbbreviatedName();
   }
}
