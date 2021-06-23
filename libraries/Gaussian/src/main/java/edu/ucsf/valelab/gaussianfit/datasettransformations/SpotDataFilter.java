/*
Copyright (c) 2013-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.datasettransformations;

import edu.ucsf.valelab.gaussianfit.data.SpotData;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Simple filter for spot data.
 * <p>
 * Spots can be filtered based on intensity and sigma (width) Setup the filter using the setSigma,
 * setIntensity, and setItemFilter functions, then use the filter class to test individual spots
 *
 * @author Nico Stuuman
 */
public class SpotDataFilter {

   private boolean useSigma_ = false;
   private double sigmaMin_ = 0;
   private double sigmaMax_ = 0;
   private boolean useIntensity_ = false;
   private double intensityMin_ = 0;
   private double intensityMax_ = 0;

   private static class Extremes {

      double minimum_;
      double maximum_;

      public Extremes(double min, double max) {
         minimum_ = min;
         maximum_ = max;
      }
   }

   private final Map<String, Extremes> itemFilter_ = new HashMap<String, Extremes>();

   public SpotDataFilter() {
   }

   public void setSigma(boolean filter, double min, double max) {
      useSigma_ = filter;
      sigmaMin_ = min;
      sigmaMax_ = max;
   }

   /**
    * @param filter - whether or not to use this filter
    * @param min    - Smallest value that will be rejected
    * @param max    - Largest value that will be rejected
    */
   public void setIntensity(boolean filter, double min, double max) {
      useIntensity_ = filter;
      intensityMin_ = min;
      intensityMax_ = max;
   }

   public void setItemFilter(String item, double min, double max) {
      itemFilter_.put(item, new Extremes(min, max));
   }

   /**
    * Indicates whether or not the spot is acceptable
    *
    * @param spot - spot Data
    * @return true if spot is acceptable
    */
   public boolean filter(SpotData spot) {
      final String INTEGRALSIGMA = SpotData.Keys.INTEGRALSIGMA;
      if (useSigma_) {
         if (spot.hasKey(INTEGRALSIGMA) && (  // return false if no IntegralSigma found?
               spot.getValue(INTEGRALSIGMA)) < sigmaMin_ ||
               spot.getValue(INTEGRALSIGMA) > sigmaMax_) {
            return false;
         }
      }
      if (useIntensity_) {
         final String INTENSITY = SpotData.Keys.APERTUREINTENSITY;
         if (spot.hasKey(INTENSITY) && (
               spot.getValue(INTENSITY) < intensityMin_ ||
                     spot.getValue(INTENSITY) > intensityMax_)) {
            return false;
         }
      }
      Set<String> itemFilterKeySet = itemFilter_.keySet();
      for (String key : itemFilterKeySet) {
         if (spot.hasKey(key)) {
            Extremes ex = itemFilter_.get(key);
            if (spot.getValue(key) < ex.minimum_ || spot.getValue(key) > ex.maximum_) {
               return false;
            }
         } // TODO: what do we do when the spot does not have the key???
         // we may need to throw an exception
         // currently, data will simply not be filtered
      }

      return true;
   }


}
