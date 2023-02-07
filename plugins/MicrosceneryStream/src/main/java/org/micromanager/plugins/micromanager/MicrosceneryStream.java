/**
 * A very simple Micro-Manager plugin, intended to be used as an example for
 * developers wishing to create their own, actually useful plugins. This one
 * demonstrates performing various common tasks, but does not do anything
 * really useful.
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


package org.micromanager.plugins.micromanager;

import org.micromanager.MenuPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.micromanager.data.*;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class MicrosceneryStream implements SciJavaPlugin, MenuPlugin, ProcessorPlugin {
   private Studio studio_;
   private MicrosceneryStreamFrame frame_;
   private MicrosceneryContext mmContext;

   /**
    * This method receives the Studio object, which is the gateway to the
    * Micro-Manager API. You should retain a reference to this object for the
    * lifetime of your plugin. This method should not do anything except for
    * store that reference, as Micro-Manager is still busy starting up at the
    * time that this is called.
    */
   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   /**
    * This method is called when your plugin is selected from the Plugins menu.
    * Typically at this time you should show a GUI (graphical user interface)
    * for your plugin.
    */
   @Override
   public void onPluginSelected() {
      if (mmContext == null){
         mmContext = new MicrosceneryContext(studio_);
      }
      if (frame_ == null) {
         // We have never before shown our GUI, so now we need to create it.
         frame_ = new MicrosceneryStreamFrame(studio_, mmContext,this);
      }
      frame_.setVisible(true);
   }

   /**
    * This string is the sub-menu that the plugin will be displayed in, in the
    * Plugins menu.
    */
   @Override
   public String getSubMenu() {
      return "";
   }

   /**
    * The name of the plugin in the Plugins menu.
    */
   @Override
   public String getName() {
      return "microscenery stream";
   }

   @Override
   public String getHelpText() {
      return "Stream data to microscenery";
   }

   @Override
   public String getVersion() {
      return "0.1";
   }

   @Override
   public String getCopyright() {
      return "Jan";
   }

   @Override
   public ProcessorConfigurator createConfigurator(PropertyMap settings) {
      if (mmContext == null){
         mmContext = new MicrosceneryContext(studio_);
      }
      if (frame_ == null) {
         // We have never before shown our GUI, so now we need to create it.
         frame_ = new MicrosceneryStreamFrame(studio_, mmContext,this);
      }
      return frame_;
   }

   @Override
   public ProcessorFactory createFactory(PropertyMap settings) {
      return () -> {
         if (mmContext == null){
            mmContext = new MicrosceneryContext(studio_);
         }
         return new ImageProcessor(mmContext);
      };
   }
}
