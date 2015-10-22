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

package org.micromanager.plugins.magellan.propsandcovariants;

import java.text.DecimalFormat;

/**
 *
 * @author Henry
 */
public class CovariantValue  implements Comparable<CovariantValue>{
   
   private double doubleValue_;
   private int intValue_;
   private String stringValue_;
   private CovariantType type_;
   
   CovariantValue(double d) {
      doubleValue_ = d;
      type_ = CovariantType.DOUBLE; 
   }
   
   CovariantValue(int i) {
      intValue_ = i;
      type_ = CovariantType.INT; 
   }
   
   CovariantValue(String s) {
      stringValue_ = s;
      type_ = CovariantType.STRING;
   }
   
   public CovariantType getType() {
      return type_;
   }

   @Override
   public String toString() {
      if (stringValue_ != null) {
         return stringValue_;
      } else if (type_ == CovariantType.DOUBLE) {
          return new DecimalFormat("0.0000").format(doubleValue_); //formatted correctly for the core
      } else {
         return intValue_ + "";
      }
   }

   public double doubleValue() {
      return doubleValue_;
   }
   
   public int intValue() {
      return intValue_;
   }

   String stringValue() {
      return stringValue_;
   }

   @Override
   public boolean equals(Object o) {
      if (!(o instanceof CovariantValue)) {
         return false;
      }
      return this.compareTo((CovariantValue)o) == 0;
   }
   
   @Override
   public int compareTo(CovariantValue o) {
      if (type_ == CovariantType.STRING) {
         return this.stringValue().compareTo(o.stringValue());
      } else if (type_ == CovariantType.DOUBLE) {
         return new Double(this.doubleValue_).compareTo(o.doubleValue_);
      } else {
         return new Integer(this.intValue_).compareTo(o.intValue_);
      }
   }

   void restrainWithinLimits(Covariant cov) {
      if (cov.hasLimits()) {
         if (type_ == CovariantType.INT) {
            intValue_ = Math.min(cov.getUpperLimit().intValue(), Math.max(intValue_, cov.getLowerLimit().intValue()));
            
         } else if (type_ == CovariantType.DOUBLE) {            
            doubleValue_ = Math.min(cov.getUpperLimit().doubleValue(), Math.max(doubleValue_, cov.getLowerLimit().doubleValue()));
         }
      }
   }
}
