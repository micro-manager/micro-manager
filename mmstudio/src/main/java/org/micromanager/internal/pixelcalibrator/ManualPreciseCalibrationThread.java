
package org.micromanager.internal.pixelcalibrator;

import com.google.common.eventbus.Subscribe;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.display.internal.event.DisplayMouseEvent;
import org.micromanager.internal.utils.*;

/**
 * The idea is to calibrate the camera/stage spatial relation by displaying an 
 * image, have the user click on something they clearly recognize, move the stage
 * have the user click again, etc.., and then calculate an affine transform
 * relating the stage movement with the user-provided image movement.
 * It is not clear if this will ever be precise.  Matching could possibly 
 * be augmented with cross-correlation.  
 * Since we have automated calibration (sometimes) working, and a simple
 * manual procedure to determine directionality, I will not finish this code
 * now, but leave here if it turns out to be useful.  To finish, look for 
 * inspiration in AutomaticCalibrationThread and ManualSImpleCalibrationThread.
 * 
 * If not used by 2019, delete.
 * 
 * @author nico
 */
public class ManualPreciseCalibrationThread extends CalibrationThread {
   private final Studio studio_;
   private final CMMCore core_;
   private final PixelCalibratorDialog dialog_;
   private final RectangleOverlay overlay_;
   private DisplayController dc_;
   
   private Map<Point2D.Double, Point2D.Double> pointPairs_;

   private DisplayWindow liveWin_;
   
   private Point2D.Double xy0_;

   private double x;
   private double y;
   private int w;
   private int h;
   private int side_small;

   private class CalibrationFailedException extends Exception {

      private static final long serialVersionUID = 4749723616733251885L;

      public CalibrationFailedException(String msg) {
         super(msg);
         if (xy0_ != null) {
            try {
               core_.setXYPosition( xy0_.x, xy0_.y);
               studio_.live().snap(true);
            } catch (Exception ex) {
               // annoying but at this point better to not bother the user 
               // with failure after failure
            }
         }
         cleanup();

      }
   }

   ManualPreciseCalibrationThread (Studio app, PixelCalibratorDialog dialog) {
      studio_ = app;
      core_ = studio_.getCMMCore();
      dialog_ = dialog;
      overlay_ = new RectangleOverlay();
   }

   private void cleanup() {
         if (overlay_ != null) {
            overlay_.setVisible(false);
         }
         if (liveWin_ != null) {
            liveWin_.setCustomTitle("Preview");
            if (overlay_ != null) {
               liveWin_.removeOverlay(overlay_);
            }
         }
   }


   private void snapImageAt(double x, double y)
           throws CalibrationFailedException {
      try {
         Point2D.Double p0 = core_.getXYStagePosition();
         if (p0.distance(x, y) > (dialog_.safeTravelRadius() / 2)) {
            throw new CalibrationFailedException("XY stage safety limit reached.");
         }
         core_.setXYPosition(x, y);
         core_.waitForDevice(core_.getXYStageDevice());
         core_.snapImage();
         TaggedImage image = core_.getTaggedImage();
         studio_.live().displayImage(studio_.data().convertTaggedImage(image));
         if (studio_.live().getDisplay() != null) {
            if (liveWin_ != studio_.live().getDisplay()) {
               liveWin_ = studio_.live().getDisplay();
               liveWin_.setCustomTitle("Calibrating...");
               overlay_.setVisible(true);
               liveWin_.addOverlay(overlay_);
            }
         }
      } catch (CalibrationFailedException e) {
         throw e;
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         throw new CalibrationFailedException(ex.getMessage());
      }

   }


