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
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import net.miginfocom.swing.MigLayout;

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.internal.DefaultDisplayManager;
import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.internal.events.DisplayActivatedEvent;
import org.micromanager.display.internal.events.LayoutChangedEvent;
import org.micromanager.events.DisplayAboutToShowEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This frame shows a set of controls that are related to the currently-on-top
 * DisplayWindow (or to a specific DisplayWindow as selected by the user). It
 * consists of a set of expandable panels in a vertical configuration.
 */
public class InspectorFrame extends MMFrame implements Inspector {
   private static final String TOPMOST_DISPLAY = "Topmost Window";
   private static final String CONTRAST_TITLE = "Contrast";
   private DisplayWindow display_;
   private ArrayList<InspectorPanel> panels_;
   private JPanel contents_;
   private JComboBox displayChooser_;

   public InspectorFrame(DisplayWindow display) {
      super();
      panels_ = new ArrayList<InspectorPanel>();
      setTitle("Image Inspector");
      setAlwaysOnTop(true);
      // Use a small title bar.
      getRootPane().putClientProperty("Window.style", "small");

      contents_ = new JPanel(new MigLayout("flowy, insets 0, gap 0, fillx"));

      // Create a dropdown menu to select which display to show info/controls
      // for. By default, we show info on the topmost display (changing when
      // that display changes).
      displayChooser_ = new JComboBox();
      populateChooser();
      if (display == null) {
         displayChooser_.setSelectedItem(TOPMOST_DISPLAY);
      }
      displayChooser_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setDisplayByName((String) displayChooser_.getSelectedItem());
         }
      });
      contents_.add(new JLabel("Show info for:"), "flowx, split 2");
      contents_.add(displayChooser_);
      JScrollPane scroller = new JScrollPane(contents_,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      add(scroller);
      setVisible(true);

      DefaultEventManager.getInstance().registerForEvents(this);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent event) {
            DefaultEventManager.getInstance().unregisterForEvents(this);
         }
      });

      // Hard-coded initial panels.
      addPanel(CONTRAST_TITLE, new HistogramsPanel());
      addPanel("Settings", new DisplaySettingsPanel());
      addPanel("Metadata", new MetadataPanel());
      addPanel("Comments", new CommentsPanel());
      addPanel("Overlays", new OverlaysPanel());

      if (display != null) {
         displayChooser_.setSelectedItem(display.getName());
         setDisplay(display);
      }
   }

   /**
    * Fill in the list of DisplayWindows the user can select from.
    */
   private void populateChooser() {
      String curItem = (String) displayChooser_.getSelectedItem();
      displayChooser_.removeAllItems();
      displayChooser_.addItem(TOPMOST_DISPLAY);
      List<DisplayWindow> allDisplays = DefaultDisplayManager.getInstance().getAllImageWindows();
      for (DisplayWindow display : allDisplays) {
         displayChooser_.addItem(display.getName());
      }
      displayChooser_.setSelectedItem(curItem);
   }

   /**
    * Set our current target display based on the provided display name.
    */
   private void setDisplayByName(String name) {
      List<DisplayWindow> allDisplays = DefaultDisplayManager.getInstance().getAllImageWindows();
      for (DisplayWindow display : allDisplays) {
         if (display.getName().contentEquals(name)) {
            setDisplay(display);
            break;
         }
      }
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
      wrapper.setBorder(BorderFactory.createRaisedBevelBorder());

      // Create a clickable header to show/hide contents.
      JPanel header = new JPanel(new MigLayout("fillx"));
      header.setLayout(new MigLayout("flowx, insets 0, fillx"));
      final JLabel label = new JLabel(title,
               UIManager.getIcon("Tree.collapsedIcon"),
               SwingConstants.LEFT);
      header.add(label, "growx");
      header.setCursor(new Cursor(Cursor.HAND_CURSOR));
      header.setBackground(new Color(220, 220, 220));
      header.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            panel.setVisible(!panel.isVisible());
            if (panel.isVisible()) {
               wrapper.add(panel, "growx, gap 0");
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
      // HACK: the specific panel with the "Contrast" title is automatically
      // visible.
      if (title.contentEquals(CONTRAST_TITLE)) {
         wrapper.add(panel, "growx, gap 0");
         panel.setVisible(true);
         label.setIcon(UIManager.getIcon("Tree.expandedIcon"));
      }
      else {
         panel.setVisible(false); // So the first click will show it.
      }

      contents_.add(wrapper, "growx");
      validate();
   }

   @Override
   public void relayout() {
      pack();
   }

   @Subscribe
   public void onNewDisplay(DisplayAboutToShowEvent event) {
      event.getDisplay().registerForEvents(this);
      displayChooser_.addItem(event.getDisplay().getName());
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      event.getDisplay().unregisterForEvents(this);
      String curDisplay = (String) displayChooser_.getSelectedItem();
      String deadDisplay = event.getDisplay().getName();
      displayChooser_.removeItem(event.getDisplay().getName());
      if (curDisplay.contentEquals(event.getDisplay().getName())) {
         // Just removed the one we had selected.
         displayChooser_.setSelectedItem(TOPMOST_DISPLAY);
      }
   }

   @Subscribe
   public void onDisplayActivated(DisplayActivatedEvent event) {
      try {
         String displayName = (String) displayChooser_.getSelectedItem();
         if (!displayName.contentEquals(TOPMOST_DISPLAY)) {
            // We're keyed to a specific display, so we don't care that another
            // one is now on top.
            return;
         }
         if (display_ == event.getDisplay()) {
            // We're already keyed to this display, so do nothing.
            return;
         }
         setDisplay(event.getDisplay());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error on new display activation");
      }
   }

   private void setDisplay(DisplayWindow display) {
      display_ = display;
      for (InspectorPanel panel : panels_) {
         try {
            panel.setDisplay(display_);
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Error dispatching new display to " + panel);
         }
      }
   }
}
