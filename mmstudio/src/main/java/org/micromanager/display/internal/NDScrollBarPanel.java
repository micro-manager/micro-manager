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

package org.micromanager.display.internal;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.BoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.utils.MustCallOnEDT;

/**
 * Component containing zero to N scroll bars to select display position.
 *
 * This is a passive UI component and does not (and should not) contain any
 * application logic.
 *
 * @author Mark A. Tsuchida, based in part on earlier version by Chris Weisiger
 */
public class NDScrollBarPanel extends JPanel implements ChangeListener {
   interface Listener {
      void scrollBarPanelHeightWillChange(NDScrollBarPanel panel,
            int currentHeight);
      void scrollBarPanelHeightDidChange(NDScrollBarPanel panel,
            int oldHeight, int newHeight);
      void scrollBarPanelDidChangePositionInUI(NDScrollBarPanel panel);
   }

   private final List<Listener> listeners_ = new ArrayList<Listener>();

   private final List<String> axes_ = new ArrayList<String>();
   private final List<JPanel> rowPanels_ = new ArrayList<JPanel>();

   private boolean shouldSuppressChangeEvents_ = false;

   private static final int ROW_HEIGHT =
         new JScrollBar(JScrollBar.HORIZONTAL).getPreferredSize().height;

   // JScrollBar/BoundedRangeModel mapping to axis position:
   // - BoundedRangeModel value == axis position (>= 0, < axis length)
   // - BoundedRangeModel min == 0
   // - BoundedRangeModel max == axis length (>= 0)
   // - BoundedRangeModel extent == (axis length > 0 ? 1 : 0)

   @MustCallOnEDT
   static NDScrollBarPanel create() {
      return new NDScrollBarPanel();
   }

   @MustCallOnEDT
   private NDScrollBarPanel() {
      super(new MigLayout("insets 0, gapy 0, fillx"));
   }

   @MustCallOnEDT
   void addListener(Listener listener) {
      listeners_.add(listener);
   }

   @MustCallOnEDT
   void removeListener(Listener listener) {
      listeners_.remove(listener);
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

      shouldSuppressChangeEvents_ = true;
      try {
         List<JPanel> newPanels = new ArrayList<JPanel>(axes.size());
         for (String axis : axes) {
            int existingIndex = axes_.indexOf(axis);
            if (existingIndex >= 0) {
               newPanels.add(rowPanels_.get(existingIndex));
            }
            else {
               newPanels.add(makeRowPanel(axis, 0));
            }
         }

         boolean isRowCountChanging = axes.size() != axes_.size();

         int oldHeight = getHeight();
         if (isRowCountChanging) {
            for (Listener l : listeners_) {
               l.scrollBarPanelHeightWillChange(this, getHeight());
            }
         }

         axes_.clear();
         axes_.addAll(axes);
         rowPanels_.clear();
         rowPanels_.addAll(newPanels);

         removeAll();
         for (JPanel rowPanel : rowPanels_) {
            add(rowPanel, "growx, wrap");
         }
         invalidate();
         setMinimumSize(new Dimension(getMinimumSize().width,
               getPreferredSize().height));

         if (isRowCountChanging) {
            int newHeight = getPreferredSize().height;
            for (Listener l : listeners_) {
               l.scrollBarPanelHeightDidChange(this, oldHeight, newHeight);
            }
         }
      }
      finally {
         shouldSuppressChangeEvents_ = false;
      }
   }

   @MustCallOnEDT
   List<String> getAxes() {
      return new ArrayList(axes_);
   }

   @MustCallOnEDT
   boolean hasAxis(String axis) {
      return axes_.contains(axis);
   }

   @MustCallOnEDT
   void setAxisLength(String axis, int length) {
      shouldSuppressChangeEvents_ = true;
      try {
         if (length < 0) {
            throw new IllegalArgumentException();
         }
         BoundedRangeModel scrollModel = getScrollBarForAxis(axis).getModel();
         scrollModel.setRangeProperties(scrollModel.getValue(),
               (length > 0 ? 1 : 0), 0, length, false);
      }
      finally {
         shouldSuppressChangeEvents_ = false;
      }
   }

   @MustCallOnEDT
   int getAxisLength(String axis) {
      return getScrollBarForAxis(axis).getModel().getMaximum();
   }

   @MustCallOnEDT
   void setAxisPosition(String axis, int position) {
      shouldSuppressChangeEvents_ = true;
      try {
         getScrollBarForAxis(axis).setValue(position);
      }
      finally {
         shouldSuppressChangeEvents_ = false;
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
            new MigLayout("insets 0, gap 0 0, fillx"));

      JLabel axisLabel = new JLabel(axis.substring(0, 1)); // Temporary
      Dimension axisLabelSize = new Dimension(ROW_HEIGHT, ROW_HEIGHT);
      axisLabel.setMinimumSize(axisLabelSize);
      axisLabel.setMaximumSize(axisLabelSize);
      axisLabel.setPreferredSize(axisLabelSize);
      axisLabel.setHorizontalAlignment(SwingConstants.CENTER);
      rowPanel.add(axisLabel, "split 2");

      JScrollBar scrollBar = new JScrollBar(JScrollBar.HORIZONTAL,
            0, (length > 0 ? 1 : 0), 0, length);
      scrollBar.getModel().addChangeListener(this);
      rowPanel.add(scrollBar, "growx, wrap");

      return rowPanel;
   }

   @Override // ChangeListener
   public void stateChanged(ChangeEvent e) {
      if (shouldSuppressChangeEvents_) {
         return;
      }
      for (Listener l : listeners_) {
         l.scrollBarPanelDidChangePositionInUI(this);
      }
   }
}