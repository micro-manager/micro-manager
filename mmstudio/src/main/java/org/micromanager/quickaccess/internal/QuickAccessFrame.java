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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Graphics;
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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.events.StartupCompleteEvent;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ScreenImage;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.MMPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

/**
 * This class shows the Quick-Access Window for frequently-used controls.
 */
public class QuickAccessFrame extends MMFrame {
   // Profile keys.
   private static final String NUM_COLS = "Number of columns in the Quick-Access Window";
   private static final String NUM_ROWS = "Number of rows in the Quick-Access Window";
   private static final String SAVED_CONFIG = "Saved configuration of the Quick-Access Window";

   private static QuickAccessFrame staticInstance_;
   public static void makeFrame(Studio studio) {
      staticInstance_ = new QuickAccessFrame(studio);
      studio.events().registerForEvents(staticInstance_);
   }

   /**
    * Show the Quick-Access Window, creating it if it does not already exist.
    */
   public static void showFrame(Studio studio) {
      staticInstance_.setVisible(true);
   }

   private Studio studio_;
   // Holds everything.
   private JPanel contentsPanel_;
   // Holds the active controls.
   private JPanel controlsPanel_;
   // Holds icons representing the current active controls.
   private JPanel configuringControlsPanel_;
   // Holds the "source" icons that can be added to the active controls, as
   // well as the rows/columns configuration fields.
   private JPanel configurePanel_;
   // Switches between normal and configure modes.
   private JToggleButton configureButton_;

   // All of the controls we have in the grid.
   private HashSet<ControlCell> controls_;
   private int numCols_;
   private int numRows_;

   private int mouseX_ = -1;
   private int mouseY_ = -1;
   private DraggableIcon draggedIcon_ = null;
   private int iconOffsetX_ = -1;
   private int iconOffsetY_ = -1;

