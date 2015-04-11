/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import acq.FixedAreaAcquisitionSettings;
import propsandcovariants.SurfaceData;
import coordinates.AffineUtils;
import coordinates.XYStagePosition;
import gui.SettingsDialog;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import imageconstruction.CoreCommunicator;
import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.PolygonsSet;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.ConvexHull2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.MonotoneChain;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.apache.commons.math3.geometry.partitioning.RegionFactory;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public abstract class SurfaceInterpolator implements XYFootprint {
   
   public static final int MIN_PIXELS_PER_INTERP_POINT = 4;
   public static final int NUM_XY_TEST_POINTS = 8;
   
   private String name_;
   //surface coordinates are neccessarily associated with the coordinate space of particular xy and z devices
   private String xyDeviceName_, zDeviceName_;
   private volatile TreeSet<Point3d> points_;
   private MonotoneChain mChain_;
   private RegionFactory<Euclidean2D> regionFacotry_ = new RegionFactory<Euclidean2D>();
   protected volatile Vector2D[] convexHullVertices_;
   protected volatile Region<Euclidean2D> convexHullRegion_;
   private volatile int numRows_, numCols_;
   private volatile List<XYStagePosition> xyPositions_;
   private volatile double xyPadding_um_ = 0;
   protected volatile double boundXMin_, boundXMax_, boundYMin_, boundYMax_;
   private volatile int boundXPixelMin_,boundXPixelMax_,boundYPixelMin_,boundYPixelMax_;
   private ExecutorService executor_; 
   protected volatile SingleResolutionInterpolation currentInterpolation_;
   private SurfaceManager manager_;
   private Future currentInterpolationTask_;
   private String pixelSizeConfig_;
   //Objects for wait/notify sync of calcualtions
   protected Object xyPositionLock_ = new Object(), interpolationLock_ = new Object(), convexHullLock_ = new Object();
   
   public SurfaceInterpolator(SurfaceManager manager, String xyDevice, String zDevice) {
      name_ = manager.getNewName();
      xyDeviceName_ = xyDevice;
      zDeviceName_ = zDevice;
      manager_ = manager;
      try {
         pixelSizeConfig_ = MMStudio.getInstance().getCore().getCurrentPixelSizeConfig();
      } catch (Exception ex) {
         ReportingUtils.showError("couldnt get pixel size config");
      }
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
   }
   
   public String getPixelSizeConfig() {
      return pixelSizeConfig_;
   }
   
   public void shutdown() {
      executor_.shutdownNow();
   }
   
   @Override
   public String toString() {
      return name_;
   }
   
   public String getName() {
      return name_;
   }
   
   public void rename(String newName) {
      name_ = newName;
      manager_.updateSurfaceTableAndCombos();
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
   
   public double getXYPadding() {
      return xyPadding_um_;
   }
   
   public void setXYPadding(double pad) {
      xyPadding_um_ = pad;
      synchronized (xyPositionLock_) {
         xyPositions_ = null;
      }
      updateXYPositionsOnly(FixedAreaAcquisitionSettings.getStoredTileOverlapPercentage());
   }

   /**
    * Blocks until convex hull vertices have been calculated
    * @return
    * @throws InterruptedException 
    */
   public Vector2D[] getConvexHullPoints() throws InterruptedException {
      // block until convex hull points available
      synchronized (convexHullLock_) {
         while (convexHullVertices_ == null) {
            convexHullLock_.wait();
         }
         return convexHullVertices_;
      }
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
   
   public SingleResolutionInterpolation waitForCurentInterpolation() throws InterruptedException {
      synchronized (interpolationLock_) {
         while (currentInterpolation_ == null) {
            interpolationLock_.wait();
         }
         return currentInterpolation_;
      }
   }
   
   public SingleResolutionInterpolation getCurrentInterpolation() {
      return currentInterpolation_;
   }

   /**
    * tests whether any part of XY stage position is lies above the interpolated surface
    * 
    * @return true if every part of position is above surface, false otherwise
    */
   public boolean isPositionCompletelyAboveSurface(XYStagePosition pos, SurfaceInterpolator surface, double zPos) {
      return testPositionRelativeToSurface(pos, surface, zPos, true);
   }
  
   /**
    * tests whether any part of XY stage position is lies above the interpolated surface
    *
    * @return true if every part of position is above surface, false otherwise
    */
   public boolean isPositionCompletelyBelowSurface(XYStagePosition pos, SurfaceInterpolator surface, double zPos ) {
      return testPositionRelativeToSurface(pos, surface, zPos, false); //no padding for testing below
   }
   
   public static boolean testPositionRelativeToSurface(XYStagePosition pos, SurfaceInterpolator surface, double zPos, boolean above) {
      //get the corners with padding added in
      Point2D.Double[] corners = getPositionCornersWithPadding(pos, surface.xyPadding_um_);
      //First check position corners before going into a more detailed set of test points
      for (Point2D.Double point : corners) {
         Double interpVal = surface.getCurrentInterpolation().getInterpolatedValue(point.x, point.y, true);
         if (interpVal == null) {
            continue;
         }
         
         if (above) { //test if point lies bleow surface + padding
            if (zPos >=  interpVal ) {   //TODO: account for different signs of Z
                return false;
            }
         } else {
            //test if point lies below surface + padding
            if (zPos <= interpVal ) {   //TODO: account for different signs of Z
               return false;
            }
         }
      }
      //then check a grid of points spanning entire position        
      //9x9 square of points to check for each position
      //square is aligned with axes in pixel space, so convert to pixel space to generate test points
      double xSpan = corners[2].getX() - corners[0].getX();
      double ySpan = corners[2].getY() - corners[0].getY();
      Point2D.Double pixelSpan = new Point2D.Double();
      AffineTransform transform = AffineUtils.getAffineTransform(surface.pixelSizeConfig_,0, 0);
      try {
         transform.inverseTransform(new Point2D.Double(xSpan, ySpan), pixelSpan);
      } catch (NoninvertibleTransformException ex) {
         ReportingUtils.showError("Problem inverting affine transform");
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
            Double interpVal = surface.getCurrentInterpolation().getInterpolatedValue(stageCoords.x, stageCoords.y, true);
            if (interpVal == null) {
               continue;
            }
            if (above) { //test if point lies bleow surface + padding
               if (zPos >= interpVal ) {   //TODO: account for different signs of Z
                  return false;
               }
            } else {
               //test if point lies below surface + padding
               if (zPos <= interpVal ) {   //TODO: account for different signs of Z
                  return false;
               }
            }

         }
      }
      return true;
   }
   
   
   
   /**
    * figure out which of the positions need to be collected at a given slice
    * Assumes positions_ contains list of all possible positions for fitting  
    * block until interpolation is detailed enough to calculate stage positions
    * @param zPos 
    */
   public ArrayList<XYStagePosition> getXYPositonsAtSlice(double zPos) throws InterruptedException {
      SingleResolutionInterpolation interp = waitForCurentInterpolation();
      double overlapPercent = FixedAreaAcquisitionSettings.getStoredTileOverlapPercentage() / 100;
      int overlapX = (int) (CoreCommunicator.getImageWidth() * overlapPercent);
      int overlapY = (int) (CoreCommunicator.getImageHeight() * overlapPercent);
      int tileWidth = (int) CoreCommunicator.getImageWidth() - overlapX;
      int tileHeight = (int) CoreCommunicator.getImageHeight() - overlapY;
      while (interp.getPixelsPerInterpPoint() >= Math.max(tileWidth,tileHeight) / NUM_XY_TEST_POINTS ) {
         synchronized (interpolationLock_) {
            interpolationLock_.wait();
         }
         interp = waitForCurentInterpolation();
      }
      ArrayList<XYStagePosition> positionsAtSlice = new ArrayList<XYStagePosition>();
      for (XYStagePosition pos : xyPositions_) {
         //TODO: support other modes of showing positions?
         if (!isPositionCompletelyAboveSurface(pos, this, zPos)) {
            positionsAtSlice.add(pos);
         }
      }

      return positionsAtSlice;
   }

   private static Point2D.Double[] getPositionCornersWithPadding(XYStagePosition pos, double xyPadding) {
      if (xyPadding == 0) {
         return pos.getDisplayedTileCorners();
      } else {
         //expand to bigger square to acount for padding
         //make two lines that criss cross the smaller square
         Point2D.Double[] corners = pos.getDisplayedTileCorners();
         double diagonalLength = new Vector2D(corners[0].x, corners[0].y).distance(new Vector2D(corners[2].x, corners[2].y));
         Vector2D center = new Vector2D(pos.getCenter().x, pos.getCenter().y);
         Point2D.Double[] paddedCorners = new Point2D.Double[4];
         Vector2D c0 = center.add(xyPadding + 0.5 * diagonalLength, new Vector2D(corners[0].x - corners[2].x, corners[0].y - corners[2].y).normalize());
         Vector2D c1 = center.add(xyPadding + 0.5 * diagonalLength, new Vector2D(corners[1].x - corners[3].x, corners[1].y - corners[3].y).normalize());
         Vector2D c2 = center.add(xyPadding + 0.5 * diagonalLength, new Vector2D(corners[2].x - corners[0].x, corners[2].y - corners[0].y).normalize());
         Vector2D c3 = center.add(xyPadding + 0.5 * diagonalLength, new Vector2D(corners[3].x - corners[1].x, corners[3].y - corners[1].y).normalize());
         paddedCorners[0] = new Point2D.Double(c0.getX(), c0.getY());
         paddedCorners[1] = new Point2D.Double(c1.getX(), c1.getY());
         paddedCorners[2] = new Point2D.Double(c2.getX(), c2.getY());
         paddedCorners[3] = new Point2D.Double(c3.getX(), c3.getY());
         return paddedCorners;
      }           
   }
   
   /**
    * Create a 2D square region corresponding to the the stage position + any extra padding
    * @param pos
    * @return 
    */
   private Region<Euclidean2D> getStagePositionRegion(XYStagePosition pos) {
       Region<Euclidean2D> square;
       Point2D.Double[] corners = pos.getDisplayedTileCorners();
      if (xyPadding_um_ == 0) {
         square = new PolygonsSet(0.0001, new Vector2D[]{
                    new Vector2D(corners[0].x, corners[0].y),
                    new Vector2D(corners[1].x, corners[1].y),
                    new Vector2D(corners[2].x, corners[2].y),
                    new Vector2D(corners[3].x, corners[3].y)});
      } else { //expand to bigger square to acount for padding
         //make two lines that criss cross the smaller square
         double diagonalLength = new Vector2D(corners[0].x, corners[0].y).distance(new Vector2D(corners[2].x, corners[2].y));
         Vector2D center = new Vector2D(pos.getCenter().x, pos.getCenter().y);
         square = new PolygonsSet(0.0001, new Vector2D[]{
                    center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(corners[0].x - corners[2].x, corners[0].y - corners[2].y).normalize()),
                    center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(corners[1].x - corners[3].x, corners[1].y - corners[3].y).normalize()),
                    center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(corners[2].x - corners[0].x, corners[2].y - corners[0].y).normalize()),
                    center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(corners[3].x - corners[1].x, corners[3].y - corners[1].y).normalize())});
      }
      return square.checkPoint(new Vector2D(pos.getCenter().x, pos.getCenter().y)) == Region.Location.OUTSIDE ? regionFacotry_.getComplement(square) : square;
   }

   private void calculateConvexHullBounds() {
      //convert convex hull vertices to pixel offsets in an arbitrary pixel space
      AffineTransform transform = AffineUtils.getAffineTransform(pixelSizeConfig_,0, 0);
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
            ReportingUtils.showError("Problem inverting affine transform");
         }
         boundYPixelMin_ = (int) Math.min(boundYPixelMin_, pixelOffset.y);
         boundYPixelMax_ = (int) Math.max(boundYPixelMax_, pixelOffset.y);
         boundXPixelMin_ = (int) Math.min(boundXPixelMin_, pixelOffset.x);
         boundXPixelMax_ = (int) Math.max(boundXPixelMax_, pixelOffset.x);
      }
   }

   protected abstract void interpolateSurface(LinkedList<Point3d> points) throws InterruptedException;

   private void fitXYPositionsToConvexHull(double overlap) throws InterruptedException {
      int fullTileWidth = CoreCommunicator.getImageWidth();
      int fullTileHeight = CoreCommunicator.getImageHeight();
      int overlapX = (int) (CoreCommunicator.getImageWidth() * overlap / 100);
      int overlapY = (int) (CoreCommunicator.getImageHeight() * overlap / 100);
      int tileWidthMinusOverlap = fullTileWidth - overlapX;
      int tileHeightMinusOverlap =  fullTileHeight - overlapY;
      int pixelPadding = (int) (xyPadding_um_ / MMStudio.getInstance().getCore().getPixelSizeUm());
      numRows_ = (int) Math.ceil( (boundYPixelMax_ - boundYPixelMin_ + pixelPadding) / (double) tileHeightMinusOverlap );
      numCols_ = (int) Math.ceil( (boundXPixelMax_ - boundXPixelMin_ + pixelPadding) / (double) tileWidthMinusOverlap );    
      
      //take center of bounding box and create grid
      int pixelCenterX = boundXPixelMin_ + (boundXPixelMax_ - boundXPixelMin_) / 2;
      int pixelCenterY = boundYPixelMin_ + (boundYPixelMax_ - boundYPixelMin_) / 2;

      AffineTransform transform = AffineUtils.getAffineTransform(pixelSizeConfig_, 0, 0);
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
         for (int row = 0; row < numRows_; row++) {
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
            double yPixelOffset = (row - (numRows_ - 1) / 2.0) * (tileHeightMinusOverlap);
            Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
            Point2D.Double stagePos = new Point2D.Double();
            transform.transform(pixelPos, stagePos);
            positions.add(new XYStagePosition(stagePos, tileWidthMinusOverlap, tileHeightMinusOverlap,
                    fullTileWidth, fullTileHeight, row, col, pixelSizeConfig_));
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
      manager_.updateSurfaceTableAndCombos();
   }
   
   /**
    * delete closest point within XY tolerance
    * @param x
    * @param y
    * @param tolerance radius in stage space
    */
   public synchronized void deleteClosestPoint(double x, double y, double tolerance) {
      double minDistance = tolerance + 1;
      Point3d minDistancePoint = null;      
      for (Point3d point : points_) {
         double distance = Math.sqrt( (x-point.x)*(x-point.x) + (y-point.y)*(y-point.y) );
         if (distance < minDistance) {
            minDistance = distance;
            minDistancePoint = point;
         }         
      }
      //delete if within tolerance
      if (minDistance < tolerance && minDistancePoint != null) {
         points_.remove(minDistancePoint);
      }

      updateConvexHullAndInterpolate(); 
      manager_.drawSurfaceOverlay(this);
   }
   
   public synchronized void addPoint(double x, double y, double z) {
      points_.add(new Point3d(x,y,z)); //for interpolation
      updateConvexHullAndInterpolate(); 
      manager_.drawSurfaceOverlay(this);
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
            manager_.drawSurfaceOverlay(SurfaceInterpolator.this);
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


      currentInterpolationTask_ = executor_.submit( new Runnable() {
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
                  fitXYPositionsToConvexHull(FixedAreaAcquisitionSettings.getStoredTileOverlapPercentage());
                  interpolateSurface(points);
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

   /**
    * return list of surface Data.
    * @return 
    */
   Collection<SurfaceData> getData() {
      ArrayList<SurfaceData> list = new ArrayList<SurfaceData>();
      for (String datumName : SurfaceData.enumerateDataTypes()) {
         try {
            list.add(new SurfaceData(this, datumName));
         } catch (Exception e) {
            //this will never happen
         }
      }
      return list;
   }

}