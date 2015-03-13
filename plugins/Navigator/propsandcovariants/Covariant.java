/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

/**
 * Interface for device property, group, or surface statistic that can be covaried
 */
public interface Covariant {

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
   
}
