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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import javax.swing.BorderFactory;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.miginfocom.swing.MigLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;

/**
 * This class shows the Quick Access Window for frequently-used controls.
 */
public final class QuickAccessFrame extends JFrame {
   private static final String OPEN_NEVER = "Never";
   private static final String OPEN_ALWAYS = "Always";
   private static final String OPEN_REMEMBER = "Remember";
   private static final String DEFAULT_TITLE = "Quick Access Panel";

   private final Studio studio_;
   // Holds everything.
   private final JPanel contentsPanel_;
   // Holds the active controls.
   private final GridPanel controlsPanel_;
   // Holds icons representing the current active controls.
   private final GridPanel configuringControlsPanel_;
   // Holds the "source" icons that can be added to the active controls, as
   // well as the rows/columns configuration fields.
   private final ConfigurationPanel configurePanel_;
   // Switches between normal and configure modes.
   private JToggleButton configureButton_;
   // List of visual dividers in the grid.
   private final HashSet<Divider> dividers_;

   // All of the controls we have in the grid.
   private final HashSet<ControlCell> controls_;
   // Dimensions of the grid.
   private int numCols_ = 3;
   private int numRows_ = 3;

   // X/Y position of the mouse when dragging icons.
   private int mouseX_ = -1;
   private int mouseY_ = -1;
   // The icon being dragged.
   private DraggableIcon draggedIcon_ = null;
   // The X/Y offsets from the top-left corner at which the icon was "grabbed"
   private int iconOffsetX_ = -1;
   private int iconOffsetY_ = -1;

