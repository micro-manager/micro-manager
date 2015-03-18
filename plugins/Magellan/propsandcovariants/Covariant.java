/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import acq.AcquisitionEvent;
import java.util.List;

/**
 * Interface for device property, group, or surface statistic that can be covaried
 */
public interface Covariant {

   public String getAbbreviatedName();
   
   public String getName();
      
   public boolean isValid(CovariantValue potentialValue);

   /**
    * return Array of possible values. Applies to discrete covariants only
    */
   public CovariantValue[] getAllowedValues();

   public boolean isDiscrete();
   
   public boolean hasLimits();

   public CovariantValue getLowerLimit();

   public CovariantValue getUpperLimit();
   
   public CovariantType getType();

   /**
    * Assumes that the covariant in question is not discrete
    * @param vals
    * @return null if there aren't any more values given the ones already taken in list
    */
   public CovariantValue getValidValue(List<CovariantValue> vals);

   public CovariantValue getCurrentValue(AcquisitionEvent evt);

   public void updateHardwareToValue(CovariantValue dVal) throws Exception;
   
}
