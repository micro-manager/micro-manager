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

import com.google.common.eventbus.Subscribe;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.BorderFactory;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.events.StartupCompleteEvent;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ScreenImage;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.MMPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

/**
 * This class represents a single control in the window.
 */
public final class ControlCell {
   private Studio studio_;
   // Plugin that created the control.
   private QuickAccessPlugin plugin_;
   // Configuration if the plugin is a WidgetPlugin.
   private PropertyMap config_;
   // Rectangle in the grid that the control occupies.
   private Rectangle rect_;
   // The control itself (in a JPanel).
   private JComponent widget_;
   // An iconified version of the control.
   private DraggableIcon icon_;

   public ControlCell(Studio studio, QuickAccessFrame frame,
         QuickAccessPlugin plugin, PropertyMap config, Rectangle rect) {
      studio_ = studio;
      plugin_ = plugin;
      config_ = config;
      rect_ = rect;
      JPanel panel = new JPanel(new MigLayout("fill, insets 0, gap 0"));
      JComponent control = null;
      if (plugin instanceof WidgetPlugin) {
         WidgetPlugin widget = (WidgetPlugin) plugin;
         if (config == null) {
            config = widget.configureControl(frame);
         }
         rect_.width = widget.getSize().width;
         rect_.height = widget.getSize().height;
         control = widget.createControl(config);
      }
      else {
         control = QuickAccessFactory.makeGUI(plugin);
      }
      panel.add(control, "align center, grow");
      widget_ = panel;
      icon_ = new DraggableIcon(studio, frame, control, null, plugin, true,
            this, rect_.getSize());
   }

   public Rectangle getRect() {
      return rect_;
   }

   public void moveTo(Point p) {
      rect_.x = p.x;
      rect_.y = p.y;
   }

   public JComponent getWidget() {
      return widget_;
   }

   public DraggableIcon getIcon() {
      return icon_;
   }

   public PropertyMap getConfig() {
      return config_;
   }

   public JSONObject toJSON() throws JSONException {
      JSONObject result = new JSONObject();
      result.put("pluginName", plugin_.getClass().getName());
      if (config_ != null) {
         result.put("config", ((DefaultPropertyMap) config_).toJSON());
      }
      result.put("rectX", rect_.x);
      result.put("rectY", rect_.y);
      result.put("rectWidth", rect_.width);
      result.put("rectHeight", rect_.height);
      return result;
   }

   @Override
   public String toString() {
      return String.format("<ControlCell for %s at %s>",
            plugin_.getName(), rect_);
   }

   public static ControlCell fromJSON(JSONObject json, Studio studio,
         QuickAccessFrame frame) {
      String pluginName = "";
      try {
         pluginName = json.getString("pluginName");
         QuickAccessPlugin plugin = studio.plugins().getQuickAccessPlugins().get(pluginName);
         PropertyMap config = null;
         if (json.has("config")) {
            config = DefaultPropertyMap.fromJSON(json.getJSONObject("config"));
         }
         Rectangle rect = new Rectangle(
               json.getInt("rectX"), json.getInt("rectY"),
               json.getInt("rectWidth"), json.getInt("rectHeight"));
         return new ControlCell(studio, frame, plugin, config, rect);
      }
      catch (JSONException e) {
         studio.logs().logError(e,
               "Unable to deserialize ControlCell from " + json);
      }
      catch (NullPointerException e) {
         studio.logs().logError(e, "Unable to reload plugin " + pluginName);
      }
      return null;
   }
}
