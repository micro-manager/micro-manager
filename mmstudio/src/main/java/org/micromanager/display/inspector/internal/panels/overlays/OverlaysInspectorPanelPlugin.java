/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.inspector.internal.panels.overlays;

import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;

/**
 * @author mark
 */
@Plugin(type = InspectorPanelPlugin.class,
      priority = Priority.NORMAL + 100.0,
      name = "Overlays",
      description = "Add overlay graphics to displayed images")
public final class OverlaysInspectorPanelPlugin implements InspectorPanelPlugin {
   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      return viewer instanceof DisplayWindow;
   }

   @Override
   public InspectorPanelController createPanelController(Studio studio) {
      return OverlaysInspectorPanelController.create(studio);
   }
}