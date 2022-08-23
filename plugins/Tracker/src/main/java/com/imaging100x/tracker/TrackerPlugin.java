///////////////////////////////////////////////////////////////////////////////
//PROJECT:        Micro-Manager-100X
//SUBSYSTEM:      100X Imaging Inc micro-manager extentsions
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June, 2008
//                Nico Stuurman, updated to current API, Jan. 2014
//
//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008
//                University of California, San Francisco, 2014
//                
//LICENSE:        This file is distributed under the GPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package com.imaging100x.tracker;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class TrackerPlugin implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Live Tracking";
   public static final String tooltipDescription =
      "Use image correlation based tracking to countersteer the XY stage";
   static private final String VERSION_INFO = "1.0";
   static private final String COPYRIGHT_NOTICE = "Copyright by 100X Imaging Inc, 2009";
   static private final String DESCRIPTION = "Live cell tracking module";
   static private final String INFO = "Not available";

   private Studio app_ = null;
   private TrackerControl frame_;

   @Override
   public String getName() {
      return menuName;
   }

   @Override
   public void setContext(Studio app) {
      app_ = app;
   }

   @Override
   public void onPluginSelected() {
      if (frame_ == null) {
         frame_ = new TrackerControl(app_);
      }
      frame_.setVisible(true);
   }

   @Override
   public String getSubMenu() {
      return "Acquisition Tools";
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
   public String getVersion() {
      return VERSION_INFO;
   }
}
