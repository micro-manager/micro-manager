
package org.micromanager.pixelcalibrator;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.MMStudio;
import org.micromanager.Studio;
import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.MathFunctions;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class CalibrationThread extends Thread {
   private final MMStudio app_;
   private final CMMCore core_;
   private final String xystage_;   //Pointer to the MM xystage.
   
   private final boolean simulated = false; //Whether or not to simulate image acquisition.

   private AffineTransform result_ = null;

   private int progress_ = 0;   //Tracks the progress of the procedure.
   private final PixelCalibratorPlugin plugin_;
   private DisplayWindow liveWin_;  //A display for the acquired images.
   private ImageWindow ccWin_;  //A display of the cross correlation of the sampled image with the reference image.
   private ImageWindow centerWin_;  //A display of the zoomed-in and interpolated center of the cross correlation. This image is searched for a maximum to determine displacement.
   private ImageProcessor referenceImage_;

   private int w;   //The width of the images
   private int h;   //The heigth of the images
   private int side_small; //The length of a side of the square that is sampled from the center of the image for cross correlation.
   
    private AffineTransform firstApprox;
    private AffineTransform secondApprox;
    private double pixelSize;
    
    private Point2D.Double initialPos;
   
    private ImageProcessor theSlide = null;

   private class CalibrationFailedException extends Exception {
      public CalibrationFailedException(String msg) {
         super(msg);
      }
   }

   CalibrationThread(Studio app, PixelCalibratorPlugin plugin) {
      app_ = (MMStudio) app;
      plugin_ = plugin;
      core_ = app_.getCMMCore();
      xystage_ = core_.getXYStageDevice();
   }

   private ImageProcessor crossCorrelate(ImageProcessor proc1, ImageProcessor proc2) {
    //Returns the cross correlation of 2 images.
      FHT h1 = new FHT(proc1);  //Create Fast Hartley Transform object.
      FHT h2 = new FHT(proc2);
      h1.transform();   //Convert to frequency domain
      h2.transform();
      FHT result = h1.conjugateMultiply(h2);    //Conjugate multiplication in the frequency domain is equivalent to correlation in the space domain
      result.inverseTransform();    //Transform back to space domain.
      result.swapQuadrants();   //This needs to be done after transforming. to get back to the original.
      result.resetMinAndMax();  //This is just to scale the contrast when displayed.
      return result;
   }

   private ImageProcessor getSubImage(ImageProcessor proc, int x, int y, int w, int h) {
      FloatProcessor proc2 = new FloatProcessor(w, h);
      proc2.insert(proc,-x,-y);
      return proc2;
   }

   private ImageProcessor snapImageAt(Point2D.Double p) throws CalibrationFailedException {
      if (simulated) {
        return getSubImage(theSlide, (int) (p.x+(3*Math.random()-1.5)), (int) (p.y+(3*Math.random()-1.5)), theSlide.getWidth(), theSlide.getHeight());
      } 
      else {
         try {
            if (initialPos.distance(p) > (plugin_.safeTravelRadiusUm_)) {
                app_.getCMMCore().setXYPosition(initialPos.x, initialPos.y);
                throw new CalibrationFailedException("XY stage safety limit reached.");
            }
            app_.getCMMCore().setXYPosition(p.x, p.y);
            core_.waitForDevice(core_.getXYStageDevice());
            Thread.sleep(10);   //Wait 10 ms for possible vibrations.
            core_.snapImage();
            TaggedImage image = core_.getTaggedImage();
            app_.live().displayImage(app_.data().convertTaggedImage(image));
            if (liveWin_ == null)
               liveWin_ = app_.getSnapLiveManager().getDisplay();
            liveWin_.setCustomTitle("Calibrating...");
            return ImageUtils.makeMonochromeProcessor(image);
         } catch (CalibrationFailedException e) {
            throw e;
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            throw new CalibrationFailedException(ex.getMessage());
         }

      }
   }
   
   private Point2D.Double measureDisplacement(Point2D.Double pi, Point2D.Double d, boolean display)
      throws InterruptedException, CalibrationFailedException
   {
        // Measures the displacement between two images by cross-correlating, and then finding the maximum value.
        // Accurate to one pixel only. pi is the initial stage position (real space) and d is the estimated displacement (pixel space).
       //Returns the measured pixel displacement.
         if (CalibrationThread.interrupted())
            throw new InterruptedException();
         ImageProcessor snap = snapImageAt(pi);
         Rectangle guessRect = new Rectangle((int) ((w-side_small)/2-d.x),(int) ((h-side_small)/2-d.y),side_small,side_small);
         ImageProcessor foundImage = getSubImage(snap,guessRect.x, guessRect.y, guessRect.width, guessRect.height);
         liveWin_.getImagePlus().setRoi(guessRect);    
        ImageProcessor result = crossCorrelate(referenceImage_, foundImage);
        Rectangle centerRect = new Rectangle(result.getWidth() / 2 - 8, result.getHeight() / 2 - 8, 16, 16);
        ImageProcessor resultCenter = getSubImage(result, centerRect.x, centerRect.y, centerRect.width, centerRect.height);
        resultCenter.setInterpolationMethod(ImageProcessor.BICUBIC);
        ImageProcessor resultCenterScaled = resultCenter.resize(resultCenter.getWidth() * 10);  //upsample by a factor of 10
        ImagePlus img = new ImagePlus(" CrossCorr Zoomed", resultCenterScaled);
        Point p = ImageUtils.findMaxPixel(img); //Find the maximum correlation within the zoomed region.
        Point pd = new Point(p.x - img.getWidth() / 2, p.y - img.getHeight() / 2);  //convert to a displacement from center.
        Point2D.Double dChange = new Point2D.Double(pd.x / 10., pd.y / 10.);    //Scale the displacement by 10 to account for the fact that we upsampled by 10.
        if (display){
            if (ccWin_ == null){
                 ccWin_ = new ImageWindow(new ImagePlus("CrossCorr", result));
            }
            else{
                ccWin_.setImage(new ImagePlus("CrossCorr", result));
            }
            ccWin_.getImagePlus().setRoi(centerRect);
            if (centerWin_ == null){
                centerWin_ = new ImageWindow(img);
            }
            else{
                centerWin_.setImage(img);
            }
            Rectangle maxRect = new Rectangle(p.x-4,p.y-4,8,8);
            centerWin_.getImagePlus().setRoi(maxRect);
        }
         return new Point2D.Double(d.x + dChange.x,d.y + dChange.y);    //The pixel position with the maximum correlation.
   }



   private Point2D.Double runSearch(Point2D.Double initPos, double dx, double dy)
      throws InterruptedException, CalibrationFailedException
   {
      Point2D.Double d = new Point2D.Double(0., 0.);
      // Now continue to double displacements and match acquired half-size images with expected half-size images
      for (int i=0;i<25;i++) {
         core_.logMessage(dx+","+dy+","+d);
         if ((2*d.x+side_small/2)>=w/2 || (2*d.y+side_small/2)>=h/2 || (2*d.x-side_small/2)<-(w/2) || (2*d.y-side_small/2)<-(h/2)) {
            break;
         }
         dx = dx * 2;   //Displacement in real space
         dy = dy * 2;   
         d.x = d.x * 2; //Estimated displacement in pixel space
         d.y = d.y * 2;
         Point2D.Double p = new Point2D.Double(initPos.x+dx, initPos.y+dy);
         try{
             d = measureDisplacement(p, d, plugin_.displayCC_); //get the measured pixel displacement.
         }
         catch (CalibrationFailedException err){
             app_.logs().showMessage("Reached end of safe distance");
             break;
         }
         incrementProgress();
      }
      return d;
   }

   private int smallestPowerOf2LessThanOrEqualTo(int x) {
      return 1 << ((int) Math.floor(Math.log(x)/Math.log(2)));
   }

   private AffineTransform getFirstApprox()
      throws InterruptedException, CalibrationFailedException
   {
      Point2D.Double p;
      if (simulated) {
            p = new Point2D.Double(0,0);
            if (theSlide == null){
                theSlide = IJ.getImage().getProcessor();
            }
      } 
      else {
         try {
            p = app_.getCMMCore().getXYStagePosition(); //Initial position.
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            throw new CalibrationFailedException(ex.getMessage());
         }
      }
      // First find the smallest detectable displacement.
      ImageProcessor baseImage = snapImageAt(p);
        
        //Get a smaller image in the middle of the full image.
      referenceImage_ = getSubImage(baseImage, (-side_small/2+w/2), (-side_small/2+h/2),side_small,side_small);

        Map<Point2D.Double, Point2D.Double> pointPairs = new HashMap<Point2D.Double, Point2D.Double>(); //A map comparing points in real space to points in pixel space. This is used for calculating the pixel size.
        pointPairs.put(new Point2D.Double(0.,0.), p); //Put 0,0 in pixel space and the initial stage position in our collection of point pairs.
      Point2D.Double d = runSearch(p,0.1,0);   //This will try to move the sample so that the initial center of the image is now near an edge. It will return the measured displacement of the image in units of pixels.
        Point2D.Double stagePos;
        try {
            stagePos = app_.getCMMCore().getXYStagePosition();
        }
        catch (Exception ex) {
            ReportingUtils.logError(ex);
            stagePos = null;
            throw new CalibrationFailedException(ex.getMessage());
        }
        pointPairs.put(new Point2D.Double(d.x, d.y),stagePos); //Store the newly found pixel displacement with the spatial displacement.
        // Re-acquire the reference image, since we may not be exactly where 
        // we started from after having called runSearch().
        baseImage = snapImageAt(p);
        referenceImage_ = getSubImage(baseImage, (-side_small/2+w/2), (-side_small/2+h/2),side_small,side_small);

        d = runSearch(p,0,0.1);   //Now do the search along the other axis.
        try {
            stagePos = app_.getCMMCore().getXYStagePosition();
        } 
        catch (Exception ex) {
            ReportingUtils.logError(ex);
            stagePos = null;
            throw new CalibrationFailedException(ex.getMessage());
        }
        pointPairs.put(new Point2D.Double(d.x, d.y),stagePos);
        return MathFunctions.generateAffineTransformFromPointPairs(pointPairs);
   }

   private void measureCorner(AffineTransform firstApprox, Point2D.Double c1, Map pointPairs)
      throws InterruptedException, CalibrationFailedException
   {
      Point2D.Double s1 = (Point2D.Double) firstApprox.transform(c1, null); //Transform from C1 (pixel space) to s1 (real space)
      Point2D.Double c2 = measureDisplacement(s1, c1, plugin_.displayCC_);
      Point2D.Double s2;
      try {
         s2 = app_.getCMMCore().getXYStagePosition();
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         throw new CalibrationFailedException(ex.getMessage());
      }
      pointPairs.put(c2, s2);
   }

   private AffineTransform getSecondApprox(AffineTransform firstApprox)
      throws InterruptedException, CalibrationFailedException
   {
        Map<Point2D.Double, Point2D.Double> pointPairs = new HashMap<Point2D.Double, Point2D.Double>(); //A map comparing points in real space to points in pixel space. This is used for calculating the pixel size.
      int ax = w/2 - side_small/2;
      int ay = h/2 - side_small/2;
     
      Point2D.Double c1 = new Point2D.Double(-ax,-ay);
      measureCorner(firstApprox, c1, pointPairs);
      incrementProgress();
      c1 = new Point2D.Double(-ax,ay);
      measureCorner(firstApprox, c1, pointPairs);
      incrementProgress();
      c1 = new Point2D.Double(ax,ay);
      measureCorner(firstApprox, c1, pointPairs);
      incrementProgress();
      c1 = new Point2D.Double(ax,-ay);
      measureCorner(firstApprox, c1, pointPairs);
      incrementProgress();
      try {
         return MathFunctions.generateAffineTransformFromPointPairs(pointPairs, 2.0, Double.MAX_VALUE);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   private AffineTransform runCalibration()
      throws InterruptedException, CalibrationFailedException
   {
      try {
         initialPos = app_.getCMMCore().getXYStagePosition();  //Determine original position
      }
      catch (Exception e) {
         throw new CalibrationFailedException(e.getMessage());
      }
      
        //Determine proper size of images and roi.
        ImageProcessor img = snapImageAt(initialPos);
        w = img.getWidth();
        h = img.getHeight();
        int w_small = smallestPowerOf2LessThanOrEqualTo(w/4);
        int h_small = smallestPowerOf2LessThanOrEqualTo(h/4);
        side_small = Math.min(w_small, h_small);
        img = null; //Delete the image.
      
      firstApprox = getFirstApprox();
      setProgress(20);
      secondApprox = getSecondApprox(firstApprox);
      if (secondApprox != null)
         ReportingUtils.logMessage(secondApprox.toString());
      try {
         app_.getCMMCore().setXYPosition(initialPos.x, initialPos.y); //Go back to home position.
         app_.live().snap(true);
      }
      catch (Exception e) {
         throw new CalibrationFailedException(e.getMessage());
      }
      liveWin_.setCustomTitle("Calibrating...done.");
      liveWin_.getImagePlus().killRoi();
      return secondApprox;
   }

   @Override
   public void run() {
      synchronized (this) {
         progress_ = 0;
      }
      result_ = null;

      try {
         result_ = runCalibration();
      }
      catch (InterruptedException e) {
         // User canceled
         SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
               plugin_.calibrationFailed(true);
            }
         });
         return;
      }
      catch (final CalibrationFailedException e) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
               ReportingUtils.showError(e);
               plugin_.calibrationFailed(false);
            }
         });
         return;
      }
      SwingUtilities.invokeLater(new Runnable() {
         @Override public void run() {
            plugin_.calibrationDone();
         }
      });
   }

   AffineTransform getResult() {
      return result_;
   }

   synchronized int getProgress() {
      return progress_;
   }

   private synchronized void incrementProgress() {
      progress_++;
      plugin_.update();
   }

   synchronized void setProgress(int value) {
      progress_ = value;
   }
}