   @SuppressWarnings("LeakingThisInConstructor")
   public QuickAccessFrame(Studio studio, JSONObject config) {
      setAlwaysOnTop(true);

      studio_ = studio;
      controls_ = new HashSet<ControlCell>();
      dividers_ = new HashSet<Divider>();

      // Hide ourselves when the close button is clicked, instead of letting
      // us be destroyed. Also leave configuration mode.
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            setVisible(false);
            configureButton_.setSelected(false);
            toggleConfigurePanel(false);
         }
      });

      // Layout overview: the active controls, then a configure button, then
      // the configuration panel (normally hidden).
      contentsPanel_ = new JPanel(new MigLayout("flowy, fill, insets 0, gap 2"));

      // We have two panels that are mutually-exclusive: controlsPanel_ and
      // configuringControlsPanel_. The latter is only visible in configuration
      // mode, and has special painting logic.
      controlsPanel_ = new GridPanel(false);
      configuringControlsPanel_ = new GridPanel(true);

      contentsPanel_.add(controlsPanel_);

      configureButton_ = new JToggleButton(
            IconLoader.getIcon("/org/micromanager/icons/gear.png"));
      configureButton_.setToolTipText("Open the configuration UI for this panel");
      configureButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            toggleConfigurePanel(configureButton_.isSelected());
         }
      });
      contentsPanel_.add(configureButton_);

      // We'll add this later, when the configure button is toggled.
      configurePanel_ = new ConfigurationPanel(
            config.optString("title", DEFAULT_TITLE));

      add(contentsPanel_);

      loadConfig(config);

      pack();

      studio_.events().registerForEvents(this);
   }

   /**
    * Reload our configuration from the provided config data.
    */
   private void loadConfig(JSONObject config) {
      if (config.length() == 0) {
         // Empty config; create a default (i.e. empty) window.
         setTitle(((DefaultQuickAccessManager) studio_.quickAccess()).getUniqueTitle(this,DEFAULT_TITLE));
         super.setBounds(100, 100, 100, 100);
         super.pack();
         setVisible(true);
         return;
      }
      try {
         numCols_ = config.getInt("numCols");
         numRows_ = config.getInt("numRows");
         setTitle(((DefaultQuickAccessManager) studio_.quickAccess()).getUniqueTitle(this, 
                  config.optString("title", DEFAULT_TITLE)));
         configurePanel_.updateSpinners(numCols_, numRows_);
         JSONArray cells = config.getJSONArray("cells");
         for (int i = 0; i < cells.length(); ++i) {
            addControl(ControlCell.fromJSON(cells.getJSONObject(i),
                     studio_, this));
         }
         JSONArray dividers = config.getJSONArray("dividers");
         for (int i = 0; i < dividers.length(); ++i) {
            dividers_.add(dividerFromJSON(dividers.getJSONObject(i)));
         }
         int xPos = config.getInt("xPos");
         int yPos = config.getInt("yPos");
         int width = config.getInt("windowWidth");
         int height = config.getInt("windowHeight");
         setBounds(xPos, yPos, width, height);

         boolean wasOpen = config.getBoolean("wasOpen");
         String openMode = config.getString("openMode");
         configurePanel_.setOpenMode(openMode);
         if (openMode.equals(OPEN_ALWAYS) ||
               (wasOpen && openMode.equals(OPEN_REMEMBER))) {
            setVisible(true);
         }
      }
      catch (JSONException e) {
         studio_.logs().logError(e, "Unable to reconstruct Quick Access Window from config.");
      }
   }

   /**
    * Create a JSONObject for storing our settings to the profile.
    * @return JSONObject containing the settings for this Quick Access Panel
    */
   public JSONObject getConfig() {
      try {
         JSONObject settings = new JSONObject();
         settings.put("numCols", numCols_);
         settings.put("numRows", numRows_);
         settings.put("title", configurePanel_.getUserTitle());

         JSONArray cells = new JSONArray();
         for (ControlCell cell : controls_) {
            cells.put(cell.toJSON());
         }
         settings.put("cells", cells);

         JSONArray dividers = new JSONArray();
         for (Divider divider : dividers_) {
            dividers.put(divider.toJSON());
         }
         settings.put("dividers", dividers);

         settings.put("wasOpen", isVisible());
         settings.put("openMode", configurePanel_.getOpenMode());
         settings.put("xPos", getLocation().x);
         settings.put("yPos", getLocation().y);
         settings.put("windowWidth", getSize().width);
         settings.put("windowHeight", getSize().height);
         return settings;
      }
      catch (JSONException e) {
         studio_.logs().logError(e, "Error saving Quick Access Window's settings");
         return null;
      }
   }

   /**
    * We need to paint any dragged icon being moved from place to place.
    */
   @Override
   public void paint(Graphics g) {
      super.paint(g);
      if (draggedIcon_ != null) {
         Point p = new Point(mouseX_, mouseY_);
         SwingUtilities.convertPointFromScreen(p, this);
         g.drawImage(draggedIcon_.getIcon().getImage(),
               p.x - iconOffsetX_, p.y - iconOffsetY_, null);
      }
   }

   /**
    * Start dragging the specified DraggableIcon, which was clicked on
    * according to the provided MouseEvent.
    * @param icon - Icon being dragged
    * @param e - The MouseEvent that started all of this
    */
   public void startDragging(DraggableIcon icon, MouseEvent e) {
      draggedIcon_ = icon;
      iconOffsetX_ = e.getX();
      iconOffsetY_ = e.getY();
   }

   /**
    * Stop dragging something.
    */
   public void stopDragging() {
      draggedIcon_ = null;
   }

   /**
    * Drop the specified plugin into the controlsPanel_ at the current mouse
    * location, creating a new control (and configuring it if necessary).
    * @param plugin The source plugin to create the new control.
    */
   public void dropPlugin(QuickAccessPlugin plugin) {
      Point p = getCell();
      if (p == null) {
         // Can't drop anything here.
         return;
      }
      // Default to 1x1 cell.
      Rectangle rect = new Rectangle(p.x, p.y, 1, 1);
      if (plugin != null) {
         PropertyMap config = null;
         try {
            if (plugin instanceof WidgetPlugin) {
               config = ((WidgetPlugin) plugin).configureControl(this);
               if (config == null) {
                  // Plugin cancelled configuration.
                  return;
               }
            }
            ControlCell cell = new ControlCell(studio_, this, plugin,
                  config, rect);
            addControl(cell);
         }
         catch (Exception e) {
            // Probably because configuration failed.
            studio_.logs().showError(e, "Unable to add control");
         }
      }
      QuickAccessFrame.this.repaint();
   }

   /**
    * Add a control to the grid of controls at the specified location and
    * dimensionality.
    * This requires removing and then re-adding all components because the
    * size of the MigLayout entity may have changed.
    */
   public void addControl(ControlCell cell) {
      controlsPanel_.removeAll();
      configuringControlsPanel_.removeAll();
      controls_.add(cell);
      // Restore controls.
      for (ControlCell c : controls_) {
         controlsPanel_.add(c.getWidget(), c.getRect());
         configuringControlsPanel_.add(c.getIcon(), c.getRect());
      }
      validate();
   }

   /**
    * Return true if there is room for a control at the desired
    * rect. Optionally include a control being moved, so we don't have to
    * worry about self-intersections.
    */
   public boolean canFitRect(Rectangle rect, ControlCell source) {
      if (rect.x < 0 || rect.x + rect.width > numCols_ ||
            rect.y < 0 || rect.y + rect.height > numRows_) {
         // Rect is out of bounds.
         return false;
      }
      for (ControlCell alt : controls_) {
         if (alt == source) {
            // Don't need to compare against yourself for hit detection.
            continue;
         }
         if (alt.getRect().intersects(rect)) {
            return false;
         }
      }
      return true;
   }

   /**
    * Move a control to a new location in the grid.
    */
   public void moveControl(ControlCell control, Point p) {
      removeControl(control, false);
      control.moveTo(p);
      addControl(control);
      validate();
      repaint();
   }

   /**
    * Clear a control from the grid.
    */
   public void removeControl(ControlCell control, boolean shouldRedraw) {
      controls_.remove(control);
      controlsPanel_.remove(control.getWidget());
      configuringControlsPanel_.remove(control.getIcon());
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
      GridPanel panel = isShown ? configuringControlsPanel_ : controlsPanel_;
      contentsPanel_.add(panel);
      contentsPanel_.add(configureButton_);
      if (isShown) {
         // Update the configure panels titel, since it may have changed since
         // the panel was created.
         configurePanel_.setUserTitle(this.getTitle());
         String paramString = "growx";
         // Depending on the grid shape, we either put the configure panel
         // below or to the right of the grid. We prefer having the panel
         // below the window.
         if (configurePanel_.getNumRows() / 1.5 > configurePanel_.getNumCols()) {
            // Tall grid instead of wide grid.
            paramString += ", newline";
         }
         contentsPanel_.add(configurePanel_, paramString);
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
         Rectangle r = control.getRect();
         boolean isVisible = (r.x + r.width <= numCols_ &&
               r.y + r.height <= numRows_);
         control.getWidget().setVisible(isVisible);
         control.getIcon().setVisible(isVisible);
      }
      // HACK: Retoggle controls so we can put the configure panel at the
      // correct orientation when the window gets too tall.
      toggleConfigurePanel(configureButton_.isSelected());
      contentsPanel_.invalidate();
      pack();
      repaint();
   }

   /**
    * Map the current mouse position (relative to the window) into a cell
    * location (in the controlsPanel_). Returns null if the location is not
    * valid.
    */
   public Point getCell() {
      Point p = new Point(mouseX_, mouseY_);
      GridPanel panel = configuringControlsPanel_;
      if (controlsPanel_.getParent() != null) {
         // Controls panel is active, not configuring panel.
         panel = controlsPanel_;
      }
      SwingUtilities.convertPointFromScreen(p, panel);
      int cellX = p.x / QuickAccessPlugin.CELL_WIDTH;
      int cellY = p.y / QuickAccessPlugin.CELL_HEIGHT;
      if (cellX >= numCols_ || cellY >= numRows_ || cellX < 0 || cellY < 0) {
         // Out of bounds.
         return null;
      }
      return new Point(cellX, cellY);
   }

   /**
    * Update the current mouse coordinates based on the provided MouseEvent.
    */
   public void updateMouse(MouseEvent event) {
      mouseX_ = event.getXOnScreen();
      mouseY_ = event.getYOnScreen();
   }

   /**
    * Update the title of this window according to the provided string.
    */
   private String updateTitle(String title) {
      String newTitle = ((DefaultQuickAccessManager) studio_.quickAccess()).getUniqueTitle(this, title);
      setTitle(newTitle);
      // This is mostly to let the Tools menu know it needs to regenerate its
      // submenu for the quick access panels.
      studio_.events().post(new QuickAccessPanelEvent());
      return newTitle;
   }

   /**
    * These panels have a fixed size based on the number of rows/columns.
    * Depending on the boolean isConfigurePanel_, they may also draw a
    * semitransparent grid over their contents (which in turn are expected to
    * be DraggableIcons instead of normal controls).
    */
   private class GridPanel extends JPanel {
      private final boolean isConfigurePanel_;
      // Current divider under the mouse, for drawing.
      private Divider curDivider_;
      
      public GridPanel(boolean isConfigurePanel) {
         super(new SparseGridLayout(QuickAccessPlugin.CELL_WIDTH,
                  QuickAccessPlugin.CELL_HEIGHT));
         isConfigurePanel_ = isConfigurePanel;
         if (isConfigurePanel_) {
            // We need to listen for mouse events that aren't claimed by our
            // icons, so that the user can toggle dividers on and off.
            addMouseListener(new MouseAdapter() {
               @Override
               public void mousePressed(MouseEvent e) {
                  // Did the user click on a divider? If so, toggle it on/off.
                  Divider divider = getDivider(e);
                  if (divider != null) {
                     if (dividers_.contains(divider)) {
                        dividers_.remove(divider);
                     }
                     else {
                        dividers_.add(divider);
                     }
                     GridPanel.this.repaint();
                  }
               }
            });
            addMouseMotionListener(new MouseAdapter() {
               @Override
               public void mouseMoved(MouseEvent e) {
                  curDivider_ = getDivider(e);
                  GridPanel.this.repaint();
               }
               @Override
               public void mouseExited(MouseEvent e) {
                  curDivider_ = null;
                  GridPanel.this.repaint();
               }
            });
         }
      }

      /**
       * Return the Divider that is closest to the mouse event, or null if
       * no divider is close enough.
       */
      private Divider getDivider(MouseEvent e) {
         // Find distance to closest column/row, within 10px.
         int columnDist = (e.getX() + 10) % QuickAccessPlugin.CELL_WIDTH;
         int rowDist = (e.getY() + 10) % QuickAccessPlugin.CELL_HEIGHT;
         if (columnDist > 20 && rowDist > 20) {
            // Too far from any grid line.
            return null;
         }
         int column = (e.getX() + 10) / QuickAccessPlugin.CELL_WIDTH;
         int row = (e.getY() + 10) / QuickAccessPlugin.CELL_HEIGHT;
         if (columnDist < rowDist) {
            // Horizontal divider.
            return new Divider(new Point(column, row),
                  new Point(column, row + 1));
         }
         else {
            // Vertical divider.
            return new Divider(new Point(column, row),
                  new Point(column + 1, row));
         }
      }

      @Override
      public void paint(Graphics g) {
         super.paint(g);
         int cellWidth = QuickAccessPlugin.CELL_WIDTH;
         int cellHeight = QuickAccessPlugin.CELL_HEIGHT;

         // Draw any dividers.
         ((Graphics2D) g).setStroke(new BasicStroke(4));
         g.setColor(Color.BLACK);
         for (Divider divider : dividers_) {
            divider.paint(g);
         }
         ((Graphics2D) g).setStroke(new BasicStroke(1));

         if (!isConfigurePanel_ || !configureButton_.isSelected()) {
            // We aren't in configure mode; just draw normally.
            return;
         }
         // Draw a grid showing the cells.
         int width = getSize().width;
         int height = getSize().height;
         g.setColor(new Color(200, 255, 200, 128));
         g.fillRect(0, 0, numCols_ * cellWidth,
               numRows_ * cellHeight);
         if (draggedIcon_ != null) {
            // Draw the cells the icon would go into in a highlighted color,
            // only if it would fit.
            Point p = getCell();
            if (p != null) {
               Rectangle rect = new Rectangle(p.x, p.y,
                     draggedIcon_.getSize().width,
                     draggedIcon_.getSize().height);
               if (canFitRect(rect, draggedIcon_.getControlCell())) {
                  g.setColor(new Color(255, 255, 150, 128));
                  g.fillRect(p.x * cellWidth, p.y * cellHeight,
                        cellWidth * rect.width, cellHeight * rect.height);
               }
            }
         }
         // Draw the grid lines.
         g.setColor(Color.BLACK);
         for (int i = 0; i < numCols_ + 1; ++i) {
            g.drawLine(i * cellWidth, 0,
                  i * cellWidth,
                  cellHeight * numRows_);
         }
         for (int i = 0; i < numRows_ + 1; ++i) {
            g.drawLine(0, i * cellHeight, numCols_ * cellWidth, i * cellHeight);
         }
         // Draw the current divider under the mouse, if any. Red if we'd
         // remove it, blue if we'd add it.
         if (curDivider_ != null) {
            g.setColor(
                  dividers_.contains(curDivider_) ? Color.RED : Color.BLUE);
            ((Graphics2D) g).setStroke(new BasicStroke(4));
            curDivider_.paint(g);
            ((Graphics2D) g).setStroke(new BasicStroke(1));
         }
      }

      @Override
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
      // Dimensionality controls.
      private final JSpinner colsControl_;
      private final JSpinner rowsControl_;
      // Title input text field
      private JTextField titleField_;
      // Dropdown menu for selecting the open mode.
      private final JComboBox openSelect_;

      /**
       * We take the title as a parameter because, at the time that this
       * constructor is invoked, we haven't loaded the correct title for the
       * window yet, so getTitle() returns the wrong value. And anyway,
       * getTitle() includes any modifications from the getUniqueTitle call.
       */
      public ConfigurationPanel(String title) {
         super(new MigLayout("flowy, insets 0, gap 0"));
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

         subPanel.add(new JLabel("Panel title:"), "split 2, span");
         titleField_ = new JTextField(title, 20);
         titleField_.addFocusListener(new FocusListener(){
            @Override
            public void focusGained(FocusEvent fe) {
               // Nothing to be done
            }

            @Override
            public void focusLost(FocusEvent fe) {
               String newTitle = updateTitle(titleField_.getText());
               titleField_.setText(newTitle);
            }
         });
         subPanel.add(titleField_, "wrap");

         subPanel.add(new JLabel("Open on launch: "), "split 2, span");
         openSelect_ = new JComboBox(new String[] {OPEN_NEVER,
            OPEN_ALWAYS, OPEN_REMEMBER});
         // The OPEN_REMEMBER string is kinda large and blows out the size
         // of the combobox, so use the OPEN_ALWAYS string to set size.
         openSelect_.setPrototypeDisplayValue(OPEN_ALWAYS);
         openSelect_.setSelectedItem(OPEN_NEVER);
         subPanel.add(openSelect_, "wrap");

         subPanel.add(new JLabel("<html>Drag controls into the grid above to add them to the panel.<br>Click on grid lines to add or remove dividers. Right-click<br>on a control in the grid to customize its icon (when possible).</html>"),
               "span, wrap, gaptop 10");

         add(subPanel, "span, wrap");

         JPanel buttonsPanel = new JPanel(
               new MigLayout("flowx, wrap " + numCols_));

         // Populate the panel with icons corresponding to controls we can
         // provide. List them in alphabetical order.
         HashMap<String, QuickAccessPlugin> plugins = studio_.plugins().getQuickAccessPlugins();
         ArrayList<String> keys = new ArrayList<String>(plugins.keySet());
         Collections.sort(keys);
         for (String key : keys) {
            JPanel iconPanel = new JPanel(new MigLayout("flowy, insets 0, fill"));
            iconPanel.setBorder(BorderFactory.createLoweredBevelBorder());
            QuickAccessPlugin plugin = plugins.get(key);
            Dimension size = new Dimension(1, 1);
            if (plugin instanceof WidgetPlugin) {
               size = ((WidgetPlugin) plugin).getSize();
            }
            DraggableIcon dragger = new DraggableIcon(studio_,
                  QuickAccessFrame.this,
                  QuickAccessFactory.makeGUI(plugin), plugin.getIcon(), plugin,
                  true, null, size);
            iconPanel.add(dragger, "alignx center");
            JLabel name = new JLabel(plugins.get(key).getName());
            name.setFont(GUIUtils.buttonFont);
            iconPanel.add(name, "alignx center");
            buttonsPanel.add(iconPanel,
                  String.format("alignx center, w %d!, h %d!",
                  QuickAccessPlugin.CELL_WIDTH,
                  QuickAccessPlugin.CELL_HEIGHT));
         }
         add(buttonsPanel, "span, wrap");
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

      /**
       * Return the title field's text.
       */
      public String getUserTitle() {
         return titleField_.getText();
      }

      public void setUserTitle(String newTitle) {
         titleField_.setText(newTitle);
      }

      /**
       * Receive a new grid size. Used to initialize the GUI when the frame
       * is created.
       */
      public void updateSpinners(int numCols, int numRows) {
         colsControl_.setValue(numCols);
         rowsControl_.setValue(numRows);
      }

      public int getNumCols() {
         return (Integer) ((SpinnerNumberModel) colsControl_.getModel()).getNumber();
      }

      public int getNumRows() {
         return (Integer) ((SpinnerNumberModel) rowsControl_.getModel()).getNumber();
      }

      public void setOpenMode(String mode) {
         openSelect_.setSelectedItem(mode);
      }

      public String getOpenMode() {
         return (String) openSelect_.getSelectedItem();
      }
   }

   /**
    * Simple class representing a pair of Points in the grid that have been
    * highlighted to create a visual divider.
    */
   private class Divider {
      public Point p1_;
      public Point p2_;

      public Divider(Point p1, Point p2) {
         p1_ = p1;
         p2_ = p2;
      }

      public void paint(Graphics g) {
         // HACK: back off from the edges slightly, so the line doesn't
         // get clipped to thinner than it should be.
         int cellWidth = QuickAccessPlugin.CELL_WIDTH;
         int cellHeight = QuickAccessPlugin.CELL_HEIGHT;
         int x1 = clampX(p1_.x * cellWidth);
         int x2 = clampX(p2_.x * cellWidth);
         int y1 = clampY(p1_.y * cellHeight);
         int y2 = clampY(p2_.y * cellHeight);
         g.drawLine(x1, y1, x2, y2);
      }

      /**
       * Ensure the given X value is in-bounds for drawing.
       */
      private int clampX(int x) {
         return Math.max(2,
               Math.min(numCols_ * QuickAccessPlugin.CELL_WIDTH - 2, x));
      }

      /**
       * As above, but for Y.
       */
      private int clampY(int y) {
         return Math.max(2,
               Math.min(numRows_ * QuickAccessPlugin.CELL_HEIGHT - 2, y));
      }

      public JSONObject toJSON() throws JSONException {
         JSONObject result = new JSONObject();
         result.put("x1", p1_.x);
         result.put("y1", p1_.y);
         result.put("x2", p2_.x);
         result.put("y2", p2_.y);
         return result;
      }

      /**
       * We want any two identical Dividers to hash the same in the dividers_
       * structure, hence we override hashCode and equals.
       */
      @Override
      public int hashCode() {
         // Allows for 100x100 grids without any hash collisions.
         return p1_.x + p2_.x * 100 + p1_.y * 10000 + p2_.y * 1000000;
      }

      @Override
      public boolean equals(Object obj) {
         if (!(obj instanceof Divider)) {
            return false;
         }
         Divider alt = (Divider) obj;
         return (p1_.x == alt.p1_.x && p1_.y == alt.p1_.y &&
               p2_.x == alt.p2_.x && p2_.y == alt.p2_.y);
      }

      @Override
      public String toString() {
         return String.format("<Divider from (%d, %d) to (%d, %d)>",
               p1_.x, p1_.y, p2_.x, p2_.y);
      }
   }

   private Divider dividerFromJSON(JSONObject json) throws JSONException {
      int x1 = json.getInt("x1");
      int y1 = json.getInt("y1");
      int x2 = json.getInt("x2");
      int y2 = json.getInt("y2");
      return new Divider(new Point(x1, y1), new Point(x2, y2));
   }

   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      if (!event.getIsCancelled()) {
         dispose();
      }
   }
}
