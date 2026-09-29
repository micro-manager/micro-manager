///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    Regents of the University of California, 2026
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

package org.micromanager.tileddataviewer;

import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DataViewerGearMenuPlugin;
import org.scijava.plugin.Plugin;

/**
 * Gear menu entry that saves what the TiledDataViewer is currently showing.
 *
 * <p>This is the TiledDataViewer counterpart of the main viewer's "Export Images
 * As Displayed": it captures the visible canvas, so zoom, contrast and overlays
 * are all baked into the output. It is deliberately distinct from the
 * Inspector's Export button, which re-composites a region from storage at full
 * resolution and ignores the current zoom.
 */
@Plugin(type = DataViewerGearMenuPlugin.class)
public final class ExportAsDisplayedPlugin implements DataViewerGearMenuPlugin {

   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Export Images As Displayed";
   }

   @Override
   public String getHelpText() {
      return "Save the viewer canvas as shown, including zoom, contrast and overlays.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright (c) Regents of the University of California";
   }

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      return viewer instanceof TiledDataViewerDataViewerAPI;
   }

   @Override
   public void onPluginSelected(DataViewer viewer) {
      new ExportAsDisplayedDlg(studio_, (TiledDataViewerDataViewerAPI) viewer).setVisible(true);
   }
}
