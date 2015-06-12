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

   public CovariantValue getCurrentValue(AcquisitionEvent evt) throws Exception;

   public void updateHardwareToValue(CovariantValue dVal) throws Exception;
   
}
