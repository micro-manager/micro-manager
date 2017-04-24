/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.plugins.magellan.propsandcovariants;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.rmi.activation.ActivationSystem;
import java.util.Arrays;
import java.util.Scanner;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.MatrixUtils;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.micromanager.plugins.magellan.acq.AcquisitionEvent;
import org.micromanager.plugins.magellan.bidc.JavaLayerImageConstructor;
import org.micromanager.plugins.magellan.coordinates.AffineUtils;
import org.micromanager.plugins.magellan.coordinates.XYStagePosition;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceInterpolator;


public class LaserPredNet {

      private static final double SEARCH_START_DIST = 400.0;
   private static final double SEARCH_TOLERANCE = 2.0;
   private static final int N_THETA_ANGLES = 12;
   private static final int N_HIST_BINS = 12;
   private static final double PHI = 0.3491;
   private static final int FOV_LASER_MODULATION_RESOLUTION = 16;
   
   private static final int N_HIDDENS = 200;
   private static final int N_INPUTS = 15;

     

   Array2DRowRealMatrix W1_, B1_, W2_, B2_;
   private double[] distanceMeans_, distanceSDs_;
   private double[][] testValues_;
   private double[] testValuesOutput_;
   private double brightness_;
   private double[] binedges_;
   
   public LaserPredNet(String filename) throws FileNotFoundException {
      readModel(filename);
      //init log bins
      double binmax = 350;
      binedges_ = new double[N_HIST_BINS + 1];
      for (int b = 0; b < N_HIST_BINS + 1; b++) {
         double linearBin = (1.0 / (double) N_HIST_BINS) * b;
         binedges_[b] = Math.pow(linearBin, 1.5) * binmax;
      }
   }
   
  public static void main (String[] args) throws FileNotFoundException {
     new LaserPredNet("./maitaimodel.csv");
  }
   
   public  byte[] getExcitations(AcquisitionEvent e, SurfaceInterpolator surf) throws InterruptedException {
      XYStagePosition xyPos = e.xyPosition_;
      double zPos = e.zPosition_;
      Point2D.Double[] corners = xyPos.getFullTileCorners();
      double tileSize = Math.abs(corners[2].x - corners[0].x);      
      int pixelDim = JavaLayerImageConstructor.getInstance().getImageHeight();
      
      AffineTransform posTransform = AffineUtils.getAffineTransform(getCurrentPixelSizeConfig(), xyPos.getCenter().x, xyPos.getCenter().y);

      
      double[][] designMat = new double[FOV_LASER_MODULATION_RESOLUTION*FOV_LASER_MODULATION_RESOLUTION][12];
//      designMatrix = [designMatrix tilePosition brightness];
       for (int r = 0; r < designMat.length; r++) {
          //calculate position for this point in FOV
          int xPosPix = (int) (((r % FOV_LASER_MODULATION_RESOLUTION) / (double) (FOV_LASER_MODULATION_RESOLUTION-1) - 0.5) * pixelDim);
          int yPosPix = (int) (((r / FOV_LASER_MODULATION_RESOLUTION) / (double) (FOV_LASER_MODULATION_RESOLUTION-1) - 0.5) * pixelDim);               
          Point2D.Double stageCoordPos =  new Point2D.Double();
          posTransform.transform(new Point2D.Double(xPosPix, yPosPix), stageCoordPos);
          //calculate histogram
          double[] hist = new double[N_HIST_BINS];
          for (int thetaIndex = 0; thetaIndex < N_THETA_ANGLES; thetaIndex++) {
             double dist = getSampledDistancesToSurface(thetaIndex, stageCoordPos.x, stageCoordPos.y, zPos, surf);
             //add count to hist
             for (int binIndex = 0; binIndex < N_HIST_BINS; binIndex++) {
                if (dist < binedges_[binIndex + 1]) {
                   hist[binIndex]++;
                   break;
                }
             }
          }
          //standardize histogram
          for (int i= 0; i < hist.length; i++) {
             hist[i] = (hist[i] - distanceMeans_[i]) / distanceSDs_[i];
          }
          
         for (int c = 0; c < designMat[0].length; c++) {
            if (c < N_HIST_BINS) {
               //add in normalized distance histogram
               designMat[r][c] = hist[c];            
            } else if (c < N_HIST_BINS + 1) { // x position                        
               double xPos = (r % FOV_LASER_MODULATION_RESOLUTION) / (double) (FOV_LASER_MODULATION_RESOLUTION-1);
               designMat[r][c] = xPos - 0.5;
            } else if (c < N_HIST_BINS + 2) { // y position                            
               double yPos = (r / FOV_LASER_MODULATION_RESOLUTION) / (double) (FOV_LASER_MODULATION_RESOLUTION-1);
               designMat[r][c] = yPos - 0.5;
            } else {
               designMat[r][c] = brightness_;
            }
         }
       }
       //use NN to predict
       return forwardPass(designMat);
   }
   
   
   public double getBrightness() {
      return brightness_;
   }
   
