///////////////////////////////////////////////////////////////////////////////
//FILE:          AcquisitionData.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id: DeviceControlGUI.java 869 2008-02-02 00:15:51Z nenad $
//
package org.micromanager.api;

import java.awt.Color;
import java.awt.Component;

import org.micromanager.navigation.PositionList;
import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MMScriptException;

/**
 * Interface to execute commands in the main panel.
 */
public interface DeviceControlGUI {
   /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void updateGUI(boolean updateConfigPadStructure);
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void initializeGUI();
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public String getVersion();
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public boolean updateImage();
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public boolean displayImage(Object pixels);
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public boolean displayImageWithStatusLine(Object pixels, String statusLine);
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void displayStatusLine(String statusLine);
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public boolean okToAcquire();
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void stopAllActivity();
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public boolean getLiveMode();
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void enableLiveMode(boolean enable);
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void setBackgroundStyle(String backgroundType); 
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public String getBackgroundStyle();
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public Color getBackgroundColor();
    /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void addMMBackgroundListener(Component frame);
   /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void removeMMBackgroundListener(Component frame);
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void setConfigChanged(boolean status);
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void refreshGUI();
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void applyContrastSettings(ContrastSettings contrast8_, ContrastSettings contrast16_);
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public ContrastSettings getContrastSettings();
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public boolean is16bit();
   
     /**
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void showXYPositionList();
   
     /***
    * All plugins and scripts should be written using ScriptInterface
    * @deprecated
    */
   public void makeActive();
   
}