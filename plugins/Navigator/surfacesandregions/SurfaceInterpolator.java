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
   
   private LinkedList<Point3d> points_;
   private Gridder2 gridder_;
   private boolean interpFlipX_, interpFlipY_;
   private MonotoneChain mChain_;
   private RegionFactory<Euclidean2D> regionFacotry_ = new RegionFactory<Euclidean2D>();
   private Vector2D[] convexHullVertices_;
   private Region<Euclidean2D> convexHullRegion_;
   private LinkedList<Vector2D> xyPoints_;
   private int numRows_, numCols_;
   private ArrayList<StageCoordinates> positions_ = new ArrayList<StageCoordinates>();
   private double xyPadding_um_ = 0, zPadding_um_;
   private double boundXMin_, boundXMax_, boundYMin_, boundYMax_;
   private int boundXPixelMin_,boundXPixelMax_,boundYPixelMin_,boundYPixelMax_;
   private float[][] interpolation_;
   
   public SurfaceInterpolator() {
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
      
      //Biggest difference between these two appears to be that Splines can come up with z values outside the range of smaple points
      //   not sure if this is good or bad yet
      gridder_ = new SibsonGridder2(new float[]{0},new float[]{0},new float[]{0});
//     gridder_ = new DiscreteSibsonGridder2(new float[0],new float[0],new float[0]); //fast, like nearest neighbor, complexity decreases with # samples
//      gridder_ = new SplinesGridder2(new float[0], new float[0], new float[0]);  //Fast and appears good

   }
   
   /**
    * REturns the number of XY positions spanned by the footprint of this surface
    * @return 
    */
   public int getNumPositions() {
      return positions_.size();
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
      return positions_;
   }

   /**
    * figure out which of the positions need to be collected at a given slice
    * Assumes positions_ contains list of all possible positions for fitting  
    * @param zPos 
    */
   public ArrayList<StageCoordinates> getXYPositonsAtSlice(double zPos) {
      ArrayList<StageCoordinates> positionsAtSlice = new ArrayList<StageCoordinates>();
      
      for (StageCoordinates pos : positions_) {
         //get the corners with padding added in
         Point2D.Double[] corners = getPositionCornersWithPadding(pos);
         //First check position corners 
         for (Point2D.Double point : corners) {
            Float interpVal = getInterpolatedValue(point.x, point.y);
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
         System.out.println("New Pos");
         outerloop:
         for (double x = 0; x <= pixelSpan.x; x += pixelSpan.x / (double) numTestPoints) {
            for (double y = 0; y <= pixelSpan.y; y += pixelSpan.y / (double) numTestPoints) {
               //convert these abritray pixel coordinates back to stage coordinates
               transform.setToTranslation(corners[0].getX(), corners[0].getY());                          
               Point2D.Double stageCoords = new Point2D.Double();
               transform.transform(new Point2D.Double(x, y), stageCoords);
               //test point for inclusion of position
               System.out.println(stageCoords.x + "\t\t" + stageCoords.y);   
               Float interpVal = getInterpolatedValue(stageCoords.x, stageCoords.y);
               if (interpVal == null) {
                  continue;
               }
               if (interpVal <= zPos + zPadding_um_) {   //TODO: account for different signs of Z
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
   
   private void interpolateSurface() {
      long startTime = System.currentTimeMillis();
      //provide interpolator with current list of data points
      float x[] = new float[points_.size()];
      float y[] = new float[points_.size()];
      float z[] = new float[points_.size()];
      for (int i = 0; i < points_.size(); i++) {
         x[i] = (float) points_.get(i).x;
         y[i] = (float) points_.get(i).y;
         z[i] = (float) points_.get(i).z;
      }
      gridder_.setScattered(z, x, y);

      //TODO: optimal sampling interval/ range--pixel?
      //Interpolation point at least every 10 pixels
      int numInterpPointsX = (int) (((boundXMax_ - boundXMin_) / MMStudio.getInstance().getCore().getPixelSizeUm()) / 10);
      int numInterpPointsY = (int) (((boundYMax_ - boundYMin_) / MMStudio.getInstance().getCore().getPixelSizeUm()) / 10);

      //do interpolation
      Sampling xSampling = new Sampling(numInterpPointsX, (boundXMax_ - boundXMin_) / (numInterpPointsX - 1), boundXMin_);
      Sampling ySampling = new Sampling(numInterpPointsY, (boundYMax_ - boundYMin_) / (numInterpPointsY - 1), boundYMin_);
      interpolation_ = gridder_.grid(xSampling, ySampling);

      System.out.println("Interpolation time: " + (System.currentTimeMillis() - startTime) );
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
      //delete positions squares (+padding) that do not overlap convez hull
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
      positions_ = positions;
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
      updateConvexHullAndInterpolate();
   }
   
   public void addPoint(double x, double y, double z) {
      points_.add(new Point3d(x,y,z)); //for interpolation
      xyPoints_.add(new Vector2D(x,y)); //for convex hull
      updateConvexHullAndInterpolate(); 
   }
   
   private void updateConvexHullAndInterpolate() {
      if (points_.size() > 2) {
         ConvexHull2D hull = mChain_.generate(xyPoints_);
         convexHullRegion_ = hull.createRegion();
         convexHullVertices_ = hull.getVertices();
         calculateConvexHullBounds();
         fitXYPositionsToConvexHull();
         interpolateSurface();
      } else {
         convexHullRegion_ = null;
         convexHullVertices_ = null;
         numRows_ = 0;
         numCols_ = 0;
      }
   }

   public LinkedList<Point3d> getPoints() {
      return points_;
   }
   
   public boolean getFlipX() {
      return interpFlipX_;
   }
   
   public boolean getFlipY() {
      return interpFlipY_;
   }
   
   private boolean isInsideConvexHull(double x, double y) {
      if (convexHullRegion_ == null) {
         return false;
      }
      return convexHullRegion_.checkPoint(new Vector2D(x, y)) != Region.Location.OUTSIDE;
   }
   
   /**
    * 
    * @param x
    * @param y
    * @return null if not inside  
    */
   public Float getInterpolatedValue(double x, double y) {
      //check if theres anyhting to interpolate
      if (points_.size() < 3) {
         return null;
      }
      if (!isInsideConvexHull(x, y)) {
         return null;
      }
      
      int numInterpPointsX = interpolation_[0].length;
      int numInterpPointsY = interpolation_.length;
      
      int xIndex = (int) Math.round(((x - boundXMin_) / (boundXMax_ - boundXMin_) ) * (numInterpPointsX - 1));
      int yIndex = (int) Math.round(((y - boundYMin_) / (boundYMax_ - boundYMin_) ) * (numInterpPointsY - 1));
      return interpolation_[yIndex][xIndex];
   }
}
