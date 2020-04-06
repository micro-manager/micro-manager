

package org.micromanager.pointandshootanalysis.data;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.alg.filter.blur.BlurImageOps;
import boofcv.alg.misc.GImageStatistics;
import boofcv.alg.misc.GPixelMath;
import boofcv.core.image.ConvertImage;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import georegression.struct.point.Point2D_I32;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.imageprocessing.BoofCVUtils;
import static org.micromanager.pointandshootanalysis.PointAndShootAnalyzer.findMinPixel;
import org.micromanager.pointandshootanalysis.algorithm.BinaryListOps;
import org.micromanager.pointandshootanalysis.algorithm.CircleMask;
import org.micromanager.pointandshootanalysis.algorithm.ContourStats;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.imageanalysis.BoofCVImageConverter;

/**
 * Stores a binary mask for a particle
 * 
 * @author nico
 */
public class ParticleData {
   private final List<Point2D_I32> mask_; // mask for particle excluding bleach spot
   private List<Point2D_I32> bleachMask_;  // mask for bleach spot
   private List<Point2D_I32> maskIncludingBleach_; // mask for particle including bleachSpot
   private Point2D_I32 centroid_;
   private Point2D_I32 bleachSpot_;
   private final int threshold_;
   private Double maskAverage_;
   private Double normalizedMaskAverage_;
   private Double bleachMaskAverage_;
   private Double normalizedBleachMaskAverage_;
   private Double maskIncludingBleachAverage_;
   private Double normalizedMaskIncludingBleachAverage_;
   
   private final static double BLEACHRATIO = 0.625; // max value for minimum pixel intensity divided
   // by average of a square ROI around the particle for it to be considered a bleach spot
   
   /**
    * Applies the offset to each point in the list
    * 
    * @param mask Input list of points
    * @param offset Offset to be applied
    * @param override Returns the input list with the offset applied to each
    *                   element when true, otherwise create a new list
    * 
    * @return List with input points offset by the given values
    */
   public static List<Point2D_I32> offset(List<Point2D_I32> mask, 
           final Point2D_I32 offset, final boolean override) {
      List<Point2D_I32> output;
      if (override) {
         output = mask;
         for (Point2D_I32 p : mask) {
            p.set(p.x + offset.x, p.y + offset.y);
         }
      } else {
         output = new ArrayList<>();
         for (Point2D_I32 p : mask) {
            output.add(new Point2D_I32(p.x + offset.x, p.y + offset.y));
         }
      }
      return output;
   }
   
   public ParticleData(List<Point2D_I32> mask, int threshold) {
      mask_ = mask;
      threshold_ = threshold;
   }
   
   public ParticleData(List<Point2D_I32> mask, int threshold, Point2D_I32 offset,  Double avg) {
      mask_ = offset(mask, offset, true);
      threshold_ = threshold;
      maskIncludingBleach_ = mask_;
      maskAverage_ = avg;
      maskIncludingBleachAverage_ = avg;
   }
   
   private ParticleData(List<Point2D_I32> mask, 
           int threshold,
           List<Point2D_I32> bleachMask,
           List<Point2D_I32> maskIncludingBleach, 
           Point2D_I32 centroid,
           Point2D_I32 bleachSpot,
           Double maskAverage,
           Double bleachMaskAverage,
           Double maskIncludingBleachAverage) {
      mask_ = mask;
      threshold_ = threshold;
      bleachMask_ = bleachMask;
      maskIncludingBleach_ = maskIncludingBleach;
      centroid_ = centroid;
      bleachSpot_ = bleachSpot;
      maskAverage_ = maskAverage;
      bleachMaskAverage_ = bleachMaskAverage;
      maskIncludingBleachAverage_ = maskIncludingBleachAverage; 
   }
   
    private ParticleData(List<Point2D_I32> mask, 
           int threshold,
           List<Point2D_I32> bleachMask,
           List<Point2D_I32> maskIncludingBleach, 
           Point2D_I32 centroid,
           Point2D_I32 bleachSpot,
           Double maskAverage,
           Double normalizedMaskAverage,
           Double bleachMaskAverage,
           Double normalizedBleachMaskAverage,
           Double maskIncludingBleachAverage,
           Double normalizedMaskIncludingBleachAverage) {
      mask_ = mask;
      threshold_ = threshold;
      bleachMask_ = bleachMask;
      maskIncludingBleach_ = maskIncludingBleach;
      centroid_ = centroid;
      bleachSpot_ = bleachSpot;
      maskAverage_ = maskAverage;
      normalizedMaskAverage_ = normalizedMaskAverage;
      bleachMaskAverage_ = bleachMaskAverage;
      normalizedBleachMaskAverage_ = normalizedBleachMaskAverage;
      maskIncludingBleachAverage_ = maskIncludingBleachAverage; 
      normalizedMaskIncludingBleachAverage_ = normalizedMaskIncludingBleachAverage;
   }
   
