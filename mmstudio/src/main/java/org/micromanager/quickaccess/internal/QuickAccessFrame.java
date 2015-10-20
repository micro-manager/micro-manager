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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ScreenImage;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.SimpleButtonPlugin;
import org.micromanager.quickaccess.ToggleButtonPlugin;
import org.micromanager.MMPlugin;
import org.micromanager.Studio;

/**
 * This class shows the Quick-Access Window for frequently-used controls.
 */
public class QuickAccessFrame extends MMFrame {
   // Default dimensions of a cell in the controls grid.
   private static final int CELL_HEIGHT = 50;
   private static final int CELL_WIDTH = 150;
   // Profile keys.
   private static final String NUM_COLS = "Number of columns in the Quick-Access Window";
   private static final String NUM_ROWS = "Number of rows in the Quick-Access Window";

   private static QuickAccessFrame staticInstance_;

   /**
    * Show the Quick-Access Window, creating it if it does not already exist.
    */
   public static void showFrame(Studio studio) {
      if (staticInstance_ == null) {
         staticInstance_ = new QuickAccessFrame(studio);
      }
      staticInstance_.setVisible(true);
   }

   private Studio studio_;
   private JPanel contentsPanel_;
   private JPanel controlsPanel_;
   private JPanel configurePanel_;
   private JToggleButton configureButton_;

   /**
    * Maps controls' locations on the grid to those controls.
    */
   private HashMap<Point, Component> gridToControl_;
   private int numCols_;
   private int numRows_;

   private int mouseX_ = -1;
   private int mouseY_ = -1;
   private ImageIcon draggedIcon_ = null;
   private QuickAccessPlugin draggedPlugin_ = null;
   private int iconOffsetX_ = -1;
   private int iconOffsetY_ = -1;

