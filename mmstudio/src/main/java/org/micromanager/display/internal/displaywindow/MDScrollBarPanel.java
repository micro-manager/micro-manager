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

package org.micromanager.display.internal.displaywindow;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.BoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.event.EventListenerSupport;
import org.micromanager.internal.utils.MustCallOnEDT;

/**
 * Component containing multi-dimensional scroll bars to select display position.
 *
 * This is a passive UI component and does not (and should not) contain any
 * application logic.
 *
 * @author Mark A. Tsuchida, based in part on earlier version by Chris Weisiger
 */
public class MDScrollBarPanel extends JPanel implements AdjustmentListener {
   public interface Listener {
      void scrollBarPanelHeightWillChange(MDScrollBarPanel panel,
            int currentHeight);
      void scrollBarPanelHeightDidChange(MDScrollBarPanel panel,
            int oldHeight, int newHeight);
      void scrollBarPanelDidChangePositionInUI(MDScrollBarPanel panel);
   }

   interface ControlsFactory {
      JComponent getControlsForAxis(String axis, int heightHint);
   }

   private final EventListenerSupport<Listener> listeners_ =
         new EventListenerSupport<> (Listener.class, Listener.class.getClassLoader());

   private final ControlsFactory leftControlsFactory_;
   private final ControlsFactory rightControlsFactory_;

   private final List<String> axes_ = new ArrayList<>();
   private final List<JPanel> rowPanels_ = new ArrayList<>();

   // We need to independently keep track of the last known positions in order
   // to filter out undesired adjustment events.
   private final List<Integer> scrollBarPositions_ = new ArrayList<>();
   private boolean shouldSuppressAdjustmentEvents_ = false;

   public static final int ROW_HEIGHT =
         new JScrollBar(JScrollBar.HORIZONTAL).getPreferredSize().height;

   // JScrollBar/BoundedRangeModel mapping to axis position:
   // - BoundedRangeModel value == axis position (>= 0, < axis length)
   // - BoundedRangeModel min == 0
   // - BoundedRangeModel max == axis length (>= 0)
   // - BoundedRangeModel extent == (axis length > 0 ? 1 : 0)

   @MustCallOnEDT
   static MDScrollBarPanel create(ControlsFactory leftControlsFactory,
         ControlsFactory rightControlsFactory)
   {
      return new MDScrollBarPanel(leftControlsFactory, rightControlsFactory);
   }

   @MustCallOnEDT
   private MDScrollBarPanel(ControlsFactory leftControlsFactory,
         ControlsFactory rightControlsFactory)
   {
      super(new MigLayout(new LC().insets("0").gridGap("0", "0").fillX()));
      leftControlsFactory_ = leftControlsFactory;
      rightControlsFactory_ = rightControlsFactory;
   }

   @MustCallOnEDT
   void addListener(Listener listener) {
      listeners_.addListener(listener, true);
   }

   @MustCallOnEDT
   void removeListener(Listener listener) {
      listeners_.removeListener(listener);
   }

   @MustCallOnEDT
   int getNumberOfAxes() {
      return axes_.size();
   }

   /**
    * Set the axes for which to display scroll bars.
    * <p>
    * State of existing axes (based on string name matching) is preserved, even
    * if axes are reordered.
    *
    * @param axes
    */
   @MustCallOnEDT
   void setAxes(List<String> axes) {
      if (axes_.equals(axes)) {
         return;
      }
      if (axes == null) {
         throw new NullPointerException();
      }

      shouldSuppressAdjustmentEvents_ = true;
      try {
         List<JPanel> newPanels = new ArrayList<>(axes.size());
         List<Integer> newScrollBarPositions = new ArrayList<>(axes.size());
         for (String axis : axes) {
            int existingIndex = axes_.indexOf(axis);
            if (existingIndex >= 0) {
               newPanels.add(rowPanels_.get(existingIndex));
               newScrollBarPositions.add(scrollBarPositions_.get(existingIndex));
            }
            else {
               newPanels.add(makeRowPanel(axis, 0));
               newScrollBarPositions.add(0);
            }
         }

         boolean isRowCountChanging = axes.size() != axes_.size();

         int oldHeight = getHeight();
         if (isRowCountChanging) {
            listeners_.fire().scrollBarPanelHeightWillChange(this, getHeight());
         }

         axes_.clear();
         axes_.addAll(axes);
         rowPanels_.clear();
         rowPanels_.addAll(newPanels);
         scrollBarPositions_.clear();
         scrollBarPositions_.addAll(newScrollBarPositions);

         removeAll();
         for (JPanel rowPanel : rowPanels_) {
            add(rowPanel, new CC().growX().wrap());
         }
         invalidate();
         setMinimumSize(new Dimension(getMinimumSize().width,
               getPreferredSize().height));

         if (isRowCountChanging) {
            int newHeight = getPreferredSize().height;
            listeners_.fire().scrollBarPanelHeightDidChange(this,
                  oldHeight, newHeight);
         }
      }
      finally {
         shouldSuppressAdjustmentEvents_ = false;
      }
   }

