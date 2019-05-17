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

package org.micromanager.magellan.coordinates;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.JOptionPane;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.Log;
import mmcorej.CMMCore;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author Henry
 */
public class AffineCalibrator {
 

   private AffineGUI affineGui_ ;
   private volatile boolean abort_ =false;
   private Datastore datastore_;
   private final double fovDiameter_um_;
   
   public AffineCalibrator(AffineGUI ag, double fovDiam) {
      affineGui_ = ag;
      fovDiameter_um_ = fovDiam;
      //new thread to not hold up EDT
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               computeAffine();
            } catch (Exception ex) {
               Log.log("Aborting due to problem calibrating affine: " + ex.toString());
            }
            affineGui_.calibrationFinished();
         }
      }, "Affine transform calibration").start();

   }
   
   public void abort() {
      abort_ = true;
      //close window
      if (datastore_ != null) {
         try {
            datastore_.close();
         } catch (IOException ex) {
            throw new RuntimeException();
         }
      }
      datastore_ = null;
   }
   
   /**
    * Snap an image at the current XY stage position, then at a series of positions
    * in a circle around the current position. Use cross correlation to determine 
    * the optimal registrations of each of the subsequent positions relative to the first
    * one in order to build up a set of corresponding coordinate pairs in pixel and 
    * XY stage space. Then use these coordinate pairs to compute a least squares fit
    * for the entries of the affine transformation matrix that relates pixels coordinates
    * and XY stage coordinates
    */
   public void computeAffine() throws Exception {
      CMMCore core = Magellan.getCore();
      String xyStage = core.getXYStageDevice();
      
      int numImages = 13;
      Point2D.Double currentPos = new Point2D.Double(core.getXPosition(xyStage), core.getYPosition(xyStage));
      Point2D.Double[] targetPositions = new Point2D.Double[numImages];
      //first xy pos is where it currently is 
      targetPositions[0] = currentPos;  
      //subsequent positions arranged in a circle with radius approximately equal to
      //the size of the field of view
      for (int i = 1; i < numImages; i++) {
          double theta = (2*Math.PI / (double) (numImages-1)) * i;
          double radius = fovDiameter_um_ / 2;
          targetPositions[i] = new Point2D.Double(currentPos.x + Math.cos(theta)*radius, currentPos.y + Math.sin(theta)*radius);          
        }
   
      Point2D.Double[] stagePositions = new Point2D.Double[numImages];
      ArrayList<Image> images = new ArrayList<Image>();
       for (int i = 0; i < numImages; i++) {
           //move stage to new position
           moveFromSameDirection(targetPositions[i].x, targetPositions[i].y);
           //capture new image, add it to display, and store it
           stagePositions[i] = new Point2D.Double(core.getXPosition(xyStage), core.getYPosition(xyStage));
           images.add(snapAndAdd(i));
           if (abort_) {
               return;
           }
      }
       datastore_.close();
       //compute pixel shifts relative to first image for all subsequent
       Point2D.Double[] pixPositions = new Point2D.Double[numImages];
       pixPositions[0] = new Point2D.Double(0, 0);
       for (int i = 1; i < numImages; i++) {
           pixPositions[i] = computePixelShift(images.get(0), images.get(i));
       }
       
       //compute affine transform based on pairs of corresponding points
       AffineTransform transform = computeAffine(stagePositions, pixPositions);
 
     //ask if user likes this affine transform and wants to store it
      int result = JOptionPane.showConfirmDialog(affineGui_, "Calulcated affine transform matrix: " + transform.toString() + ". Would you like to store these settings?",
               "Store calculated affine transform?", JOptionPane.YES_NO_OPTION);
      if (result == JOptionPane.YES_OPTION) {
         //store affine
         AffineUtils.storeAffineTransform(core.getCurrentPixelSizeConfig(),transform);
         //mark as updated
         AffineUtils.transformUpdated(core.getCurrentPixelSizeConfig(), transform);
      }
      //Ask if user wants to also use this to calibrate pixel size
      double pixelSize = Math.sqrt(Math.abs(transform.getDeterminant()));
      result = JOptionPane.showConfirmDialog(affineGui_, "Would you like to store a pixel size of "+ pixelSize +"?",
               "Store pixel size calibration?", JOptionPane.YES_NO_OPTION);
      if (result == JOptionPane.YES_OPTION) {
         //store affine
         Magellan.getCore().setPixelSizeUm(Magellan.getCore().getCurrentPixelSizeConfig(), pixelSize);         
         JOptionPane.showMessageDialog(affineGui_, "Don't forget to save the updated Hardware configuration");
      }
   }
   
   /**
    * Always move from same direction can give more accurate results if there
    * is some hysteresis in the XY stage
    */
    private void moveFromSameDirection(double x, double y) throws Exception {
        //move to a postion up and the the left of the  one requested, then to the one requested
        CMMCore core = Magellan.getCore();
        core.setXYPosition(x - fovDiameter_um_ / 2, y - fovDiameter_um_ / 2);
        core.waitForDevice(core.getXYStageDevice());
        core.setXYPosition(x, y);
        core.waitForDevice(core.getXYStageDevice());
    }
   
    /**
    * Use fourier transforms to compute cross correlation, which gives best 
    * translation to register two images
    */
   private Point2D.Double computePixelShift(Image img1, Image img2) {
       String name1 = "ImageToFT1";
       String name2 = "ImageToFT2";
       ImagePlus ip1 = getImagePlus(img1, name1);
       ImagePlus ip2 = getImagePlus(img2, name2);
           
       ip1.show();
       IJ.run("FFT");
       ip1.close();
       ip2.show();
       IJ.run("FFT");
       ip2.close();
       IJ.run("FD Math...", "image1=[FFT of "+name1+"] operation=Correlate image2=[FFT of "+name2+"] result=Result do");
       WindowManager.getImage("FFT of "+name1).close();
       WindowManager.getImage("FFT of "+name2).close();
       ImagePlus resultIP = WindowManager.getImage("Result");
       float[] pix = (float[]) resultIP.getProcessor().getPixels();
       Point2D.Double maxLocation = new Point2D.Double(0, 0);
       float maxVal = Float.MIN_VALUE;
       for (int i =0; i < pix.length; i++) {
           if (maxVal < pix[i]) {
               maxVal = pix[i];
               maxLocation.x = i % resultIP.getWidth();
               maxLocation.y = i / resultIP.getWidth();
           }
       }
       //compute relative to center
       maxLocation.x = maxLocation.x - resultIP.getWidth()/2;
       maxLocation.y = maxLocation.y - resultIP.getHeight()/2;
       resultIP.close();
      return maxLocation;
   }
   
   
   private ImagePlus getImagePlus(Image img, String name) {
          ImagePlus ip = null;
       if (img.getBytesPerPixel() == 1) {
           ip = new ImagePlus(name, new ByteProcessor(img.getWidth(), img.getHeight(), (byte[]) img.getRawPixels()));
       } else {
           ip = new ImagePlus(name, new ShortProcessor(img.getWidth(), img.getHeight(), (short[]) img.getRawPixels(), null ));
       }
       return ip;
   }

    private Image snapAndAdd(int index) throws Exception {
        boolean liveOn = Magellan.getStudio().live().getIsLiveModeOn();
        if (liveOn) {
            Magellan.getStudio().live().setSuspended(true);
        }
        List<Image> images = Magellan.getStudio().live().snap(false);
        if (datastore_ == null) {
            datastore_ = Magellan.getStudio().displays().show(images.get(0));
        } else {
            Coords.CoordsBuilder builder = Magellan.getStudio().data().getCoordsBuilder();
            builder.time(0);
            builder.channel(0);
            builder.stagePosition(index);
            builder.z(0);
            datastore_.putImage(images.get(0).copyAtCoords(builder.build()));
        }
        if (liveOn) {
            Magellan.getStudio().live().setSuspended(false);
        }
        return images.get(0);
    }
    
    /**
      * Compute least squares fit of entries in the affine transform matrix based on sets of corresponding points
      * in pixel coordinates and stage coordinates
      */
   private AffineTransform computeAffine(Point2D.Double[] stagePositions, Point2D.Double[] pixPositions) { 
      // s = Ma
      //aLS = (M^t * M)^-1 * s
      
    //s is a (2 * # of points)x1 columns vector with stage coords
    double[] sData = new double[2*stagePositions.length];
    for (int i = 0; i < stagePositions.length; i++) {
        sData[2*i] = stagePositions[i].x;
        sData[2*i + 1] = stagePositions[i].y;
    }
    RealMatrix s = new Array2DRowRealMatrix(sData);
      
    //M is matrix with pixel coords
    double[][] MData = new double[2*stagePositions.length][6];
    for (int i = 0; i < stagePositions.length; i++) {
        //two rows per iteration
        MData[2*i][0] = pixPositions[i].x;
        MData[2*i][1] = pixPositions[i].y;
        MData[2*i][2] = 1;
        MData[2*i][3] = 0;
        MData[2*i][4] = 0;
        MData[2*i][5] = 0;
        MData[2*i + 1][0] = 0;
        MData[2*i + 1][1] = 0;
        MData[2*i + 1][2] = 0;
        MData[2*i + 1][3] = pixPositions[i].x;
        MData[2*i + 1][4] = pixPositions[i].y;
        MData[2*i + 1][5] = 1;
    }
      RealMatrix M = new Array2DRowRealMatrix(MData);
      
      RealMatrix hatMat = M.transpose().multiply(M);
      RealMatrix pInv = (new LUDecomposition(hatMat)).getSolver().getInverse().multiply(M.transpose());
      RealMatrix A = pInv.multiply(s);
      AffineTransform transform = new AffineTransform(new double[]{A.getEntry(0,0),A.getEntry(3,0),A.getEntry(1,0),A.getEntry(4,0)});
      return transform;  
   } 
}
