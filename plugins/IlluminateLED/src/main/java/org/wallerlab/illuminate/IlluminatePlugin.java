/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wallerlab.illuminate;

/**
 * This example plugin pops up a dialog box that says "Hello, world!".
 *
 * Copyright University of California
 *
 * LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */
import java.util.logging.Level;
import java.util.logging.Logger;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class IlluminatePlugin implements MenuPlugin, SciJavaPlugin  {
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
        IlluminateControllerFrame controller_frame;

        // Search for LED Array Device
        StrVector devices = mmCore.getLoadedDevices();
        boolean led_array_found = false;
        String device_name = "";
        
        System.out.println("Devices found:");
        for (int i = 0; i < devices.size(); i++) {
            try {
                if ("Illuminate-Led-Array".equals(devices.get(i)))
                {
                    led_array_found = true;
                    device_name = devices.get(i);
                    System.out.println(devices.get(i));
                }
            } catch (Exception ex) {
                Logger.getLogger(IlluminatePlugin.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (led_array_found)
            try {
                controller_frame = new IlluminateControllerFrame(mmCore, device_name, debugFlag);
        } catch (Exception ex) {
            Logger.getLogger(IlluminatePlugin.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * This method determines which sub-menu of the Plugins menu we are placed
     * into.
     */
    @Override
    public String getSubMenu() {
        return "Illumination";
        // Indicates that we should show up in the root Plugins menu.
//      return "";
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

