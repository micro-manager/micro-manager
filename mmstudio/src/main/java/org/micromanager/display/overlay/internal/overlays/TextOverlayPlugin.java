///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2022
//
// COPYRIGHT:    Altos Labs, 2022
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

package org.micromanager.display.overlay.internal.overlays;

import org.micromanager.display.overlay.Overlay;
import org.micromanager.display.overlay.OverlayPlugin;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;


/**
 * Plugin to display text (such as channel name) on images.
 */
@Plugin(type = OverlayPlugin.class,
      priority = Priority.HIGH,
      name = "TextOverlay",
      description = "Display Text")
public final class TextOverlayPlugin implements OverlayPlugin {
   @Override
   public Overlay createOverlay() {
      return TextOverlay.create();
   }
}