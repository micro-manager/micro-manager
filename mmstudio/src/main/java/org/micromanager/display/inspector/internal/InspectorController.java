// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.display.inspector.internal;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.EventPublisher;
import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.DataViewerCollection;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeInactiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeInvisibleEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeVisibleEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.display.internal.event.InspectorDidCloseEvent;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.WindowPositioning;
import org.scijava.plugin.Plugin;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.events.ShutdownCommencingEvent;


/**
 * Implementation of the Inspector window.
 *
 * @author Mark A. Tsuchida, based in part on earlier version by Chris Weisiger
 */
public final class InspectorController
      implements EventPublisher, PopupMenuListener, Closeable
{
   private static final String FRONTMOST_VIEWER_ITEM = "Frontmost Window";

   private final DataViewerCollection viewerCollection_;
   private final Studio studio_;

   private JFrame frame_;
   private JPanel headerPanel_;
   private JScrollPane scrollPane_;
   private VerticalMultiSplitPane sectionsPane_;
   private JComboBox viewerComboBox_;
   private Object viewerComboBoxSelection_;
   private JButton viewerToFrontButton_;
   private DataViewer viewer_;

   private AttachmentStrategy attachmentStrategy_ =
         new NullAttachmentStrategy();

   private final List<SectionInfo> sections_ = new ArrayList<>();

   private final EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());

   /**
    * Since it is very difficult to associated an InpectorSectionController 
    * and InspectorSectionController with a plugin, and/or each other, 
    * we do it here manually.
    */
   private class SectionInfo {
      public final InspectorPanelController inspectorPanelController_;
      public final InspectorSectionController inspectorSectionController_;
      public final InspectorPanelPlugin plugin_;
      public SectionInfo (InspectorPanelController ipc, 
              InspectorSectionController isc, InspectorPanelPlugin p){
         inspectorPanelController_ = ipc;
         inspectorSectionController_ = isc;
         plugin_ = p;
      }
   }
   // Type for combo box items (data viewers)
   private static final class ViewerItem {
      private final DataViewer viewer_;

      ViewerItem(DataViewer viewer) {
         viewer_ = viewer;
      }

      public DataViewer getDataViewer() {
         return viewer_;
      }

      @Override
      public String toString() {
         return viewer_.getName();
      }
   }

   public static InspectorController create(Studio studio, DataViewerCollection viewers) {
      InspectorController instance = new InspectorController(studio, viewers);
      instance.makeUI();
      viewers.registerForEvents(instance);
      instance.updateDataViewerChooser();
      studio.events().registerForEvents(instance);
      return instance;
   }

   private InspectorController(Studio studio, DataViewerCollection viewers) {
      studio_ = studio;
      viewerCollection_ = viewers;
   }

   private void makeUI() {
      frame_ = new MMFrame(); // TODO
      frame_.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      frame_.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent w) {
            close();
         }
      });
      frame_.setAlwaysOnTop(false); // TODO
      frame_.getRootPane().putClientProperty("Window.style", "small");
      frame_.setLayout(new MigLayout(
            new LC().fill().insets("0").gridGap("0", "0")));

      headerPanel_ = new JPanel(new MigLayout(
            new LC().fill().insets("0").gridGap("0", "0")));
      headerPanel_.add(new JLabel("Inspect:"),
            new CC().gapBefore("rel").split(3));
      viewerComboBox_ = new JComboBox();
      viewerComboBox_.setToolTipText("Select the image window to inspect");
      viewerComboBox_.setRenderer(
            ComboBoxSeparatorRenderer.create(viewerComboBox_.getRenderer()));
      viewerComboBox_.addPopupMenuListener(this);
      viewerComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            viewerComboBoxActionPerformed(e);
         }
      });
      viewerComboBox_.setMinimumSize(new Dimension(
            240, viewerComboBox_.getMinimumSize().height));
      headerPanel_.add(viewerComboBox_, new CC().growX().pushX());
      viewerToFrontButton_ = new JButton("To Front");
      viewerToFrontButton_.setToolTipText("Bring this window to the front");
      viewerToFrontButton_.setEnabled(false);
      viewerToFrontButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            viewerToFrontButtonActionPerformed(e);
         }
      });
      headerPanel_.add(viewerToFrontButton_, new CC().wrap());

      sectionsPane_ = VerticalMultiSplitPane.create(0, true);
      scrollPane_ = new JScrollPane(sectionsPane_,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane_.setBorder(BorderFactory.createEmptyBorder());

      frame_.add(headerPanel_, new CC().growX().pushX().wrap());
      frame_.add(scrollPane_, new CC().grow().push().wrap());

      frame_.pack();

      // The frame's minimum size will need to be updated when there are panels
      // inserted, but for now the packed size is the minimum.
      frame_.setMinimumSize(frame_.getPreferredSize());
   }

   @Override
   public void close() {
      studio_.events().unregisterForEvents(this);
      viewerCollection_.unregisterForEvents(this);
      if (frame_ != null) {
         detachFromDataViewer();
         frame_.setVisible(false);
         frame_.dispose();
         frame_ = null;
      }
      eventBus_.post(InspectorDidCloseEvent.create(this));
   }
   
   @Subscribe
   public void closeRequested( ShutdownCommencingEvent sce) {
      close();
   }

   public void setVisible(boolean visible) {
      frame_.setVisible(visible);
   }

   public void attachToFixedDataViewer(DataViewer viewer) {
      if (viewer == null || viewer.isClosed()) {
         return;
      }
      attachmentStrategy_ = new FixedAttachmentStrategy(viewer);
      attachmentStrategy_.attachmentStrategySelected();
      viewerComboBox_.getModel().setSelectedItem(viewer.getName());
   }

   public void attachToFrontmostDataViewer() {
      attachmentStrategy_ = new FrontmostAttachmentStrategy();
      attachmentStrategy_.attachmentStrategySelected();
      viewerComboBox_.getModel().setSelectedItem(FRONTMOST_VIEWER_ITEM);
   }

   public boolean isAttachedToFrontmostDataViewer() {
      return attachmentStrategy_ instanceof FrontmostAttachmentStrategy;
   }

   private void updateDataViewerChooserImpl(List<DataViewer> viewers) {
      if (frame_ == null) {
         return;
      }

      Object saveSelection = viewerComboBox_.getModel().getSelectedItem();

      // Disable the combo box while we modify it; otherwise it fires action
      // events for the "changes"
      ActionListener[] listeners = viewerComboBox_.getActionListeners();
      for (ActionListener listener : listeners) {
         viewerComboBox_.removeActionListener(listener);
      }

      viewerComboBox_.removeAllItems();
      viewerComboBox_.addItem(FRONTMOST_VIEWER_ITEM);
      if (!viewers.isEmpty()) {
         viewerComboBox_.addItem(new JSeparator(JSeparator.HORIZONTAL));
      }
      for (DataViewer viewer : viewers) {
         viewerComboBox_.addItem(new ViewerItem(viewer));
      }

      viewerComboBox_.getModel().setSelectedItem(saveSelection);
      for (ActionListener listener : listeners) {
         viewerComboBox_.addActionListener(listener);
      }
   }

   private void updateDataViewerChooser() {
      updateDataViewerChooserImpl(viewerCollection_.getAllDataViewers());
   }

   private void updateDataViewerChooserExcluding(DataViewer viewer) {
      List<DataViewer> viewers = viewerCollection_.getAllDataViewers();
      viewers.remove(viewer);
      updateDataViewerChooserImpl(viewers);
   }

   private void showPanelsForDataViewer(DataViewer viewer) {
      // TODO We need to store and reuse panels, because their height should
      // not change when we reattach to another image.
      // TODO Also each panel needs to detach from previous viewer


      for (SectionInfo sectionInfo : sections_) {
         sectionInfo.inspectorPanelController_.detachDataViewer();
      }
      sections_.clear();
      
      if (viewer != null && !viewer.isClosed()) {
         List<InspectorPanelPlugin> plugins = new ArrayList<InspectorPanelPlugin>(
               studio_.plugins().getInspectorPlugins().values());
         Collections.sort(plugins, new Comparator<InspectorPanelPlugin>() {
            @Override
            public int compare(InspectorPanelPlugin o1, InspectorPanelPlugin o2) {
               Plugin p1 = o1.getClass().getAnnotation(Plugin.class);
               Plugin p2 = o2.getClass().getAnnotation(Plugin.class);
               return -Double.compare(p1.priority(), p2.priority());
            }
         });

         // This feels like a ball of tangled up wire:
         for (InspectorPanelPlugin plugin : plugins) {
            if (plugin.isApplicableToDataViewer(viewer)) {
               SectionInfo locatedSection = null;
               for (SectionInfo si : sections_) {
                     if (si.plugin_.equals(plugin)) {
                        locatedSection = si;
                  }
               }
               if (locatedSection == null) {
                  InspectorPanelController panelController
                          = plugin.createPanelController(studio_);
                  InspectorSectionController section
                          = InspectorSectionController.create(this, panelController);
                  panelController.addInspectorPanelListener(section);
                  locatedSection = new SectionInfo(panelController, section, plugin);
               }
               locatedSection.inspectorPanelController_.attachDataViewer(viewer);
               sections_.add(locatedSection);
            }
         }
      }


      sectionsPane_ = VerticalMultiSplitPane.create(sections_.size(), true);
      for (int i = 0; i < sections_.size(); ++i) {
         InspectorSectionController sectionController = 
                 sections_.get(i).inspectorSectionController_;
         sectionsPane_.setComponentAtIndex(i,
               sectionController.getSectionPanel());
         sectionsPane_.setComponentResizeEnabled(i,
               sectionController.isVerticallyResizableByUser() &&
               sectionController.isExpanded());
      }
      scrollPane_.setViewportView(sectionsPane_);

      GraphicsConfiguration config = GUIUtils.getGraphicsConfigurationContaining(1, 1);
      // TODO Set initial (factory default) position of frame
      WindowPositioning.setUpBoundsMemory(frame_, InspectorController.class, null);
      // TODO Attach MM menus to frame

      frame_.setMinimumSize(new Dimension(frame_.getPreferredSize().width + 16,
            frame_.getMinimumSize().height));
      frame_.setSize(
            Math.max(frame_.getWidth(), frame_.getMinimumSize().width),
            frame_.getHeight());
   }

   void inspectorSectionWillChangeHeight(InspectorSectionController section) {
   }

   void inspectorSectionDidChangeHeight(InspectorSectionController section)
   {
      int index = -1;
      for (int i=0; i < sections_.size(); i++) {
         if (section.equals(sections_.get(i).inspectorSectionController_)) {
            index = i;
         }
      }
      if (index < 0) {
         return;
      }

      // Remove the preferred size set by the multi-split pane.
      section.getSectionPanel().setPreferredSize(null);

      sectionsPane_.resizeToFitPreferredSizes();
      sectionsPane_.setComponentResizeEnabled(index,
            section.isVerticallyResizableByUser() && section.isExpanded());
      sectionsPane_.revalidate();
      sectionsPane_.repaint();

      // TODO Window (and scroll pane) height should be adjusted to
      // 1) Always remove any extra space in scroll panel
      // 2) Grow window vertically, within screen, if scroll panel is showing
      // vertical scroll bar
   }

   private void viewerComboBoxActionPerformed(ActionEvent e) {
      Object selectedItem = viewerComboBox_.getModel().getSelectedItem();
      if (selectedItem instanceof JSeparator) {
         viewerComboBox_.getModel().setSelectedItem(viewerComboBoxSelection_);
         return;
      }
      if (selectedItem == viewerComboBoxSelection_) {
         return;
      }

      if (selectedItem == FRONTMOST_VIEWER_ITEM) {
         attachToFrontmostDataViewer();
         viewerToFrontButton_.setEnabled(false);
      }
      else if (selectedItem instanceof ViewerItem) {
         DataViewer viewer = ((ViewerItem) selectedItem).getDataViewer();
         attachToFixedDataViewer(viewer);
         viewerToFrontButton_.setEnabled(true);
      }
   }

   private void viewerToFrontButtonActionPerformed(ActionEvent e) {
      Object selectedItem = viewerComboBox_.getSelectedItem();
      // TODO: in this case, the Selected item is of type String.  
      // I don't understand why, but for now just deal with it
      if (selectedItem instanceof ViewerItem) {
         ViewerItem vi = (ViewerItem) selectedItem;
         if (vi.getDataViewer() instanceof DisplayController) {
            ((DisplayController) vi.getDataViewer()).getWindow().toFront();
         }
      } else if (selectedItem instanceof String) {
         List<DataViewer> viewers = viewerCollection_.getAllDataViewers();
         for (DataViewer viewer : viewers) {
            if (viewer.getName().equals(selectedItem)) {
               if (viewer instanceof DisplayWindow) {
                  ((DisplayWindow) viewer).getWindow().toFront();
               }
            }
         }
      }
   }

   @Override
   public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      viewerComboBoxSelection_ = viewerComboBox_.getModel().getSelectedItem();
   }

   @Override
   public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
   }

   @Override
   public void popupMenuCanceled(PopupMenuEvent e) {
   }

   @Subscribe
   public void onEvent(DataViewerDidBecomeVisibleEvent e) {
      updateDataViewerChooser();
      attachmentStrategy_.viewerShown(e.getDataViewer());
   }

   @Subscribe
   public void onEvent(DataViewerDidBecomeInvisibleEvent e) {
      attachmentStrategy_.viewerHidden(e.getDataViewer());
      updateDataViewerChooser();
   }

   @Subscribe
   public void onEvent(DataViewerDidBecomeActiveEvent e) {
      attachmentStrategy_.viewerActivated(e.getDataViewer());
   }

   @Subscribe
   public void onEvent(DataViewerDidBecomeInactiveEvent e) {
      attachmentStrategy_.viewerDeactivated(e.getDataViewer());
   }

   @Subscribe
   public void onEvent(DataViewerWillCloseEvent e) {
      attachmentStrategy_.viewerWillClose(e.getDataViewer());
      updateDataViewerChooserExcluding(e.getDataViewer());
   }

   private void attachToDataViewer(DataViewer viewer) {
      if (viewer == null || viewer.isClosed()) {
         detachFromDataViewer(); // Just in case
         return;
      }
      if (viewer != viewer_) {
         frame_.setTitle(String.format("Inspect \"%s\"", viewer.getName()));
         showPanelsForDataViewer(viewer);
         viewer_ = viewer;
      }
   }

   private void detachFromDataViewer() {
      frame_.setTitle(String.format("Inspector: No Image"));
      showPanelsForDataViewer(null);
   }

   @Override
   public void registerForEvents(Object recipient) {
      eventBus_.register(recipient);
   }

   @Override
   public void unregisterForEvents(Object recipient) {
      eventBus_.unregister(recipient);
   }


   // Stragety classes for different ways in which to determine which viewer to
   // attach to
   private interface AttachmentStrategy {
      void attachmentStrategySelected();
      void viewerShown(DataViewer viewer);
      void viewerHidden(DataViewer viewer);
      void viewerActivated(DataViewer viewer);
      void viewerDeactivated(DataViewer viewer);
      void viewerWillClose(DataViewer viewer);
   }

   private class NullAttachmentStrategy implements AttachmentStrategy {
      @Override
      public void attachmentStrategySelected() {
         detachFromDataViewer();
      }

      @Override
      public void viewerShown(DataViewer viewer) {
      }

      @Override
      public void viewerHidden(DataViewer viewer) {
      }

      @Override
      public void viewerActivated(DataViewer viewer) {
      }

      @Override
      public void viewerDeactivated(DataViewer viewer) {
      }

      @Override
      public void viewerWillClose(DataViewer viewer) {
      }
   }

   private class FixedAttachmentStrategy implements AttachmentStrategy {
      private final DataViewer viewer_;

      public FixedAttachmentStrategy(DataViewer viewer) {
         viewer_ = viewer;
      }

      @Override
      public void attachmentStrategySelected() {
         if (viewer_ != null) {
            attachToDataViewer(viewer_);
         }
         else {
            detachFromDataViewer();
         }
         // TODO Coordinate with explicit show/hide of the inspector
         InspectorController.this.setVisible(viewer_.isVisible());
      }

      @Override
      public void viewerShown(DataViewer viewer) {
         if (viewer == viewer_) {
            // TODO Coordinate with explicit show/hide of the inspector
            InspectorController.this.setVisible(true);
         }
      }

      @Override
      public void viewerHidden(DataViewer viewer) {
         if (viewer == viewer_) {
            // TODO Coordinate with explicit show/hide of the inspector
            InspectorController.this.setVisible(false);
         }
      }

      @Override
      public void viewerActivated(DataViewer viewer) {
      }

      @Override
      public void viewerDeactivated(DataViewer viewer) {
      }

      @Override
      public void viewerWillClose(DataViewer viewer) {
         if (viewer == viewer_) {
            InspectorController.this.close();
         }
      }
   }

   private class FrontmostAttachmentStrategy
         implements AttachmentStrategy
   {
      private DataViewer viewer_;

      @Override
      public void attachmentStrategySelected() {
         viewer_ = viewerCollection_.getActiveDataViewer();
         if (viewer_ != null) {
            attachToDataViewer(viewer_);
         }
         else {
            detachFromDataViewer();
         }
      }

      @Override
      public void viewerShown(DataViewer viewer) {
      }

      @Override
      public void viewerHidden(DataViewer viewer) {
      }

      @Override
      public void viewerActivated(DataViewer viewer) {
         attachToDataViewer(viewer);
         viewer_ = viewer;
      }

      @Override
      public void viewerDeactivated(DataViewer viewer) {
         if (viewer == viewer_) {
            detachFromDataViewer();
            viewer_ = null;
         }
      }

      @Override
      public void viewerWillClose(DataViewer viewer) {
         if (viewer == viewer_) {
            detachFromDataViewer();
            viewer_ = null;
         }
      }
   }
}