package org.micromanager.pointandshootanalysis.data;

import java.util.Map;

/**
 * Data structure that holds the interpretation of a (bleached) particle track.
 * DataCalculates the average size of a particle, fits recovery of the bleach
 * spot, as well as of the particle itself
 *
 * <p>TODO: Add t 1/2 and Rsquared
 *
 * @author nico
 */
public class TrackInfo {

   public final String imageName_;
   public final String trackID_; // Unique ID, usually: bleachframeNr: x, y all in integers
   public final Double size_;  // size in ???
   // Single exponential recovery: Y = Y0 + (Plateau - Y0) * ( 1 - exp(-K*x))
   // x is time in seconds
   // Y is bleach corrected, normalized intensity
   // Y0 is the intercept (i.e. lowest intensity)
   // k is rate constant ( in 1 / seconds )
   // Mobile fraction: Plateau - Y0
   // Immobile fraction: 1 - mobile fraction
   public final Double bleachIntercept_;
   public final Double bleachPlateau_;
   public final Double bleachRateConstant_;
   public final Double bleachMobileFraction_;
   public final Double bleachImmobileFraction_;

   // same, but now for the particle as a whole (including the bleach spot)
   public final Double particleIntercept_;
   public final Double particlePlateau_;
   public final Double particleRateConstant_;
   public final Double particleMobileFraction_;
   public final Double particleImmobileFraction_;

   private TrackInfo(
         String imageName,
         String trackID,
         Double size,
         Double bleachIntercept,
         Double bleachPlateau,
         Double bleachRateConstant,
         Double bleachMobileFraction,
         Double bleachImmobileFraction,
         Double particleIntercept,
         Double particlePlateau,
         Double particleRateConstant,
         Double particleMobileFraction,
         Double particleImmobileFraction) {
      imageName_ = imageName;
      trackID_ = trackID;
      size_ = size;
      bleachIntercept_ = bleachIntercept;
      bleachPlateau_ = bleachPlateau;
      bleachRateConstant_ = bleachRateConstant;
      bleachMobileFraction_ = bleachMobileFraction;
      bleachImmobileFraction_ = bleachImmobileFraction;

      particleIntercept_ = particleIntercept;
      particlePlateau_ = particlePlateau;
      particleRateConstant_ = particleRateConstant;
      particleMobileFraction_ = particleMobileFraction;
      particleImmobileFraction_ = particleImmobileFraction;
   }

   public static class Builder {

      private final String imageName_;
      private final String trackID_;
      private Double size_;
      private Double bleachIntercept_;
      private Double bleachPlateau_;
      private Double bleachRateConstant_;
      private Double bleachMobileFraction_;
      private Double bleachImmobileFraction_;

      // same, but now for the particle as a whole (including the bleach spot)
      private Double particleIntercept_;
      private Double particlePlateau_;
      private Double particleRateConstant_;
      private Double particleMobileFraction_;
      private Double particleImmobileFraction_;


      public Builder(String imageName, String trackID) {
         imageName_ = imageName;
         trackID_ = trackID;
      }

      public Builder size(Double size) {
         size_ = size;
         return this;
      }

      public Builder bleachIntercept(Double bleachIntercept) {
         bleachIntercept_ = bleachIntercept;
         return this;
      }

      public Builder bleachPlateau(Double bleachPlateau) {
         bleachPlateau_ = bleachPlateau;
         return this;
      }

      public Builder bleachRateConstant(Double bleachRateConstant) {
         bleachRateConstant_ = bleachRateConstant;
         return this;
      }

      public Builder bleachMobileFraction(Double bleachMobileFraction) {
         bleachMobileFraction_ = bleachMobileFraction;
         return this;
      }

      public Builder bleachImmobileFraction(Double bleachImmobileFraction) {
         bleachImmobileFraction_ = bleachImmobileFraction;
         return this;
      }

      public Builder particleIntercept(Double particleIntercept) {
         particleIntercept_ = particleIntercept;
         return this;
      }

      public Builder particlePlateau(Double particlePlateau) {
         particlePlateau_ = particlePlateau;
         return this;
      }

      public Builder particleRateConstant(Double particleRateConstant) {
         particleRateConstant_ = particleRateConstant;
         return this;
      }

      public Builder particleMobileFraction(Double particleMobileFraction) {
         particleMobileFraction_ = particleMobileFraction;
         return this;
      }

      public Builder particleImmobileFraction(Double particleImmobileFraction) {
         particleImmobileFraction_ = particleImmobileFraction;
         return this;
      }

      public TrackInfo build() {
         return new TrackInfo(imageName_,
               trackID_,
               size_,
               bleachIntercept_,
               bleachPlateau_,
               bleachRateConstant_,
               bleachMobileFraction_,
               bleachImmobileFraction_,
               particleIntercept_,
               particlePlateau_,
               particleRateConstant_,
               particleMobileFraction_,
               particleImmobileFraction_
         );
      }
   }

   public static TrackInfo calculateTrackInfo(PASData pasData,
                                              Map<Integer, Double> controlAvgIntensity) {
      Map<Integer, ParticleData> track = pasData.particleDataTrack();

      Builder b = new Builder("", "");

      return b.build();

   }

}
