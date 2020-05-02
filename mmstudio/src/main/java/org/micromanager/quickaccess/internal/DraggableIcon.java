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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ScreenImage;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;

/**
 * This class represents an icon that can be dragged around the window,
 * for adding or removing controls.
 */
public final class DraggableIcon extends JLabel {
   /**
    * Paths to images inside the JAR that we can provide as custom icons.
    * Each of these will have "/org/micromanager/icons/" prepended and
    * ".png" postpended when they are actually loaded.
    */
   private static final String[] JAR_ICONS = new String[] {
      "application_view_list", "arrow_down", "arrow_inout", "arrow_out",
         "arrow_refresh", "arrow_right", "arrow_up", "asterisk_orange",
         "camera", "camera_go", "camera_plus_arrow", "cancel", "chart_curve",
         "cog", "color_filter", "contrast", "control_pause",
         "control_play_blue", "control_stop_blue", "cross", "delete", "disk",
         "empty", "eye-out", "eye", "file", "film", "film_delete", "film_file",
         "film_go", "find", "flag_green", "folder", "fullscreen", "gear",
         "linkflat", "linkflat_active", "lock_locked", "lock_open",
         "lock_super", "minus", "move", "move_hand", "move_hand_on",
         "page", "page_find", "page_save", "pause", "play",
         "plus", "plus_green", "script", "shutter_closed",
         "shutter_closed_auto", "shutter_open", "shutter_open_auto",
         "snapAppend", "windowed", "wrench_orange", "zoom", "zoom_in",
         "zoom_out"
   };

   private Studio studio_;
   private QuickAccessFrame frame_;
   private JComponent component_;
   private QuickAccessPlugin plugin_;
   private ControlCell parentCell_;
   private boolean isReified_;
   private ImageIcon icon_;
   private Dimension widgetSize_;

