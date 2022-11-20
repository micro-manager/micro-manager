/**
 * This example plugin pops up a dialog box that says "Hello, world!".
 *
 * <p>Copyright University of California
 *
 * <p>LICENSE:      This file is distributed under the BSD license.
 * License text is included with the source distribution.
 *
 * <p>This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * <p>IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.helloworld;

import javax.swing.JOptionPane;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class HelloWorldPlugin implements SciJavaPlugin, MenuPlugin {
   // Provides access to the MicroManager API.
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   /**
    * This method is called when the plugin's menu option is selected.
    */
   @Override
   public void onPluginSelected() {
      JOptionPane.showMessageDialog(null, "Hello, world!", "Hello world!",
            JOptionPane.PLAIN_MESSAGE);
   }

   /**
    * This method determines which sub-menu of the Plugins menu we are placed
    * into.
    */
   @Override
   public String getSubMenu() {
      return "Developer Tools";
      // Indicates that we should show up in the root Plugins menu.
   }

   @Override
   public String getName() {
      return "Hello, World!";
   }

   @Override
   public String getHelpText() {
      return "Displays a simple greeting.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "University of California, 2015";
   }
}
