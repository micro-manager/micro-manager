
package edu.valelab.gaussianfit.datasettransformations;

import edu.valelab.gaussianfit.data.SpotData;

/**
 * Simple filter for spot data.
 * 
 * Spots can be filtered based on intensity and sigma (width)
 * Setup the filter using the setSigma and setIntensity functions,
 * then use the filter class to test individual spots
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
   
   public SpotDataFilter() {
   }
   
   public void setSigma(boolean filter, double min, double max) {
      useSigma_ = filter;
      sigmaMin_ = min;
      sigmaMax_ = max;
   }
   
   public void setIntensity(boolean filter, double min, double max) {
      useIntensity_ = filter;
      intensityMin_ = min;
      intensityMax_ = max;
   }
   
   /**
    * Indicates whether or not the spot is acceptable 
    * 
    * @param spot - spot Data
    * @return true if spot is acceptable
    */
   public boolean filter (SpotData spot) {
      if (useSigma_) {
         if (spot.getSigma() < sigmaMin_ || spot.getSigma() > sigmaMax_)
            return false;
      }
      if (useIntensity_) {
         if (spot.getIntensity() < intensityMin_ || spot.getIntensity() > intensityMax_)
            return false;
      }
      
      return true;
   }
   
   
   
}
