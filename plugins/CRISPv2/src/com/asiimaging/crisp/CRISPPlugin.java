///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
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

package com.asiimaging.crisp;

import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

import com.asiimaging.crisp.utils.WindowUtils;

/**
 * The original plugin was authored by Nico Stuurman, and then rewritten by Vikram Kopuri.
 * The current code is based on the work of the previous authors. 
 * 
 * [Changelog]
 * v2.1.7 - you can plot focus curve data on MS2000 and you can view focus curve data in general
 * v2.1.6 - disable spinners during focus lock and set the polling rate correctly on startup
 * v2.1.5 - detects AxisLetter on Tiger, the property name differs between Whizkid and Tiger.
 * v2.1.4 - CRISP class now detects variations in property names: "LED Intensity" vs "LED Intensity(%)"
 * v2.1.3 - added saving and loading user settings, query controller for initial values
 * v2.1.2 - plugin detects the type of controller, setRefreshPropertyValues on ms2k fixed
 * v2.1.1 - added lock range and timer polling rate
 * v2.1.0 - rewrite by Brandon Simpson
 * v2.0.2 - show offset and sum values
 * v2.0.1 - pause refresh during Log Cal to avoid getting error
 * v2.0.0 - rewrite by Vikram Kopuri
 * v1.0.0 - initial plugin by Nico Stuurman
 */
public class CRISPPlugin implements MMPlugin {
    
    public final static String copyright = "Applied Scientific Instrumentation (ASI), 2014-2020";
    public final static String description = "Interface to control ASIs CRISP Autofocus system.";
    public final static String menuName = "ASI CRISP Control Beta";
    public final static String version = "2.1.7";
    
    private ScriptInterface gui;
    private CRISPFrame frame;
    
    @Override
    public void setApp(final ScriptInterface app) {
        gui = app;
        
        setSystemLookAndFeel();
        //setNimbusLookAndFeel();
        
        // only one instance of the plugin can be open
        if (WindowUtils.isOpen(frame)) {
            WindowUtils.close(frame);
        }
        
        try {
            frame = new CRISPFrame(gui);
            frame.setVisible(true);
        } catch (Exception e) {
            gui.showError(e);
        }
    }
    
    @Override
    public void dispose() {
        // the main app calls this method to remove the plugin window
        WindowUtils.dispose(frame);
    }
    
    @Override
    public void show() {
    }
   
    @Override
    public String getInfo() {
        return menuName;
    }
    
    @Override
    public String getVersion() {
        return version;
    }
   
    @Override
    public String getCopyright() {
        return copyright;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    private void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {
            // look and feel -> doesn't matter if we can't make the ui look nice
        }
    }
    private void setNimbusLookAndFeel() {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignore) {
            // if Nimbus is not available you can set the GUI to another look and feel
        }
    }
}
