package org.micromanager.internal;

import com.google.common.eventbus.Subscribe;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Line;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
//import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.inspector.internal.panels.intensity.ImageStatsPublisher.ImageStatsChangedEvent;
import org.micromanager.internal.graph.GraphData;
import org.micromanager.internal.graph.GraphFrame;

/**
 * This class collects information related to the Line Profile display.
 */
public final class LineProfile {
   private GraphData lineProfileData_;
   private final GraphFrame profileWin_;
   private final DisplayWindow display_;

   /**
    * Constructs the Line Profile object.
    *
    * @param display Viewer we should look at
    */
   public LineProfile(DisplayWindow display) {
      display_ = display;
      display_.registerForEvents(this);
      calculateLineProfileData(display_.getImagePlus());

      profileWin_ = new GraphFrame(this::updateLineProfile);
      profileWin_.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent event) {
            cleanup();
         }
      });
      profileWin_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      profileWin_.setData(lineProfileData_);
      profileWin_.setLabels("Pixel", "Intensity");
      profileWin_.setAutoScale();
      profileWin_.setTitle("Line profile for " + display_.getName());
      profileWin_.setVisible(true);
   }

   private void calculateLineProfileData(ImagePlus imp) {
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

   public void updateLineProfile() {
      calculateLineProfileData(WindowManager.getCurrentImage());
      profileWin_.setData(lineProfileData_);
   }

   @Subscribe
   public void onPixelsSet(ImageStatsChangedEvent event) {
      updateLineProfile();
   }

   // TODO
   /*
   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      cleanup();
   }
*/

   /**
    * Unregister from display events and dispose of the profile Window.
    */
   public void cleanup() {
      display_.unregisterForEvents(this);
      if (profileWin_ != null) {
         profileWin_.dispose();
      }
   }
}
