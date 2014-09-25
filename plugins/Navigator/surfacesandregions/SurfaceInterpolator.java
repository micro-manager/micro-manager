/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import coordinates.StageCoordinates;
import edu.mines.jtk.dsp.Sampling;
import edu.mines.jtk.interp.Gridder2;
import edu.mines.jtk.interp.SibsonGridder2;
import gui.SettingsDialog;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import javax.vecmath.Point3d;
import coordinates.AffineUtils;
import edu.mines.jtk.interp.RadialGridder2;
import edu.mines.jtk.interp.SibsonInterpolator2;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
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
public class SurfaceInterpolator {
   
   public static final int MIN_PIXELS_PER_INTERP_POINT = 1;
   
   private LinkedList<Point3d> points_;
//   private Gridder2 gridder_;
   private SibsonInterpolator2 interpolator_;
   private MonotoneChain mChain_;
   private RegionFactory<Euclidean2D> regionFacotry_ = new RegionFactory<Euclidean2D>();
   private volatile Vector2D[] convexHullVertices_;
   private Region<Euclidean2D> convexHullRegion_;
   private LinkedList<Vector2D> xyPoints_;
   private int numRows_, numCols_;
   private volatile ArrayList<StageCoordinates> xyPositions_;
   private double xyPadding_um_ = 0, zPadding_um_;
   private double boundXMin_, boundXMax_, boundYMin_, boundYMax_;
   private int boundXPixelMin_,boundXPixelMax_,boundYPixelMin_,boundYPixelMax_;
   private ExecutorService executor_; 
   private volatile Interpolation currentInterpolation_;
   private SurfaceManager manager_;
   private Future currentInterpolationTask_;
   