   @MustCallOnEDT
   List<String> getAxes() {
      return new ArrayList<>(axes_);
   }

   @MustCallOnEDT
   boolean hasAxis(String axis) {
      return axes_.contains(axis);
   }

   @MustCallOnEDT
   void setAxisLength(String axis, int length) {
      shouldSuppressAdjustmentEvents_ = true;
      try {
         if (length < 0) {
            throw new IllegalArgumentException();
         }
         BoundedRangeModel scrollModel = getScrollBarForAxis(axis).getModel();
         scrollModel.setRangeProperties(scrollModel.getValue(),
               (length > 0 ? 1 : 0), 0, length, false);
      }
      finally {
         shouldSuppressAdjustmentEvents_ = false;
      }
   }

   @MustCallOnEDT
   int getAxisLength(String axis) {
      return getScrollBarForAxis(axis).getModel().getMaximum();
   }

   @MustCallOnEDT
   void setAxisPosition(String axis, int position) {
      shouldSuppressAdjustmentEvents_ = true;
      try {
         getScrollBarForAxis(axis).setValue(position);
         int index = axes_.indexOf(axis);
         if (index >= 0) { // Should be true
            scrollBarPositions_.set(index, position);
         }
      }
      finally {
         shouldSuppressAdjustmentEvents_ = false;
      }
   }

   @MustCallOnEDT
   int getAxisPosition(String axis) {
      return getScrollBarForAxis(axis).getValue();
   }

   @MustCallOnEDT
   private JScrollBar getScrollBarForAxis(String axis) {
      int index = axes_.indexOf(axis);
      if (index < 0) {
         throw new NoSuchElementException(getClass().getSimpleName() +
               " has no axis " + axis);
      }
      JPanel rowPanel = rowPanels_.get(index);
      for (Component c : rowPanel.getComponents()) {
         if (c instanceof JScrollBar) {
            return (JScrollBar) c;
         }
      }
      throw new IllegalStateException(); // Shouldn't happen
   }

   @MustCallOnEDT
   private JPanel makeRowPanel(String axis, int length) {
      JPanel rowPanel = new JPanel(
            new MigLayout(new LC().insets("0").gridGap("0", "0").fillX()));

      if (leftControlsFactory_ != null) {
         JComponent leftControls =
               leftControlsFactory_.getControlsForAxis(axis, ROW_HEIGHT);
         rowPanel.add(leftControls, new CC());
      }

      JScrollBar scrollBar = new JScrollBar(JScrollBar.HORIZONTAL,
            0, (length > 0 ? 1 : 0), 0, length);
      scrollBar.addAdjustmentListener(this);
      rowPanel.add(scrollBar, new CC().growX().pushX());

      if (rightControlsFactory_ != null) {
         JComponent rightControls =
               rightControlsFactory_.getControlsForAxis(axis, ROW_HEIGHT);
         rowPanel.add(rightControls, new CC());
      }

      return rowPanel;
   }

   @Override
   public void adjustmentValueChanged(AdjustmentEvent e) {
      if (shouldSuppressAdjustmentEvents_) {
         return;
      }

      // Skip events that don't actually change the scroll bar position
      JScrollBar scrollBar = (JScrollBar) e.getSource();
      ROW_LOOP: for (int i = 0; i < axes_.size(); ++i) {
         JPanel rowPanel = rowPanels_.get(i);
         for (Component c : rowPanel.getComponents()) {
            if (c == scrollBar) {
               int knownPosition = scrollBarPositions_.get(i);
               if (e.getValue() == knownPosition) {
                  return;
               }
               break ROW_LOOP;
            }
         }
      }

      listeners_.fire().scrollBarPanelDidChangePositionInUI(this);
   }
}