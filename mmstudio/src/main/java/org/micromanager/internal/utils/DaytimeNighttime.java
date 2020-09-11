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
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.TableModel;
import org.micromanager.ApplicationSkin;
import org.micromanager.ApplicationSkin.SkinMode;
import org.micromanager.Studio;
import org.micromanager.events.internal.DefaultApplicationSkinEvent;

/*
 * This class controls the colors of the user interface
 * Note we use ColorUIResources instead of Colors because Colors don't
 * interact well with Look and Feel; see
 * http://stackoverflow.com/questions/27933017/cant-update-look-and-feel-on-the-fly
 */
public final class DaytimeNighttime implements ApplicationSkin {

   // Key into the user's profile for the current display mode.
   private static final String BACKGROUND_MODE = "current window style (as per ApplicationSkin.SkinMode)";
   // List of keys to UIManager.put() method for setting the background color
   // look and feel. Selected from this page:
   // http://alvinalexander.com/java/java-uimanager-color-keys-list
   // Each of these keys will have ".background" appended to it later.
   private static final String[] BACKGROUND_COLOR_KEYS = new String[] {
         "Button", "CheckBox", "ColorChooser", "EditorPane",
         "FormattedTextField", "InternalFrame", "Label", "List", "MenuBar",
         "OptionPane", "Panel", "PasswordField", "ProgressBar",
         "RadioButton", "ScrollBar", "ScrollPane", "Slider", "Spinner",
         "SplitPane", "Table", "TableHeader", "TextArea",
         "TextField", "TextPane", "ToggleButton", "ToolBar", "Tree",
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
         "RadioButton", "ScrollPane", "Separator", "Slider", "Spinner",
         "SplitPane", "Table", "TableHeader", "TextArea", "TextField",
         "TextPane", "ToolBar", "Tree", "Viewport"
   };

   // As above, but for disabled text; each of these keys will have
   // ".disabledText" appended to it later.
   private static final String[] DISABLED_TEXT_COLOR_KEYS = new String[] {
         "Button", "CheckBox", "RadioButton", "ToggleButton"
   };

   // Keys that we have to specify manually; nothing will be appended to them.
   private static final String[] MANUAL_TEXT_COLOR_KEYS = new String[] {
      "Tree.textForeground", "TitledBorder.titleColor", "OptionPane.messageForeground"
   };

   // As above, but for background color.
   private static final String[] MANUAL_BACKGROUND_COLOR_KEYS = new String[] {
         "ComboBox.buttonBackground", "Tree.textBackground",
   };

   // background color of the UI
   private final HashMap<SkinMode, ColorUIResource> background_;
   // background color of disabled UI elements.
   private final HashMap<SkinMode, ColorUIResource> disabledBackground_;
   // Lighter background colors, for certain elements.
   private final HashMap<SkinMode, ColorUIResource> lightBackground_;
   // background color of pads in the UI
   private final HashMap<SkinMode, ColorUIResource> padBackground_;
   // Color of enabled text.
   private final HashMap<SkinMode, ColorUIResource> enabledTextColor_;
   // Color of disabled text.
   private final HashMap<SkinMode, ColorUIResource> disabledTextColor_;

   // Mode we were in before suspendToMode() was called.
   private SkinMode suspendedMode_ = null;
   
   // only the UserProfile is used.  This class could depend on UserProfile
   // instead, but it is unclear to me if the UserProfile can change while 
   // the application executes, so better to always request the pointer that 
   // Studio has
   private final Studio studio_;

   public static DaytimeNighttime create(Studio studio)
   {
      DaytimeNighttime skin = new DaytimeNighttime(studio);
      skin.loadStoredSkin();
      return skin;
   }

