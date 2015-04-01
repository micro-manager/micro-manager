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

import com.google.common.eventbus.Subscribe;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;

import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import net.miginfocom.swing.MigLayout;

import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.internal.events.DisplayActivatedEvent;
import org.micromanager.display.internal.events.LayoutChangedEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This frame shows a set of controls that are related to the currently-on-top
 * DisplayWindow (or to a specific DisplayWindow as selected by the user). It
 * consists of a set of expandable panels in a vertical configuration.
 */
public class InspectorFrame extends MMFrame implements Inspector {
   ArrayList<InspectorPanel> panels_;
   JPanel contents_;

   public InspectorFrame() {
      super();
      panels_ = new ArrayList<InspectorPanel>();
      setTitle("Image inspector");
      setAlwaysOnTop(true);
      setResizable(false);
      // Use a small title bar.
      getRootPane().putClientProperty("Window.style", "small");

      contents_ = new JPanel(new MigLayout("flowy, insets 0, gap 0, fillx"));
      contents_.setBorder(BorderFactory.createRaisedBevelBorder());
      add(contents_);
      setMinimumSize(new Dimension(100, 100));
      setVisible(true);

      DefaultEventManager.getInstance().registerForEvents(this);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent event) {
            DefaultEventManager.getInstance().unregisterForEvents(this);
         }
      });

      // Hard-coded initial panels.
      addPanel("Contrast", new HistogramsPanel());
      addPanel("Metadata", new MetadataPanel());
      addPanel("Comments", new CommentsPanel());
//      addPanel("Overlays", new OverlaysPanel());
   }

   /**
    * Add a new InspectorPanel to the window.
    */
   public void addPanel(String title, final InspectorPanel panel) {
      panels_.add(panel);
      panel.setInspector(this);
      // Wrap the panel in our own panel which includes the header.
      final JPanel wrapper = new JPanel(
            new MigLayout("flowy, insets 0, fillx"));
      wrapper.setBorder(BorderFactory.createLineBorder(new Color(32, 32, 32)));

      // Create a clickable header to show/hide contents.
      JPanel header = new JPanel();
      header.setLayout(new MigLayout("flowx, insets 0, fillx"));
      final JLabel label = new JLabel(title,
               UIManager.getIcon("Tree.collapsedIcon"),
               SwingConstants.LEFT);
      header.add(label);
      header.setCursor(new Cursor(Cursor.HAND_CURSOR));
      header.setBackground(new Color(220, 220, 220));
      header.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            panel.setVisible(!panel.isVisible());
            if (panel.isVisible()) {
               wrapper.add(panel);
               label.setIcon(UIManager.getIcon("Tree.expandedIcon"));
            }  
            else {
               wrapper.remove(panel);
               label.setIcon(UIManager.getIcon("Tree.collapsedIcon"));
            }  
            pack();
         }
      });
      wrapper.add(header, "growx");
      wrapper.add(panel);

      contents_.add(wrapper);
      validate();
   }

   @Override
   public void relayout() {
      pack();
   }

   @Subscribe
   public void onDisplayActivated(DisplayActivatedEvent event) {
      // TODO: only push this out if the active display is actually different
      // from the display we are currently "hooked" to.
      for (InspectorPanel panel : panels_) {
         try {
            panel.setDisplay(event.getDisplay());
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Error dispatching new display to " + panel);
         }
      }
   }
}