   public SurfaceInterpolator(SurfaceManager manager) {
      manager_ = manager;
      points_ = new LinkedList<Point3d>();
      mChain_ = new MonotoneChain(true);  
      xyPoints_ = new LinkedList<Vector2D>();
      
//      Other possible interp methods:
//      RadialGridder2 //Good interp, but time scales with number of points
//      NearestGridder2 //looks good and fast, but discrete nature might lead to problems for interp between slices
//      BlendedGridder2 //Similar to natural neighbor since euclidean distances are used
//      SibsonGridder2 //slowwwww
      
      //useful for debugging
//           gridder_ = new NearestGridder2(new float[0],new float[0],new float[0]); //fast, like nearest neighbor, complexity decreases with # samples
      
      interpolator_ = new SibsonInterpolator2(new float[]{0},new float[]{0},new float[]{0});
//      gridder_ = new SibsonGridder2(new float[]{0},new float[]{0},new float[]{0});      
//      gridder_ = new RadialGridder2(new RadialGridder2.Biharmonic(),new float[]{0},new float[]{0},new float[]{0});
         
      executor_ = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Interpolation calculation thread ");
         }
      });      
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
   
   public double getZPadding() {
      return zPadding_um_;
   }
   
   public void setZPadding(double pad) {
      zPadding_um_ = pad;
   }
   
   public double getXYPadding() {
      return xyPadding_um_;
   }
   
   public void setXYPadding(double pad) {
      xyPadding_um_ = pad;
      fitXYPositionsToConvexHull();
   }
   
   public double getWidth_um() {
      double pixelSize = MMStudio.getInstance().getCore().getPixelSizeUm();        
      int imageWidth = (int) MMStudio.getInstance().getCore().getImageWidth();
      int pixelWidth = numCols_ * (imageWidth - SettingsDialog.getOverlapX()) + SettingsDialog.getOverlapX();
      return pixelSize * pixelWidth;
   }
   
   public double getHeight_um() {
      double pixelSize = MMStudio.getInstance().getCore().getPixelSizeUm();
      int imageHeight = (int) MMStudio.getInstance().getCore().getImageHeight();
      int pixelHeight = numRows_ * (imageHeight - SettingsDialog.getOverlapY()) + SettingsDialog.getOverlapY();
      return pixelSize * pixelHeight;
   }

   public Vector2D[] getConvexHullPoints() {
      return convexHullVertices_;
   }
   
   public ArrayList<StageCoordinates> getPositions() {
      return xyPositions_;
   }
   
   public Interpolation getCurrentInterpolation() {
      return currentInterpolation_;
   }

   /**
    * figure out which of the positions need to be collected at a given slice
    * Assumes positions_ contains list of all possible positions for fitting  
    * @param zPos 
    */
   public ArrayList<StageCoordinates> getXYPositonsAtSlice(double zPos, Interpolation interp) {
      ArrayList<StageCoordinates> positionsAtSlice = new ArrayList<StageCoordinates>();
      for (StageCoordinates pos : xyPositions_) {
         //get the corners with padding added in
         Point2D.Double[] corners = getPositionCornersWithPadding(pos);
         //First check position corners 
         for (Point2D.Double point : corners) {
            Float interpVal = interp.getInterpolatedValue(point.x, point.y);
            if (interpVal == null) {
               continue;
            }
            if (interpVal <= zPos + zPadding_um_) {   //TODO: account for different signs of Z
               positionsAtSlice.add(pos);
               break;
            }
         }
         //then check a grid of points spanning entire position
         
         //9x9 square of points to check for each position
         int numTestPoints = 8;
         //square is aligned with axes in pixel space, so convert to pixel space to generate test points
         double xSpan = corners[2].getX() - corners[0].getX();
         double ySpan = corners[2].getY() - corners[0].getY();
         Point2D.Double pixelSpan = new Point2D.Double();
         AffineTransform transform = AffineUtils.getAffineTransform(0, 0);
         try {
            transform.inverseTransform(new Point2D.Double(xSpan, ySpan), pixelSpan);
         } catch (NoninvertibleTransformException ex) {
            ReportingUtils.showError("Problem inverting affine transform");
         }
         outerloop:
         for (double x = 0; x <= pixelSpan.x; x += pixelSpan.x / (double) numTestPoints) {
            for (double y = 0; y <= pixelSpan.y; y += pixelSpan.y / (double) numTestPoints) {
               //convert these abritray pixel coordinates back to stage coordinates
               transform.setToTranslation(corners[0].getX(), corners[0].getY());                          
               Point2D.Double stageCoords = new Point2D.Double();
               transform.transform(new Point2D.Double(x, y), stageCoords);
               //test point for inclusion of position
               Float interpVal = interp.getInterpolatedValue(stageCoords.x, stageCoords.y);
               if (interpVal == null) {
                  continue;
               }
               if (interpVal <= zPos + zPadding_um_) {   //TODO: account for different signs of Z?
                  positionsAtSlice.add(pos);
                  break outerloop;
               }

            }
         }
      }
      return positionsAtSlice;
   }
   
   private Point2D.Double[] getPositionCornersWithPadding(StageCoordinates pos) {
      if (xyPadding_um_ == 0) {
         return pos.corners;
      } else {
         //expand to bigger square to acount for padding
         //make two lines that criss cross the smaller square
         double diagonalLength = new Vector2D(pos.corners[0].x, pos.corners[0].y).distance(new Vector2D(pos.corners[2].x, pos.corners[2].y));
         Vector2D center = new Vector2D(pos.center.x, pos.center.y);
         Point2D.Double[] corners = new Point2D.Double[4];
         Vector2D c0 = center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(pos.corners[0].x - pos.corners[2].x, pos.corners[0].y - pos.corners[2].y).normalize());
         Vector2D c1 = center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(pos.corners[1].x - pos.corners[3].x, pos.corners[1].y - pos.corners[3].y).normalize());
         Vector2D c2 = center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(pos.corners[2].x - pos.corners[0].x, pos.corners[2].y - pos.corners[0].y).normalize());
         Vector2D c3 = center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(pos.corners[3].x - pos.corners[1].x, pos.corners[3].y - pos.corners[1].y).normalize());
         corners[0] = new Point2D.Double(c0.getX(), c0.getY());
         corners[1] = new Point2D.Double(c1.getX(), c1.getY());
         corners[2] = new Point2D.Double(c2.getX(), c2.getY());
         corners[3] = new Point2D.Double(c3.getX(), c3.getY());
         return corners;
      }           
   }
   
   /**
    * Create a 2D square region corresponding to the the stage position + any extra padding
    * @param pos
    * @return 
    */
   private Region<Euclidean2D> getStagePositionRegion(StageCoordinates pos) {
       Region<Euclidean2D> square;
      if (xyPadding_um_ == 0) {
         square = new PolygonsSet(0.0001, new Vector2D[]{
                    new Vector2D(pos.corners[0].x, pos.corners[0].y),
                    new Vector2D(pos.corners[1].x, pos.corners[1].y),
                    new Vector2D(pos.corners[2].x, pos.corners[2].y),
                    new Vector2D(pos.corners[3].x, pos.corners[3].y)});
      } else { //expand to bigger square to acount for padding
         //make two lines that criss cross the smaller square
         double diagonalLength = new Vector2D(pos.corners[0].x, pos.corners[0].y).distance(new Vector2D(pos.corners[2].x, pos.corners[2].y));
         Vector2D center = new Vector2D(pos.center.x, pos.center.y);
         square = new PolygonsSet(0.0001, new Vector2D[]{
                    center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(pos.corners[0].x - pos.corners[2].x, pos.corners[0].y - pos.corners[2].y).normalize()),
                    center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(pos.corners[1].x - pos.corners[3].x, pos.corners[1].y - pos.corners[3].y).normalize()),
                    center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(pos.corners[2].x - pos.corners[0].x, pos.corners[2].y - pos.corners[0].y).normalize()),
                    center.add(xyPadding_um_ + 0.5 * diagonalLength, new Vector2D(pos.corners[3].x - pos.corners[1].x, pos.corners[3].y - pos.corners[1].y).normalize())});
      }
      return square.checkPoint(new Vector2D(pos.center.x, pos.center.y)) == Region.Location.OUTSIDE ? regionFacotry_.getComplement(square) : square;
   }

   private void calculateConvexHullBounds() {
      //convert convex hull vertices to pixel offsets in an arbitrary pixel space
      AffineTransform transform = AffineUtils.getAffineTransform(0, 0);
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

   private void interpolateSurface(LinkedList<Point3d> points) {
      try {

         //provide interpolator with current list of data points
         float x[] = new float[points.size()];
         float y[] = new float[points.size()];
         float z[] = new float[points.size()];
         for (int i = 0; i < points.size(); i++) {
            x[i] = (float) points.get(i).x;
            y[i] = (float) points.get(i).y;
            z[i] = (float) points.get(i).z;
         }
//      gridder_.setScattered(z, x, y);
         interpolator_.setSamples(z, x, y);

         int maxPixelDimension = (int) (Math.max(boundXMax_ - boundXMin_, boundYMax_ - boundYMin_) / MMStudio.getInstance().getCore().getPixelSizeUm());
         //Start with at least 20 interp points and go smaller and smaller until every pixel interped?
         int pixelsPerInterpPoint = 1;
         while (maxPixelDimension / (pixelsPerInterpPoint + 1) > 20) {
            pixelsPerInterpPoint *= 2;
         }
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }

         while (pixelsPerInterpPoint >= MIN_PIXELS_PER_INTERP_POINT) {
            System.out.println("Interpolating, pixels per interp point: " + pixelsPerInterpPoint);
            int numInterpPointsX = (int) (((boundXMax_ - boundXMin_) / MMStudio.getInstance().getCore().getPixelSizeUm()) / pixelsPerInterpPoint);
            int numInterpPointsY = (int) (((boundYMax_ - boundYMin_) / MMStudio.getInstance().getCore().getPixelSizeUm()) / pixelsPerInterpPoint);

            //do interpolation
            Sampling xSampling = new Sampling(numInterpPointsX, (boundXMax_ - boundXMin_) / (numInterpPointsX - 1), boundXMin_);
            Sampling ySampling = new Sampling(numInterpPointsY, (boundYMax_ - boundYMin_) / (numInterpPointsY - 1), boundYMin_);
            interpolator_.setNullValue(Float.MIN_VALUE);
            interpolator_.useConvexHullBounds();
            float[][] interpVals = interpolator_.interpolate(xSampling, ySampling);
            currentInterpolation_ = new Interpolation(pixelsPerInterpPoint, interpVals, boundXMin_, boundXMax_, boundYMin_, boundYMax_, convexHullRegion_);
            pixelsPerInterpPoint /= 2;
         }
      } catch (InterruptedException e) {
      }
   }

   private void fitXYPositionsToConvexHull() {
      
      int tileWidth = (int) MMStudio.getInstance().getCore().getImageWidth() - SettingsDialog.getOverlapX();
      int tileHeight = (int) MMStudio.getInstance().getCore().getImageHeight() - SettingsDialog.getOverlapY();
      int pixelPadding = (int) (xyPadding_um_ / MMStudio.getInstance().getCore().getPixelSizeUm());
      numRows_ = (int) Math.ceil( (boundYPixelMax_ - boundYPixelMin_ + pixelPadding) / (double) tileHeight );
      numCols_ = (int) Math.ceil( (boundXPixelMax_ - boundXPixelMin_ + pixelPadding) / (double) tileWidth );    
      
      //take center of bounding box and create grid
      int pixelCenterX = boundXPixelMin_ + (boundXPixelMax_ - boundXPixelMin_) / 2;
      int pixelCenterY = boundYPixelMin_ + (boundYPixelMax_ - boundYPixelMin_) / 2;

      AffineTransform transform = AffineUtils.getAffineTransform(0, 0);
      ArrayList<StageCoordinates> positions = new ArrayList<StageCoordinates>();     
      Point2D.Double gridCenterStageCoords = new Point2D.Double();
      transform.transform(new Point2D.Double(pixelCenterX, pixelCenterY), gridCenterStageCoords);
      gridCenterStageCoords.x += convexHullVertices_[0].getX();
      gridCenterStageCoords.y += convexHullVertices_[0].getY();
      //set affine transform translation relative to grid center
      transform.setToTranslation(gridCenterStageCoords.x, gridCenterStageCoords.y);
      for (int col = 0; col < numCols_; col++) {
         double xPixelOffset = (col - (numCols_ - 1) / 2.0) * (tileWidth);
         for (int row = 0; row < numRows_; row++) {
            double yPixelOffset = (row - (numRows_ - 1) / 2.0) * (tileHeight);
            Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
            Point2D.Double stagePos = new Point2D.Double();
            transform.transform(pixelPos, stagePos);
            String label = "Grid_" + col + "_" + row;
            positions.add(new StageCoordinates(label, stagePos, tileWidth, tileHeight));
         }
      }
      //delete positions squares (+padding) that do not overlap convex hull
      for (int i = positions.size() - 1; i >= 0; i--) {
         StageCoordinates pos = positions.get(i);
         //create square region correpsonding to stage pos
         Region<Euclidean2D> square = getStagePositionRegion(pos);     
         //if convex hull and position have no intersection, delete
         Region<Euclidean2D> intersection = regionFacotry_.intersection(square, convexHullRegion_);
         if (intersection.isEmpty()) {
            positions.remove(i);
         }
         square.getBoundarySize();
      }             
      xyPositions_ = positions;
   }
   
   /**
    * delete closest point within XY tolerance
    * @param x
    * @param y
    * @param tolerance radius in stage space
    */
   public void deleteClosestPoint(double x, double y, double tolerance) {
      double minDistance = tolerance + 1;
      int minDistanceIndex = -1;
      for (int i = 0; i < points_.size(); i++) {
         double distance = Math.sqrt( (x-points_.get(i).x)*(x-points_.get(i).x) + (y-points_.get(i).y)*(y-points_.get(i).y) );
         if (distance < minDistance) {
            minDistance = distance;
            minDistanceIndex = i;
         }         
      }
      //delete if within tolerance
      if (minDistance < tolerance) {
         points_.remove(minDistanceIndex);
         xyPoints_.remove(minDistanceIndex);
      }
      //duplicate points for use on caluclation thread
      LinkedList<Point3d> points = new LinkedList<Point3d>(points_);
      LinkedList<Vector2D> xyPoints = new LinkedList<Vector2D>(xyPoints_);
      updateConvexHullAndInterpolate(points,xyPoints); 
   }
   
   public void addPoint(double x, double y, double z) {
      points_.add(new Point3d(x,y,z)); //for interpolation
      xyPoints_.add(new Vector2D(x,y)); //for convex hull
      //duplicate points for use on caluclation thread
      LinkedList<Point3d> points = new LinkedList<Point3d>(points_);
      LinkedList<Vector2D> xyPoints = new LinkedList<Vector2D>(xyPoints_);
      updateConvexHullAndInterpolate(points,xyPoints); 
   }
   
   /**
    * Constantly checks for interrupts in case list of points is updated
    * @param points
    * @param xyPoints 
    */
   private synchronized void updateConvexHullAndInterpolate(final LinkedList<Point3d> points, final LinkedList<Vector2D> xyPoints) {
      xyPositions_ = null;
      convexHullVertices_ = null;
      if (currentInterpolationTask_ != null) {
         //interpolation points have changed so cancel exisiting ones
         currentInterpolationTask_.cancel(true);
         currentInterpolation_ = null;
      }
      Runnable interpRunnable = new Runnable() {
         @Override
         public void run() {
                     
            if (points.size() > 2) {
               ConvexHull2D hull = mChain_.generate(xyPoints);
               if (Thread.interrupted()) {
                  return;
               }
               convexHullVertices_ = hull.getVertices();
               if (Thread.interrupted()) {
                  return;
               }
               convexHullRegion_ = hull.createRegion();
               if (Thread.interrupted()) {                  
                  return;
               }
               calculateConvexHullBounds();
               if (Thread.interrupted()) {                  
                  return;
               }
               fitXYPositionsToConvexHull();
               if (Thread.interrupted()) {
                  return;
               }
               interpolateSurface(points);
            } else {
               convexHullRegion_ = null;
               convexHullVertices_ = null;
               numRows_ = 0;
               numCols_ = 0;
            }

         }
      };
      currentInterpolationTask_ = executor_.submit(interpRunnable);
   }

   public Point3d[] getPoints() {
      return points_.toArray(new Point3d[0]);
   }
   
}