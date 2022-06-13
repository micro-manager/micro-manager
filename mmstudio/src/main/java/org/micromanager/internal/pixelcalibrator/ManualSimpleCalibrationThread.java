///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2018
//
// COPYRIGHT:    Regents of the University of California, 2018
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

package org.micromanager.internal.pixelcalibrator;

import com.google.common.eventbus.Subscribe;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.display.internal.event.DisplayMouseEvent;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * Simple, manual way of calibrating.  Only provides directionality, no skew or tilt.
 *
 * @author Nico
 */
public class ManualSimpleCalibrationThread extends CalibrationThread {
   private final Studio studio_;
   private final CMMCore core_;
   private final PixelCalibratorDialog dialog_;
   private DialogFrame dialogFrame_;
   private DisplayController dc_;

   private final JLabel explanationLabel_;
   private Point2D.Double initialStagePosition_;
   private final Point2D[] points_;

   private int counter_;


   ManualSimpleCalibrationThread(Studio studio, PixelCalibratorDialog dialog) {
      studio_ = studio;
      core_ = studio_.getCMMCore();
      dialog_ = dialog;
      explanationLabel_ = new JLabel();
      int nrPoints = 3;
      points_ = new Point2D[nrPoints];
   }

   @Override
   public void run() {
      synchronized (this) {
         progress_ = 0;
      }
      final ManualSimpleCalibrationThread instance = this;
      result_ = null;
      counter_ = 0;

      try {
         initialStagePosition_ = core_.getXYStagePosition();
      } catch (Exception ex) {
         ReportingUtils.showError(
               "Failed to set stage position. Can not calibrate without working stage");
         dialog_.calibrationFailed(true);
         return;
      }

      SwingUtilities.invokeLater(() -> dialogFrame_ = new DialogFrame(instance));

      //running_.set(true);
      DisplayWindow display = studio_.live().getDisplay();
      if (display == null) {
         studio_.live().snap(true);
         long waitUntil = System.currentTimeMillis() + 5000;
         display = studio_.live().getDisplay();
         while (display == null && System.currentTimeMillis() < waitUntil) {
            try {
               display = studio_.live().getDisplay();
               Thread.sleep(100);
            } catch (InterruptedException ex) {
               ReportingUtils.logError(ex);
            }
         }
      }
      if (display == null) {
         ReportingUtils.showError("Preview window did not open. Is the exposure time very long?");
         dialogFrame_.dispose();
      } else if (display instanceof DisplayController) {
         dc_ = (DisplayController) display;
         dc_.registerForEvents(this);
         synchronized (CalibrationThread.class) {
            try {
               CalibrationThread.class.wait();
            } catch (InterruptedException ie) {
               ReportingUtils.logError(ie);
            }
            dialogFrame_.dispose();
         }
      }
   }

