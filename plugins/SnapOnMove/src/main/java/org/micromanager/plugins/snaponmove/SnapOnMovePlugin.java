// Snap-on-Move Preview for Micro-Manager
//
// Author: Mark A. Tsuchida
//
// Copyright (C) 2016 Open Imaging, Inc.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation
// and/or other materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its
// contributors may be used to endorse or promote products derived from this
// software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package org.micromanager.plugins.snaponmove;

import com.google.common.eventbus.Subscribe;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.events.ShutdownCommencingEvent;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * MenuPlugin class for Snap-on-Move.
 *
 * Manages unique MainController instance, which does the actual work.
 */
@Plugin(type = MenuPlugin.class)
public class SnapOnMovePlugin implements SciJavaPlugin, MenuPlugin {
   private Studio studio_;
   private MainController controller_;
   private ConfigFrame frame_;


   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public void onPluginSelected() {
      if (controller_ == null) {
         controller_ = new MainController(studio_);
         studio_.events().registerForEvents(this);
      }

      if (frame_ == null) {
         frame_ = new ConfigFrame(controller_);
         frame_.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
               frame_ = null;
            }
         });
      }
      frame_.setVisible(true);
      frame_.toFront();
   }

   @Subscribe
   public void onShutdown(ShutdownCommencingEvent e) {
      frame_.dispose();
      frame_ = null;

      if (controller_ != null) {
         controller_.setEnabled(false);
         controller_ = null;
      }
      studio_.events().unregisterForEvents(this);
   }

   @Override
   public String getSubMenu() {
      return "Beta";
   }

   @Override
   public String getName() {
      return "Snap-on-Move Preview";
   }

   @Override
   public String getHelpText() {
      return "Update preview image when the stage has moved";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "2016 Open Imaging, Inc.";
   }
}
