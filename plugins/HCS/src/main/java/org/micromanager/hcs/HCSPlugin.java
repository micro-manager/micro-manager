///////////////////////////////////////////////////////////////////////////////
//FILE:           HCSPlugin.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      high content screening
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June 3, 2008
//                Mark Tsuchida
//                Nico Stuurman
//
//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008
//                Regents of the University of California, 2014-2016
//
//LICENSE:        This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//


package org.micromanager.hcs;

import com.google.common.eventbus.Subscribe;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.events.ShutdownCommencingEvent;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class HCSPlugin implements MenuPlugin, SciJavaPlugin {
   static public final String VERSION_INFO = "1.5.0";
   static private final String COPYRIGHT_NOTICE = "Copyright by UCSF, 2013";
   static private final String DESCRIPTION = "Generate imaging site positions for micro-well plates and slides";
   static private final String NAME = "HCS Site Generator";

   private Studio studio_;
   private SiteGenerator frame_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
      studio_.events().registerForEvents(this);
   }

   @Override
   public String getSubMenu() {
      return "Acquisition Tools";
   }

   @Override
   public void onPluginSelected() {
      if (frame_ == null) {
         frame_ = new SiteGenerator(studio_);
      }
      frame_.setVisible(true);
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
   
   @Subscribe
   public void closeRequested(ShutdownCommencingEvent sce) {
      if (!sce.isCanceled() && frame_ != null) {
         frame_.dispose();
      }
   }
}
