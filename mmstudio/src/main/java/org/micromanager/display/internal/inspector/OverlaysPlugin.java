///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal.inspector;

import org.micromanager.Studio;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.InspectorPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * This plugin makes the OverlayPanels used to draw on top of images.
 */
@Plugin(type = InspectorPlugin.class)
public final class OverlaysPlugin implements InspectorPlugin, SciJavaPlugin {
   @Override
   public void setContext(Studio studio) {}

   @Override
   public InspectorPanel createPanel() {
      return new OverlaysPanel();
   }

   @Override
   public String getName() {
      return "Overlays";
   }

   @Override
   public String getHelpText() {
      return "Draw overlays on image displays";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright (c) 2015 Regents of the University of California and Open Imaging Inc.";
   }
}
