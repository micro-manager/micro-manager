/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.displaywindow;

import com.google.common.eventbus.Subscribe;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.micromanager.data.Coords;
import org.micromanager.display.DisplayPositionChangedEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.display.internal.link.AbstractLinkAnchor;
import org.micromanager.display.internal.link.LinkAnchor;
import org.micromanager.display.internal.link.LinkManager;

/**
 *
 * @author mark
 */
class AxisLinker {
   private final DisplayController viewer_;
   private final String axis_;
   private final AxisLinkAnchor anchor_;
   private final LinkManager linkManager_;

   // TODO This anchor can be generalized to DataViewer
   private class AxisLinkAnchor extends AbstractLinkAnchor<Integer> {
      @Override
      public Object getLinkageGroup() {
         return axis_; // TODO include axis-ness
      }

      @Override
      public Integer getCurrentValue() {
         return viewer_.getDisplayPosition().getIndex(axis_);
      }

      @Override
      public boolean receivePropagatedValue(Integer value) {
         Coords oldPos, newPos;
         do {
            oldPos = viewer_.getDisplayPosition();
            // TODO Is there a more efficient way to break the loop?
            // Cleanest would be for compareAndSet to take a 'setter' Object,
            // accessible in DisplayPositionChangedEvent.
            if (oldPos.getIndex(axis_) == value) {
               return true;
            }
            newPos = oldPos.copyBuilder().index(axis_, value).build();
            if (!viewer_.getDataProvider().hasImage(newPos)) {
               return false;
            }
         } while (!viewer_.compareAndSetDisplayPosition(oldPos, newPos));
         return true;
      }

      private DisplayController getViewer() {
         return viewer_;
      }

      private void propagate(int p) {
         propagateValue(p);
      }
   }

   public static AxisLinker create(LinkManager linkManager,
         DisplayController viewer, String axis)
   {
      AxisLinker instance = new AxisLinker(linkManager, viewer, axis);
      viewer.registerForEvents(instance);
      return instance;
   }

   private AxisLinker(LinkManager linkManager, DisplayController viewer,
         String axis)
   {
      linkManager_ = linkManager;
      viewer_ = viewer;
      axis_ = axis;
      anchor_ = new AxisLinkAnchor();
      linkManager.registerAnchor(anchor_);
   }

   void updatePopupMenu(JPopupMenu menu) {
      menu.removeAll();

      Collection<LinkAnchor<Integer>> linked = anchor_.getLinkedPeers();

      JMenuItem unlinkItem = new JMenuItem("Unlink");
      unlinkItem.setEnabled(!linked.isEmpty());
      unlinkItem.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            anchor_.unlink();
         }
      });
      menu.add(unlinkItem);
      menu.addSeparator();

      for (LinkAnchor<Integer> peer : anchor_.getLinkablePeers()) {
         final AxisLinkAnchor anchor = (AxisLinkAnchor) peer;
         if (anchor == anchor_) {
            continue;
         }
         JCheckBoxMenuItem item = new JCheckBoxMenuItem(
               anchor.getViewer().getName());
         item.setSelected(linked.contains(anchor));
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               anchor_.linkToPeer(anchor);
            }
         });
         menu.add(item);
      }
   }

   @Subscribe
   public void onEvent(DisplayPositionChangedEvent e) {
      Coords oldPos = e.getPreviousDisplayPosition();
      Coords newPos = e.getDisplayPosition();
      if (oldPos.getIndex(axis_) != newPos.getIndex(axis_)) {
         anchor_.propagate(newPos.getIndex(axis_));
      }
   }
   
   @Subscribe
   public void onEvent(DataViewerWillCloseEvent e) {
      // this should always be the case, but better safe than sorry
      if (e.getDataViewer().equals(viewer_)) {
         anchor_.unlink();
         linkManager_.unregisterAnchor(anchor_);
      }
   }
}