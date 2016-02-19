///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, May 2009
//
// COPYRIGHT:    100X Imaging Inc, 2009
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
package org.micromanager.internal.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

import org.micromanager.AutofocusManager;
import org.micromanager.AutofocusPlugin;
import org.micromanager.MMPlugin;
import org.micromanager.Studio;

/**
 * Manages different instances of autofocus devices, both Java plugin and MMCore based.
 * The class is designed to be instantiated in the top level gui and used to obtain
 * the list of available focusing devices, as well as for selecting a default one.
 */
public class DefaultAutofocusManager implements AutofocusManager {
   private Studio app_;
   private Vector<AutofocusPlugin> afs_;
   private AutofocusPlugin currentAfDevice_;
   private AutofocusPropertyEditor afDlg_;
   
   public DefaultAutofocusManager(Studio app) {
      afs_ = new Vector<AutofocusPlugin>();
      currentAfDevice_ = null;
      app_ = app;
   }
   
   @Override
   public void setAutofocusMethod(AutofocusPlugin plugin) {
      currentAfDevice_ = plugin;
   }

   @Override
   public void setAutofocusMethodByName(String name) {
      for (AutofocusPlugin plugin : afs_) {
         if (plugin.getName().equals(name)) {
            currentAfDevice_ = plugin;
            return;
         }
      }
      throw new IllegalArgumentException("Invalid autofocus plugin name " + name);
   }

   @Override
   public AutofocusPlugin getAutofocusMethod() {
      return currentAfDevice_;
   }

   @Override
   public List<String> getAllAutofocusMethods() {
      ArrayList<String> result = new ArrayList<String>();
      for (AutofocusPlugin plugin : afs_) {
         result.add(plugin.getName());
      }
      return result;
   }

   @Override
   public void refresh() {
      afs_.clear();
      CMMCore core = app_.getCMMCore();

      // first check core autofocus
      StrVector afDevs = core.getLoadedDevicesOfType(DeviceType.AutoFocusDevice);
      for (int i=0; i<afDevs.size(); i++) {
         CoreAutofocus caf = new CoreAutofocus();
         try {
            core.setAutoFocusDevice(afDevs.get(i));
            caf.setContext(app_);
            if (caf.getName().length() != 0) {
               afs_.add(caf);
               if (currentAfDevice_ == null)
                  currentAfDevice_ = caf;
            }
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }

      // then check Java
      for (MMPlugin plugin : app_.plugins().getAutofocusPlugins().values()) {
         afs_.add((AutofocusPlugin) plugin);
      }

      // make sure the current autofocus is still in the list, otherwise set it to something...
      boolean found = false;
      if (currentAfDevice_ != null) {
         for (AutofocusPlugin af : afs_) {
            if (af.getName().equals(currentAfDevice_.getName())) {
               found = true;
               currentAfDevice_ = af;
            }
         }
      }
      if (!found && afs_.size() > 0)
         currentAfDevice_ = afs_.get(0);
  
      // Show new list in Options Dialog
      if (afDlg_ != null) 
         afDlg_.rebuild();

   }
      
   public void showOptionsDialog() {
      if (afDlg_ == null)
         afDlg_ = new AutofocusPropertyEditor(this);
      afDlg_.setVisible(true);
      if (currentAfDevice_ != null) {
         currentAfDevice_.applySettings();
         currentAfDevice_.saveSettings();
      }
   }

   public void closeOptionsDialog() {
      if (afDlg_ != null)
         afDlg_.cleanup();
   }

   /**
    * Returns a list of available af device names
    * NOTE: we operate based on the "device name" rather than the plugin class
    * name, because the latter is the same for all autofocus device adapters
    * (it's always CoreAutofocus).
    * @return - array of af names
    */
   public String[] getAfDevices() {
      String afDevs[] = new String[afs_.size()];
      int count = 0;
      for (AutofocusPlugin af : afs_) {
         afDevs[count++] = af.getName();
      }
      return afDevs;
   }

   /**
    * NOTE: we operate based on the "device name" rather than the plugin class
    * name, because the latter is the same for all autofocus device adapters
    * (it's always CoreAutofocus).
    */
   public boolean hasDevice(String dev) {
      for (AutofocusPlugin af : afs_) {
         if (af.getName().equals(dev))
            return true;
      }
      return false;
   }
}