   public QuickAccessFrame(Studio studio) {
      super("Quick-Access Tools");
      studio_ = studio;
      controls_ = new HashSet<ControlCell>();
      numCols_ = studio_.profile().getInt(QuickAccessFrame.class,
            NUM_COLS, 3);
      numRows_ = studio_.profile().getInt(QuickAccessFrame.class,
            NUM_ROWS, 3);

      // Hide ourselves when the close button is clicked, instead of letting
      // us be destroyed.
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            setVisible(false);
         }
      });

      // Layout overview: the active controls, then a configure button, then
      // the configuration panel (normally hidden).
      contentsPanel_ = new JPanel(new MigLayout("flowy, fill"));

      // We have two panels that are mutually-exclusive: controlsPanel_ and
      // configuringControlsPanel_. The latter is only visible in configuration
      // mode, and has special painting logic.
      controlsPanel_ = new GridPanel(false);
      configuringControlsPanel_ = new GridPanel(true);

      contentsPanel_.add(controlsPanel_);

      configureButton_ = new JToggleButton("Configure");
      configureButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            toggleConfigurePanel(configureButton_.isSelected());
         }
      });
      contentsPanel_.add(configureButton_);

      configurePanel_ = new ConfigurationPanel();

      add(contentsPanel_);
      pack();
      // Note that showFrame() will call setVisible() for us.
   }

   /**
    * Check the user's profile to see if we have any saved settings there.
    */
   @Subscribe
   public void onStartupComplete(StartupCompleteEvent event) {
      boolean shouldShow = false;
      String configStr = studio_.profile().getString(
            QuickAccessFrame.class, SAVED_CONFIG, null);
      if (configStr == null) {
         // Nothing saved.
         return;
      }
      try {
         JSONArray config = new JSONArray(configStr);
         for (int i = 0; i < config.length(); ++i) {
            addControl(controlCellFromJSON(config.getJSONObject(i)));
            shouldShow = true;
         }
      }
      catch (JSONException e) {
         studio_.logs().logError(e, "Unable to reconstruct Quick-Access Window from config.");
      }
      if (shouldShow) {
         setVisible(true);
      }
   }

   /**
    * Save our current state to the profile.
    */
   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      JSONArray settings = new JSONArray();
      for (ControlCell cell : controls_) {
         settings.put(cell.toJSON());
      }
      studio_.profile().setString(QuickAccessFrame.class, SAVED_CONFIG,
            settings.toString());
   }

   /**
    * We need to paint any dragged icon being moved from place to place.
    */
   @Override
   public void paint(Graphics g) {
      super.paint(g);
      if (draggedIcon_ != null) {
         g.drawImage(draggedIcon_.icon_.getImage(),
               mouseX_ - iconOffsetX_ + getInsets().left,
               mouseY_ - iconOffsetY_ + getInsets().top, null);
      }
   }

   /**
    * Drop the specified plugin into the controlsPanel_ at the current mouse
    * location, creating a new control (and configuring it if necessary).
    * @param plugin The source plugin to create the new control.
    */
   private void dropPlugin(QuickAccessPlugin plugin) {
      Point p = getCell();
      if (p == null) {
         // Can't drop anything here.
         return;
      }
      // Default to 1x1 cell.
      Rectangle rect = new Rectangle(p.x, p.y, 1, 1);
      if (p != null && plugin != null) {
         PropertyMap config = null;
         if (plugin instanceof WidgetPlugin) {
            config = ((WidgetPlugin) plugin).configureControl(this);
         }
         ControlCell cell = new ControlCell(plugin, config, rect);
         addControl(cell);
      }
      QuickAccessFrame.this.repaint();
   }

   /**
    * Add a control to the grid of controls at the specified location and
    * dimensionality.
    * This requires removing and then re-adding all components because the
    * size of the MigLayout entity may have changed.
    */
   private void addControl(ControlCell cell) {
      controlsPanel_.removeAll();
      configuringControlsPanel_.removeAll();
      controls_.add(cell);
      // Restore controls.
      for (ControlCell c : controls_) {
         controlsPanel_.add(c.widget_, c.rect_);
         configuringControlsPanel_.add(c.icon_, c.rect_);
      }
      validate();
   }

   /**
    * Return true if there is room for a control at the desired
    * rect. Optionally include a control being moved, so we don't have to
    * worry about self-intersections.
    */
   private boolean canFitRect(Rectangle rect, ControlCell source) {
      for (ControlCell alt : controls_) {
         if (alt == source) {
            // Don't need to compare against yourself for hit detection.
            continue;
         }
         if (alt.rect_.intersects(rect)) {
            return false;
         }
      }
      return true;
   }

   /**
    * Move a control to a new location in the grid.
    */
   private void moveControl(ControlCell control, Point p) {
      removeControl(control, false);
      control.rect_.x = p.x;
      control.rect_.y = p.y;
      addControl(control);
      validate();
      repaint();
   }

   /**
    * Clear a control from the grid.
    */
   private void removeControl(ControlCell control, boolean shouldRedraw) {
      controls_.remove(control);
      controlsPanel_.remove(control.widget_);
      configuringControlsPanel_.remove(control.icon_);
      if (shouldRedraw) {
         validate();
         repaint();
      }
   }

   /**
    * Toggle visibility of the customization panel.
    */
   private void toggleConfigurePanel(boolean isShown) {
      contentsPanel_.removeAll();
      contentsPanel_.add(isShown ? configuringControlsPanel_ : controlsPanel_);
      contentsPanel_.add(configureButton_);
      if (isShown) {
         contentsPanel_.add(configurePanel_, "growx");
      }
      pack();
   }

   /**
    * Change the shape of the grid we use.
    */
   private void setGridSize(int cols, int rows) {
      numCols_ = cols;
      numRows_ = rows;
      // Show/hide controls depending on if they're in-bounds.
      for (ControlCell control : controls_) {
         Rectangle r = control.rect_;
         boolean isVisible = (r.x + r.width < numCols_ &&
               r.y + r.height < numRows_);
         control.widget_.setVisible(isVisible);
         control.icon_.setVisible(isVisible);
      }
      contentsPanel_.invalidate();
      pack();
      repaint();
   }

   /**
    * Map the current mouse position (relative to the window) into a cell
    * location (in the controlsPanel_). Returns null if the location is not
    * valid.
    */
   private Point getCell() {
      Point p = new Point(mouseX_, mouseY_);
      SwingUtilities.convertPointFromScreen(p, controlsPanel_);
      int cellX = p.x / QuickAccessPlugin.CELL_WIDTH;
      int cellY = p.y / QuickAccessPlugin.CELL_HEIGHT;
      if (cellX >= numCols_ || cellY >= numRows_ || cellX < 0 || cellY < 0) {
         // Out of bounds.
         return null;
      }
      return new Point(cellX, cellY);
   }

   /**
    * These panels have a fixed size based on the number of rows/columns.
    * Depending on the boolean isConfigurePanel_, they may also draw a
    * semitransparent grid over their contents (which in turn are expected to
    * be DraggableIcons instead of normal controls).
    */
   private class GridPanel extends JPanel {
      private boolean isConfigurePanel_;
      public GridPanel(boolean isConfigurePanel) {
         super(new SparseGridLayout(QuickAccessPlugin.CELL_WIDTH,
                  QuickAccessPlugin.CELL_HEIGHT));
         isConfigurePanel_ = isConfigurePanel;
      }

      @Override
      public void paint(Graphics g) {
         super.paint(g);
         if (!isConfigurePanel_ || !configureButton_.isSelected()) {
            // We aren't in configure mode; just draw normally.
            return;
         }
         // Draw a grid showing the cells.
         int width = getSize().width;
         int height = getSize().height;
         g.setColor(new Color(200, 255, 200, 128));
         g.fillRect(0, 0, numCols_ * QuickAccessPlugin.CELL_WIDTH,
               numRows_ * QuickAccessPlugin.CELL_HEIGHT);
         if (draggedIcon_ != null) {
            // Draw the cells the icon would go into in a highlighted color,
            // or in red if the icon's corresponding control would not fit.
            Point p = getCell();
            if (p != null) {
               Rectangle rect = new Rectangle(p.x, p.y,
                     draggedIcon_.getSize().width,
                     draggedIcon_.getSize().height);
               if (canFitRect(rect, draggedIcon_.parent_)) {
                  g.setColor(new Color(255, 200, 200, 128));
               }
               else {
                  g.setColor(new Color(255, 50, 50, 192));
               }
               g.fillRect(p.x * QuickAccessPlugin.CELL_WIDTH,
                     p.y * QuickAccessPlugin.CELL_HEIGHT,
                     QuickAccessPlugin.CELL_WIDTH * rect.width,
                     QuickAccessPlugin.CELL_HEIGHT * rect.height);
            }
         }
         // Draw the grid lines.
         g.setColor(Color.BLACK);
         for (int i = 0; i < numCols_ + 1; ++i) {
            g.drawLine(i * QuickAccessPlugin.CELL_WIDTH, 0,
                  i * QuickAccessPlugin.CELL_WIDTH,
                  QuickAccessPlugin.CELL_HEIGHT * numRows_);
         }
         for (int i = 0; i < numRows_ + 1; ++i) {
            g.drawLine(0, i * QuickAccessPlugin.CELL_HEIGHT,
                  numCols_ * QuickAccessPlugin.CELL_WIDTH,
                  i * QuickAccessPlugin.CELL_HEIGHT);
         }
      }

      public Dimension getPreferredSize() {
         return new Dimension(numCols_ * QuickAccessPlugin.CELL_WIDTH,
               numRows_ * QuickAccessPlugin.CELL_HEIGHT);
      }
   }

   /**
    * This class exposes a GUI for adding and removing controls from the
    * main window.
    */
   private class ConfigurationPanel extends JPanel {
      // Maps iconified versions of controls to the plugins that generated
      // them.
      private JSpinner colsControl_;
      private JSpinner rowsControl_;

      public ConfigurationPanel() {
         super(new MigLayout(
                  String.format("flowx, wrap %d", numCols_)));
         setBorder(BorderFactory.createLoweredBevelBorder());

         // Add controls for setting rows/columns.
         // It really bugs me how redundant this code is. Java!
         JPanel subPanel = new JPanel(new MigLayout("flowx"));
         subPanel.add(new JLabel("Columns: "));
         colsControl_ = new JSpinner(
               new SpinnerNumberModel(numCols_, 1, 99, 1));
         subPanel.add(colsControl_);
         colsControl_.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
               updateSize();
            }
         });

         subPanel.add(new JLabel("Rows: "));
         rowsControl_ = new JSpinner(
               new SpinnerNumberModel(numCols_, 1, 99, 1));
         subPanel.add(rowsControl_, "wrap");
         rowsControl_.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
               updateSize();
            }
         });
         subPanel.add(new JLabel("Drag controls into the grid above to add them to the window."), "span, wrap");

         add(subPanel, "span, wrap");

         // Populate the panel with icons corresponding to controls we can
         // provide. List them in alphabetical order.
         HashMap<String, QuickAccessPlugin> plugins = studio_.plugins().getQuickAccessPlugins();
         ArrayList<String> keys = new ArrayList<String>(plugins.keySet());
         Collections.sort(keys);
         for (String key : keys) {
            JPanel iconPanel = new JPanel(new MigLayout("flowy, insets 0"));
            iconPanel.setBorder(BorderFactory.createLoweredBevelBorder());
            QuickAccessPlugin plugin = plugins.get(key);
            Dimension size = new Dimension(1, 1);
            if (plugin instanceof WidgetPlugin) {
               size = ((WidgetPlugin) plugin).getSize();
            }
            DraggableIcon dragger = new DraggableIcon(
                  QuickAccessFactory.makeGUI(plugin), plugin.getIcon(), plugin,
                  null, size);
            iconPanel.add(dragger,
                  String.format("alignx center, w %d!, h %d!",
                     QuickAccessPlugin.CELL_WIDTH,
                     QuickAccessPlugin.CELL_HEIGHT));
            JLabel name = new JLabel(plugins.get(key).getName());
            name.setFont(GUIUtils.buttonFont);
            iconPanel.add(name, "alignx center");
            add(iconPanel, "alignx center");
         }
      }

      /**
       * Change the grid size. Just a helper function to avoid duplication.
       */
      private void updateSize() {
         try {
            setGridSize(
                  (Integer) ((SpinnerNumberModel) colsControl_.getModel()).getNumber(),
                  (Integer) ((SpinnerNumberModel) rowsControl_.getModel()).getNumber());
         }
         catch (Exception e) {
            // Ignore it.
         }
      }
   }

   /**
    * This class represents a single control in the window.
    */
   private class ControlCell {
      // Plugin that created the control.
      public QuickAccessPlugin plugin_;
      // Configuration if the plugin is a WidgetPlugin.
      public PropertyMap config_;
      // Rectangle in the grid that the control occupies.
      public Rectangle rect_;
      // The control itself (in a JPanel).
      public JComponent widget_;
      // An iconified version of the control.
      public DraggableIcon icon_;

      public ControlCell(QuickAccessPlugin plugin, PropertyMap config,
            Rectangle rect) {
         plugin_ = plugin;
         config_ = config;
         rect_ = rect;
         JPanel panel = new JPanel(new MigLayout("fill"));
         JComponent control = null;
         if (plugin instanceof WidgetPlugin) {
            WidgetPlugin widget = (WidgetPlugin) plugin;
            if (config == null) {
               config = widget.configureControl(QuickAccessFrame.this);
            }
            rect_.width = widget.getSize().width;
            rect_.height = widget.getSize().height;
            control = widget.createControl(config);
         }
         else {
            control = QuickAccessFactory.makeGUI(plugin);
         }
         panel.add(control, "align center");
         widget_ = panel;
         icon_ = new DraggableIcon(control, null, null, this, rect_.getSize());
      }

      public JSONObject toJSON() {
         try {
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
         catch (JSONException e) {
            studio_.logs().logError(e, "Error serializing ControlCell");
            return null;
         }
      }

      @Override
      public String toString() {
         return String.format("<ControlCell for %s at %s>",
               plugin_.getName(), rect_);
      }
   }

   private ControlCell controlCellFromJSON(JSONObject json) {
      String pluginName = "";
      try {
         pluginName = json.getString("pluginName");
         QuickAccessPlugin plugin = studio_.plugins().getQuickAccessPlugins().get(pluginName);
         PropertyMap config = null;
         if (json.has("config")) {
            config = DefaultPropertyMap.fromJSON(json.getJSONObject("config"));
         }
         Rectangle rect = new Rectangle(
               json.getInt("rectX"), json.getInt("rectY"),
               json.getInt("rectWidth"), json.getInt("rectHeight"));
         return new ControlCell(plugin, config, rect);
      }
      catch (JSONException e) {
         studio_.logs().logError(e,
               "Unable to deserialize ControlCell from " + json);
      }
      catch (NullPointerException e) {
         studio_.logs().logError(e, "Unable to reload plugin " + pluginName);
      }
      return null;
   }

   /**
    * This class represents an icon that can be dragged around the window,
    * for adding or removing controls.
    */
   private class DraggableIcon extends JLabel {
      private JComponent component_;
      private QuickAccessPlugin plugin_;
      private ControlCell parent_;
      private ImageIcon icon_;

      /**
       * icon and plugin are both optional. If plugin is null, then we assume
       * this references an instantiated control which can be removed from
       * the controlsPanel_ when dragged out of the grid. Otherwise, we want to
       * create a new control when the icon is dragged into the grid). If icon
       * is null, then we generate an icon from the JComponent.
       * @param size Number of cells (width x height) taken up by the icon.
       */
      public DraggableIcon(final JComponent component, ImageIcon icon,
            final QuickAccessPlugin plugin, final ControlCell parent,
            Dimension size) {
         super();
         component_ = component;
         plugin_ = plugin;
         parent_ = parent;
         icon_ = icon;
         if (icon_ == null) {
            // Render the image, then downscale to fit the available space.
            Image render = ScreenImage.createImage(component);
            int width = render.getWidth(null);
            int height = render.getHeight(null);
            int maxWidth = QuickAccessPlugin.CELL_WIDTH * size.width;
            int maxHeight = QuickAccessPlugin.CELL_HEIGHT * size.height;
            if (width > maxWidth || height > maxHeight) {
               // Too big; we must downscale.
               double scale = Math.min((double) width / maxWidth,
                     (double) height / maxHeight);
               render = render.getScaledInstance((int) (width * scale),
                     (int) (height * scale), Image.SCALE_DEFAULT);
            }
            icon_ = new ImageIcon(render);
         }
         setIcon(icon_);
         if (plugin != null) {
            setToolTipText(plugin.getHelpText());
         }
         // When the user clicks and drags the icon, we need to move it
         // around.
         MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               draggedIcon_ = DraggableIcon.this;
               iconOffsetX_ = e.getX();
               iconOffsetY_ = e.getY();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
               // We need to get the mouse coordinates with respect to the
               // upper-left corner of the window's content pane.
               Window parent = SwingUtilities.getWindowAncestor(
                     DraggableIcon.this);
               mouseX_ = e.getXOnScreen() - parent.getLocation().x -
                  parent.getInsets().left;
               mouseY_ = e.getYOnScreen() - parent.getLocation().y -
                  parent.getInsets().top;
               QuickAccessFrame.this.repaint();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
               draggedIcon_ = null;
               // Stop dragging; create/move/destroy controls as appropriate.
               if (parent_ != null) {
                  // Have an existing control; move or destroy it.
                  Point p = getCell();
                  if (p == null) {
                     // Dragged out of the grid; remove it.
                     removeControl(parent_, true);
                  }
                  else if (canFitRect(
                           new Rectangle(p.x, p.y, getSize().width,
                              getSize().height), parent_)) {
                     // Move it to a new location.
                     moveControl(parent_, p);
                  }
               }
               else if (plugin_ != null) {
                  // Add a new control to the grid.
                  dropPlugin(plugin_);
               }
               else {
                  // This should be impossible.
                  studio_.logs().logError("DraggableIcon with both null plugin and null ControlCell");
               }
               QuickAccessFrame.this.repaint();
            }
         };
         addMouseListener(adapter);
         addMouseMotionListener(adapter);
      }

      public Dimension getSize() {
         if (parent_ == null) {
            if (plugin_ instanceof WidgetPlugin) {
               return ((WidgetPlugin) plugin_).getSize();
            }
            return new Dimension(1, 1);
         }
         return new Dimension(parent_.rect_.width, parent_.rect_.height);
      }
   }
}
