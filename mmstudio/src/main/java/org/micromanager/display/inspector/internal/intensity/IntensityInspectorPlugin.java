/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal.intensity;

import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.InspectorPlugin;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.internal.ImageStatsPublisher;

/**
 *
 * @author mark
 */
@Plugin(type = InspectorPlugin.class,
      priority = Priority.VERY_HIGH_PRIORITY,
      name = "Intensity Scaling",
      description = "View and adjust intensity scaling")
public class IntensityInspectorPlugin implements InspectorPlugin {
   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      return viewer.getDataProvider() != null &&
            viewer instanceof ImageStatsPublisher;
   }

   @Override
   public InspectorPanelController createPanel() {
      return IntensityPanelController.create();
   }
}