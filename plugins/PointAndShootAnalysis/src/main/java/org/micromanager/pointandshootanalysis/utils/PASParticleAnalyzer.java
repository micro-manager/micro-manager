
package org.micromanager.pointandshootanalysis.utils;

import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.ImageStatistics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nico
 */
public class PASParticleAnalyzer extends ParticleAnalyzer {

   public class ParticleData {
      private final Point centroid_;
      private final int area_;

      public ParticleData(Point centroid, int area) {
         centroid_ = centroid;
         area_ = area;
      }

      public Point getCentroid() {
         return centroid_;
      }

      public Integer getArea() {
         return area_;
      }

   }

   List<ParticleData> lData_ = new ArrayList<>();

   /**
    * Constructs a ParticleAnalyzer using the default min and max circularity values (0 and 1).
    */
   public PASParticleAnalyzer(int options, int measurements, ResultsTable rt, double minSize,
                              double maxSize) {
      super(options, measurements, rt, minSize, maxSize, 0.0, 1.0);
   }

   @Override
   protected void saveResults(ImageStatistics stats, Roi roi) {
      lData_.add(new ParticleData(
            new Point((int) Math.round(stats.xCentroid), (int) Math.round(stats.yCentroid)),
            (int) stats.area));
   }

   public void clearData() {
      lData_.clear();
   }

   public List<ParticleData> getData() {
      return lData_;
   }
}
