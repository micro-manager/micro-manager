/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal.panels.metadata;

import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;

/**
 *
 * @author mark
 */
@Plugin(type = InspectorPanelPlugin.class,
      priority = Priority.HIGH + 100,
      name = "Plane Metadata",
      description = "View image plane metadata")
public final class PlaneMetadataInspectorPanelPlugin implements InspectorPanelPlugin {
   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      // This should always be true; just a sanity check
      return viewer.getDataProvider() != null;
   }

   @Override
   public InspectorPanelController createPanelController(Studio studio) {
      return PlaneMetadataInspectorPanelController.create();
   }
}