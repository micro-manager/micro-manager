///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIM.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class ASIdiSPIM implements MMPlugin {
   
   public static final boolean oSPIM = false;
   
   public final static String menuName = "ASI " + (oSPIM ? "oSPIM" : "diSPIM");
   public final static String tooltipDescription = "Control the " + menuName;
   public final static Color borderColor = Color.gray;
   
   private ScriptInterface gui_;
   private static ASIdiSPIMFrame myFrame_ = null;

   @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;
      // close frame before re-load if already open
      // if frame has been opened and then closed (myFrame != null) but it won't be displayable 
      if (myFrame_ != null && myFrame_.isDisplayable()) {
         WindowEvent wev = new WindowEvent(myFrame_, WindowEvent.WINDOW_CLOSING);
         myFrame_.dispatchEvent(wev);
      }
      // create brand new instance of plugin frame every time
      try {
         myFrame_ = new ASIdiSPIMFrame(gui_);
         myFrame_.setBackground(gui_.getBackgroundColor());
         gui_.addMMListener(myFrame_);
         gui_.addMMBackgroundListener(myFrame_);
      } catch (Exception e) {
         gui_.showError(e);
      }
      myFrame_.setVisible(true);
   }
   
   public static ASIdiSPIMFrame getFrame() {
      return myFrame_;
   }
   
   /**
    * The main app calls this method to remove the module window
    */
   @Override
   public void dispose() {
      if (myFrame_ != null)
         myFrame_.dispose();
   }

   @Override
   public void show() {
      @SuppressWarnings("unused")
      String ig = menuName;
   }

   @Override
   public String getInfo () {
      return menuName;
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }

   @Override
   public String getVersion() {
      return "0.3";
   }

   @Override
   public String getCopyright() {
      return "University of California and Applied Scientific Instrumentation (ASI), 2013-2016";
   }
}