   private DaytimeNighttime(Studio studio) {
      studio_ = studio;
      // Possible: make UI to let user set these colors
      background_ = new HashMap<>();
      background_.put(SkinMode.DAY,
            new ColorUIResource(java.awt.SystemColor.control));
      background_.put(SkinMode.NIGHT,
            new ColorUIResource(new Color(64, 64, 64)));

      disabledBackground_ = new HashMap<>();
      disabledBackground_.put(SkinMode.DAY,
            new ColorUIResource(Color.LIGHT_GRAY));
      disabledBackground_.put(SkinMode.NIGHT,
            new ColorUIResource(new Color(32, 32, 32)));

      lightBackground_ = new HashMap<>();
      lightBackground_.put(SkinMode.DAY,
            new ColorUIResource(java.awt.SystemColor.control));
      // 37.5% gray; dodging both the OSX checkmark (25% gray) and disabled
      // text (50% gray).
      lightBackground_.put(SkinMode.NIGHT,
            new ColorUIResource(new Color(96, 96, 96)));

      padBackground_ = new HashMap<>();
      padBackground_.put(SkinMode.DAY,
            new ColorUIResource(Color.white));
      padBackground_.put(SkinMode.NIGHT,
            new ColorUIResource(java.awt.SystemColor.control));

      enabledTextColor_ = new HashMap<>();
      enabledTextColor_.put(SkinMode.DAY,
            new ColorUIResource(20, 20, 20));
      enabledTextColor_.put(SkinMode.NIGHT,
            new ColorUIResource(200, 200, 200));

      disabledTextColor_ = new HashMap<>();
      disabledTextColor_.put(SkinMode.DAY,
            new ColorUIResource(100, 100, 100));
      disabledTextColor_.put(SkinMode.NIGHT,
            new ColorUIResource(120, 120, 120));
   }

   @Override
   public void setSkin(SkinMode mode) {
      setMode(mode, true);
   }

   /**
    * This version of the function allows us to specify whether or not the
    * UI should be updated after changing modes. Not updating is only generally
    * wanted in cases where a one-off component must be created that doesn't
    * adhere to our custom look and feel; see suspendToMode() below.
    */
   private void setMode(SkinMode mode, boolean shouldUpdateUI) {
      storeSkin(mode);

      // Ensure every GUI object type gets the right background color.
      for (String key : BACKGROUND_COLOR_KEYS) {
         UIManager.put(key + ".background", background_.get(mode));
      }
      for (String key : LIGHTER_BACKGROUND_COLOR_KEYS) {
         UIManager.put(key + ".background", lightBackground_.get(mode));
      }
      for (String key : MANUAL_TEXT_COLOR_KEYS) {
         UIManager.put(key, enabledTextColor_.get(mode));
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
         SwingUtilities.invokeLater(() -> {
            // Update existing components.
            for (Window w : Window.getWindows()) {
               SwingUtilities.updateComponentTreeUI(w);
            }

            // Alert any listeners of the change.
            if(studio_ != null && studio_.events() != null)
              studio_.events().post(new DefaultApplicationSkinEvent(mode));
         });
      }
   }

   /**
    * If the specified mode is not currently active, then we switch to that
    * mode without updating the UI. Useful if a component must be generated
    * with a nonstandard look-and-feel.
    * @param mode SkinMode to switch to (but without updating the UI)
    */
   @Override
   public void suspendToMode(SkinMode mode) {
      suspendedMode_ = getSkin();
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
   @Override
   public void resume() {
      if (suspendedMode_ != null) {
         setMode(suspendedMode_, false);
         suspendedMode_ = null;
      }
   }

   /**
    * Set a new default background mode in the user's profile.
    * @param mode new default background mode
    */
   private void storeSkin(SkinMode mode) {
      studio_.profile().getSettings(
              DaytimeNighttime.class).putString(
            BACKGROUND_MODE, mode.getDesc());
   }

   /**
    * Return the current stored background mode from the profile.
    * @return current stored background mode from the profile
    */
   @Override
   public SkinMode getSkin() {
      return SkinMode.fromString(studio_.profile().
              getSettings(DaytimeNighttime.class).getString(
            BACKGROUND_MODE, SkinMode.NIGHT.getDesc()));
   }

   /**
    * Load the stored skin from the profile and apply it.
    */
   public void loadStoredSkin() {
      setSkin(getSkin());
   }

   /**
    * Return the current background color.
    * @return current background color
    */
   @Override
   public Color getBackgroundColor() {
      return background_.get(getSkin());
   }

   /**
    * Return the current "lighter" background color.
    * @return light background color
    */
   @Override
   public Color getLightBackgroundColor() {
      return lightBackground_.get(getSkin());
   }

   /**
    * Return a proper "disabled" background color based on the current mode.
    * @return "disabled" background color based on the current mode
    */
   @Override
   public Color getDisabledBackgroundColor() {
      return disabledBackground_.get(getSkin());
   }

   /**
    * Return the current color for enabled text.
    * @return current color for enabled text
    */
   @Override
   public Color getEnabledTextColor() {
      return enabledTextColor_.get(getSkin());
   }

   /**
    * Return the current color for disabled text.
    * @return current color for disabled text.
    */
   @Override
   public Color getDisabledTextColor() {
      return disabledTextColor_.get(getSkin());
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
       * @param g Graphics to be painted
       */
      @Override
      public void paint(Graphics g) {
         validateColors();
         super.paint(g);
      }
   }
}
