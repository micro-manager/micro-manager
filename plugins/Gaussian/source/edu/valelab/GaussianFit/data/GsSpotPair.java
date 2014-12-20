
package edu.valelab.GaussianFit.data;

import java.awt.geom.Point2D;

/**
 *
 * @author nico
 */
public class GsSpotPair {
   private final GaussianSpotData fgs_;
   private final Point2D.Double fp_;
   private final Point2D.Double sp_;

   public GsSpotPair(GaussianSpotData fgs, Point2D.Double fp, 
           Point2D.Double sp) {
      fgs_ = fgs;
      fp_ = fp;
      sp_ = sp;
   }

   public GaussianSpotData getGSD() {
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
