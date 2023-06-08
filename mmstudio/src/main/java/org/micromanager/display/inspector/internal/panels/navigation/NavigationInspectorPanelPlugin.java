package org.micromanager.display.inspector.internal.panels.navigation;

import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;

/**
 * Creates the plugin to add the Navigation panel to the Inspector.
 *
 * @author Nico Stuurman, Altos Labs 2023
 */
@Plugin(type = InspectorPanelPlugin.class,
      priority = Priority.NORMAL + 100.0,
      name = "Navigation",
      description = "Add overlay graphics to displayed images")
public final class NavigationInspectorPanelPlugin implements InspectorPanelPlugin {

   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      return viewer instanceof DisplayWindow;
   }

   @Override
   public InspectorPanelController createPanelController(Studio studio) {
      return NavigationInspectorPanelController.create(studio);
   }
}

