///////////////////////////////////////////////////////////////////////////////
// PROJECT:       ImgSharpnessPlugin
//
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nick Anthony, 2021
//
// COPYRIGHT:    Northwestern University, 2021
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
package org.micromanager.sharpnessinspector;

import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;
import org.micromanager.display.inspector.internal.panels.intensity.ImageStatsPublisher;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;

/** @author Nick Anthony */
@Plugin(
    type = InspectorPanelPlugin.class,
    priority = Priority.NORMAL,
    name = "Focus Sharpness",
    description = "View quantitative image sharpness.")
public class SharpnessInspectorPlugin implements InspectorPanelPlugin {
  @Override
  public boolean isApplicableToDataViewer(DataViewer viewer) {
    return viewer.getDataProvider() != null && viewer instanceof ImageStatsPublisher;
  }

  @Override
  public InspectorPanelController createPanelController(Studio studio) {
    return SharpnessInspectorController.create(studio);
  }

  public static String README =
      "This plugin provides a real-time plot of image sharpness. Simply click and drag on an image display to select an ROI and the plugin will begin evaluating the sharpness "
          + "of the image within the ROI using the selected method. Sharpness is evaluated using the same code that is used by the OughtaFocus plugin, for more information about the various evaluation"
          + " methods please view the documentation for the OughtaFocus autofocus plugin.";
}
