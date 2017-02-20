///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIM.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013-2015
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

package org.micromanager.asidispim;

import java.awt.Color;
import java.awt.event.WindowEvent;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.events.ShutdownCommencingEvent;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 *
 * @author nico
 */
@Plugin(type = MenuPlugin.class)
public class ASIdiSPIM implements MenuPlugin, SciJavaPlugin {
   public static final boolean OSPIM = false;
   public final static String MENUNAME = "ASI " + (OSPIM ? "oSPIM" : "diSPIM");
   public final static String TOOLTIPDESCRIPTION = "Control the " + MENUNAME; 
   public final static Color BORDERCOLOR = Color.gray;

   private Studio gui_;
   private static ASIdiSPIMFrame myFrame_ = null;

  
   public static ASIdiSPIMFrame getFrame() {
      return myFrame_;
   }
 
   @Override
   public String getVersion() {
      return "0.4";
   }

   @Override
   public String getCopyright() {
      return "University of California and Applied Scientific Precision(ASI), 2013-2017";
   }

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public void onPluginSelected() {
      // close frame before re-load if already open
      // if frame has been opened and then closed (myFrame != null) but it won't be displayable 
      if (myFrame_ != null && myFrame_.isDisplayable()) {
         WindowEvent wev = new WindowEvent(myFrame_, WindowEvent.WINDOW_CLOSING);
         myFrame_.dispatchEvent(wev);
      }
      // create brand new instance of plugin frame every time
      try {
         myFrame_ = new ASIdiSPIMFrame(gui_);
         gui_.events().registerForEvents(myFrame_);
      } catch (Exception e) {
         gui_.logs().showError(e);
      }
      myFrame_.setVisible(true);
   }

   @Override
   public void setContext(Studio studio) {
      gui_ = studio;
      gui_.events().registerForEvents(this);
   }

   @Override
   public String getName() {
      return MENUNAME;
   }

   @Override
   public String getHelpText() {
      return TOOLTIPDESCRIPTION;
   }
   
   public void closeRequested( ShutdownCommencingEvent sce){
      if (myFrame_ != null) {
         myFrame_.dispose();
      }
   }
}
