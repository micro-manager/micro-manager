/*
Copyright (c) 2019, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL REGENTS OF THE UNIVERSITY OF CALIFORNIA BE LIABLE 
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.wallerlab.illuminate;

import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class IlluminatePlugin implements MenuPlugin, SciJavaPlugin {
   // Provides access to the MicroManager API.

   private Studio studio_;
   public static boolean debugFlag = true;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   /**
    * This method is called when the plugin's menu option is selected.
    */
   @Override
   public void onPluginSelected() {

      // Create copy of the mmCore object to pass to sub-class
      CMMCore mmCore = studio_.getCMMCore();

      // Create LED Map
      IlluminateControllerFrame controllerFrame;

      // Search for LED Array Device
      StrVector devices = mmCore.getLoadedDevices();
      boolean ledArrayFound = false;
      String deviceName = "";

      System.out.println("Devices found:");
      for (int i = 0; i < devices.size(); i++) {
         try {
            if ("Illuminate-Led-Array".equals(devices.get(i))) {
               ledArrayFound = true;
               deviceName = devices.get(i);
               System.out.println(devices.get(i));
            }
         } catch (Exception ex) {
            Logger.getLogger(IlluminatePlugin.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
      if (ledArrayFound) {
         try {
            controllerFrame = new IlluminateControllerFrame(mmCore, deviceName, debugFlag);
         } catch (Exception ex) {
            Logger.getLogger(IlluminatePlugin.class.getName()).log(Level.SEVERE, null, ex);
         }
      } else {
         studio_.logs()
               .showMessage("LED Array not found.  This plugin is no fun without the hardware");
      }

   }

   /**
    * This method determines which sub-menu of the Plugins menu we are placed
    * into.
    */
   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public String getName() {
      return "Illuminate LED Array";
   }

   @Override
   public String getHelpText() {
      return "Illuminate LED Array Plugin";
   }

   @Override
   public String getVersion() {
      return "0.1";
   }

   @Override
   public String getCopyright() {
      return "University of California, 2019";
   }
}
