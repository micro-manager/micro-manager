/**
 * SequenceBufferMonitor plugin
 *
 * Display Core sequence buffer usage in real time.
 *
 * AUTHOR:       Mark Tsuchida
 * COPYRIGHT:    University of California, San Francisco, 2014
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.plugins.sequencebuffermonitor;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class SequenceBufferMonitor implements MMPlugin {
   public static final String menuName = "Sequence Buffer Monitor";
   public static final String tooltipDescription =
      "Display sequence buffer usage";

   private ScriptInterface app_;

   @Override
   public void setApp(ScriptInterface app) {
      app_ = app;
   }

   @Override
   public void dispose() {
      app_ = null;
   }

   @Override
   public void show() {
      SequenceBufferMonitorFrame frame = new SequenceBufferMonitorFrame(app_);
      frame.setVisible(true);
      frame.start();
   }

   @Override
   public String getInfo () {
      return tooltipDescription;
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }

   @Override
   public String getVersion() {
      return "0.0";
   }

   @Override
   public String getCopyright() {
      return "University of California, San Francisco, 2014";
   }
}
