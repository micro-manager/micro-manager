///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// -----------------------------------------------------------------------------
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

package org.micromanager.display.overlay;

import org.micromanager.MMGenericPlugin;

/**
 * A plugin providing overlays.
 *
 * <p>To create an overlay plugin, annotate your class like this:
 *
 * <pre><code>
 * {@literal @}Plugin(type = OverlayPlugin.class,
 *       priority = Prioroty.NORMAL_PRIORITY,      // Suggests order in menu
 *       name = "My Overlay",                      // User-visible name
 *       description = "Show Wonderful Indicator") // Tooltip
 * public class MyOverlayPlugin implements OverlayPlugin {
 *    // ...
 * }
 * </code></pre>
 *
 * @author Chris Weisiger, Mark A. Tsuchida
 */
public interface OverlayPlugin extends MMGenericPlugin {
  public Overlay createOverlay();
}
