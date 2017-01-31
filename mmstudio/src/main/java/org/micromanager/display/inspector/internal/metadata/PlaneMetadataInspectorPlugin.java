/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal.metadata;

import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.InspectorPlugin;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;
import org.micromanager.display.inspector.InspectorPanelController;

/**
 *
 * @author mark
 */
@Plugin(type = InspectorPlugin.class,
      priority = Priority.HIGH_PRIORITY + 100,
      name = "Plane Metadata",
      description = "View image plane metadata")
public class PlaneMetadataInspectorPlugin implements InspectorPlugin {
   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      // This should always be true; just a sanity check
      return viewer.getDataProvider() != null;
   }

   @Override
   public InspectorPanelController createPanel() {
      return PlaneMetadataPanelController.create();
   }
}