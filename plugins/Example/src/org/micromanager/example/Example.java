/**
 * A very simple Micro-Manager plugin
 * 
 * This plugin does nothing, its sole function is to make it easier for developers
 * to start writing Micro-Manager plugins.
 * 
 * Copy this code to a location of your choice, change the name of the project
 * (and the classes), build the jar file and copy it to the mmplugins folder
 * in your Micro-Manager directory.
 * 
 * Once you have it loaded and running, you can attach the NetBean debugger
 * and use all of NetBean's functionality to debug your code.  If you make a 
 * generally useful plugin, please do not hesitate to send a copy to
 * info@micro-managaer.org for inclusion in the Micro-Manager source code 
 * repository.
 * 
 * Nico Stuurman, 2012
 * copyright University of California
 *  
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


package org.micromanager.example;

import mmcorej.CMMCore;
import org.micromanager.MMPlugin;
import org.micromanager.Studio;


public class Example implements MMPlugin {
   public static String menuName = "Example";
   public static String tooltipDescription = "This Micro-Manager plugin does nothing. "
		   +"Its only purpose is to be an example for developers wishing to write their own plugin.";
   private CMMCore core_;
   private Studio gui_;
   private ExampleFrame myFrame_;

   @Override
   public void setApp(Studio app) {
      gui_ = app;                                        
      core_ = app.getCMMCore();
      if (myFrame_ == null)
         myFrame_ = new ExampleFrame(gui_);
      myFrame_.setVisible(true);
   }

   @Override
   public void dispose() {
      // nothing todo:
   }

   @Override
   public void show() {
      myFrame_.setVisible(true);
   }

   public void configurationChanged() {
   }

   @Override
   public String getInfo () {
      return "Example plugin";
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }
   
   @Override
   public String getVersion() {
      return "1.1";
   }
   
   @Override
   public String getCopyright() {
      return "University of California, 2012-2015";
   }
}
