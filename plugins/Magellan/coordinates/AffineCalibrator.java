/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package coordinates;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij3d.image3d.FHTImage3D;
import imageconstruction.CoreCommunicator;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class AffineCalibrator {
 

   private CountDownLatch nextImageLatch_ = new CountDownLatch(1);
   private AffineGUI affineGui_ ;
   private volatile boolean abort_ =false;
   
   public AffineCalibrator(AffineGUI ag) {
      affineGui_ = ag;
      //new thread to not hold up EDT
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               computeAffine();
            } catch (Exception ex) {
               ReportingUtils.showError("Aborting due to problem calibrating affine: " + ex.toString());
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
   }
   
   public void computeAffine() throws Exception {
      CMMCore core = MMStudio.getInstance().getCore();
      String xyStage = core.getXYStageDevice();
      Point2D.Double[] stagePositions = new Point2D.Double[3], pixPositions = new Point2D.Double[3];

      stagePositions[0] = new Point2D.Double(core.getXPosition(xyStage) ,core.getYPosition(xyStage) );
      TaggedImage img0 = snapAndAdd();
      nextImageLatch_ = new CountDownLatch(1);
      ReportingUtils.showMessage("Move stage to new position with same features visible, and press Capture");
      nextImageLatch_.await();
      if(abort_) {        
         return;
      }
      
      stagePositions[1] = new Point2D.Double(core.getXPosition(xyStage), core.getYPosition(xyStage));
      TaggedImage img1 = snapAndAdd();
      nextImageLatch_ = new CountDownLatch(1);
      ReportingUtils.showMessage("Move stage to new position with same features visible, and press Capture");
      nextImageLatch_.await();
      if(abort_) {
         return;
      }
      
      stagePositions[2] = new Point2D.Double(core.getXPosition(xyStage), core.getYPosition(xyStage));
      TaggedImage img2 = snapAndAdd();

      //use Xcorr to calculate pixel coordinates
//      IJ.log("Calculating Affine Transform. This process may take several minutes");
      pixPositions[0] = new Point2D.Double(0,0); // define first one as the origin
      pixPositions[1] = crossCorrelate(img0, img1);
      pixPositions[2] = crossCorrelate(img0, img2);

      //3) compute affine from three pairs of points
      AffineTransform transform = computeAffine(pixPositions[0], pixPositions[1], pixPositions[2],
              stagePositions[0], stagePositions[1], stagePositions[2]);
      
     //ask if user likes this affine transform and wants to store it
      int result = JOptionPane.showConfirmDialog(affineGui_, "Calulcated affine transform matrix: " + transform.toString() + ". Would you like to store these settings?",
               "Store calculated affine transform?", JOptionPane.YES_NO_OPTION);
      if (result == JOptionPane.YES_OPTION) {
         //store affine
         Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);
         JavaUtils.putObjectInPrefs(prefs, "affine_transform_" + core.getCurrentPixelSizeConfig(), transform);
         //mark as updated
         AffineUtils.transformUpdated(core.getCurrentPixelSizeConfig(), transform);
      }
   }

   private Point2D.Double crossCorrelate(TaggedImage img1, TaggedImage img2) throws Exception {    
      //double the width of iamges used for xCorr to support offsets bigger than half the iage size
       int width = 2 * MDUtils.getWidth(img1.tags);
       int height = 2 * MDUtils.getHeight(img1.tags);
       ImageStack stack1 = new ImageStack(width, height);
       ImageStack stack2 = new ImageStack(width, height);
       byte[] newPix1 = new byte[width * height];
       byte[] newPix2 = new byte[width * height];
       //fill with background pix value to not throw things off
       byte[] sortedPix1 = Arrays.copyOf((byte[]) img1.pix, (width / 2) * (height / 2));
       byte[] sortedPix2 = Arrays.copyOf((byte[]) img2.pix, (width / 2) * (height / 2));
       Arrays.sort(sortedPix1); 
       Arrays.sort(sortedPix2); 
       Arrays.fill(newPix1, sortedPix1[(int) (sortedPix1.length * 0.1)]);
       Arrays.fill(newPix2, sortedPix2[(int) (sortedPix2.length * 0.1)]);
       for (int y = 0; y < height / 2; y++) {
           System.arraycopy(img1.pix, y*(width/2), newPix1, (y+height/4)*width + width/4, width/2);
           System.arraycopy(img2.pix, y*(width/2), newPix2, (y+height/4)*width + width/4, width/2);
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
   private TaggedImage snapAndAdd() throws Exception {
      ScriptInterface gui = MMStudio.getInstance();
      CMMCore core = gui.getMMCore();
      boolean liveOn = gui.isLiveModeOn();
      gui.enableLiveMode(false);
      core.snapImage();  
      TaggedImage image = core.getTaggedImage();
      gui.addToAlbum(image);
      gui.enableLiveMode(liveOn);
      return image;
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
