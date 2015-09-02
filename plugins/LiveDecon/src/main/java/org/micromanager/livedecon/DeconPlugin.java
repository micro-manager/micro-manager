///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2015
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

package org.micromanager.livedecon;

import java.util.ArrayList;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.MenuPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Plugin for setting up live deconvolution with saving of original data.
 */
@Plugin(type = MenuPlugin.class)
public class DeconPlugin implements MenuPlugin, ProcessorPlugin, SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public void onPluginSelected() {
      // Replace the current application pipeline with a PipelineSaver (to
      // preserve raw data) and our own plugin.
      ArrayList<ProcessorPlugin> plugins = new ArrayList<ProcessorPlugin>();
      ProcessorPlugin saver = studio_.plugins().getProcessorPlugins().get("Image Saver");
      saver.setContext(studio_);
      plugins.add(saver);
      plugins.add(this);
      studio_.data().setApplicationPipeline(plugins);
   }

   @Override
   public ProcessorConfigurator createConfigurator() {
      return new DeconConfigurator(studio_);
   }

   @Override
   public ProcessorFactory createFactory(PropertyMap settings) {
      return new DeconFactory(settings, studio_);
   }

   @Override
   public String getName() {
      return "Live Deconvolution";
   }

   @Override
   public String getSubMenu() {
      return "Acquisition Tools";
   }

   @Override
   public String getHelpText() {
      return "Deconvolves Z-stacks as they are acquired.";
   }

   @Override
   public String getVersion() {
      return "Version 1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright University of California San Francisco, 2015";
   }
}
