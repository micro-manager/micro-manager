package edu.ucsf.valelab.mmclearvolumeplugin;

import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;

/**
 * Binding to ClearVolume 3D viewer: View Micro-Manager datasets in 3D.
 *
 * <p>AUTHOR: Nico Stuurman COPYRIGHT: Regents of the University of California,
 * 2015
 * LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * <p>This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * <p>IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 *
 * @author nico
 */

@Plugin(type = InspectorPanelPlugin.class,
      priority = Priority.NORMAL,
      name = "3D (ClearVolume)",
      description = "Interact with 3D (ClearVolume) Viewer")
public final class CVInspectorPanelPlugin implements InspectorPanelPlugin {

   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      return viewer instanceof CVViewer;
   }

   @Override
   public InspectorPanelController createPanelController(Studio studio) {
      return new CVInspectorPanelController();
   }

}