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
import java.awt.Graphics;
import java.awt.Window;
import java.util.HashMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.plaf.ColorUIResource;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;
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
         "Button", "CheckBox", "ColorChooser", "EditorPane",
         "FormattedTextField", "InternalFrame", "Label", "List", "MenuBar",
         "OptionPane", "Panel", "PasswordField", "ProgressBar",
         "RadioButton", "ScrollBar", "ScrollPane", "Slider", "Spinner",
         "SplitPane", "TabbedPane", "Table", "TableHeader", "TextArea",
         "TextField", "TextPane", "ToggleButton", "TollBar", "Tree",
         "Viewport"
   };

   // Keys that get a slightly lighter background for "night" mode. We do this
   // because unfortunately on OSX, the checkmark for selected menu items is
   // 25% gray, and thus invisible against our normal background color.
   private static final String[] LIGHTER_BACKGROUND_COLOR_KEYS = new String[] {
         "CheckBoxMenuItem", "ComboBox", "Menu", "MenuItem", "PopupMenu",
         "RadioButtonMenuItem"
   };

   // Improve text legibility against dark backgrounds. These will have
   // ".foreground" appended to them later.
   private static final String[] ENABLED_TEXT_COLOR_KEYS = new String[] {
         "CheckBox", "ColorChooser", "FormattedTextField",
         "InternalFrame", "Label", "List",
         "OptionPane", "Panel", "ProgressBar",
         "RadioButton", "ScrollPane", "Slider", "Spinner",
         "SplitPane", "TabbedPane", "Table", "TextArea", "TextField",
         "TollBar", "Tree", "Viewport"
   };

   // As above, but for disabled text; each of these keys will have
   // ".disabledText" appended to it later.
   private static final String[] DISABLED_TEXT_COLOR_KEYS = new String[] {
         "Button", "CheckBox", "RadioButton", "ToggleButton"
   };

   // Keys that we have to specify manually; nothing will be appended to them.
   private static final String[] MANUAL_BACKGROUND_COLOR_KEYS = new String[] {
         "ComboBox.buttonBackground",
   };

   // background color of the UI
   private static HashMap<String, ColorUIResource> background_;
   // background color of disabled UI elements.
   private static HashMap<String, ColorUIResource> disabledBackground_;
   // Lighter background colors, for certain elements.
   private static HashMap<String, ColorUIResource> lightBackground_;
   // background color of pads in the UI
   private static HashMap<String, ColorUIResource> padBackground_;
   // Color of enabled text.
   private static HashMap<String, ColorUIResource> enabledTextColor_;
   // Color of disabled text.
   private static HashMap<String, ColorUIResource> disabledTextColor_;

   // Mode we were in before suspendToMode() was called.
   private static String suspendedMode_ = null;

   static {
      // Possible: make UI to let user set these colors
      background_ = new HashMap<String, ColorUIResource>();
      background_.put(CompatibilityInterface.DAY,
            new ColorUIResource(java.awt.SystemColor.control));
      background_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(new Color(64, 64, 64)));

      disabledBackground_ = new HashMap<String, ColorUIResource>();
      disabledBackground_.put(CompatibilityInterface.DAY,
            new ColorUIResource(Color.LIGHT_GRAY));
      disabledBackground_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(new Color(32, 32, 32)));

      lightBackground_ = new HashMap<String, ColorUIResource>();
      lightBackground_.put(CompatibilityInterface.DAY,
            new ColorUIResource(java.awt.SystemColor.control));
      // 37.5% gray; dodging both the OSX checkmark (25% gray) and disabled
      // text (50% gray).
      lightBackground_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(new Color(96, 96, 96)));

      padBackground_ = new HashMap<String, ColorUIResource>();
      padBackground_.put(CompatibilityInterface.DAY,
            new ColorUIResource(Color.white));
      padBackground_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(java.awt.SystemColor.control));

      enabledTextColor_ = new HashMap<String, ColorUIResource>();
      enabledTextColor_.put(CompatibilityInterface.DAY,
            new ColorUIResource(20, 20, 20));
      enabledTextColor_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(200, 200, 200));

      disabledTextColor_ = new HashMap<String, ColorUIResource>();
      disabledTextColor_.put(CompatibilityInterface.DAY,
            new ColorUIResource(100, 100, 100));
      disabledTextColor_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(60, 60, 60));
   }

   public static void setMode(String mode) {
      setMode(mode, true);
   }

   /**
    * This version of the function allows us to specify whether or not the
    * UI should be updated after changing modes. Not updating is only generally
    * wanted in cases where a one-off component must be created that doesn't
    * adhere to our custom look and feel; see suspendToMode() below.
    */
   private static void setMode(String mode, boolean shouldUpdateUI) {
      if (!(mode.contentEquals(CompatibilityInterface.DAY) ||
            mode.contentEquals(CompatibilityInterface.NIGHT))) {
         throw new IllegalArgumentException("Invalid background style \"" +
               mode + "\"");
      }
      storeBackgroundMode(mode);

      // Ensure every GUI object type gets the right background color.
      for (String key : BACKGROUND_COLOR_KEYS) {
         UIManager.put(key + ".background", background_.get(mode));
      }
      for (String key : LIGHTER_BACKGROUND_COLOR_KEYS) {
         UIManager.put(key + ".background", lightBackground_.get(mode));
      }
      for (String key : MANUAL_BACKGROUND_COLOR_KEYS) {
         UIManager.put(key, background_.get(mode));
      }
      for (String key : ENABLED_TEXT_COLOR_KEYS) {
         UIManager.put(key + ".foreground", enabledTextColor_.get(mode));
      }
      // Improve contrast of disabled text against backgrounds.
      for (String key : DISABLED_TEXT_COLOR_KEYS) {
         UIManager.put(key + ".disabledText", disabledTextColor_.get(mode));
      }
      if (shouldUpdateUI) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               // Update existing components.
               for (Window w : Window.getWindows()) {
                  SwingUtilities.updateComponentTreeUI(w);
               }
            }
         });
      }
   }

   /**
    * If the specified mode is not currently active, then we switch to that
    * mode without updating the UI. Useful if a component must be generated
    * with a nonstandard look-and-feel.
    */
   public static void suspendToMode(String mode) {
      suspendedMode_ = getBackgroundMode();
      if (suspendedMode_.equals(mode)) {
         // Already in the desired mode.
         suspendedMode_ = null;
         return;
      }
      setMode(mode, false);
   }

   /**
    * Restores the mode that was active before suspendToMode was called.
    */
   public static void resume() {
      if (suspendedMode_ != null) {
         setMode(suspendedMode_, false);
         suspendedMode_ = null;
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

   /**
    * Return the current background color.
    */
   public static Color getBackgroundColor() {
      return background_.get(getBackgroundMode());
   }

   /**
    * Return a proper "disabled" background color based on the current mode.
    */
   public static Color getDisabledBackgroundColor() {
      return disabledBackground_.get(getBackgroundMode());
   }

   /**
    * Return the current color for enabled text.
    */
   public static Color getEnabledTextColor() {
      return enabledTextColor_.get(getBackgroundMode());
   }

   /**
    * Return the current color for disabled text.
    */
   public static Color getDisabledTextColor() {
      return disabledTextColor_.get(getBackgroundMode());
   }

   /**
    * Because tables don't obey look and feel color commands, we have this
    * custom table that picks up its background based on the current mode.
    */
   public static class Table extends JTable {
      public Table() {
         super();
         validateColors();
      }

      public Table(TableModel model) {
         super(model);
         validateColors();
      }

      private void validateColors() {
         Color background = getBackground();
         Color targetBackground = UIManager.getColor("Table.background");
         if (!background.equals(targetBackground)) {
            setBackground(targetBackground);
         }
         Color foreground = getForeground();
         Color targetForeground = UIManager.getColor("Table.foreground");
         if (!foreground.equals(targetForeground)) {
            setForeground(targetForeground);
         }
      }

      /**
       * Before painting, ensure our color is correct.
       */
      @Override
      public void paint(Graphics g) {
         validateColors();
         super.paint(g);
      }
   }
}
