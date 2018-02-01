package org.micromanager.pixelcalibrator;
import org.apache.commons.math.util.MathUtils;

import java.awt.geom.AffineTransform;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;

import org.micromanager.internal.dialogs.CalibrationEditor;
import org.micromanager.internal.dialogs.CalibrationListDlg;
import org.micromanager.internal.MMStudio;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class PixelCalibratorPlugin implements MenuPlugin, SciJavaPlugin {
   public static final String MENU_NAME = "Pixel Calibrator";
   public static final String TOOL_TIP_DESCRIPTION =
      "Calibrate pixel size by moving XY stage and computing " +
      "image displacement";

   private CMMCore core_;
   private MMStudio app_;
   private CalibrationThread calibrationThread_;
   private PixelCalibratorDialog dialog_;

   double safeTravelRadiusUm_ = 1000;

   public void dispose() {
      stopCalibration();
      if (dialog_ != null) {
         dialog_.setVisible(false);
         dialog_.dispose();
         dialog_ = null;
      }
   }

   @Override
   public String getCopyright() {
      return "University of California, San Francisco, 2009. Author: Arthur Edelstein";
   }

   @Override
   public String getHelpText() {
      return TOOL_TIP_DESCRIPTION;
   }

   @Override
   public String getVersion() {
      return "V1.0";
   }

   @Override
   public void setContext(Studio app) {
      app_ = (MMStudio) app;
      core_ = app.getCMMCore();
   }

   @Override
   public String getName() {
      return MENU_NAME;
   }

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public void onPluginSelected() {
      if (dialog_ == null) {
         dialog_ = new PixelCalibratorDialog(this);
         dialog_.setVisible(true);
      } else {
         dialog_.setPlugin(this);
         dialog_.toFront();
      }
   }

   private double getPixelSize(AffineTransform cameraToStage) {
      return Math.sqrt(Math.abs(cameraToStage.getDeterminant()));
   }

   public synchronized boolean isCalibrationRunning() {
      if (calibrationThread_ != null) {
         return calibrationThread_.isAlive();
      } else {
         return false;
      }
   }

   void startCalibration() {
      calibrationThread_ = new CalibrationThread(app_, this);
      if (!calibrationThread_.isAlive()) {
         calibrationThread_.start();
      }
      dialog_.updateStatus(true, 0);
   }




   void stopCalibration() {
      if (calibrationThread_ != null && calibrationThread_.isAlive()) {
         calibrationThread_.interrupt();
         try {
            calibrationThread_.join();
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   void showFailureMessage() {
      ReportingUtils.showMessage("Calibration failed. Please improve the contrast by adjusting the\n" +
            "sample, focus and illumination. Also make sure the specimen is\n" +
            "securely immobilized on the stage. When you are ready, press\n" +
            "Start to try again.");
   }

   void promptToSaveResult() {
      AffineTransform result = calibrationThread_.getResult();
      if (result == null) {
         // (Shouldn't have gotten here)
         showFailureMessage();
         return;
      }

      double pixelSize = MathUtils.round(getPixelSize(result), 4);

      CalibrationListDlg calDialog = app_.getCalibrationListDlg();
      calDialog.updateCalibrations();
      calDialog.setVisible(true);

      try {
         app_.compat().setCameraTransform(result, core_.getCurrentPixelSizeConfig());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error saving camera transform");
      }

      int response = JOptionPane.showConfirmDialog(null,
            String.format("Affine transform parameters: XScale=%.2f YScale=%.2f XShear=%.4f YShear=%.4f\n", result.getScaleX(), result.getScaleY(), result.getShearX(), result.getShearY()) + 
            "<html>The Pixel Calibrator plugin has measured a pixel size of " + pixelSize + " &#956;m.<br>" + "Do you wish to store this value in your pixel calibration settings?</html>",
            "Pixel calibration succeeded!",
            JOptionPane.YES_NO_OPTION);

      if (response == JOptionPane.YES_OPTION) {
         String pixelConfig;
         try {
            pixelConfig = core_.getCurrentPixelSizeConfig();
            if (pixelConfig.length() > 0) {
               core_.setPixelSizeUm(pixelConfig, pixelSize);

            }
            else {
               CalibrationEditor editor = new CalibrationEditor(app_, 
                       "Res", NumberUtils.doubleToDisplayString(pixelSize));
               editor.setCore(core_);
               editor.editNameOnly();
               editor.setVisible(true);
            }

            calDialog.updateCalibrations();
            calDialog.setVisible(true);
         }
         catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   void update() {
      calibrationThread_.getProgress();
      final double progress = calibrationThread_.getProgress() / 24.;
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            try {
            if (dialog_ != null)
            dialog_.updateStatus(calibrationThread_.isAlive(), progress);
            } catch (NullPointerException e) {
               // Do nothing.
            }
         }
      });

   }

   void calibrationDone() {
      dialog_.updateStatus(false, 1.);
      promptToSaveResult();
   }

   void calibrationFailed(boolean canceled) {
      dialog_.updateStatus(false, 0.);
      if (!canceled) {
         showFailureMessage();
      }
   }
}