   /**
    * icon and plugin are both optional. If isReified is true, then we
    * assume this references an instantiated control which can be removed
    * from the controlsPanel_ when dragged out of the grid. Otherwise, we
    * want to create a new control when the icon is dragged into the grid.
    * If icon is null, then we generate an icon from the JComponent.
    * @param size Number of cells (width x height) taken up by the icon.
    */
   public DraggableIcon(Studio studio, QuickAccessFrame frame,
         final JComponent component, ImageIcon icon,
         final QuickAccessPlugin plugin, boolean isReified,
         final ControlCell parentCell, Dimension size) {
      studio_ = studio;
      frame_ = frame;
      component_ = component;
      plugin_ = plugin;
      isReified_ = isReified;
      parentCell_ = parentCell;
      widgetSize_ = size;
      icon_ = icon;
      if (icon_ == null) {
         try {
            // Render the image, then downscale to fit the available space.
            Image image = ScreenImage.createImage(component);
            image = resizeToFit(image);
            icon_ = new ImageIcon(image);
         }
         catch (Exception e) {
            studio_.logs().logError(e, "Unable to create icon for Quick Access Plugin " + plugin.getName());
         }
      }
      setIcon(icon_);
      setToolTipText(plugin.getHelpText());

      // When the user clicks and drags the icon, we need to move it
      // around.
      MouseAdapter adapter = new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            frame_.updateMouse(e);
            if (SwingUtilities.isLeftMouseButton(e) &&
                  !e.isControlDown()) {
               // Start dragging.
               frame_.startDragging(DraggableIcon.this, e);
            }
            // Right-click, or control click (for OSX support), for a
            // customizable-icon plugin.
            else if ((SwingUtilities.isRightMouseButton(e) ||
                     e.isControlDown()) &&
                  isReified_ && parentCell_ != null &&
                  plugin_ instanceof WidgetPlugin &&
                  ((WidgetPlugin) plugin_).getCanCustomizeIcon()) {
               // Start customizing the icon.
               JPopupMenu menu = new JPopupMenu();
               menu.add(new IconGrid(menu));
               menu.show(DraggableIcon.this, e.getX(), e.getY());
            }
         }
         @Override
         public void mouseDragged(MouseEvent e) {
            frame_.updateMouse(e);
            frame_.repaint();
         }
         @Override
         public void mouseReleased(MouseEvent e) {
            frame_.stopDragging();
            // Stop dragging; create/move/destroy controls as appropriate.
            if (parentCell_ != null) {
               // Have an existing control; move or destroy it.
               Point p = frame_.getCell();
               if (p == null) {
                  // Dragged out of the grid; remove it.
                  frame_.removeControl(parentCell_, true);
               }
               else if (frame_.canFitRect(
                        new Rectangle(p.x, p.y, getSize().width,
                           getSize().height), parentCell_)) {
                  // Move it to a new location.
                  frame_.moveControl(parentCell_, p);
               }
            }
            else if (plugin_ != null) {
               // Add a new control to the grid.
               frame_.dropPlugin(plugin_);
            }
            else {
               // This should be impossible.
               studio_.logs().logError("DraggableIcon with both null plugin and null ControlCell");
            }
            frame_.repaint();
         }
      };
      addMouseListener(adapter);
      addMouseMotionListener(adapter);
   }

   /**
    * Create a resized copy of the provided image that will fit into a cell.
    */
   private Image resizeToFit(Image image) {
      int width = image.getWidth(null);
      int height = image.getHeight(null);
      int maxWidth = QuickAccessPlugin.CELL_WIDTH * widgetSize_.width;
      int maxHeight = QuickAccessPlugin.CELL_HEIGHT * widgetSize_.height;
      if (width <= maxWidth && height <= maxHeight) {
         // Image is fine.
         return image;
      }
      // Too big; we must downscale.
      double scale = Math.min((double) width / maxWidth,
            (double) height / maxHeight);
      return image.getScaledInstance((int) (width * scale),
            (int) (height * scale), Image.SCALE_DEFAULT);
   }

   public Dimension getSize() {
      if (parentCell_ == null) {
         if (plugin_ instanceof WidgetPlugin) {
            return ((WidgetPlugin) plugin_).getSize();
         }
         return new Dimension(1, 1);
      }
      Rectangle rect = parentCell_.getRect();
      return new Dimension(rect.width, rect.height);
   }

   public ControlCell getControlCell() {
      return parentCell_;
   }

   public ImageIcon getIcon() {
      return icon_;
   }

   /**
    * Grid of icons the user can pick from, that we can embed into a popup
    * menu. This is used for customizing icons.
    */
   private class IconGrid extends JPanel implements MenuElement {
      public IconGrid(JPopupMenu menu) {
         super(new MigLayout("flowx, insets 0, gap 0"));

         addItem(menu, "Image File...", "span, split 2", null, 0,
               new Runnable() {
            @Override
            public void run() {
               loadIconFromFile();
            }
         });
         addItem(menu, "Color Swatch...", "wrap",
               DefaultQuickAccessManager.createSwatch(Color.RED, 16), 1,
               new Runnable() {
                  @Override
                  public void run() {
                     makeColorSwatch();
                  }
               });

         for (int i = 0; i < JAR_ICONS.length; ++i) {
            final String iconName = JAR_ICONS[i];
            String iconPath =
                  "/org/micromanager/icons/" + iconName + ".png";
            addItem(menu, null, "", IconLoader.getIcon(iconPath), i,
                  new Runnable() {
                     @Override
                     public void run() {
                        loadJarIcon(iconName);
                     }
                  });
         }
      }

      /**
       * Add an entry to our grid of options.
       */
      private void addItem(final JPopupMenu menu, final String name,
            String params, Icon icon, int index, final Runnable action) {
         final JLabel label;
         if (name != null) {
            label = new JLabel(name, icon, SwingConstants.LEADING);
            label.setFont(GUIUtils.buttonFont);
         }
         else {
            label = new JLabel(icon);
         }
         // Mostly for the benefit of the "empty" icon.
         label.setMinimumSize(new Dimension(20, 20));
         label.setBorder(BorderFactory.createLoweredBevelBorder());
         // Respond to being clicked on.
         label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
               menu.setVisible(false);
               action.run();
            }
         });
         params += (index % 8 == 7) ? "wrap" : "";
         add(label, params + ", growx");
      }

      /**
       * Load a custom icon from a file.
       */
      private void loadIconFromFile() {
         JFileChooser chooser = new JFileChooser();
         chooser.setDialogTitle("Please select an image file");
         chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
         if (chooser.showOpenDialog(frame_) != JFileChooser.APPROVE_OPTION) {
            // User cancelled.
            return;
         }
         try {
            File imageFile = chooser.getSelectedFile();
            // Ensure file is loadable.
            Image image = ImageIO.read(imageFile);
            // Update config of the control, remove it, and recreate it.
            PropertyMap config = parentCell_.getConfig();
            JSONObject iconJson = new JSONObject();
            iconJson.put(DefaultQuickAccessManager.ICON_TYPE,
                  DefaultQuickAccessManager.CUSTOM_FILE);
            iconJson.put(DefaultQuickAccessManager.ICON_PATH,
                  imageFile.getAbsolutePath());
            config = config.copyBuilder()
               .putString(WidgetPlugin.CUSTOM_ICON_STRING, iconJson.toString())
               .build();
            frame_.removeControl(parentCell_, false);
            frame_.addControl(new ControlCell(studio_, frame_, plugin_,
                     config, parentCell_.getRect()));
         }
         catch (IOException e) {
            studio_.logs().showError(e, "Unable to open file " + chooser.getSelectedFile());
         }
         catch (JSONException e) {
            studio_.logs().logError(e, "Unable to save icon JSON");
         }
      }

      /**
       * Set up a color swatch icon.
       */
      private void makeColorSwatch() {
         Color color = JColorChooser.showDialog(frame_,
               "Choose a color for the icon.", Color.WHITE);
         PropertyMap config = parentCell_.getConfig();
         try {
            JSONObject iconJson = new JSONObject();
            iconJson.put(DefaultQuickAccessManager.ICON_TYPE,
                  DefaultQuickAccessManager.COLOR_SWATCH);
            iconJson.put(DefaultQuickAccessManager.ICON_COLOR, color.getRGB());
            config = config.copyBuilder()
               .putString(WidgetPlugin.CUSTOM_ICON_STRING, iconJson.toString())
               .build();
            frame_.removeControl(parentCell_, false);
            frame_.addControl(new ControlCell(studio_, frame_, plugin_,
                     config, parentCell_.getRect()));
         }
         catch (JSONException e) {
            studio_.logs().logError(e, "Unable to create color swatch icon");
         }
      }

      /**
       * Use the specified icon from the jar.
       * @param name The filename, minus ".png", and not including the
       * "/org/micromanager/icons/" bit either.
       */
      private void loadJarIcon(String name) {
         PropertyMap config = parentCell_.getConfig();
         try {
            JSONObject iconJson = new JSONObject();
            iconJson.put(DefaultQuickAccessManager.ICON_TYPE,
                  DefaultQuickAccessManager.JAR_ICON);
            iconJson.put(DefaultQuickAccessManager.ICON_PATH, name);
            config = config.copyBuilder()
               .putString(WidgetPlugin.CUSTOM_ICON_STRING, iconJson.toString())
               .build();
            frame_.removeControl(parentCell_, false);
            frame_.addControl(new ControlCell(studio_, frame_, plugin_,
                     config, parentCell_.getRect()));
         }
         catch (JSONException e) {
            studio_.logs().logError(e, "Unable to create color swatch icon");
         }
      }

      @Override
      public Component getComponent() {
         return this;
      }

      @Override
      public MenuElement[] getSubElements() {
         return new MenuElement[0];
      }

      @Override
      public void menuSelectionChanged(boolean isIncluded) {}
      @Override
      public void processKeyEvent(KeyEvent event, MenuElement[] path,
            MenuSelectionManager manager) {}
      @Override
      public void processMouseEvent(MouseEvent event, MenuElement[] path,
            MenuSelectionManager manager) {
         super.processMouseMotionEvent(event);
      }
   }
}
