///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.quickaccess.internal;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.base.Charsets;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.Files;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.events.StartupCompleteEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ScreenImage;
import org.micromanager.quickaccess.QuickAccessManager;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;

/**
 * This class is responsible for managing the different Quick Access Windows.
 */
public final class DefaultQuickAccessManager implements QuickAccessManager {
   // These strings are used for formatting the JSON used to describe custom
   // icons.
   public static final String ICON_TYPE = "custom icon type";
   public static final String CUSTOM_FILE = "icon loaded from user-provided file";
   public static final String JAR_ICON = "icon loaded from our jar";
   public static final String COLOR_SWATCH = "color swatch icon";
   public static final String ICON_PATH = "path to icon file";
   public static final String ICON_COLOR = "RGB color int for color swatches";

   private static final String SAVED_CONFIG = "Saved configuration for quick access windows";

   private final Studio studio_;
   private final ArrayList<QuickAccessFrame> knownPanels_;

   public DefaultQuickAccessManager(Studio studio) {
      studio_ = studio;
      studio.events().registerForEvents(this);
      knownPanels_ = new ArrayList<QuickAccessFrame>();
   }

   /**
    * Check the user's profile to see if we have any saved settings there.
    * @param event Event that is fired when MM completes startup
    */
   @Subscribe
   public void onStartupComplete(StartupCompleteEvent event) {
      try {
         String configStr = studio_.profile().
                 getSettings(QuickAccessManager.class).getString( SAVED_CONFIG, null);
         if (configStr == null) {
            // Nothing saved.
            return;
         }
         loadConfig(configStr);
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Unable to reload Quick Access config");
      }
   }

   /**
    * Load settings from the provided config string.
    */
   private void loadConfig(String configStr) {
      try {
         JSONObject config = new JSONObject(configStr);
         JSONArray panels = config.getJSONArray("panels");
         for (int i = 0; i < panels.length(); ++i) {
            JSONObject panelConfig = panels.getJSONObject(i);
            addPanel(panelConfig);
         }
      }
      catch (JSONException e) {
         studio_.logs().logError(e, "Unable to reconstruct Quick Access Window from config.");
      }
   }

