///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
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

package org.micromanager.display.internal.inspector;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.border.TitledBorder;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Image;

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.OverlayPanel;
import org.micromanager.display.internal.DefaultDisplayManager;
import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.display.internal.events.CanvasDrawEvent;
import org.micromanager.display.internal.events.LayoutChangedEvent;
import org.micromanager.display.internal.events.NewOverlayEvent;
import org.micromanager.display.internal.MMVirtualStack;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class contains panels used to draw overlays on the image canvas.
 */
class OverlaysPanel extends InspectorPanel {
   private static final String NO_OVERLAY = "   ";
   private ArrayList<OverlayPanel> overlays_;
   private DisplayWindow display_;
   private MMVirtualStack stack_;
   private ImagePlus plus_;
   private Inspector inspector_;
   
   public OverlaysPanel() {
      overlays_ = new ArrayList<OverlayPanel>();
      setLayout(new MigLayout("flowy"));
      // Provide a button that, when clicked, shows a popup menu of overlays
      // that can be added.
      // Icon is public domain, taken from
      // https://openclipart.org/detail/16950/add
      JButton adder = new JButton("Add overlay...",
            new ImageIcon(getClass().getResource("/org/micromanager/icons/plus_green.png")));
      adder.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            showPopup(e);
         }
      });
      add(adder);
   }

   private void showPopup(MouseEvent event) {
      String[] titles = DefaultDisplayManager.getInstance().getOverlayTitles();
      final JPopupMenu menu = new JPopupMenu();
      for (final String title : titles) {
         JMenuItem item = new JMenuItem(title);
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               addNewPanel(title);
            }
         });
         menu.add(item);
      }
      menu.show(event.getComponent(), 0, event.getComponent().getHeight());
   }

   /**
    * Create a new overlay panel of the appropriate type, enclosed in a
    * titled border that includes a close button.
    */
   private void addNewPanel(String title) {
      final OverlayPanel panel = DefaultDisplayManager.getInstance().createOverlayPanel(title);
      panel.setDisplay(display_);
      overlays_.add(panel);

      final JPanel container = new JPanel(new MigLayout("flowy, insets 0"));
      // TODO: add move up/down buttons to change order in which overlays are
      // drawn (and iconify buttons while we're at it)
      JButton closeButton = new JButton("Remove", new ImageIcon(
               getClass().getResource("/org/micromanager/icons/cross.png")));
      closeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            remove(container);
            overlays_.remove(panel);
            if (inspector_ != null) {
               inspector_.relayout();
            }
            display_.requestRedraw();
         }
      });
      container.add(closeButton, "gap 0");
      container.add(panel);
      container.setBorder(new TitledBorder(title));
      add(container);
      if (inspector_ != null) {
         inspector_.relayout();
      }
      panel.redraw();
   }

   @Subscribe
   public void onCanvasDraw(CanvasDrawEvent event) {
      List<Image> images = display_.getDisplayedImages();
      if (images.size() == 0) {
         // Nothing to draw on yet.
         return;
      }
      Image image = images.get(0);
      for (OverlayPanel overlay : overlays_) {
         overlay.drawOverlay(event.getGraphics(), display_, image,
               event.getCanvas());
      }
   }

   @Override
   public synchronized void setDisplay(DisplayWindow display) {
      if (display_ != null) {
         try {
            display_.unregisterForEvents(this);
         }
         catch (IllegalArgumentException e) {
            // Must've already unregistered; ignore it.
         }
      }
      display_ = display;
      for (OverlayPanel overlay : overlays_) {
         overlay.setDisplay(display);
      }
      if (display_ != null) {
         stack_ = ((DefaultDisplayWindow) display).getStack();
         plus_ = display.getImagePlus();
         display_.registerForEvents(this);
      }
   }

   @Override
   public void setInspector(Inspector inspector) {
      inspector_ = inspector;
   }

   @Override
   public synchronized void cleanup() {
      if (display_ != null) {
         try {
            display_.unregisterForEvents(this);
         }
         catch (IllegalArgumentException e) {
            // Must've already unregistered; ignore it.
         }
      }
   }
}