   private Point2D.Double runSearch(final double dxi, final double dyi)
      throws InterruptedException, CalibrationFailedException
   {

      double dx = dxi;
      double dy = dyi;
      Point2D.Double d = new Point2D.Double(0., 0.);

      // Now continue to double displacements and match acquired half-size 
      // images with expected half-size images

      for (int i=0;i<25;i++) {

         core_.logMessage(dx+","+dy+","+d);
         if ((2*d.x+side_small/2)>=w/2 || (2*d.y+side_small/2)>=h/2 || (
                 2*d.x-side_small/2)<-(w/2) || (2*d.y-side_small/2)<-(h/2)) {
            break;
         }

         dx *= 2;
         dy *= 2;
         
         d.x *= 2;
         d.y *= 2;

         d = measureDisplacement(x+dx, y+dy, d, false);
         incrementProgress();
      }
      Point2D.Double stagePos;
      try {
         stagePos = core_.getXYStagePosition();
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         stagePos = null;
         throw new CalibrationFailedException(ex.getMessage());
      }
      pointPairs_.put(new Point2D.Double(d.x, d.y),stagePos);
      return stagePos;

   }

   
   private Point2D.Double measureDisplacement(double x1, double y1, Point2D.Double d,
           boolean display)
           throws InterruptedException, CalibrationFailedException 
   {      
      if (AutomaticCalibrationThread.interrupted()) {
         throw new InterruptedException();
      }
      snapImageAt(x1, y1);
      // TODO: 
      // Prompt the user to click and register the location
      
      // overlay_.set(guessRect);
      Point2D.Double dChange = new Point2D.Double();
      // TODO: dChange should be the displacement from the reference point
      return new Point2D.Double(d.x + dChange.x, d.y + dChange.y);
   }
   
   private int smallestPowerOf2LessThanOrEqualTo(int x) {
      return 1 << ((int) Math.floor(Math.log(x)/Math.log(2)));
   }


   private AffineTransform getFirstApprox()
           throws InterruptedException, CalibrationFailedException {

      Point2D.Double p;
      try {
         p = core_.getXYStagePosition();
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         throw new CalibrationFailedException(ex.getMessage());
      }
      x = p.x;
      y = p.y;

      // First find the smallest detectable displacement.
      snapImageAt(x,y);

      try {
         w = studio_.live().getDisplay().getDataProvider().getAnyImage().getWidth();
         h = studio_.live().getDisplay().getDataProvider().getAnyImage().getHeight();
      } catch (IOException io) {
      }
      int w_small = smallestPowerOf2LessThanOrEqualTo(w/4);
      int h_small = smallestPowerOf2LessThanOrEqualTo(h/4);
      side_small = Math.min(w_small, h_small);
/*
      referenceImage_ = getSubImage(baseImage, (-side_small/2+w/2),
              (-side_small/2+h/2),side_small,side_small);

      pointPairs_.clear();
      pointPairs_.put(new Point2D.Double(0.,0.),new Point2D.Double(x,y));
      runSearch(0.1,0,simulate);

      // Re-acquire the reference image, since we may not be exactly where 
      // we started from after having called runSearch().
      referenceImage_ = getSubImage(baseImage, (-side_small/2+w/2),
            (-side_small/2+h/2),side_small,side_small);
*/
      runSearch(0,0.1);

      return MathFunctions.generateAffineTransformFromPointPairs(pointPairs_);
   }
   

   private void measureCorner(final AffineTransform firstApprox, final Point c1, 
           final boolean simulate)
      throws InterruptedException, CalibrationFailedException
   {
      Point2D.Double c1d = new Point2D.Double(c1.x, c1.y);
      Point2D.Double s1 = (Point2D.Double) firstApprox.transform(c1d, null);
      //Point2D.Double c2 = measureDisplacement(s1.x, s1.y, c1d, false, simulate);
      Point2D.Double s2;
      try {
         s2 = core_.getXYStagePosition();
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         throw new CalibrationFailedException(ex.getMessage());
      }
      //pointPairs_.put(new Point2D.Double(c2.x, c2.y), s2);
      incrementProgress();
   }
   

   private AffineTransform getSecondApprox(final AffineTransform firstApprox, 
           final boolean simulate)
      throws InterruptedException, CalibrationFailedException
   {
      pointPairs_.clear();
      int ax = w/2 - side_small/2;
      int ay = h/2 - side_small/2;

      measureCorner(firstApprox, new Point(-ax,-ay), simulate);
      measureCorner(firstApprox, new Point(-ax,ay), simulate);
      measureCorner(firstApprox, new Point(ax,ay), simulate);
      measureCorner(firstApprox, new Point(ax,-ay), simulate);
      try {
         return MathFunctions.generateAffineTransformFromPointPairs(
                 pointPairs_, 2.0, Double.MAX_VALUE);
      } catch (Exception ex) {
         ReportingUtils.logError(ex.getMessage());
      }
      return null;
   }

   
   @Subscribe
   public void processMouseEvent(DisplayMouseEvent dme) {
      if (dme.getEvent().getClickCount() == 1 && 
              dme.getEvent().getButton() == 1) {
         int modifiersEx = dme.getEvent().getModifiersEx();
         boolean pressed  = InputEvent.BUTTON1_DOWN_MASK == (modifiersEx & InputEvent.BUTTON1_DOWN_MASK);
         if (pressed) {
               
         }
      }
   }
   

