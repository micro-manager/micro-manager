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

package org.micromanager.display.internal.overlays;

import org.micromanager.Studio;
import org.micromanager.display.OverlayPanelFactory;
import org.micromanager.display.OverlayPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = OverlayPlugin.class)
public final class ScaleBarPlugin implements OverlayPlugin, SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public OverlayPanelFactory createFactory() {
      return new ScaleBarFactory(studio_);
   }

   @Override
   public String getName() {
      return "Scale Bar";
   }

   @Override
   public String getHelpText() {
      return "Display a scale bar on the image";
   }

   @Override
   public String getVersion() {
      return "Version 1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright 2015 Regents of the University of California; Open Imaging Inc.";
   }
}
