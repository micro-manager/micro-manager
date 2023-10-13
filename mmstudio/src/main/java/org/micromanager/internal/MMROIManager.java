///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       
//
// COPYRIGHT:    University of California, San Francisco, 2014
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

package org.micromanager.internal;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Manages Regions of interest (ROI). This mainly involves receiving UI input
 * declaring the user's desire for an ROI to be send to the camera.
 */
public class MMROIManager {
   private final MMStudio studio_;

   public MMROIManager(MMStudio studio) {
      studio_ = studio;
   }

   /**
    * Enquires with the UI what ROI(s) are set, and send these to the camera.
    */
   public void setROI() {
      ImagePlus curImage = getCurrentImagePlus();
      if (curImage == null) {
         studio_.logs().showError("There is no open image window.");
         return;
      }

      Roi roi = curImage.getRoi();
      if (roi == null) {
         // Nothing to be done.
         studio_.logs().showError(
               "There is no selection in the image window.\n"
                     + "Use the ImageJ rectangle tool to draw the ROI.");
         return;
      }
      if (roi.getType() == Roi.RECTANGLE) {
         try {
            studio_.app().setROI(updateROI(roi));
         } catch (Exception e) {
            // Core failed to set new ROI.
            studio_.logs().logError(e, "Unable to set new ROI");
         }
         return;
      }
      // Dealing with multiple ROIs; this may not be supported.
      try {
         if (!(roi instanceof ShapeRoi && studio_.core().isMultiROISupported())) {
            handleError("ROI must be a rectangle.\nUse the ImageJ rectangle tool to draw the ROI.");
            return;
         }
      } catch (Exception e) {
         handleError("Unable to determine if multiple ROIs is supported");
         return;
      }
      // Generate list of rectangles for the ROIs.
      ArrayList<Rectangle> rois = new ArrayList<>();
      for (Roi subRoi : ((ShapeRoi) roi).getRois()) {
         // HACK: just use the bounding box of each sub-ROI. Determining if
         // sub-ROIs are rectangles is difficult (they "decompose" to Polygons
         // once there's more than one at a time, so as far as I can tell we
         // would have to test each angle of each polygon to see if it's
         // 90 degrees and has the correct handedness), and this provides a
         // good- enough solution for now.
         rois.add(updateROI(subRoi));
      }
      try {
         setMultiROI(rois);
      } catch (Exception e) {
         // Core failed to set new ROI.
         studio_.logs().logError(e, "Unable to set new ROI");
      }
   }

   /**
    * Adjust the provided rectangular ROI based on any current ROI that may be
    * in use.
    * Also correct for image rotation and/or flipping that may have been
    * introduced by the Image Flipper plugin.
    */
   private Rectangle updateROI(Roi roi) {
      Rectangle r = roi.getBounds();

      // If the image has ROI info attached to it, correct for the offsets.
      // Otherwise, assume the image was taken with the current camera ROI
      // (which is a horrendously buggy way to do things, but that was the
      // old behavior and I'm leaving it in case there are cases where it is
      // necessary).
      Rectangle originalROI = null;
      Integer rotation = 0;
      Boolean isMirrored = false;

      DataViewer viewer = studio_.displays().getActiveDataViewer();
      if (viewer != null) {
         try {
            List<Image> images = viewer.getDisplayedImages();
            // Just take the first one.
            Metadata metadata = images.get(0).getMetadata();
            if (metadata != null) {
               originalROI = metadata.getROI();
               if (metadata.getUserData().containsInteger(
                       "ImageFlipper-Rotation")) {
                  rotation = metadata.getUserData().getInteger(
                          "ImageFlipper-Rotation", 0);
               }
               if (metadata.getUserData().containsString("ImageFlipper-Mirror")) {
                  isMirrored = metadata.getUserData().getString(
                          "ImageFlipper-Mirror", "Off").equals("On");
               }
            }

         } catch (IOException e) {
            ReportingUtils.showError(e, "There was an error determining the selected ROI");
         }
      }

      if (originalROI == null) {
         try {
            originalROI = studio_.core().getROI();
         } catch (Exception e) {
            // Core failed to provide an ROI.
            studio_.logs().logError(e, "Unable to get core ROI");
            return null;
         }
      }

      // correct for rotation and/or flipping
      if (rotation == 90) {
         int temp = r.x;
         r.x = r.y;
         r.y = temp;
         temp = r.width;
         r.width = r.height;
         r.height = temp;
         r.y = originalROI.height - r.y - r.height;
      } else if (rotation == 180) {
         r.x = originalROI.width - r.x - r.width;
         r.y = originalROI.height - r.y - r.height;
      } else if (rotation == 270) {
         int temp = r.y;
         r.y = r.x;
         r.x = temp;
         temp = r.width;
         r.width = r.height;
         r.height = temp;
         r.x = originalROI.width - r.x - r.width;
      }
      if (isMirrored) {
         r.x = originalROI.width - r.x - r.width;
      }

      r.x += originalROI.x;
      r.y += originalROI.y;
      return r;
   }

   /**
    * Set the ROI to the center quadrant of the current ROI.
    */
   public void setCenterQuad() {
      ImagePlus curImage = getCurrentImagePlus();
      if (curImage == null) {
         return;
      }

      Rectangle r = curImage.getProcessor().getRoi();
      int width = r.width / 2;
      int height = r.height / 2;
      int xOffset = r.x + width / 2;
      int yOffset = r.y + height / 2;

      curImage.setRoi(xOffset, yOffset, width, height);
      Roi roi = curImage.getRoi();
      try {
         studio_.app().setROI(updateROI(roi));
      } catch (Exception e) {
         // Core failed to set new ROI.
         studio_.logs().logError(e, "Unable to set new ROI");
      }
   }

   /**
    * Clears the ROI, i.e. set the camera back to use its full frame.
    */
   public void clearROI() {
      studio_.live().setSuspended(true);
      try {
         studio_.core().clearROI();
         studio_.cache().refreshValues();

      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      studio_.live().setSuspended(false);
   }

   private void setMultiROI(List<Rectangle> rois) throws Exception {
      studio_.live().setSuspended(true);
      studio_.core().setMultiROI(rois);
      studio_.cache().refreshValues();
      studio_.live().setSuspended(false);
   }

   private void handleError(String message) {
      studio_.live().setLiveModeOn(false);
      JOptionPane.showMessageDialog(studio_.uiManager().frame(), message);
      studio_.core().logMessage(message);
   }

   private ImagePlus getCurrentImagePlus() {
      DataViewer dv = studio_.displays().getActiveDataViewer();
      DisplayWindow dw = null;
      if (dv instanceof DisplayWindow) {
         dw = (DisplayWindow) dv;
      }
      ImagePlus curImage = null;
      if (dw != null) {
         curImage = dw.getImagePlus();
      }
      return curImage;
   }

}
