/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal.panels.comments;

import org.micromanager.Studio;
import org.micromanager.data.Datastore;
import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;

/**
 *
 * @author mark
 */
@Plugin(type = InspectorPanelPlugin.class,
      priority = Priority.HIGH,
      name = "Comments",
      description = "View and edit image comments")
public final class CommentsInspectorPanelPlugin implements InspectorPanelPlugin {
   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      // TODO We should allow read-only view of non-Datastore DataProvider
      return viewer.getDataProvider() instanceof Datastore;
   }

   @Override
   public InspectorPanelController createPanelController(Studio studio) {
      return CommentsInspectorPanelController.create();
   }
}