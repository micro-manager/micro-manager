/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf;

import com.asiimaging.tirf.model.TIRFControlModel;
import com.asiimaging.tirf.ui.TIRFControlFrame;
import com.asiimaging.tirf.ui.utils.WindowUtils;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class TIRFControlPlugin implements MenuPlugin, SciJavaPlugin {

   public static final String copyright = "Applied Scientific Instrumentation (ASI), 2022";
   public static final String description = "Controls an ASI Ring TIRF microscope.";
   public static final String menuName = "ASI Ring TIRF Control";
   public static final String version = "0.3.0";

   private Studio studio;
   private TIRFControlFrame frame;
   private TIRFControlModel model;

   @Override
   public void setContext(final Studio studio) {
      this.studio = studio;
   }

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public void onPluginSelected() {
      // only one instance of the plugin can be open
      if (WindowUtils.isOpen(frame)) {
         WindowUtils.close(frame);
      }

      frame = new TIRFControlFrame(studio);
      model = new TIRFControlModel(studio);

      frame.setModel(model);
      model.setFrame(frame);

      if (model.validate()) {
         model.loadSettings();
         frame.createUserInterface();
      } else {
         frame.createErrorInterface();
      }

      // show the frame
      frame.setVisible(true);
      frame.toFront();
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
