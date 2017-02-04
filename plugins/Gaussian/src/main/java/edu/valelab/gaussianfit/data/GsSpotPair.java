
package edu.valelab.gaussianfit.data;

import java.awt.geom.Point2D;

/**
 *
 * @author nico
 */
public class GsSpotPair {
   private final SpotData fgs_;
   private final Point2D.Double fp_;
   private final Point2D.Double sp_;

   public GsSpotPair(SpotData fgs, Point2D.Double fp, Point2D.Double sp) {
      fgs_ = fgs;
      fp_ = fp;
      sp_ = sp;
   }

   public SpotData getGSD() {
      return fgs_;
   }

   public Point2D.Double getfp() {
      return fp_;
   }

   public Point2D.Double getsp() {
      return sp_;
   }
   
   public GsSpotPair copy() {
      return new GsSpotPair(fgs_, fp_, sp_);
   }
   
}