 public byte[] forwardPass(double[][] x) {
     double[] ones = new double[x.length];
     Arrays.fill(ones, 1.0);
     Array2DRowRealMatrix onesMat = new Array2DRowRealMatrix(ones);
      //assume x is properly normalized
      new Array2DRowRealMatrix(x[0]);
      Array2DRowRealMatrix xMat = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(x);
      Array2DRowRealMatrix h = xMat.multiply(W1_).add(onesMat.multiply(B1_));
      relu(h);
      Array2DRowRealMatrix z = (Array2DRowRealMatrix) h.multiply(W2_.transpose()).add(onesMat.multiply(B2_));
      byte[] powers = new byte[z.getRowDimension()*z.getColumnDimension()];
      for (int i = 0; i < powers.length; i++) {
         powers[i] = (byte) Math.max(0, Math.min(255, z.getEntry(i, 0)));
      }
      return powers;  
   }
   
  private static void relu(Array2DRowRealMatrix activations) {      
      for (int r = 0; r < activations.getRowDimension(); r++) {
         for (int c = 0; c < activations.getColumnDimension(); c++) {
            if (activations.getEntry(r, c) < 0) {
               activations.setEntry(r, c, 0.0);
            }
         }
      }
   }
  
   private void readModel(String filename) throws FileNotFoundException {
      Scanner s = new Scanner(new File(filename));
      double[][] w1 = new double[N_INPUTS][N_HIDDENS];
      double[][] b1 = new double[1][N_HIDDENS];
      double[][] w2 = new double[1][N_HIDDENS];
      double[][] b2 = new double[1][1];
      double[][] var = null;      
      int index = 0;
      int matCount = 0;
      while(s.hasNext()) {
         String line = s.nextLine();
         if (line.toLowerCase().startsWith("fc") || line.toLowerCase().startsWith("output")) {
            //new variable
            if (matCount == 0) {
               var = w1;
            } else if (matCount == 1) {
               var = b1;
            } else if (matCount == 2) {
               var = w2;
            } else {
               var = b2;
            }
            matCount++;
            index = 0;
         } else if (line.toLowerCase().startsWith("distance")) {
            break;
         } else {
            String[] entries = line.split(",");
            for (int i = 0; i < entries.length; i++) {
               try{
               var[index / var[0].length][index % var[0].length] = Double.parseDouble(entries[i]); 
               } catch (Exception e) {
                  int t = 6;
               }
               index++;
            }
         }    
      }
      String meanStr = s.nextLine(); // means
      String[] entries = meanStr.split(",");
      distanceMeans_ = new double[N_HIST_BINS]; 
      for (int i = 0; i < entries.length; i++) {
          distanceMeans_[i] = Double.parseDouble(entries[i]);
      }
      s.nextLine(); // burn SD title
      String sdStr = s.nextLine(); 
      entries = sdStr.split(",");
      distanceSDs_ = new double[N_HIST_BINS]; 
      for (int i = 0; i < entries.length; i++) {
          distanceSDs_[i] = Double.parseDouble(entries[i]);
      }
      s.nextLine(); // burn test values title
      int numTestVals = 4;
      testValuesOutput_ = new double[numTestVals];
      testValues_ = new double[numTestVals][N_HIST_BINS + 3];
      for (int i = 0 ; i < numTestVals; i++) {
         String valsString = s.nextLine();
         entries = valsString.split(",");
         for (int k = 0; k < entries.length; k++) {
            testValues_[i][k] = Double.parseDouble(entries[k]);
         }
         testValuesOutput_[i] = Double.parseDouble(s.nextLine());
      }
      
      //convert model to Apache commons matrices
      W1_ = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(w1);
      B1_ = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(b1);
      W2_ = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(w2);
      B2_ = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(b2);    
      
                   
      //Run tests
       byte[] output = forwardPass(testValues_);
       for (int k = 0; k < output.length; k++) {
          System.out.println("Calculated: " + (output[k]&0xff)  + "\tGround truth:" + testValuesOutput_[k]);
       }  
      
   }
   
      
   
   
   
