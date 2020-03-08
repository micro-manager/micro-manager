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

package org.micromanager.magellan.internal.surfacesandregions;

import org.micromanager.magellan.internal.acq.MagellanGUIAcquisitionSettings;
import org.micromanager.magellan.internal.coordinates.MagellanAffineUtils;
import org.micromanager.magellan.internal.coordinates.XYStagePosition;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;
import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.PolygonsSet;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.ConvexHull2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.MonotoneChain;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.apache.commons.math3.geometry.partitioning.RegionFactory;

/**
 *
 * @author Henry
 */
public abstract class SurfaceInterpolator extends XYFootprint {
   
   public static final int NUM_XY_TEST_POINTS = 8;
  
   private static final int ABOVE_SURFACE = 0;
   private static final int BELOW_SURFACE = 1; 
   
   //surface coordinates are neccessarily associated with the coordinate space of particular xy and z devices
   private final String zDeviceName_;
   private final boolean towardsSampleIsPositive_;
   protected volatile TreeSet<Point3d> points_;
   private MonotoneChain mChain_;
   private final RegionFactory<Euclidean2D> regionFacotry_ = new RegionFactory<Euclidean2D>();
   protected volatile Vector2D[] convexHullVertices_;
   protected volatile Region<Euclidean2D> convexHullRegion_;
   private volatile int numRows_, numCols_;
   private volatile List<XYStagePosition> xyPositions_;
   protected volatile double boundXMin_, boundXMax_, boundYMin_, boundYMax_;
   protected volatile int boundXPixelMin_,boundXPixelMax_,boundYPixelMin_,boundYPixelMax_;
   protected volatile int minPixelsPerInterpPoint_ = 1;
   private ExecutorService executor_; 
   protected volatile SingleResolutionInterpolation currentInterpolation_;
   private volatile Future currentInterpolationTask_;
   //Objects for wait/notify sync of calcualtions
   protected Object xyPositionLock_ = new Object(), interpolationLock_ = new Object(), convexHullLock_ = new Object();
 
   
   public SurfaceInterpolator(String xyDevice, String zDevice) {    
      super(xyDevice);
      name_ = manager_.getNewSurfaceName();
      zDeviceName_ = zDevice;
      //store points sorted by z coordinate to easily find the top, for generating slice index 0 position
      points_ = new TreeSet<Point3d>(new Comparator<Point3d>() {
         @Override
         public int compare(Point3d p1, Point3d p2) {
            if (p1.z > p2.z) {
               return 1;
            } else if (p1.z < p2.z) {
               return -1;
            } else {
               if (p1.x > p2.x) {
                  return 1;
               } else if (p1.x < p2.x) {
                  return -1;
               } else {
                  if (p1.y > p2.y) {
                     return 1;
                  } else if (p1.y < p2.y) {
                     return -1;
                  } else {
                     return 0;
                  }
               }
            }
         }
      });
      mChain_ = new MonotoneChain(true);      
      executor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Interpolation calculation thread ");
         }
      });    
      try {
         int dir = Magellan.getCore().getFocusDirection(zDevice);
         if (dir > 0) {
            towardsSampleIsPositive_ = true;
         } else if (dir < 0) {
             towardsSampleIsPositive_ = false;
         } else {
            throw new Exception();
         }
      } catch (Exception e) {
         Log.log("Couldn't get focus direction of Z drive. Configre using Tools--Hardware Configuration Wizard");
         throw new RuntimeException();
      }

   }
   
   public int getMinPixelsPerInterpPoint() {
      return minPixelsPerInterpPoint_;
   }
   
   public String getZDevice() {
      return zDeviceName_;
   }
     
   public void delete() {
      executor_.shutdownNow();
      synchronized(convexHullLock_){
         convexHullLock_.notifyAll();
      }
      synchronized (interpolationLock_) {
         interpolationLock_.notifyAll();
      }
      synchronized (xyPositionLock_) {
         xyPositionLock_.notifyAll();
      }
   }
   
   /**
    * Returns the number of XY positions spanned by the footprint of this surface
    * @return 
    */
   public int getNumPositions() {
      if (xyPositions_ == null) {
         return -1;
      }
      return xyPositions_.size();
   }

   /**
    * Blocks until convex hull vertices have been calculated
    * @return
    * @throws InterruptedException 
    */
   public Vector2D[] getConvexHullPoints() {
      // block until convex hull points available
      synchronized (convexHullLock_) {
         while (convexHullVertices_ == null) {
            try {
               convexHullLock_.wait();
            } catch (InterruptedException ex) {
               throw new RuntimeException();
            }
         }
         return convexHullVertices_;
      }
   }

   @Override
   public List<XYStagePosition> getXYPositionsNoUpdate() {
      return xyPositions_;
   }

   
   @Override
   public List<XYStagePosition> getXYPositions(double overlap) throws InterruptedException {
      synchronized (xyPositionLock_) {
         updateXYPositionsOnly(overlap);
         while (xyPositions_ == null) {
            xyPositionLock_.wait();
         }
         return xyPositions_;
      }
   }
   
   public SingleResolutionInterpolation getCurentInterpolation() {
      return currentInterpolation_;
   }
    
   public SingleResolutionInterpolation waitForCurentInterpolation() throws InterruptedException {
      synchronized (interpolationLock_) {
         if (currentInterpolation_ == null) {
            while (currentInterpolation_ == null) {
               interpolationLock_.wait();
            }
            return currentInterpolation_;
         }
         return currentInterpolation_;
      }
   }
   
   public boolean isDefinedAtPosition(XYStagePosition position) {
      //create square region correpsonding to stage pos
      Region<Euclidean2D> square = getStagePositionRegion(position);
      //if convex hull and position have no intersection, delete
      Region<Euclidean2D> intersection = regionFacotry_.intersection(square, convexHullRegion_);
      if (intersection.isEmpty()) {
         return false;
      } else {
         return true;
      }
   }

   /**
    * tests whether any part of XY stage position is lies above the interpolated surface
    * 
    * @return true if every part of position is above surface, false otherwise
    */
   public boolean isPositionCompletelyAboveSurface(XYStagePosition pos, SurfaceInterpolator surface, double zPos, boolean extrapolate) {
      return testPositionRelativeToSurface(pos, surface, zPos, ABOVE_SURFACE, extrapolate);
   }
  
   /**
    * tests whether any part of XY stage position is lies above the interpolated surface
    *
    * @return true if every part of position is above surface, false otherwise
    */
   public boolean isPositionCompletelyBelowSurface(XYStagePosition pos, SurfaceInterpolator surface, double zPos, boolean extrapolate ) {
      return testPositionRelativeToSurface(pos, surface, zPos, BELOW_SURFACE, extrapolate);
   }
   
   /**
    * test whether XY position is completely abve or completely below surface
    * @throws InterruptedException 
    */
   public boolean testPositionRelativeToSurface(XYStagePosition pos, SurfaceInterpolator surface, double zPos, 
           int mode, boolean extrapolate) {
      //get the corners with padding added in
      Point2D.Double[] corners = pos.getDisplayedTileCorners();
      //First check position corners before going into a more detailed set of test points
      for (Point2D.Double point : corners) {
         float interpVal;
         if (!surface.getCurentInterpolation().isInterpDefined(point.x, point.y)) {
            if (extrapolate) {
               interpVal = surface.getExtrapolatedValue(point.x, point.y);
            } else {
               continue;
            }
         } else {
            interpVal = surface.getCurentInterpolation().getInterpolatedValue(point.x, point.y);
         }
         if ((towardsSampleIsPositive_ && mode == ABOVE_SURFACE && zPos >= interpVal)
                 || (towardsSampleIsPositive_ && mode == BELOW_SURFACE && zPos <= interpVal)
                 || (!towardsSampleIsPositive_ && mode == ABOVE_SURFACE && zPos <= interpVal)
                 || (!towardsSampleIsPositive_ && mode == BELOW_SURFACE) && zPos >= interpVal) {
            return false;
         }
      }
      //then check a grid of points spanning entire position        
      //9x9 square of points to check for each position
      //square is aligned with axes in pixel space, so convert to pixel space to generate test points
      double xSpan = corners[2].getX() - corners[0].getX();
      double ySpan = corners[2].getY() - corners[0].getY();
      Point2D.Double pixelSpan = new Point2D.Double();
      AffineTransform transform = MagellanAffineUtils.getAffineTransform(0, 0);
      try {
         transform.inverseTransform(new Point2D.Double(xSpan, ySpan), pixelSpan);
      } catch (NoninvertibleTransformException ex) {
         Log.log("Problem inverting affine transform");
      }
      outerloop:
      for (double x = 0; x <= pixelSpan.x; x += pixelSpan.x / (double) NUM_XY_TEST_POINTS) {
         for (double y = 0; y <= pixelSpan.y; y += pixelSpan.y / (double) NUM_XY_TEST_POINTS) {
            //convert these abritray pixel coordinates back to stage coordinates
             double[] transformMaxtrix = new double[6];
             transform.getMatrix(transformMaxtrix);
             transformMaxtrix[4] = corners[0].getX();
             transformMaxtrix[5] = corners[0].getY();
             //create new transform with translation applied
             transform = new AffineTransform(transformMaxtrix);
            Point2D.Double stageCoords = new Point2D.Double();
            transform.transform(new Point2D.Double(x, y), stageCoords);
            //test point for inclusion of position
            float interpVal;
            if (!surface.getCurentInterpolation().isInterpDefined(stageCoords.x, stageCoords.y)) {
               if (extrapolate) {
                  interpVal = surface.getExtrapolatedValue(stageCoords.x, stageCoords.y);
               } else {
                  continue;
               }
            } else {
               interpVal = surface.getCurentInterpolation().getInterpolatedValue(stageCoords.x, stageCoords.y);
            }
            if ((towardsSampleIsPositive_ && mode == ABOVE_SURFACE && zPos >= interpVal)
                    || (towardsSampleIsPositive_ && mode == BELOW_SURFACE && zPos <= interpVal)
                    || (!towardsSampleIsPositive_ && mode == ABOVE_SURFACE && zPos <= interpVal)
                    || (!towardsSampleIsPositive_ && mode == BELOW_SURFACE) && zPos >= interpVal) {
               return false;
            }
         }
      }
      return true;
   }
   
   /**
    * Create a 2D square region corresponding to the the stage position + any extra padding
    * @param pos
    * @return 
    */
   private Region<Euclidean2D> getStagePositionRegion(XYStagePosition pos) {
       Region<Euclidean2D> square;
       Point2D.Double[] corners = pos.getDisplayedTileCorners();
      square = new PolygonsSet(0.0001, new Vector2D[]{
         new Vector2D(corners[0].x, corners[0].y),
         new Vector2D(corners[1].x, corners[1].y),
         new Vector2D(corners[2].x, corners[2].y),
         new Vector2D(corners[3].x, corners[3].y)});

      return square.checkPoint(new Vector2D(pos.getCenter().x, pos.getCenter().y)) == Region.Location.OUTSIDE ? regionFacotry_.getComplement(square) : square;
   }

   private void calculateConvexHullBounds() {
      //convert convex hull vertices to pixel offsets in an arbitrary pixel space
      AffineTransform transform = MagellanAffineUtils.getAffineTransform(0, 0);
      boundYPixelMin_ = Integer.MAX_VALUE;
      boundYPixelMax_ = Integer.MIN_VALUE; 
      boundXPixelMin_ = Integer.MAX_VALUE;
      boundXPixelMax_ = Integer.MIN_VALUE;
      boundXMin_ = Double.MAX_VALUE;
      boundXMax_ = Double.MIN_VALUE;
      boundYMin_ = Double.MAX_VALUE;
      boundYMax_ = Double.MIN_VALUE;
      for (int i = 0; i < convexHullVertices_.length; i++) {
         //calculate edges of interpolation bounding box
         //for later use by interpolating function
         boundXMin_ = Math.min(boundXMin_, convexHullVertices_[i].getX());
         boundXMax_ = Math.max(boundXMax_, convexHullVertices_[i].getX());
         boundYMin_ = Math.min(boundYMin_, convexHullVertices_[i].getY());
         boundYMax_ = Math.max(boundYMax_, convexHullVertices_[i].getY());
         //also get pixel bounds of convex hull for fitting of XY positions
         double dx = convexHullVertices_[i].getX() - convexHullVertices_[0].getX();
         double dy = convexHullVertices_[i].getY() - convexHullVertices_[0].getY();
         Point2D.Double pixelOffset = new Point2D.Double(); // pixel offset from convex hull vertex 0;
         try {
            transform.inverseTransform(new Point2D.Double(dx, dy), pixelOffset);
         } catch (NoninvertibleTransformException ex) {
            Log.log("Problem inverting affine transform");
         }
         boundYPixelMin_ = (int) Math.min(boundYPixelMin_, pixelOffset.y);
         boundYPixelMax_ = (int) Math.max(boundYPixelMax_, pixelOffset.y);
         boundXPixelMin_ = (int) Math.min(boundXPixelMin_, pixelOffset.x);
         boundXPixelMax_ = (int) Math.max(boundXPixelMax_, pixelOffset.x);
      }
   }

   protected abstract void interpolateSurface(LinkedList<Point3d> points) throws InterruptedException;
   
   /**
    * calculated ad hoc unlike interpolated values which are cached
    */
   public abstract float getExtrapolatedValue(double x, double y);

   private void fitXYPositionsToConvexHull(double overlap) throws InterruptedException {
      int fullTileWidth = (int) Magellan.getCore().getImageWidth();
      int fullTileHeight = (int) Magellan.getCore().getImageHeight();
      int overlapX = (int) (Magellan.getCore().getImageWidth() * overlap / 100);
      int overlapY = (int) (Magellan.getCore().getImageHeight() * overlap / 100);
      int tileWidthMinusOverlap = fullTileWidth - overlapX;
      int tileHeightMinusOverlap =  fullTileHeight - overlapY;
      numRows_ = (int) Math.ceil( (boundYPixelMax_ - boundYPixelMin_ ) / (double) tileHeightMinusOverlap );
      numCols_ = (int) Math.ceil( (boundXPixelMax_ - boundXPixelMin_ ) / (double) tileWidthMinusOverlap );    
      
      //take center of bounding box and create grid
      int pixelCenterX = boundXPixelMin_ + (boundXPixelMax_ - boundXPixelMin_) / 2;
      int pixelCenterY = boundYPixelMin_ + (boundYPixelMax_ - boundYPixelMin_) / 2;

      AffineTransform transform = MagellanAffineUtils.getAffineTransform( 0, 0);
      ArrayList<XYStagePosition> positions = new ArrayList<XYStagePosition>();     
      Point2D.Double gridCenterStageCoords = new Point2D.Double();
      transform.transform(new Point2D.Double(pixelCenterX, pixelCenterY), gridCenterStageCoords);
      gridCenterStageCoords.x += convexHullVertices_[0].getX();
      gridCenterStageCoords.y += convexHullVertices_[0].getY();
      //set affine transform translation relative to grid center
      double[] transformMaxtrix = new double[6];
      transform.getMatrix(transformMaxtrix);
      transformMaxtrix[4] = gridCenterStageCoords.x;
      transformMaxtrix[5] = gridCenterStageCoords.y;
      //create new transform with translation applied
      transform = new AffineTransform(transformMaxtrix);
      //add all positions of rectangle around convex hull
      for (int col = 0; col < numCols_; col++) {
         double xPixelOffset = (col - (numCols_ - 1) / 2.0) * (tileWidthMinusOverlap);
         //snaky pattern
         if (col % 2 == 0) {
             for (int row = 0; row < numRows_; row++) {
                 if (Thread.interrupted()) {
                     throw new InterruptedException();
                 }
                 double yPixelOffset = (row - (numRows_ - 1) / 2.0) * (tileHeightMinusOverlap);
                 Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
                 Point2D.Double stagePos = new Point2D.Double();
                 transform.transform(pixelPos, stagePos);
                 AffineTransform posTransform = MagellanAffineUtils.getAffineTransform( stagePos.x, stagePos.y);
                 positions.add(new XYStagePosition(stagePos, tileWidthMinusOverlap, tileHeightMinusOverlap,
                         fullTileWidth, fullTileHeight, row, col, posTransform));
             }
         } else {
            for (int row = numRows_-1; row >= 0; row--) {
                 if (Thread.interrupted()) {
                     throw new InterruptedException();
                 }
                 double yPixelOffset = (row - (numRows_ - 1) / 2.0) * (tileHeightMinusOverlap);
                 Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
                 Point2D.Double stagePos = new Point2D.Double();
                 transform.transform(pixelPos, stagePos);
                 AffineTransform posTransform = MagellanAffineUtils.getAffineTransform( stagePos.x, stagePos.y);
                 positions.add(new XYStagePosition(stagePos, tileWidthMinusOverlap, tileHeightMinusOverlap,
                         fullTileWidth, fullTileHeight, row, col, posTransform));
             }     
         }
      }
      //delete positions squares (+padding) that do not overlap convex hull
      for (int i = positions.size() - 1; i >= 0; i--) {
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
         XYStagePosition pos = positions.get(i);
         //create square region correpsonding to stage pos
         Region<Euclidean2D> square = getStagePositionRegion(pos);     
         //if convex hull and position have no intersection, delete
         Region<Euclidean2D> intersection = regionFacotry_.intersection(square, convexHullRegion_);
         if (intersection.isEmpty()) {
            positions.remove(i);
         }
         square.getBoundarySize();
      }
      if (Thread.interrupted()) {
         throw new InterruptedException();
      }
      synchronized (xyPositionLock_) {
         xyPositions_ = positions;
         xyPositionLock_.notifyAll();
      }
         
      //let manger know new parmas caluclated
      manager_.surfaceOrGridUpdated(this);
   }

      
   public synchronized void deleteAllPoints() {
      points_.clear();
   }
   
   public synchronized void deletePointsWithinZRange(double zMin, double zMax) {
      ArrayList<Point3d> toRemove = new ArrayList<Point3d>();
      for (Point3d point : points_) {
         if (point.z >= zMin && point.z <= zMax) {
            toRemove.add(point);
         }
      }
      for (Point3d point : toRemove) {
         points_.remove(point);
      }

      updateConvexHullAndInterpolate();
      manager_.surfaceOrGridUpdated(this);
   }

   /**
    * delete closest point within XY tolerance
    * @param x
    * @param y
    * @param toleranceXY radius in stage space
    */
   public synchronized void deleteClosestPoint(double x, double y, double toleranceXY, double zMin, double zMax) {
      double minDistance = toleranceXY + 1;
      Point3d minDistancePoint = null;      
      for (Point3d point : points_) {
         double distance = Math.sqrt( (x-point.x)*(x-point.x) + (y-point.y)*(y-point.y) );
         if (distance < minDistance && point.z >= zMin && point.z <= zMax) {
            minDistance = distance;
            minDistancePoint = point;
         }         
      }
      //delete if within tolerance
      if (minDistance < toleranceXY && minDistancePoint != null) {
         points_.remove(minDistancePoint);
      }

      updateConvexHullAndInterpolate(); 
      manager_.surfaceOrGridUpdated(this);
   }
   
   public synchronized void addPoint(double x, double y, double z) {
      points_.add(new Point3d(x,y,z)); //for interpolation
      updateConvexHullAndInterpolate(); 
      manager_.surfaceOrGridUpdated(this);
   }
   
   //redo XY position fitting, but dont need to reinterpolate
   private void updateXYPositionsOnly(final double overlap) {
      synchronized (xyPositionLock_) {
         xyPositions_ = null;
      }
      executor_.submit( new Runnable() {
         @Override
         public void run() {
            try {
               fitXYPositionsToConvexHull(overlap);
            } catch (InterruptedException ex) {
               //this won't happen
               return;
            }
            manager_.surfaceOrGridUpdated(SurfaceInterpolator.this);
         }         
      });
   }

   private synchronized void updateConvexHullAndInterpolate() {
      //duplicate points for use on caluclation thread
      final LinkedList<Point3d> points = new LinkedList<Point3d>(points_);
      if (currentInterpolationTask_ != null && !currentInterpolationTask_.isDone()) {
         //cancel current interpolation because interpolation points have changed, call does not block
         currentInterpolationTask_.cancel(true);
      }
      //don't want one of the get methods returning a null object thinking it has a value
      synchronized (convexHullLock_) {
         convexHullVertices_ = null;
         convexHullRegion_ = null;
      }
      synchronized (interpolationLock_) {
         currentInterpolation_ = null;
      }
      synchronized (xyPositionLock_) {
         xyPositions_ = null;
      }
      numRows_ = 0;
      numCols_ = 0;


      currentInterpolationTask_ = executor_.submit(new Runnable() {
         @Override
         public void run() {
            if (points.size() > 2) {
               try {
                  //convert xyPoints to a vector2d for convex hull calculation
                  LinkedList<Vector2D> xyPoints = new LinkedList<Vector2D>();
                  for (Point3d p : points) {
                     xyPoints.add(new Vector2D(p.x, p.y));
                  }
                  ConvexHull2D hull = null;
                   hull = mChain_.generate(xyPoints);
                  if (Thread.interrupted()) {
                     throw new InterruptedException();
                  }
                  synchronized (convexHullLock_) {
                     convexHullVertices_ = hull.getVertices();
                     convexHullLock_.notifyAll();
                  }
                  if (Thread.interrupted()) {
                     throw new InterruptedException();
                  }
                  convexHullRegion_ = hull.createRegion();
                  if (Thread.interrupted()) {
                     throw new InterruptedException();
                  }
                  calculateConvexHullBounds();
                  if (Thread.interrupted()) {
                     throw new InterruptedException();
                  }
                  //use the most recently set overlap value for display purposes. When it comes time to calc the real thing, 
                  //get it from the acquisition settings
                  fitXYPositionsToConvexHull(MagellanGUIAcquisitionSettings.getStoredTileOverlapPercentage());
                  //Interpolate surface as specified by the subclass method
                  interpolateSurface(points);
                  //let manager handle event firing to acquisitions using surface
                  manager_.surfaceOrGridUpdated(SurfaceInterpolator.this);
               } catch (InterruptedException e) {
                  return;
               }
            }
         }
      });
   }
   
   /**
    * block until a higher resolution surface is available. Might experience spurious wakeups
    * @throws InterruptedException 
    */
   public void waitForHigherResolutionInterpolation() throws InterruptedException {
      synchronized (interpolationLock_) {
         interpolationLock_.wait();
      }
   }

   public synchronized Point3d[] getPoints() {
      return points_.toArray(new Point3d[0]);
   }


   
}
