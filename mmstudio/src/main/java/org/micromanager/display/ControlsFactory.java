///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display API
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

package org.micromanager.display;

import java.awt.Component;
import java.util.List;

/**
 * A ControlsFactory is used to provide custom controls for a DisplayWindow.
 * It exposes a method that makes a list of Components, which will be
 * included in the DisplayWindow's controls, underneath the axis scrollbars.
 * This class is necessary because of the user's ability to create duplicates
 * of DisplayWindows, which necessitates being able to duplicate the controls
 * as well. If you do not want to provide any custom controls, then use the
 * DisplayManager.createDisplay() method that does not take a ControlsFactory
 * argument.
 *
 * Usage example:
 *
 * <pre><code>
 * Datastore store = mm.data().createRAMDatastore();
 * DisplayWindow display = mm.displays().createDisplay(store,
 *       new ControlsFactory() {
 *          {@literal @}Override
 *          public List&lt;Component&gt; makeControls(DisplayWindow disp) {
 *             make your controls here;
 *             return controls;
 *          }
 *      }
 * );
 * </code></pre>
 */
public abstract class ControlsFactory {
   public abstract List<Component> makeControls(DisplayWindow display);
}
