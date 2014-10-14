package org.micromanager;

import ij.gui.Line;
import ij.gui.Roi;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.WindowManager;

import java.awt.Rectangle;

import javax.swing.JFrame;

import org.micromanager.graph.GraphData;
import org.micromanager.graph.GraphFrame;

import org.micromanager.utils.ReportingUtils;

/**
 * This class collects information related to the Line Profile display.
 */
public class LineProfile {
   private static GraphData lineProfileData_;
   private static GraphFrame profileWin_;
   
   public static void openLineProfileWindow() {
      if (WindowManager.getCurrentWindow() == null ||
            WindowManager.getCurrentWindow().isClosed()) {
         ReportingUtils.logError("Nope, window bad");
         return;
      }
      calculateLineProfileData(WindowManager.getCurrentImage());
      if (lineProfileData_ == null) {
         ReportingUtils.logError("Nope, null data");
         return;
      }
      profileWin_ = new GraphFrame();
      profileWin_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      profileWin_.setData(lineProfileData_);
      profileWin_.setAutoScale();
      profileWin_.setTitle("Live line profile");
      MMStudio studio = MMStudio.getInstance();
      profileWin_.setBackground(studio.getBackgroundColor());
      studio.addMMBackgroundListener(profileWin_);
      profileWin_.setVisible(true);
   }

   public static void calculateLineProfileData(ImagePlus imp) {
      Roi roi = imp.getRoi();
      if (roi == null || !roi.isLine()) {
         // if there is no line ROI, create one
         Rectangle r = imp.getProcessor().getRoi();
         int iWidth = r.width;
         int iHeight = r.height;
         int iXROI = r.x;
         int iYROI = r.y;
         if (roi == null) {
            iXROI += iWidth / 2;
            iYROI += iHeight / 2;
         }

         roi = new Line(iXROI - iWidth / 4, iYROI - iWidth / 4, iXROI
               + iWidth / 4, iYROI + iHeight / 4);
         imp.setRoi(roi);
         roi = imp.getRoi();
      }

      ImageProcessor ip = imp.getProcessor();
      ip.setInterpolate(true);
      Line line = (Line) roi;

      if (lineProfileData_ == null) {
         lineProfileData_ = new GraphData();
      }
      lineProfileData_.setData(line.getPixels());
   }

   public static void updateLineProfile() {
      if (WindowManager.getCurrentWindow() == null || profileWin_ == null
            || !profileWin_.isShowing()) {
         return;
      }

      calculateLineProfileData(WindowManager.getCurrentImage());
      profileWin_.setData(lineProfileData_);
   }

   public static void cleanup() {
      if (profileWin_ != null) {
         MMStudio.getInstance().removeMMBackgroundListener(profileWin_);
         profileWin_.dispose();
      }
   }
}