   /**
    *
    * @return return distance to surface interpolation based on x y and z points
    */
   private static double getSampledDistancesToSurface(int angleIndex, double x, double y, double z, SurfaceInterpolator surface) throws InterruptedException {
      double dTheta = Math.PI * 2.0 / (double) N_THETA_ANGLES;
      Vector3D initialPoint = new Vector3D(x, y, z);
      double[] distances = new double[N_THETA_ANGLES];
      double theta = angleIndex * dTheta;
      //calculate unit vector in theta phi direction
      Vector3D directionUnitVec = new Vector3D(Math.cos(theta) * Math.sin(PHI), Math.sin(theta) * Math.sin(PHI), Math.cos(PHI)).scalarMultiply(-1);
      //binary search 
      double initialDist = SEARCH_START_DIST;
      //start with a point outside and then binary line search for the distance
      while (isWithinSurace(surface, initialPoint.add(directionUnitVec.scalarMultiply(initialDist)))) {
         initialDist *= 2;
      }
      return binarySearch(initialPoint, directionUnitVec, 0, initialDist, surface);
   }
   
   private static boolean isWithinSurace(SurfaceInterpolator surface, Vector3D point) throws InterruptedException {
     boolean defined = surface.waitForCurentInterpolation().isInterpDefined(point.getX(), point.getY());
     if (!defined) {
        return false;
     }
     float interpVal = surface.waitForCurentInterpolation().getInterpolatedValue(point.getX(), point.getY());
     return point.getZ() > interpVal;
   }
   
   private static double binarySearch(Vector3D initialPoint, Vector3D direction, double minDistance, double maxDistance, SurfaceInterpolator surf) throws InterruptedException {      
      double halfDistance = (minDistance + maxDistance) / 2;
      //if the distance has been narrowed to a sufficiently small interval, return
      if (maxDistance - minDistance < SEARCH_TOLERANCE) {
         return halfDistance;
      }
      //check if point is above surface in 
      Vector3D searchPoint = initialPoint.add(direction.scalarMultiply(halfDistance));
      boolean withinSurface = isWithinSurace(surf, searchPoint);
      if (!withinSurface) {
         return binarySearch(initialPoint, direction, minDistance, halfDistance, surf);
      } else {
         return binarySearch(initialPoint, direction, halfDistance, maxDistance, surf);
      }
   }
   
   
    private static String getCurrentPixelSizeConfig() {   
      try {
         return Magellan.getCore().getCurrentPixelSizeConfig();
      } catch (Exception ex) {
         Log.log("couldnt get pixel size config");
         throw new RuntimeException();
      }
   }
   
}