   /**
    * 
    * @return 
    */
   public ParticleData deepCopy() {
      return new ParticleData( 
              this.mask_ != null ? new ArrayList<>(this.mask_) : null, 
              threshold_,
              this.bleachMask_ != null? new ArrayList<>(this.bleachMask_) : null, 
              this.maskIncludingBleach_ != null ? new ArrayList<>(this.maskIncludingBleach_) : null, 
              this.centroid_ != null ? new Point2D_I32(this.centroid_) : null, 
              this.bleachSpot_ != null ? new Point2D_I32(this.bleachSpot_) : null,
              this.maskAverage_ != null ? maskAverage_ : null,
              this.normalizedBleachMaskAverage_ != null ? normalizedBleachMaskAverage_ : null,
              this.bleachMaskAverage_ != null ? bleachMaskAverage_ : null,
              this.normalizedBleachMaskAverage_ != null ? normalizedBleachMaskAverage_ : null,
              this.maskIncludingBleachAverage_ != null ? maskIncludingBleachAverage_ : null,
              this.normalizedMaskIncludingBleachAverage_ != null ? 
                      normalizedMaskIncludingBleachAverage_ : null);
   }
   
    public ParticleData copy() {
      return new ParticleData( 
              this.mask_, 
              this.threshold_,
              this.bleachMask_, 
              this.maskIncludingBleach_, 
              this.centroid_, 
              this.bleachSpot_,
              this.maskAverage_,
              this.normalizedBleachMaskAverage_,
              this.bleachMaskAverage_,
              this.normalizedBleachMaskAverage_,
              this.maskIncludingBleachAverage_,
              this.normalizedMaskIncludingBleachAverage_ );
   }
   
   /**
    * Lazy calculation of centroid.
    * 
    * @return Centroid of this particle 
    */
   public Point2D_I32 getCentroid() {
      if (centroid_ == null) {
         centroid_ = ContourStats.centroid(mask_);
      }
      return centroid_;
   }
   
   public void setBleachSpot(Point2D_I32 bleachSpot) {
      bleachSpot_ = bleachSpot;
   }
   
   public Point2D_I32 getBleachSpot() {
      return bleachSpot_;
   }
   
   public List<Point2D_I32> getMask() {
      return mask_;
   }
   
   public int getThreshold() {
      return threshold_;
   }
   
   public List<Point2D_I32> getBleachMask() {
      return bleachMask_;
   }
   
   public List<Point2D_I32> getMaskIncludingBleach() {
      return maskIncludingBleach_;
   }
   
   public Double getMaskAvg() {
      return maskAverage_;
   }
   
   public Double getNormalizedMaskAvg() {
      return normalizedMaskAverage_;
   }
   
   public void setNormalizedMaskAvg(double avg) {
      normalizedMaskAverage_ = avg;
   }
   
   public Double getBleachMaskAvg() {
      return bleachMaskAverage_;
   }
   
   public Double getNormalizedBleachMaskAvg() {
      return normalizedBleachMaskAverage_;
   }
   
   public void setNormalizedBleachMaskAvg(double avg) {
      normalizedBleachMaskAverage_ = avg;
   }
   
   public Double getMaskIncludingBleachAvg() {
      return maskIncludingBleachAverage_;
   }
   
   public Double getNormalizedMaskIncludingBleachAvg() {
      return normalizedMaskIncludingBleachAverage_;
   }
   
