///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
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
import org.micromanager.data.Image;
import org.micromanager.display.DataViewer;
import org.micromanager.internal.utils.ReportingUtils;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MMROIManager {
  private final MMStudio studio_;

  public MMROIManager(MMStudio studio) {
    studio_ = studio;
  }

  public void setROI() {
    ImagePlus curImage = WindowManager.getCurrentImage();
    if (curImage == null) {
      studio_.logs().showError("There is no open image window.");
      return;
    }

    Roi roi = curImage.getRoi();
    if (roi == null) {
      // Nothing to be done.
      studio_
          .logs()
          .showError(
              "There is no selection in the image window.\nUse the ImageJ rectangle tool to draw the ROI.");
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

  /** Adjust the provided rectangular ROI based on any current ROI that may be in use. */
  private Rectangle updateROI(Roi roi) {
    Rectangle r = roi.getBounds();

    // If the image has ROI info attached to it, correct for the offsets.
    // Otherwise, assume the image was taken with the current camera ROI
    // (which is a horrendously buggy way to do things, but that was the
    // old behavior and I'm leaving it in case there are cases where it is
    // necessary).
    Rectangle originalROI = null;

    DataViewer viewer = studio_.displays().getActiveDataViewer();
    if (viewer != null) {
      try {
        List<Image> images = viewer.getDisplayedImages();
        // Just take the first one.
        originalROI = images.get(0).getMetadata().getROI();
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

    r.x += originalROI.x;
    r.y += originalROI.y;
    return r;
  }

  public void setCenterQuad() {
    ImagePlus curImage = WindowManager.getCurrentImage();
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
}
