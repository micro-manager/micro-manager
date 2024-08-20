/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic;

import com.asiimaging.plogic.ui.utils.WindowUtils;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class PLogicControlPlugin implements MenuPlugin, SciJavaPlugin {

   public static final String copyright = "Applied Scientific Instrumentation (ASI), 2024";
   public static final String description = "An interface for the Tiger Programmable Logic Card.";
   public static final String menuName = "ASI PLogic Control";
   public static final String version = "0.1.0";

   private Studio studio_;
   private PLogicControlFrame frame_;
   private PLogicControlModel model_;

   @Override
   public void setContext(final Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public void onPluginSelected() {
      // only one instance of the plugin can be open
      if (WindowUtils.isOpen(frame_)) {
         WindowUtils.close(frame_);
      }

      try {
         model_ = new PLogicControlModel(studio_);
         final boolean isDeviceFound = model_.findDevices();
         frame_ = new PLogicControlFrame(model_, isDeviceFound);
         frame_.setVisible(true);
         frame_.toFront();
      } catch (Exception e) {
         if (studio_ != null) {
            studio_.logs().showError(e);
         }
      }
   }

   @Override
   public String getName() {
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
   public String getHelpText() {
      return description;
   }

}