   public void setNormalizedMaskIncludingBleachAvg(double avg) {
      normalizedMaskIncludingBleachAverage_ = avg;
   }
   
   
   /**
    * Finds the centroid of the particle closest to the given input coordinates
    * 
    * @param dp Micro-Manager data source
    * @param cb Micro-Manager Coords builder.  For efficiency only
    * @param frame Frame number in which to look for the particle centroid
    * @param startCenter input xy position around which to look
    * @param halfBoxSize Defines size of the Box in which the code looks for a particle
    *        Box is p.x - halfBoxSize, p.y - halfBoxSize; p.x + halfBoxSize, p.y + halfBoxSize
    * 
    * @return particle (or null if not found)
    * @throws IOException 
    */
   public static ParticleData centralParticle(final DataProvider dp, 
           final Coords.Builder cb,
           final int frame, 
           final Point2D_I32 startCenter,
           final int halfBoxSize) throws IOException {

      ImageGray sub = BoofCVImageConverter.subImage(dp, cb, frame, startCenter, halfBoxSize);
      if (sub == null) {
         return null;
      }
      GrayU8 mask = new GrayU8(sub.width, sub.height);      
            
      int threshold = BoofCVUtils.compressedMaxEntropyThreshold(sub, 256);
      GThresholdImageOps.threshold(sub, mask, threshold, false);
      
      // Remove small particles
      mask = BinaryImageOps.erode4(mask, 1, null);
      mask = BinaryImageOps.dilate4(mask, 1, null);
      
      // Fill hole caused by bleaching.  We could be more sophisticated and only
      // do this when needed, but how do we find out?
      // mask = BinaryImageOps.dilate8(mask, 2, null);
      // mask = BinaryImageOps.erode8(mask, 2, null);
      
      
      GrayS32 contourImg = new GrayS32(sub.width, sub.height);
      List<Contour> contours = BinaryImageOps.contour(mask, ConnectRule.FOUR, contourImg);
      List<List<Point2D_I32>> clusters = BinaryImageOps.labelToClusters(
              contourImg, contours.size(), null);
      List<ParticleData> particles = new ArrayList<>();
      GrayU16 originalImage = (GrayU16) sub;
      clusters.forEach((cluster) -> {
         double avg = averageIntensity(originalImage, cluster, null);
         particles.add(new ParticleData(cluster, threshold,
                 new Point2D_I32(startCenter.x - halfBoxSize, startCenter.y - halfBoxSize),
                 avg));
      });
      if (particles.isEmpty()) {
         // TODO: not good, log
         return null;
      }
      
      return (ContourStats.nearestParticle(startCenter, particles));
     
   }
    
   /**
    * 
    * @param preBleach
    * @param current
    * @param track - track with ParticleData used to look back in time
    *                Should not be modified!
    * @param frame  - Frame that the code is currently working on
    * @param particle - Particle for the current frame. BleachSpot will be added to it
    * @param bleachSpotRadius - Radius for the bleachSpot
    * @param offset
    * @param maxDistance
    * @return 
    */
   public static ParticleData addBleachSpotToParticle(
           GrayF32 preBleach, 
           GrayU16 current, 
           Map<Integer, ParticleData> track, 
           int frame,
           ParticleData particle, 
           Point2D_I32 offset, 
           int bleachSpotRadius, 
           double maxDistance) {
      
      final int particleSizeCutoff = 96;
      // if particle is too small, draw the bleach spot at the centroid
      // whole particle
      if (particle.mask_.size() < particleSizeCutoff) {
         List<Point2D_I32> bleachMask = getBleachMask(particle.getCentroid(), bleachSpotRadius);
         Double avg = averageIntensity(current, bleachMask, offset);
         return new ParticleData(particle.getMask(), 
                 particle.getThreshold(), 
                 bleachMask,
                 bleachMask, 
                 particle.getCentroid(), 
                 particle.getCentroid(), 
                 avg, 
                 avg, 
                 avg);
      }
      
      // divide the current mask by the prebleach mask, blur, find the minimum pixel and hope it is the bleach spot
      GrayF32 fCurrent = new GrayF32(current.getWidth(), current.getHeight());
      ConvertImage.convert((GrayU16) current, fCurrent);
      GrayF32 dResult = new GrayF32(preBleach.width, preBleach.height);
      GPixelMath.divide(fCurrent, preBleach, dResult);
      GrayF32 gResult = new GrayF32(dResult.width, dResult.height);
      BlurImageOps.gaussian(dResult, gResult, 3, -1, null);
      Point2D_I32 minPixel = findMinPixel(gResult);
      
      
      
      // Make sure that this is not an abberrant bleachSpot by checking the position
      // of previous bleachspots.  If it moved too far away (normalized by the centroid
      // of the particle) reposition to the original bleachspot position
      int nrBack = 1;
      ParticleData previousParticle = null;
      boolean previousBleachFound = false;
      while ( !previousBleachFound && 
              nrBack < 10 && 
              (frame - nrBack) > 0) {
         previousParticle = track.get(frame - nrBack);
         nrBack++;

         if (previousParticle != null && previousParticle.getBleachSpot() != null) {
            double distance = particle.getCentroid().distance(previousParticle.getCentroid());
            distance = distance < 1.0 ? 1.0 : distance;
            if (minPixel.distance(new Point2D_I32(previousParticle.getBleachSpot().getX() - offset.x,
                    previousParticle.getBleachSpot().getY() - offset.y)) > 2 * distance) {
               minPixel = new Point2D_I32(
                       previousParticle.getBleachSpot().getX() + particle.getCentroid().getX()
                       - previousParticle.getCentroid().getX() - offset.x,
                       previousParticle.getBleachSpot().getY() + particle.getCentroid().getY()
                       - previousParticle.getCentroid().getY() - offset.y
               );
            }
            previousBleachFound = true;
         }
      }

      if (minPixel.distance(new Point2D_I32(gResult.width / 2, gResult.height / 2)) 
              < maxDistance) {
         double mean = GImageStatistics.mean(gResult);
         double value = gResult.get(minPixel.x, minPixel.y);
         // TODO: evaluate this ratio and add other criteria to determine if this is
         // really the bleach spot
         boolean isRealBleach = (value / mean) < BLEACHRATIO;
         if (previousParticle != null && previousParticle.getMaskAvg() != null) {
            isRealBleach = isRealBleach && (value / previousParticle.getMaskAvg()) < 0.9;
         }
         if (isRealBleach) {
            Point2D_I32 bleachPoint = 
                    new Point2D_I32(minPixel.x + offset.x, minPixel.y + offset.y);
            return addBleachSpotToParticle(particle, current, offset, 
                           bleachPoint, bleachSpotRadius);
         }
      }      
      return particle;
   }
   
