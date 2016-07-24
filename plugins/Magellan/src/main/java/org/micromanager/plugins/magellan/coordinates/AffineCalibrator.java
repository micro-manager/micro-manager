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

package main.java.org.micromanager.plugins.magellan.coordinates;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij3d.image3d.FHTImage3D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.JOptionPane;
import main.java.org.micromanager.plugins.magellan.main.Magellan;
import main.java.org.micromanager.plugins.magellan.misc.Log;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import main.java.org.micromanager.plugins.magellan.misc.ProgressBar;


/**
 *
 * @author Henry
 */
public class AffineCalibrator {
 

   private CountDownLatch nextImageLatch_ = new CountDownLatch(1);
   private AffineGUI affineGui_ ;
   private volatile boolean abort_ =false;
   private Datastore datastore_;
   
   public AffineCalibrator(AffineGUI ag) {
      affineGui_ = ag;
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
   
   public void readyForNextImage() {
      nextImageLatch_.countDown();
   }
   
   public void abort() {
      abort_ = true;
      readyForNextImage();
      //close window
      if (datastore_ != null) {
         datastore_.close();
      }
      datastore_ = null;
   }
   
   public void computeAffine() throws Exception {
      IJ.log("Automatic affine calibration.");
      IJ.log(" ");
      IJ.log("This module will attempt to estimate the affine transformation matrix based on \n"
              + "three images of the same object in a different part of the field of view. Between \n"
              + "each successive image, the XY stage must be tranlsated so that the object is in a \n"
              + "different part of the field of view. The first image has already been captured.");
      CMMCore core = Magellan.getCore();
      String xyStage = core.getXYStageDevice();
      Point2D.Double[] stagePositions = new Point2D.Double[3], pixPositions = new Point2D.Double[3];
      
      stagePositions[0] = new Point2D.Double(core.getXPosition(xyStage) ,core.getYPosition(xyStage) );
      Image img0 = snapAndAdd(0);
      nextImageLatch_ = new CountDownLatch(1);
      Log.log("Move stage to new position with same features visible, and press Capture");
      nextImageLatch_.await();
      if(abort_) {        
         return;
      }
      
      stagePositions[1] = new Point2D.Double(core.getXPosition(xyStage), core.getYPosition(xyStage));
      Image img1 = snapAndAdd(1);
      nextImageLatch_ = new CountDownLatch(1);
      Log.log("Move stage to new position with same features visible, and press Capture");
      nextImageLatch_.await();
      if(abort_) {
         return;
      }
      
      stagePositions[2] = new Point2D.Double(core.getXPosition(xyStage), core.getYPosition(xyStage));
      Image img2 = snapAndAdd(2);

      //use Xcorr to calculate pixel coordinates
      ProgressBar progressBar = new ProgressBar("Computing affine transform (may take several minutes)", 0, 3);
      progressBar.setProgress(0);
      progressBar.setVisible(true);
      pixPositions[0] = new Point2D.Double(0, 0); // define first one as the origin
      progressBar.setProgress(1);
      pixPositions[1] = crossCorrelate(img0, img1);
      progressBar.setProgress(2);
      pixPositions[2] = crossCorrelate(img0, img2);
      progressBar.setProgress(3);
      progressBar.setVisible(false);


      //3) compute affine from three pairs of points
      AffineTransform transform = computeAffine(pixPositions[0], pixPositions[1], pixPositions[2],
              stagePositions[0], stagePositions[1], stagePositions[2]);
      
     //ask if user likes this affine transform and wants to store it
      int result = JOptionPane.showConfirmDialog(affineGui_, "Calulcated affine transform matrix: " + transform.toString() + ". Would you like to store these settings?",
               "Store calculated affine transform?", JOptionPane.YES_NO_OPTION);
      if (result == JOptionPane.YES_OPTION) {
         //store affine
         AffineUtils.storeAffineTransform(core.getCurrentPixelSizeConfig(),transform);
         //mark as updated
         AffineUtils.transformUpdated(core.getCurrentPixelSizeConfig(), transform);
      }
   }

   private Point2D.Double crossCorrelate(Image img1, Image img2) throws Exception {    
      //double the width of iamges used for xCorr to support offsets bigger than half the iage size
       int width = 2 *img1.getWidth();
       int height = 2 * img1.getHeight();
       ImageStack stack1 = new ImageStack(width, height);
       ImageStack stack2 = new ImageStack(width, height);
       boolean eightBit = img1.getBytesPerPixel() == 1;
       Object newPix1, newPix2, sortedPix1, sortedPix2;
      if (eightBit) {
         newPix1 = new byte[width * height];
         newPix2 = new byte[width * height];
         //fill with background pix value to not throw things off
         sortedPix1 = Arrays.copyOf((byte[]) img1.getRawPixels(), (width / 2) * (height / 2));
         sortedPix2 = Arrays.copyOf((byte[]) img2.getRawPixels(), (width / 2) * (height / 2));
         Arrays.sort((byte[]) sortedPix1);
         Arrays.sort((byte[]) sortedPix2);
         Arrays.fill((byte[]) newPix1, ((byte[]) sortedPix1)[(int) (((byte[]) sortedPix1).length * 0.1)]);
         Arrays.fill((byte[]) newPix2, ((byte[]) sortedPix2)[(int) (((byte[]) sortedPix2).length * 0.1)]);
      } else {
         newPix1 = new short[width * height];
         newPix2 = new short[width * height];
         //fill with background pix value to not throw things off
         sortedPix1 = Arrays.copyOf((short[]) img1.getRawPixels(), (width / 2) * (height / 2));
         sortedPix2 = Arrays.copyOf((short[]) img2.getRawPixels(), (width / 2) * (height / 2));
         Arrays.sort((short[]) sortedPix1);
         Arrays.sort((short[]) sortedPix2);
         Arrays.fill((short[]) newPix1, ((short[]) sortedPix1)[(int) (((short[]) sortedPix1).length * 0.1)]);
         Arrays.fill((short[]) newPix2, ((short[]) sortedPix2)[(int) (((short[]) sortedPix2).length * 0.1)]);
      }

      for (int y = 0; y < height / 2; y++) {
         System.arraycopy(img1.getRawPixels(), y*(width/2), newPix1, (y+height/4)*width + width/4, width/2);
           System.arraycopy(img2.getRawPixels(), y*(width/2), newPix2, (y+height/4)*width + width/4, width/2);
       }
       stack1.addSlice(null, newPix1);
       stack2.addSlice(null, newPix2);
      ImagePlus ip1 = new ImagePlus("ip1", stack1);
      ImagePlus ip2 = new ImagePlus("ip2", stack2);
      //convert to 32 bit floats
      new ImageConverter(ip1).convertToGray32();
      new ImageConverter(ip2).convertToGray32();
      ImageStack xCorrStack = FHTImage3D.crossCorrelation(ip1.getStack(), ip2.getStack());
      ImagePlus xCorr = new ImagePlus("XCorr", xCorrStack);        
      double maxVal = xCorr.getStatistics(ImagePlus.MIN_MAX).max;
          
//      xCorr.show();
      //find pixel cooridinates of maxVal
      for (int x = 0; x < width; x++) {
         for (int y = 0; y < height; y++) {
            if (((FloatProcessor) xCorr.getProcessor()).getPixelValue(x, y) == maxVal) {
               xCorr.close();
               ip1.close(); 
               ip2.close();
               return new Point2D.Double( width/2 - x, height/2 - y);
            }
         }
      }
      xCorr.close();
      ip1.close();
      ip2.close();
      throw new Exception("Coudnlt find pixel max valu in xCorr Image");
   }
   
   /**
    * 
    * @return pixels
    * @throws Exception 
    */
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
         builder.time(index); 
         datastore_.putImage(images.get(0).copyAtCoords(builder.build()));
      }
      if (liveOn) {
          Magellan.getStudio().live().setSuspended(false);
      }
      return images.get(0);
   }

   //see http://stackoverflow.com/questions/22954239/given-three-points-compute-affine-transformation
   private AffineTransform computeAffine(Point2D.Double pix1, Point2D.Double pix2 ,Point2D.Double pix3,
           Point2D.Double stage1, Point2D.Double stage2, Point2D.Double stage3) {
      //solve system x' = xA for A, x is stage points and x' is pixel points
      //6x6 matrix
      RealMatrix pixPoints = new Array2DRowRealMatrix(new double[][]{
                 {pix1.x, pix1.y, 1, 0, 0, 0}, {0, 0, 0, pix1.x, pix1.y, 1},
                 {pix2.x, pix2.y, 1, 0, 0, 0}, {0, 0, 0, pix2.x, pix2.y, 1}, 
                 {pix3.x, pix3.y, 1, 0, 0, 0}, {0, 0, 0, pix3.x, pix3.y, 1}});
      //6x1 matrix
      RealMatrix stagePoints = new Array2DRowRealMatrix(new double[]{stage1.x, stage1.y, stage2.x, stage2.y, stage3.x, stage3.y});
      //invert stagePoints matrix
      RealMatrix stagePointsInv = new LUDecomposition(pixPoints).getSolver().getInverse();
      RealMatrix A = stagePointsInv.multiply(stagePoints);
      AffineTransform transform = new AffineTransform(new double[]{A.getEntry(0,0),A.getEntry(3,0),A.getEntry(1,0),A.getEntry(4,0)});
      return transform;
   }

  
}
