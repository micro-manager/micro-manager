package org.micromanager.tileddataviewer;

import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;

@Plugin(type = InspectorPanelPlugin.class,
      priority = Priority.NORMAL - 50.0,
      name = "NDViewer2 Controls",
      description = "Zoom, view, and export controls for NDViewer2")
public final class TiledDataViewerInspectorPanelPlugin implements InspectorPanelPlugin {

   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      return viewer instanceof TiledDataViewerDataViewerAPI;
   }

   @Override
   public InspectorPanelController createPanelController(Studio studio) {
      return new TiledDataViewerInspectorPanelController(studio);
   }
}