   /**
    * Save the current setup to the user's profile.
    */
   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      String config = getConfig(false);
      studio_.profile().getSettings(QuickAccessManager.class).
              putString(SAVED_CONFIG, config);
   }

   /**
    * Create a string version of our config.
    */
   private String getConfig(boolean isPretty) {
      try {
         // We store the array in a JSONObject, instead of storing the array
         // directly, to give us room to expand in future.
         JSONObject config = new JSONObject();
         JSONArray panelConfigs = new JSONArray();
         for (QuickAccessFrame panel : knownPanels_) {
            panelConfigs.put(panel.getConfig());
         }
         config.put("panels", panelConfigs);
         return config.toString(isPretty ? 2 : 0);
      }
      catch (JSONException e) {
         studio_.logs().logError(e, "Error saving Quick Access Window config");
         return "";
      }
   }

   @Override
   public void showPanels() {
      if (knownPanels_.isEmpty()) {
         addPanel(new JSONObject());
      }
      for (QuickAccessFrame panel : knownPanels_) {
         panel.setVisible(true);
      }
   }

   @Override
   public Map<String, JFrame> getPanels() {
      HashMap<String, JFrame> result = new HashMap<String, JFrame>();
      for (QuickAccessFrame panel : knownPanels_) {
         result.put(panel.getTitle(), panel);
      }
      return result;
   }

   @Override
   public Icon getCustomIcon(PropertyMap config, Icon defaultIcon) {
      String configString = config.getString(WidgetPlugin.CUSTOM_ICON_STRING,
            null);
      if (configString == null) {
         // No custom icon info.
         return defaultIcon;
      }
      try {
         JSONObject json = new JSONObject(configString);
         String iconType = json.getString(ICON_TYPE);
         if (iconType.equals(CUSTOM_FILE)) {
            // Load the icon from file.
            String iconPath = json.getString(ICON_PATH);
            try {
               Image image = ImageIO.read(new File(iconPath));
               return new ImageIcon(image);
            }
            catch (IOException e) {
               studio_.logs().showError(e, "Unable to find image at " + iconPath);
            }
         }
         else if (iconType.equals(COLOR_SWATCH)) {
            // Create a square icon by rendering a JLabel.
            Color color = new Color(json.getInt(ICON_COLOR));
            return createSwatch(color, QuickAccessPlugin.CELL_HEIGHT - 16);
         }
         else if (iconType.equals(JAR_ICON)) {
            // Load the icon from our jar.
            return IconLoader.getIcon("/org/micromanager/icons/" +
                  json.getString(ICON_PATH) + ".png");
         }
         else {
            studio_.logs().logError("Unsupported icon type " + iconType);
         }
      }
      catch (JSONException e) {
         studio_.logs().logError(e, "Unable to create custom icon");
      }
      return defaultIcon;
   }

   @Override
   public void saveSettingsToFile(File file) {
      try {
         String settings = getConfig(true);
         FileWriter writer = new FileWriter(file);
         writer.write(settings + "\n");
         writer.close();
      }
      catch (IOException e) {
         studio_.logs().logError(e, "Error saving settings to " + file);
      }
   }

   @Override
   public void loadSettingsFromFile(File file) {
      try {
         loadConfig(Files.toString(file, Charsets.UTF_8));
      }
      catch (IOException e) {
         studio_.logs().logError(e, "Error loading settings from " + file);
      }
   }

   /**
    * Dynamically generate an icon that's a square of the specified color
    * with the specified edge length.
    */
   public static ImageIcon createSwatch(Color color, int size) {
      JLabel tmp = new JLabel();
      tmp.setBackground(color);
      tmp.setOpaque(true);
      tmp.setSize(new Dimension(size, size));
      tmp.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      return new ImageIcon(ScreenImage.createImage(tmp));
   }

   /**
    * Create a new panel with the provided configuration information.
    */
   private void addPanel(JSONObject config) {
      knownPanels_.add(new QuickAccessFrame(studio_, config));
      studio_.events().post(new QuickAccessPanelEvent());
   }

   /**
    * Prompt the user to delete the specified panel.
    */
   public void promptToDelete(JFrame panel) {
      if (!(panel instanceof QuickAccessFrame)) {
         // This should never happen.
         studio_.logs().logError(
               "Asked to delete JFrame that isn't a QuickAccessFrame.");
      }
      if (JOptionPane.showConfirmDialog(panel,
         "Really delete this panel and lose its configuration?",
         "Confirm Panel Deletion", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
         deletePanel((QuickAccessFrame) panel);
         panel.dispose();
      }
   }

   /**
    * Provides a unique string for the provided panel, based on the provided
    * base title and the titles of the other panels.
    * @param panel QuickAccessFrame whose title we want to test
    * @param base Proposed new title for this frame
    * @return Unique title
    */
   public String getUniqueTitle(QuickAccessFrame panel, String base) {
      for (QuickAccessFrame alt : knownPanels_) {
         if (alt != panel) {
            if (base.equals(alt.getTitle())) {
               // Only need to modify if the names are equal
               String[] baseParts = base.split(" ");
               try {
                  int nr = NumberUtils.displayStringToInt(baseParts[baseParts.length - 1]);
                  nr++;
                  baseParts[baseParts.length - 1] = "" + nr;
                  // reassemble the name
                  base = baseParts[0];
                  for (int i = 1; i < baseParts.length; i++) {
                     base += " " + baseParts[i];
                  }
               } catch (ParseException pe) {
                  // last part was not a number, so we can safely use 1
                  base += " 1";
               }
               // since we modified the title, we need to test again against all titles
               // use recursion:
               return getUniqueTitle(panel, base);
            }
         }
      }
      return base;
   }

   /**
    * Create a new, empty panel.
    */
   public void createNewPanel() {
      addPanel(new JSONObject());
   }

   /**
    * Remove an existing panel.
    * @param panel The panel to be removed
    */
   public void deletePanel(QuickAccessFrame panel) {
      knownPanels_.remove(panel);
      studio_.events().post(new QuickAccessPanelEvent());
   }
}
