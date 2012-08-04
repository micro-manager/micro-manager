///////////////////////////////////////////////////////////////////////////////
//FILE:          SplitView.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2012
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

package org.micromanager.splitview;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/** 
 * Micro-Manager plugin that can split the acquired image top-down or left-right
 * and display the split image as a two channel image.
 *
 * @author nico
 */
public class SplitView implements MMPlugin {
   public static String menuName = "SplitView";
   public static String tooltipDescription = "Used with Dual View or Optosplit microscopes, which" +
   		" projects 2 channels from a single sample side by side onto a single camera.  This plugin " +
   		"opens a viewer that displays both " +
   		"components overlayed in a single, 2-channel image";
   private CMMCore core_;
   private ScriptInterface gui_;
   private SplitViewFrame myFrame_;

   @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;                                        
      core_ = app.getMMCore();
      if (myFrame_ == null) {
         try {
            myFrame_ = new SplitViewFrame(gui_);
            gui_.addMMBackgroundListener(myFrame_);
         } catch (Exception e) {
            e.printStackTrace();
            return;
         }
      }
      myFrame_.setVisible(true);
   }

   @Override
   public void dispose() {
      //if (myFrame_ != null)
         //myFrame_.safePrefs();
   }

   @Override
   public void show() {
         String ig = "SplitView";
   }

   @Override
   public void configurationChanged() {
   }

   @Override
   public String getInfo () {
      return "SplitView Plugin";
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }
   
   @Override
   public String getVersion() {
      return "0.1";
   }
   
   @Override
   public String getCopyright() {
      return "University of California, 2011";
   }
}