   public static ParticleData addBleachSpotToParticle(
           ParticleData particle,            
           GrayU16 current,            
           Point2D_I32 offset, 
           Point2D_I32 bleachPoint,
           int bleachSpotRadius) {
      List<Point2D_I32> bleachMask = getBleachMask(bleachPoint, bleachSpotRadius);
     
            List<Point2D_I32> mask = particle.getMask();
            
            // remove bleached pixels from the mask
            for (Point2D_I32 pixel : bleachMask) {
               if (mask.contains(pixel)) {
                  mask.remove(pixel);
               }
            }
            List<Point2D_I32> maskIncludingBleach = BinaryListOps.setToList(
                    BinaryListOps.combineSets(
                            BinaryListOps.listToSet(mask), 
                            BinaryListOps.listToSet(bleachMask)));
            Double maskAvg = averageIntensity(current, mask, offset);
            Double bleachMaskAvg = averageIntensity(current, bleachMask, offset);
            Double maskIncludingBleachAvg = averageIntensity(current, 
                    maskIncludingBleach, offset);
            
            // TODO: fill holes in maskIncludingBleach            
            Point2D_I32 newCentroid = ContourStats.centroid(maskIncludingBleach);
            return new ParticleData(mask, particle.getThreshold(), bleachMask,
                     maskIncludingBleach, newCentroid, bleachPoint,
                     maskAvg, bleachMaskAvg, maskIncludingBleachAvg);
   }
   
   
   private static List<Point2D_I32> getBleachMask(Point2D_I32 center, int radius) {
      CircleMask cm = new CircleMask(radius);
      Set<Point2D_I32> bleachSet = new HashSet<>();
      bleachSet.add(center);
      for (int x = 0; x < cm.getMask().length; x++) {
         for (int y = 0; y < cm.getMask()[x].length; y++) {
            if (cm.getMask()[x][y]) {
               bleachSet.add(new Point2D_I32(center.x + x, center.y + y));
               bleachSet.add(new Point2D_I32(center.x + x, center.y - y));
               bleachSet.add(new Point2D_I32(center.x - x, center.y + y));
               bleachSet.add(new Point2D_I32(center.x - x, center.y - y));
            }
         }
      }
      List<Point2D_I32> bleachMask = new ArrayList<>();
      bleachSet.forEach((pixel) -> {
         bleachMask.add(pixel);
      });

      return bleachMask;
   }

   
   private static Double averageIntensity(GrayU16 originalImage, 
           List<Point2D_I32> cluster, Point2D_I32 offset) {
      try {
         long sum = 0;
         if (offset == null) {
            for (Point2D_I32 p : cluster) {
               sum += originalImage.unsafe_get(p.x, p.y) & 0xffff;
            }
         } else {
            for (Point2D_I32 p : cluster) {
               sum += originalImage.unsafe_get(p.x- offset.x, p.y- offset.y) & 0xffff;
            }
         }
         return ((double) sum / (double) cluster.size());
      } catch (ArrayIndexOutOfBoundsException aie) {
         System.out.println("Programming error");
      }
      return null;
   }
   
}
