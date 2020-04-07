// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.display.inspector.internal.panels.metadata;

import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;

/**
 *
 * @author Mark A. Tsuchida
 */
@Plugin(type = InspectorPanelPlugin.class,
      priority = Priority.HIGH + 200,
      name = "Summary Metadata",
      description = "View dataset metadata")
public class SummaryMetadataInspectorPanelPlugin implements InspectorPanelPlugin {
   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      // This should always be true; just a sanity check
      return viewer.getDataProvider() != null;
   }

   @Override
   public InspectorPanelController createPanelController(Studio studio) {
      return SummaryMetadataInspectorPanelController.create();
   }
}