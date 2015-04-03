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
import java.util.ArrayList;

import javax.swing.border.TitledBorder;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;

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

import org.micromanager.events.internal.DefaultEventManager;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class contains panels used to draw overlays on the image canvas.
 */
class OverlaysPanel extends InspectorPanel {
   private ArrayList<OverlayPanel> overlays_;
   private JComboBox chooser_;
   private DisplayWindow display_;
   private MMVirtualStack stack_;
   private ImagePlus plus_;
   private Inspector inspector_;
   
   public OverlaysPanel() {
      overlays_ = new ArrayList<OverlayPanel>();
      DefaultEventManager.getInstance().registerForEvents(this);
      setLayout(new MigLayout("flowy"));
      // Add a chooser that creates a new panel of the appropriate type when
      // selected.
      String[] titles = DefaultDisplayManager.getInstance().getOverlayTitles();
      chooser_ = new JComboBox(titles);
      chooser_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            addNewPanel((String) chooser_.getSelectedItem());
         }
      });
      add(chooser_);
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
      JButton closeButton = new JButton("Close");
      closeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            remove(container);
            overlays_.remove(panel);
            if (inspector_ != null) {
               inspector_.relayout();
            }
         }
      });
      container.add(closeButton, "gap 0");
      container.add(panel);
      container.setBorder(new TitledBorder(title));
      add(container);
      if (inspector_ != null) {
         inspector_.relayout();
      }
   }

   @Subscribe
   public void onCanvasDraw(CanvasDrawEvent event) {
      Image image = display_.getDatastore().getImage(stack_.getCurrentImageCoords());
      for (OverlayPanel overlay : overlays_) {
         overlay.drawOverlay(event.getGraphics(), display_, image,
               event.getCanvas());
      }
   }

   @Subscribe
   public void onNewOverlay(NewOverlayEvent event) {
      chooser_.addItem(event.getFactory().getTitle());
   }

   public void cleanup() {
      display_.unregisterForEvents(this);
      DefaultEventManager.getInstance().unregisterForEvents(this);
   }

   @Override
   public void setDisplay(DisplayWindow display) {
      if (display_ != null) {
         display_.unregisterForEvents(this);
      }
      display_ = display;
      stack_ = ((DefaultDisplayWindow) display).getStack();
      plus_ = display.getImagePlus();
      for (OverlayPanel overlay : overlays_) {
         overlay.setDisplay(display);
      }
      display_.registerForEvents(this);
   }

   @Override
   public void setInspector(Inspector inspector) {
      inspector_ = inspector;
   }
}