   private AffineTransform runCalibration()
      throws InterruptedException, CalibrationFailedException
   {
      return runCalibration(false);
   }

   
   private AffineTransform runCalibration(boolean simulation)
      throws InterruptedException, CalibrationFailedException
   {
      pointPairs_ = new HashMap<Point2D.Double, Point2D.Double>();
      try {
          xy0_ = core_.getXYStagePosition();
      }
      catch (Exception e) {
         throw new CalibrationFailedException(e.getMessage());
      }
      final AffineTransform firstApprox = getFirstApprox();
      setProgress(20);
      final AffineTransform secondApprox = getSecondApprox(firstApprox, simulation);
      if (secondApprox != null) {
         ReportingUtils.logMessage(secondApprox.toString());
      }
      try {
         core_.setXYPosition( xy0_.x, xy0_.y);
         studio_.live().snap(true);
      }
      catch (Exception e) {
         throw new CalibrationFailedException(e.getMessage());
      }
      overlay_.setVisible(false);
      if (liveWin_ != null) {
         liveWin_.setCustomTitle("Preview");
         liveWin_.removeOverlay(overlay_);
      }
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
            @Override 
            public void run() {
               cleanup();
               dialog_.calibrationFailed(true);
            }
         });
         return;
      }
      catch (final CalibrationFailedException e) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override 
            public void run() {
               cleanup();
               ReportingUtils.showError(e);
               dialog_.calibrationFailed(false);
            }
         });
         return;
      }
      SwingUtilities.invokeLater(new Runnable() {
         @Override 
         public void run() {
            dialog_.calibrationDone();
         }
      });
   }

   private synchronized void incrementProgress() {
      progress_++;
      dialog_.update();
   }

   synchronized void setProgress(int value) {
      progress_ = value;
   }
   
   
   private class DialogFrame extends JFrame {

      private static final long serialVersionUID = -7944616693940334489L;
      private final Object caller_;
      private final JButton okButton_;
      private final JLabel explanationLabel_;

      public DialogFrame(Object caller) {
         caller_ = caller;
         explanationLabel_ = new JLabel();
         super.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
         super.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
               dispose();
            }
         });
         super.setLayout(new MigLayout());
         final String label1Text = "<html>This method creates an affine transform based on"
                 + " a <br>pixelSize of "
                 + NumberUtils.doubleToDisplayString(dialog_.getCalibratedPixelSize() * 1000.0)
                 + " nm per pixel.  If this is not "
                 + "correct, <br>please cancel and first set the correct pixelSize.<br><br>"
                 + "Focus the image in the Preview window and use the <br>mouse pointer to click "
                 + "on an object somehwere <br>near the center of the image.";
         explanationLabel_.setText(label1Text);
         super.add(explanationLabel_, "span 2, wrap");

         okButton_ = new JButton("OK");
         okButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae){
               explanationLabel_.setText(label1Text);
               okButton_.setVisible(false);
            }
         });
         okButton_.setVisible(false);
         super.add(okButton_, "tag ok");

         JButton cancelButton = new JButton("Cancel");
         cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               dispose();
            }
         });
         super.add(cancelButton, "tag cancel, wrap");
         super.pack();

         super.setLocation(200, 200);
         WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);
         super.setVisible(true);
      }

      public void setLabelText(String newText) {
         if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
               @Override
               public void run() {
                  setLabelText(newText);
               }
             });
         }
         explanationLabel_.setText(newText);
      }

      @Override
      public void dispose() {
         super.dispose();
         if (dc_ != null) {
            dc_.unregisterForEvents(caller_);
         }
         synchronized(CalibrationThread.class) {
            CalibrationThread.class.notifyAll();
         }
         //running_.set(false);
         dialog_.calibrationFailed(true);
      }

      public void setOKButtonVisible(boolean visible) {
         okButton_.setVisible(visible);
      }
   }


}
