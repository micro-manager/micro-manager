///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, July, 2007
//
// COPYRIGHT:    University of California, San Francisco, 2007
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
// CVS:          $Id: ProgressBar.java 2 2007-02-27 23:33:17Z nenad $
//
package org.micromanager.internal.utils;

import java.awt.Color;
import java.awt.Window;
import java.util.HashMap;

import javax.swing.plaf.ColorUIResource;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.micromanager.CompatibilityInterface;

/*
 * This class controls the colors of the user interface
 * Note we use ColorUIResources instead of Colors because Colors don't
 * interact well with Look and Feel; see
 * http://stackoverflow.com/questions/27933017/cant-update-look-and-feel-on-the-fly
 */
public class DaytimeNighttime {

   // Key into the user's profile for the current display mode.
   private static final String BACKGROUND_MODE = "current window style (as per CompatibilityInterface.BACKGROUND_OPTIONS)";
   // List of keys to UIManager.put() method for setting the background color
   // look and feel. Selected from this page:
   // http://alvinalexander.com/java/java-uimanager-color-keys-list
   // Each of these keys will have ".background" appended to it later.
   private static final String[] BACKGROUND_COLOR_KEYS = new String[] {
         "Button", "CheckBox", "CheckBoxMenuItem", "ColorChooser",
         "ComboBox", "EditorPane", "FormattedTextField", "InternalFrame",
         "Label", "List", "Menu", "MenuBar", "MenuItem", "OptionPane",
         "Panel", "PasswordField", "PopupMenu", "ProgressBar", "RadioButton",
         "RadioButtonMenuItem", "ScrollBar", "ScrollPane", "Slider", "Spinner",
         "SplitPane", "TabbedPane", "Table", "TableHeader", "TextArea",
         "TextField", "TextPane", "ToggleButton", "TollBar", "Tree",
         "Viewport"
   };

   // As above, but for disabled text; each of these keys will have
   // ".disabledText" appended to it later.
   private static final String[] DISABLED_TEXT_COLOR_KEYS = new String[] {
         "Button", "CheckBox", "RadioButton", "ToggleButton"
   };

   // background color of the UI
   private static HashMap<String, ColorUIResource> background_;
   // background color of pads in the UI
   private static HashMap<String, ColorUIResource> padBackground_;
   // Color of disabled text.
   private static HashMap<String, ColorUIResource> disabledTextColor_;

   static {
      // Possible: make UI to let user set these colors
      background_ = new HashMap<String, ColorUIResource>();
      background_.put(CompatibilityInterface.DAY,
            new ColorUIResource(java.awt.SystemColor.control));
      // TODO: this 25% gray color is exactly the same color as the checkbox
      // in a menu item, on OSX at least.
      background_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(new Color(64, 64, 64)));

      padBackground_ = new HashMap<String, ColorUIResource>();
      padBackground_.put(CompatibilityInterface.DAY,
            new ColorUIResource(Color.white));
      padBackground_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(java.awt.SystemColor.control));

      disabledTextColor_ = new HashMap<String, ColorUIResource>();
      disabledTextColor_.put(CompatibilityInterface.DAY,
            new ColorUIResource(UIManager.getColor("CheckBox.disabledText")));
      disabledTextColor_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(200, 200, 200));
   }

   public static void setMode(String mode) {
      if (!(mode.contentEquals(CompatibilityInterface.DAY) ||
            mode.contentEquals(CompatibilityInterface.NIGHT))) {
         throw new IllegalArgumentException("Invalid background style \"" +
               mode + "\"");
      }
      storeBackgroundMode(mode);

      // Ensure every GUI object type gets the right background color.
      for (String key : BACKGROUND_COLOR_KEYS) {
         UIManager.put(key + ".background",
               background_.get(mode));
      }
      // Ensure disabled text is still legible.
      for (String key : DISABLED_TEXT_COLOR_KEYS) {
         UIManager.put(key + ".disabledText",
            disabledTextColor_.get(mode));
      }
      // Update existing components.
      for (Window w : Window.getWindows()) {
         SwingUtilities.updateComponentTreeUI(w);
      }
   }

   /**
    * Set a new default background mode in the user's profile.
    */
   public static void storeBackgroundMode(String mode) {
      DefaultUserProfile.getInstance().setString(DaytimeNighttime.class,
            BACKGROUND_MODE, mode);
   }

   /**
    * Return the current stored background mode from the profile.
    */
   public static String getBackgroundMode() {
      return DefaultUserProfile.getInstance().getString(DaytimeNighttime.class,
            BACKGROUND_MODE, CompatibilityInterface.DAY);
   }
}
