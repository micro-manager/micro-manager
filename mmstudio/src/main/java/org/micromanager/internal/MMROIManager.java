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
import org.micromanager.data.Coords;
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
   // Standard device-adapter properties reporting the full sensor size.
   private static final String ON_CAMERA_X_SIZE = "OnCameraCCDXSize";
   private static final String ON_CAMERA_Y_SIZE = "OnCameraCCDYSize";

   // Grouping all ROI complaints under one title and group keeps the Messages window
   // from filling up when the same out-of-range preset is clicked repeatedly.
   private static final String ROI_ALERT_TITLE = "Region of Interest";

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
            if (studio_.core().getNumberOfCameraChannels() < 2) {
               studio_.app().setROI(updateROI(studio_.core().getCameraDevice(), roi));
               if (!studio_.live().isLiveModeOn() && studio_.settings().getSnapAfterRoiButton()) {
                  studio_.live().snap(true);
               }
            } else {
               studio_.live().setSuspended(true);
               try {
                  for (int c = 0; c < studio_.core().getNumberOfCameraChannels(); c++) {
                     Rectangle r = updateROI(studio_.core().getCameraChannelName(c), roi);
                     studio_.core().setROI(studio_.core().getCameraChannelName(c),
                             r.x, r.y, r.width, r.height);
                  }
               } catch (Exception e) {
                  studio_.logs().showError(e);
               } finally {
                  studio_.cache().refreshValues();
                  studio_.live().setSuspended(false);
                  if (!studio_.live().isLiveModeOn()
                        && studio_.settings().getSnapAfterRoiButton()) {
                     studio_.live().snap(true);
                  }
               }
            }
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
         rois.add(updateROI(studio_.core().getCameraDevice(), subRoi));
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
   private Rectangle updateROI(String camera, Roi roi) {
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
            // Multiple camera images are always multi-channel,
            // so find all images ignoring the channel
            List<Image> images = viewer.getDataProvider().getImagesIgnoringAxes(
                    viewer.getDisplayedImages().get(0).getCoords(), Coords.C);
            // Find the image that matches the requested camera
            Image image = findImageTakenWithCamera(images, camera);
            if (image == null) {
               studio_.logs().logMessage(
                        "Unable to find image taken with camera " + camera);
               return r;
            }
            Metadata metadata = image.getMetadata();
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
      applyRoiToCameras(curImage.getRoi());
   }

   /**
    * Sends the given ImageJ ROI to the camera(s), correcting it for the current camera
    * ROI and for any Image Flipper transformation.
    */
   private void applyRoiToCameras(Roi roi) {
      try {
         if (studio_.core().getNumberOfCameraChannels() < 2) {
            studio_.app().setROI(updateROI(studio_.core().getCameraDevice(), roi));
            if (!studio_.live().isLiveModeOn() && studio_.settings().getSnapAfterRoiButton()) {
               studio_.live().snap(true);
            }
         } else {
            // Address each camera by name.  Application.setROI() sets the ROI on the
            // current camera only, so using it in this loop would set every rectangle
            // on the same camera in turn and leave only the last one in force.
            studio_.live().setSuspended(true);
            try {
               for (int c = 0; c < studio_.core().getNumberOfCameraChannels(); c++) {
                  String cameraChannel = studio_.core().getCameraChannelName(c);
                  Rectangle r = updateROI(cameraChannel, roi);
                  if (r == null) {
                     // updateROI() could not work out this camera's current ROI and has
                     // logged why; leave it alone rather than abandoning the others.
                     continue;
                  }
                  studio_.core().setROI(cameraChannel, r.x, r.y, r.width, r.height);
               }
            } catch (Exception e) {
               studio_.logs().showError(e);
            } finally {
               studio_.cache().refreshValues();
               studio_.live().setSuspended(false);
               if (!studio_.live().isLiveModeOn()
                     && studio_.settings().getSnapAfterRoiButton()) {
                  studio_.live().snap(true);
               }
            }
         }
      } catch (Exception e) {
         // Core failed to set new ROI.
         studio_.logs().logError(e, "Unable to set new ROI");
      }
   }

   /**
    * Sets the camera ROI to a rectangle given in absolute, full-chip coordinates.
    *
    * <p>Unlike {@link #setROI()} and {@link #setCenterQuad()}, which interpret a
    * selection relative to whatever ROI is currently in force, the rectangle passed here
    * is taken to be relative to the full sensor.  Applying the same rectangle twice
    * therefore leaves the camera in the same state, which is what makes stored crop
    * presets reproducible.  The rectangle is clamped to the sensor bounds.
    *
    * @param r the desired ROI, in full-chip coordinates
    */
   public void setAbsoluteROI(Rectangle r) {
      if (r == null) {
         return;
      }

      Rectangle target = new Rectangle(r);
      // A null chip size means we could not work out how big the sensor is; send the
      // rectangle as given and let the device adapter reject it if it is unreasonable.
      Rectangle chip = getFullChipBounds();
      if (chip != null) {
         String chipSize = chip.width + " x " + chip.height;
         target = target.intersection(chip);
         if (target.isEmpty()) {
            // Nothing happened, so say so where the user cannot miss it.
            studio_.alerts().postAlert(ROI_ALERT_TITLE, MMROIManager.class,
                  "ROI not applied. The requested ROI (" + describe(r)
                        + ") lies entirely outside the " + chipSize + " sensor.");
            return;
         }
         if (!target.equals(r)) {
            // Partly off the chip.  Applying the overlap is more useful than refusing,
            // but say so: otherwise the camera quietly ends up with an ROI that is not
            // the one that was asked for.
            studio_.alerts().postAlert(ROI_ALERT_TITLE, MMROIManager.class,
                  "ROI too large. The requested ROI (" + describe(r)
                        + ") extends beyond the " + chipSize + " sensor; using "
                        + describe(target) + " instead.");
         }
      }

      try {
         if (studio_.core().getNumberOfCameraChannels() < 2) {
            studio_.app().setROI(target);
         } else {
            studio_.live().setSuspended(true);
            try {
               for (int c = 0; c < studio_.core().getNumberOfCameraChannels(); c++) {
                  studio_.core().setROI(studio_.core().getCameraChannelName(c),
                        target.x, target.y, target.width, target.height);
               }
            } finally {
               studio_.cache().refreshValues();
               studio_.live().setSuspended(false);
            }
         }
         if (!studio_.live().isLiveModeOn() && studio_.settings().getSnapAfterRoiButton()) {
            studio_.live().snap(true);
         }
      } catch (Exception e) {
         // Core failed to set new ROI.
         studio_.logs().showError(e, "Unable to set new ROI");
      }
   }

   /**
    * Formats a rectangle the same way the crop presets file spells one out, so that
    * messages about a bad preset can be compared with the file at a glance.
    */
   private static String describe(Rectangle r) {
      return "x: " + r.x + ", y: " + r.y + ", width: " + r.width + ", height: " + r.height;
   }

   /**
    * Returns the full sensor area of the current camera, i.e. the ROI that would be in
    * force after clearing it.  The origin is always (0, 0).
    *
    * <p>Returns null rather than a guess when the camera does not report its chip size
    * and an ROI is already set: the current ROI is only a lower bound in that case, and
    * callers are better off not clamping at all than clamping to a size that is too
    * small.
    *
    * @return the full chip bounds, or null if they could not be determined
    */
   public Rectangle getFullChipBounds() {
      try {
         String camera = studio_.core().getCameraDevice();
         if (camera == null || camera.isEmpty()) {
            return null;
         }
         if (studio_.core().hasProperty(camera, ON_CAMERA_X_SIZE)
               && studio_.core().hasProperty(camera, ON_CAMERA_Y_SIZE)) {
            try {
               int width = Integer.parseInt(
                     studio_.core().getProperty(camera, ON_CAMERA_X_SIZE).trim());
               int height = Integer.parseInt(
                     studio_.core().getProperty(camera, ON_CAMERA_Y_SIZE).trim());
               if (width > 0 && height > 0) {
                  return new Rectangle(0, 0, width, height);
               }
            } catch (NumberFormatException e) {
               // Device reported a chip size we cannot read; fall through to the ROI.
               studio_.logs().logError(e, "Unreadable chip size reported by " + camera);
            }
         }
         // No usable chip-size property.  The current ROI tells us the chip size only
         // when it is the full frame; once an ROI is set it is a lower bound that can
         // be far smaller than the sensor.  Guessing from it would clamp valid
         // rectangles down to the current ROI, and each crop would shrink the guess
         // again, so report "unknown" instead and let the caller skip clamping.
         Rectangle roi = studio_.core().getROI();
         if (roi != null && roi.x == 0 && roi.y == 0) {
            return new Rectangle(0, 0, roi.width, roi.height);
         }
         return null;
      } catch (Exception e) {
         studio_.logs().logError(e, "Unable to determine the full chip size");
         return null;
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
      if (!studio_.live().isLiveModeOn() && studio_.settings().getSnapAfterRoiButton()) {
         studio_.live().snap(true);
      }
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

   private Image findImageTakenWithCamera(List<Image> images, String camera) {
      for (Image image : images) {
         Metadata metadata = image.getMetadata();
         if (metadata != null) {
            String imageCam = metadata.getCamera();
            if (imageCam != null && imageCam.equals(camera)) {
               return image;
            }
         }
      }
      return null;
   }

}