   public QuickAccessFrame(Studio studio) {
      super("Quick-Access Tools");
      studio_ = studio;
      gridToControl_ = new HashMap<Point, Component>();
      numCols_ = studio_.profile().getInt(QuickAccessFrame.class,
            NUM_COLS, 3);
      numRows_ = studio_.profile().getInt(QuickAccessFrame.class,
            NUM_ROWS, 3);

      // Hide ourselves when the close button is clicked.
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            setVisible(false);
         }
      });

      // Stop dragging things when the mouse is released.
      addMouseListener(new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent e) {
            draggedIcon_ = null;
         }
      });

      // Layout overview: the active controls, then a configure button, then
      // the configuration panel (normally hidden).
      contentsPanel_ = new JPanel(new MigLayout("flowy, fill, debug"));

      /**
       * This panel needs special logic a) to highlight the cell the mouse is
       * on when dragging components, and b) to ensure that its default size is
       * sane.
       */
      controlsPanel_ = new JPanel(new MigLayout("flowy, wrap 3")) {
         @Override
         public void paint(Graphics g) {
            super.paint(g);
            if (!configureButton_.isSelected() && false) {
               // We aren't in configure mode; just draw normally.
               return;
            }
            // Draw a grid showing the cells.
            int width = getSize().width;
            int height = getSize().height;
            g.setColor(new Color(200, 255, 200, 128));
            g.fillRect(0, 0, width, height);
            if (draggedIcon_ != null) {
               // Draw the current cell in a highlighted color. Note that
               // mouse coordinates are with respect to the window, not this
               // panel.
               g.setColor(new Color(255, 200, 200, 128));
               int cellX = (mouseX_ - getLocation().x) / CELL_WIDTH;
               int cellY = (mouseY_ - getLocation().y) / CELL_HEIGHT;
               g.fillRect(cellX * CELL_WIDTH, cellY * CELL_HEIGHT,
                     CELL_WIDTH, CELL_HEIGHT);
            }
            // Draw the grid lines.
            g.setColor(Color.BLACK);
            for (int i = 0; i < numCols_ - 1; ++i) {
               g.drawLine((i + 1) * CELL_WIDTH, 0, (i + 1) * CELL_WIDTH,
                     CELL_HEIGHT * numRows_);
            }
            for (int i = 0; i < numRows_ - 1; ++i) {
               g.drawLine(0, (i + 1) * CELL_HEIGHT, numCols_ * CELL_WIDTH,
                     (i + 1) * CELL_HEIGHT);
            }
         }

         @Override
         public Dimension getPreferredSize() {
            return new Dimension(CELL_WIDTH * numCols_,
                  CELL_HEIGHT * numRows_);
         }
      };
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
    * We need to paint any dragged icon being moved from place to place.
    */
   @Override
   public void paint(Graphics g) {
      super.paint(g);
      if (draggedIcon_ != null) {
         g.drawImage(draggedIcon_.getImage(), mouseX_ - iconOffsetX_,
               mouseY_ - iconOffsetY_, null);
      }
   }

   /**
    * Add a control to the grid of controls at the specified location.
    */
   private void addControl(Point loc, Component control) {
      if (gridToControl_.containsKey(loc)) {
         Component oldControl = gridToControl_.get(loc);
         controlsPanel_.remove(oldControl);
      }
      gridToControl_.put(loc, control);
      controlsPanel_.add(control, "cell " + loc.x + " " + loc.y);
      validate();
   }

   /**
    * Toggle visibility of the customization panel.
    */
   private void toggleConfigurePanel(boolean isShown) {
      if (isShown) {
         contentsPanel_.add(configurePanel_, "growx");
      }
      else {
         contentsPanel_.remove(configurePanel_);
      }
      pack();
   }

   /**
    * This class exposes a GUI for adding and removing controls from the
    * main window.
    */
   private class ConfigurationPanel extends JPanel {
      // Maps iconified versions of controls to the plugins that generated
      // them.
      private HashMap<ImageIcon, MMPlugin> iconToPlugin_;

      public ConfigurationPanel() {
         super(new MigLayout());
         iconToPlugin_ = new HashMap<ImageIcon, MMPlugin>();
         setBorder(BorderFactory.createLoweredBevelBorder());

         // Populate the panel with icons corresponding to controls we can
         // provide. List them in alphabetical order.
         HashMap<String, QuickAccessPlugin> plugins = studio_.plugins().getQuickAccessPlugins();
         ArrayList<String> keys = new ArrayList<String>(plugins.keySet());
         Collections.sort(keys);
         for (String key : keys) {
            final QuickAccessPlugin plugin = plugins.get(key);
            final ImageIcon icon = new ImageIcon(
                  ScreenImage.createImage(
                     QuickAccessFactory.makeGUI(plugin)));
            iconToPlugin_.put(icon, plugin);
            JLabel label = new JLabel(icon);
            // When the user clicks and drags the icon, we need to move it
            // around.
            MouseAdapter adapter = new MouseAdapter() {
               @Override
               public void mousePressed(MouseEvent e) {
                  draggedIcon_ = icon;
                  draggedPlugin_ = plugin;
                  iconOffsetX_ = e.getX();
                  iconOffsetY_ = e.getY();
               }
               @Override
               public void mouseDragged(MouseEvent e) {
                  // We need to get the mouse coordinates with respect to
                  // contentsPanel_.
                  Window parent = SwingUtilities.getWindowAncestor(
                        ConfigurationPanel.this);
                  mouseX_ = e.getXOnScreen() - parent.getLocation().x;
                  mouseY_ = e.getYOnScreen() - parent.getLocation().y;
                  QuickAccessFrame.this.repaint();
               }
               @Override
               public void mouseReleased(MouseEvent e) {
                  draggedIcon_ = null;
                  QuickAccessFrame.this.repaint();
               }
            };
            label.addMouseListener(adapter);
            label.addMouseMotionListener(adapter);
            add(label, "split 2, flowy");
            add(new JLabel(plugins.get(key).getName()), "alignx center");
         }
      }
   }
}
