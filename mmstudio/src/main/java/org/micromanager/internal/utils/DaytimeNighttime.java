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
         "SplitPane", "TabbedPane", "TextArea", "TextField",
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
   // Lighter background colors, for certain elements.
   private static HashMap<String, ColorUIResource> lightBackground_;
   // background color of pads in the UI
   private static HashMap<String, ColorUIResource> padBackground_;
   // Color of enabled text.
   private static HashMap<String, ColorUIResource> enabledTextColor_;
   // Color of disabled text.
   private static HashMap<String, ColorUIResource> disabledTextColor_;

   static {
      // Possible: make UI to let user set these colors
      background_ = new HashMap<String, ColorUIResource>();
      background_.put(CompatibilityInterface.DAY,
            new ColorUIResource(java.awt.SystemColor.control));
      background_.put(CompatibilityInterface.NIGHT,
            new ColorUIResource(new Color(64, 64, 64)));

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
    * Because tables don't obey look and feel color commands, we have this
    * custom table that picks up its background based on the current mode.
    */
   public static class Table extends JTable {
      public Table() {
         super();
         validateBackground();
      }

      public Table(TableModel model) {
         super(model);
         validateBackground();
      }

      private void validateBackground() {
         Color background = getBackground();
         Color targetBackground = UIManager.getColor("Table.background");
         if (!background.equals(targetBackground)) {
            setBackground(targetBackground);
         }
      }

      /**
       * Before painting, ensure our color is correct.
       */
      @Override
      public void paint(Graphics g) {
         validateBackground();
         super.paint(g);
      }
   }
}
