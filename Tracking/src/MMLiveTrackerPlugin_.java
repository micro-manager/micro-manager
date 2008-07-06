<<<<<<< .working
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;

/*
 * Created on Dec 8, 2006
 * author: Nenad Amodaj
 */

/**
 * ImageJ plugin wrapper for uManager.
 */
public class MMLiveTrackerPlugin_ implements PlugIn {
   
   private CMMCore core_;
   private int intervalMs_ = 7500;
   private Timer timer_;
   private ImageProcessor ipPrevious_ = null;
   private ImageProcessor ipCurrent_ = null;
   
   private int offset_ = 100;
   private int resolution_ = 5;
   //private static final String TRACK_TITLE = "MM Live Tracking";
   private static final String Stage = "XYStage";
   private Roi roi_;
   private double pixelSizeUm_ = 0.02;
   private ImageStack stack_;
   

   public void run(String arg) {
      
      core_ = MMStudioPlugin.getMMCoreInstance();
      if (core_ == null) {
         IJ.error("Micro-Manager Studio must be running!");
         return;
      }
      
      
      // Setup timer
      ActionListener timerHandler = new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            IJ.write("Timer tick!");
            snapSingleImage();
            processOneFrame(true);
            //snapSingleImage();
            //processOneFrame(false);
         }

      };
      
      timer_ = new Timer(intervalMs_, timerHandler);
      timer_.stop();
                  
      TrackerControlDlg tdlg = new TrackerControlDlg();
      tdlg.intervalMs_ = intervalMs_;
      tdlg.pixelSizeUm_ = pixelSizeUm_;
      tdlg.setVisible(true);
      
      if (!tdlg.track_) {
         IJ.write("Cancelled!");
         timer_.stop();
         return;
      }
      
      intervalMs_ = tdlg.intervalMs_;
      pixelSizeUm_  = tdlg.pixelSizeUm_;
      resolution_ = tdlg.resolutionPix_;
      offset_ = tdlg.offsetPix_;
      timer_.setDelay(intervalMs_);
      track(); 
   }
   
   public void track() {

      //roiStart_ = roi_.getBoundingRect();
      IJ.write("Tracking!");
      timer_.start();
   }

   private boolean snapSingleImage() {

      ImagePlus implus = WindowManager.getImage(MMStudioMainFrame.LIVE_WINDOW_TITLE);
      if (implus == null) {
         IJ.write("Image window with title " + MMStudioMainFrame.LIVE_WINDOW_TITLE + " closed.\n" + "Tracker plugin is now exiting.");
         timer_.stop();
         
         // create a stack window
         ImagePlus impStack = new ImagePlus("Tracker stack", stack_);
         impStack.show();
         impStack.draw();
         return false;
      }
      
      roi_ = implus.getRoi();
      if (roi_ == null || roi_.getType() != Roi.RECTANGLE) {
         IJ.write("Rectangular roi requred.");
         timer_.stop();
         return false;
      }
      
      try {
         core_.snapImage();
         Object img = core_.getImage();
         implus.getProcessor().setPixels(img);
         implus.updateAndDraw();
         ImageWindow iwnd = implus.getWindow();
         iwnd.getCanvas().paint(iwnd.getCanvas().getGraphics());
         ipCurrent_ = implus.getProcessor();
         if (stack_ == null)
            stack_ = new ImageStack(implus.getProcessor().getWidth(), implus.getHeight());         
         stack_.addSlice(Integer.toString(stack_.getSize()+1), implus.getProcessor());
      } catch (Exception e) {
         IJ.error(e.getMessage());
         timer_.stop();
         return false;
      }
      
      IJ.write("Frame acquired!");
      return true;
   }
   
   private void processOneFrame(boolean moveStage) {
      if (ipPrevious_ == null) {
         ipPrevious_ = ipCurrent_;
         return;
      }
      
      // iterate on all offsets
      int kMax = 0;
      int lMax = 0;
      
      Rectangle r = roi_.getBoundingRect();
      IJ.write("ROI pos: " + r.x + "," + r.y);
      
      double corScale = r.width * r.height;
      
      double maxCor = 0; // <<<
      for (int k=-offset_; k<offset_; k += resolution_) {
         for (int l=-offset_; l<offset_; l += resolution_) {

            // calculate correlation
            double sum = 0.0;
            double meanPrev = 0.0;
            double meanCur = 0.0;
            for (int i=0; i<r.height; i++) {
               for (int j=0; j<r.width; j++) {
                  int pixPrev = ipPrevious_.getPixel(r.x + j + l, r.y + i + k);
                  int pixCur = ipCurrent_.getPixel(r.x + j + l, r.y + i + k);
                  sum += (double)pixPrev*pixCur;
                  meanPrev += pixPrev;
                  meanCur += pixCur;
               }
            }
            sum /= corScale;
            meanPrev /= corScale;
            meanCur /= corScale;
            sum /= meanPrev*meanCur;
            
            // check foer max value
            if (sum > maxCor) {
               maxCor = sum;
               kMax = k;
               lMax = l;
            }
         }
      }
      
      IJ.write("maxc=" + maxCor + ", offset=(" + lMax + "," + kMax + ")");
      ipPrevious_ = ipCurrent_;
      
      if (moveStage) {
         
         // offset in um
         double x = lMax * pixelSizeUm_;
         double y = kMax * pixelSizeUm_;
         
         try {
            // obtain current XY stage position
            // NOTE: due to Java parameter passing convention, x and y parameters must be arrays
            double[] xCur = new double[1];
            double[] yCur = new double[1];            
            core_.getXYPosition(Stage, xCur, yCur);
            
            // update the XY position based on the offset
            core_.setXYPosition(Stage, xCur[0]-x, yCur[0]-y);
            core_.waitForDevice(Stage);
            
         } catch (Exception e) {
            IJ.error(e.getMessage());
            timer_.stop();
         } // relative motion
      } else {
         // move the roi
         roi_.setLocation(r.x+lMax, r.y+kMax);
      }
      
   }

}
=======
import ij.IJ;
import ij.plugin.PlugIn;
import mmcorej.CMMCore;

/*
 * Created on Dec 8, 2006
 * author: Nenad Amodaj
 */

/**
 * ImageJ plugin wrapper for uManager.
 */
public class MMLiveTrackerPlugin_ implements PlugIn {
   
   public void run(String arg) {
      
      CMMCore core = MMStudioPlugin.getMMCoreInstance();
      if (core == null) {
         IJ.error("Micro-Manager Studio must be running!");
         return;
      }
          
      TrackerControlDlg tdlg = new TrackerControlDlg(core);
      tdlg.setVisible(true);       
   }
   
}
>>>>>>> .merge-right.r1367
