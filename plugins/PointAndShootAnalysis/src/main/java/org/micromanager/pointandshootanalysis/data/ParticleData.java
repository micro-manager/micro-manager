
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
import java.util.Set;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import static org.micromanager.pointandshootanalysis.PointAndShootAnalyzer.findMinPixel;
import org.micromanager.pointandshootanalysis.algorithm.BinaryListOps;
import org.micromanager.pointandshootanalysis.algorithm.CircleMask;
import org.micromanager.pointandshootanalysis.algorithm.ContourStats;
import org.micromanager.pointandshootanalysis.algorithm.ThresholdImageOps;
import org.micromanager.pointandshootanalysis.algorithm.Utils;

/**
 * Stores a binary mask for a particle
 * 
 * @author nico
 */
public class ParticleData {
   private final List<Point2D_I32> mask_;
   private List<Point2D_I32> bleachMask_;
   private List<Point2D_I32> maskIncludingBleach_;
   private Point2D_I32 centroid_;
   private Point2D_I32 bleachSpot_;
   private Double maskAverage_;
   private Double bleachMaskAverage_;
   private Double maskIncludingBleachAverage_;
   
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
   
   public ParticleData(List<Point2D_I32> mask) {
      mask_ = mask;
   }
   
   public ParticleData(List<Point2D_I32> mask, Point2D_I32 offset, Double avg) {
      mask_ = offset(mask, offset, true);
      maskAverage_ = avg;
   }
   
   private ParticleData(List<Point2D_I32> mask, 
           List<Point2D_I32> bleachMask,
           List<Point2D_I32> maskIncludingBleach, 
           Point2D_I32 centroid,
           Point2D_I32 bleachSpot,
           Double maskAverage,
           Double bleachMaskAverage,
           Double maskIncludingBleachAverage) {
      mask_ = mask;
      bleachMask_ = bleachMask;
      maskIncludingBleach_ = maskIncludingBleach;
      centroid_ = centroid;
      bleachSpot_ = bleachSpot;
      maskAverage_ = maskAverage;
      bleachMaskAverage_ = bleachMaskAverage;
      maskIncludingBleachAverage_ = maskIncludingBleachAverage; 
   }
   
   /**
    * 
    * @return 
    */
   public ParticleData deepCopy() {
      return new ParticleData( 
              this.mask_ != null ? new ArrayList<>(this.mask_) : null, 
              this.bleachMask_ != null? new ArrayList<>(this.bleachMask_) : null, 
              this.maskIncludingBleach_ != null ? new ArrayList<>(this.maskIncludingBleach_) : null, 
              this.centroid_ != null ? new Point2D_I32(this.centroid_) : null, 
              this.bleachSpot_ != null ? new Point2D_I32(this.bleachSpot_) : null,
              this.maskAverage_ != null ? maskAverage_ : null,
              this.bleachMaskAverage_ != null ? bleachMaskAverage_ : null,
              this.maskIncludingBleachAverage_ != null ? maskIncludingBleachAverage_ : null);
   }
   
    public ParticleData copy() {
      return new ParticleData( 
              this.mask_, 
              this.bleachMask_, 
              this.maskIncludingBleach_, 
              this.centroid_, 
              this.bleachSpot_,
              this.maskAverage_,
              this.bleachMaskAverage_,
              this.maskIncludingBleachAverage_);
   }
   
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
   
   public List<Point2D_I32> getBleachMask() {
      return bleachMask_;
   }
   
   public List<Point2D_I32> getMaskIncludingBleach() {
      return maskIncludingBleach_;
   }
   
   public Double getMaskAvg() {
      return maskAverage_;
   }
   
   public Double getBleachMaskAvg() {
      return bleachMaskAverage_;
   }
   
   public Double getMaskIncludingBleachAvg() {
      return maskIncludingBleachAverage_;
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
      int threshold = (int) ThresholdImageOps.computeLi(sub, 
              0.0, (double)sub.getImageType().getDataType().getMaxValue());
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
         particles.add(new ParticleData(cluster, 
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
    * @param particle
    * @param offset
    * @param maxDistance
    * @return 
    */
   public static ParticleData addBleachSpotToParticle(GrayF32 preBleach, 
           GrayU16 current, ParticleData particle, Point2D_I32 offset, double maxDistance) {
      final int particleSizeCutoff = 16;
      // if particle is too small, simply assume that the bleachspot covers the
      // whole particle
      if (particle.mask_.size() < particleSizeCutoff) {
         Double avg = averageIntensity(current, particle.getMask(), offset);
         return new ParticleData(particle.getMask(), particle.getMask(),
                 particle.getMask(), particle.getCentroid(), 
                 particle.getCentroid(), avg, avg, avg);
      }
      
      GrayF32 fCurrent = new GrayF32(current.getWidth(), current.getHeight());
      ConvertImage.convert((GrayU16) current, fCurrent);
      GrayF32 dResult = new GrayF32(preBleach.width, preBleach.height);
      GPixelMath.divide(fCurrent, preBleach, dResult);
      GrayF32 gResult = new GrayF32(dResult.width, dResult.height);
      BlurImageOps.gaussian(dResult, gResult, 3, -1, null);
      Point2D_I32 minPixel = findMinPixel(gResult);
      
      if (Utils.distance(minPixel, new Point2D_I32(gResult.width / 2, gResult.height / 2)) 
              < maxDistance) {
         double mean = GImageStatistics.mean(gResult);
         double value = gResult.get(minPixel.x, minPixel.y);
         // TODO: evaluate this ratio and add other criteria to determine if this is
         // really the bleach spot
         if ( (value / mean) < 0.5) {
            Point2D_I32 bleachPoint = 
                    new Point2D_I32(minPixel.x + offset.x, minPixel.y + offset.y);
            CircleMask cm = new CircleMask(3);
            Set<Point2D_I32> bleachSet = new HashSet<>();
            bleachSet.add(bleachPoint);
            for (int x = 0; x < cm.getMask().length; x++) {
               for (int y = 0; y < cm.getMask()[x].length; y++) {
                  if (cm.getMask()[x][y]) {
                     bleachSet.add(new Point2D_I32(bleachPoint.x + x, bleachPoint.y + y));
                     bleachSet.add(new Point2D_I32(bleachPoint.x + x, bleachPoint.y - y));
                     bleachSet.add(new Point2D_I32(bleachPoint.x - x, bleachPoint.y + y));
                     bleachSet.add(new Point2D_I32(bleachPoint.x - x, bleachPoint.y - y));
                  }
               }
            }
            List<Point2D_I32> bleachMask = new ArrayList<>();
            bleachSet.forEach((pixel) -> {
               bleachMask.add(pixel);
            });
            //bleachMask = ParticleData.offset(bleachMask, offset, false);
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
            return new ParticleData(mask, bleachMask,
                     maskIncludingBleach, newCentroid, particle.getBleachSpot(),
                     maskAvg, bleachMaskAvg, maskIncludingBleachAvg);
            
         }
      }
      
      return particle;
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
