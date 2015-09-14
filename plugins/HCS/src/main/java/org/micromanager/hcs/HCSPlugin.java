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

package org.micromanager.hcs;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class HCSPlugin implements MenuPlugin, SciJavaPlugin {
   private Studio studio_;
   static public final String VERSION_INFO = "1.5.0";
   static private final String COPYRIGHT_NOTICE = "Copyright by UCSF, 2013";
   static private final String DESCRIPTION = "Generate imaging site positions for micro-well plates and slides";
   static private final String NAME = "High-Content Screening";

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getSubMenu() {
      return "Acquisition Tools";
   }

   @Override
   public void onPluginSelected() {
      SiteGenerator frame = new SiteGenerator(studio_);
      frame.setVisible(true);
   }

   @Override
   public String getCopyright() {
      return COPYRIGHT_NOTICE;
   }

   @Override
   public String getHelpText() {
      return DESCRIPTION;
   }

   @Override
   public String getName() {
      return NAME;
   }

   @Override
   public String getVersion() {
      return VERSION_INFO;
   }

}
