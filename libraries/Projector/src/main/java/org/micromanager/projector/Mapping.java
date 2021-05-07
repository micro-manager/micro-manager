package org.micromanager.projector;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Map;

/**
 * Class to hold information relating camera and projection device. Contains the affine transforms
 * to relate one to the other. Also contains information about the camera binning and ROI during
 * calibration that can be used to correct if the current ROI/binning is different.
 *
 * @author Nico
 */
public class Mapping {

   // Multiple Affine Transforms, each applicable within the Polygon that is its key
   private final Map<Polygon, AffineTransform> transformMap_;
   // Global Affine Transform for the whole image.  This is less precise than the transformMap_
   private final AffineTransform approximateTransform_;
   // ROI of the camera when the mapping was generated
   private final Rectangle cameraROI_;
   // Binning of the camera when the mapping was generated
   private final int cameraBinning_;

   private Mapping(Map<Polygon, AffineTransform> transformMap,
         AffineTransform approximateTransform, Rectangle cameraROI,
         int cameraBinning) {
      transformMap_ = transformMap;
      approximateTransform_ = approximateTransform;
      cameraROI_ = cameraROI;
      cameraBinning_ = cameraBinning;
   }

   public static class Builder {

      private Map<Polygon, AffineTransform> transformMap_;
      private AffineTransform approximateTransform_;
      private Rectangle cameraROI_;
      private int cameraBinning_ = 1;

      public Builder() {
      }

      public Builder setMap(Map<Polygon, AffineTransform> transformMap) {
         transformMap_ = transformMap;
         return this;
      }

      public Builder setApproximateTransform(AffineTransform transform) {
         approximateTransform_ = transform;
         return this;
      }

      public Builder setROI(final Rectangle cameraROI) {
         cameraROI_ = cameraROI;
         return this;
      }

      public Builder setBinning(final int cameraBinning) {
         cameraBinning_ = cameraBinning;
         return this;
      }

      public Mapping build() {
         return new Mapping(transformMap_, approximateTransform_, cameraROI_, cameraBinning_);
      }
   }

   public Map<Polygon, AffineTransform> getMap() {
      return transformMap_;
   }

   public AffineTransform getApproximateTransform() {
      return approximateTransform_;
   }

   public Rectangle getCameraROI() {
      return cameraROI_;
   }

   public int getBinning() {
      return cameraBinning_;
   }

}