   /**
    * Processes mouse event.
    *
    * @param dme Mouse Event to be processed.
    */
   @Subscribe
   public void processMouseEvent(DisplayMouseEvent dme) {
      if (dme.getEvent().getClickCount() == 1
            && dme.getEvent().getButton() == 1) {
         int modifiersEx = dme.getEvent().getModifiersEx();
         boolean pressed =
               InputEvent.BUTTON1_DOWN_MASK == (modifiersEx & InputEvent.BUTTON1_DOWN_MASK);
         if (pressed) {
            points_[counter_] = dme.getCenterLocation();
            double d = dialog_.getCalibratedPixelSize();
            String label1Text = "";
            try {
               int minSize = (int) Math.min(
                     core_.getImageWidth(), core_.getImageHeight());
               int nrPixels = minSize / 4;
               switch (counter_) {
                  case 0:
                     points_[counter_] = dme.getCenterLocation();
                     core_.setRelativeXYPosition(d * nrPixels, 0.0);
                     label1Text = "<html>Perfect!  <br><br>The stage was moved " + d * nrPixels
                           + " microns along the x axis.<br><br>" + " Click on the same object";
                     break;
                  case 1:
                     points_[counter_] = dme.getCenterLocation();
                     core_.setRelativeXYPosition(-d * nrPixels, d * nrPixels);
                     label1Text = "<html>Nice!  <br><br>The stage was moved " + d * nrPixels
                           + " microns along the y axis.<br><br>" + " Click on the same object";
                     break;
                  case 2:
                     points_[counter_] = dme.getCenterLocation();
                     core_.setRelativeXYPosition(0, -d * nrPixels);
                     // Done!  now calculate affine transform, and ask the user
                     // if OK.
                     counter_ = 0;
                     super.result_ = calculateAffineTransform(d, points_);
                     if (result_ == null) {
                        label1Text = "<html>Could not figure out orientation. <br><br>"
                              + "Try again?<br><br>";
                        if (dialogFrame_ != null) {
                           dialogFrame_.setLabelText(label1Text);
                           dialogFrame_.setOKButtonVisible(true);
                        }
                     } else {
                        dialogFrame_.dispose();
                        dialog_.calibrationDone();
                        return;
                     }
                     break;
                  default: {
                     ReportingUtils.logError("Wrong input for switch statement");
                     break;
                  }
               }
               counter_++;

               studio_.live().snap(true);

               if (dialogFrame_ != null) {
                  dialogFrame_.setLabelText(label1Text);
               }
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
      }
   }


   private AffineTransform calculateAffineTransform(double pixelSize, Point2D[] points) {
      AffineTransform at = AffineUtils.doubleToAffine(AffineUtils.noTransform());
      boolean rotate = Math.abs(points[1].getX() - points[0].getX())
            < Math.abs(points[1].getY() - points[0].getY());
      // sanity check for rotate
      if (!(rotate == Math.abs(points[2].getY() - points[0].getY())
            < Math.abs(points[2].getX() - points[0].getX()))) {
         return null;
      }
      // Figured out direction experimentally..  It does not make sens to me either
      int xDirection = -1;
      int yDirection = -1;
      if (!rotate) {
         if (points[1].getX() < points[0].getX()) {
            xDirection = 1;
         }
         if (points[2].getY() < points[0].getY()) {
            yDirection = 1;
         }
      } else {
         xDirection = 1;
         if (points[1].getY() > points[0].getY()) {
            xDirection = -1;
         }
         if (points[2].getX() > points[0].getX()) {
            yDirection = 1;
         }
      }

      at.scale(xDirection * pixelSize, yDirection * pixelSize);
      if (rotate) {
         at.rotate(-Math.PI * 0.5);
      }

      return at;
   }

   private class DialogFrame extends JFrame {

      private static final long serialVersionUID = -7944616693940334489L;
      private final Object caller_;
      private final JButton okButton_;

      public DialogFrame(Object caller) {
         caller_ = caller;
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
               + "on an object somewhere <br>near the center of the image.";
         explanationLabel_.setText(label1Text);
         super.add(explanationLabel_, "span 2, wrap");

         okButton_ = new JButton("OK");
         okButton_.addActionListener(ae -> {
            counter_ = 0;
            explanationLabel_.setText(label1Text);
            okButton_.setVisible(false);
         });
         okButton_.setVisible(false);
         super.add(okButton_, "tag ok");

         JButton cancelButton = new JButton("Cancel");
         cancelButton.addActionListener(e -> dispose());
         super.add(cancelButton, "tag cancel, wrap");
         super.pack();
         super.setIconImage(Toolkit.getDefaultToolkit().getImage(
               getClass().getResource("/org/micromanager/icons/microscope.gif")));
         super.setLocation(200, 200);
         WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);
         super.setVisible(true);
      }

      public void setLabelText(String newText) {
         if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setLabelText(newText));
         }
         explanationLabel_.setText(newText);
      }

      @Override
      public void dispose() {
         super.dispose();
         if (dc_ != null) {
            dc_.unregisterForEvents(caller_);
         }
         synchronized (CalibrationThread.class) {
            CalibrationThread.class.notifyAll();
         }
         //running_.set(false);
         dialog_.calibrationFailed(true);
      }

      public void setOKButtonVisible(boolean visible) {
         okButton_.setVisible(visible);
      }

   }

   private synchronized void incrementProgress() {
      progress_++;
      dialog_.update();
   }

   synchronized void setProgress(int value) {
      progress_ = value;
   }

   private class CalibrationFailedException extends Exception {

      private static final long serialVersionUID = 4749723616733251885L;

      public CalibrationFailedException(String msg) {
         super(msg);
         if (initialStagePosition_ != null) {
            try {
               core_.setXYPosition(initialStagePosition_.x, initialStagePosition_.y);
               studio_.live().snap(true);
            } catch (Exception ex) {
               // annoying but at this point better to not bother the user 
               // with failure after failure
            }
         }

      }
   }
}
