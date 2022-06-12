// Copyright (C) 2017 Open Imaging, Inc.
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;

/**
 * Like a JSplitPane, but contains multiple components, not just 2.
 * <p>
 * This is more than just nested {@code JSplitPane}s: it ensures that each
 * component can be resized without affecting the size of the other components.
 * An extra draggable divider is added below the last (bottom) component to
 * allow resizing the latter.
 * <p>
 * Caveat: The vertical scroll bar of a containing scroll panel will change
 * length when the mouse enters the split pane dividers. This is due to a hack
 * used to get the desired behavior from a set of nested {@code JSplitPane}s.
 * Also, using the scroll wheel when the cursor is positioned on a divider will
 * cause odd effects. Both of these issues could be mitigated by notifying the
 * scroll pane and disabling the scroll bar and mouse wheel.
 * <p>
 * If used without a scroll pane, a {@code ComponentListener} should be added
 * so that the container can appropriately resize.
 * <p>
 * This class could be generalized to also work for horizontal splits.
 *
 * @author Mark A. Tsuchida
 */
public class VerticalMultiSplitPane extends JPanel implements Scrollable {
   private final List<JSplitPane> splitPanes_ = new ArrayList<JSplitPane>();
   private final JComponent extraSpace_ = new JPanel();

   private boolean isPreparedForDividerDrag_ = false;
   private boolean isTrackingDividerDrag_ = false;
   private boolean isUnprepareForDragPending_ = false;

   private boolean debug = false;

   public static VerticalMultiSplitPane create(int numChildren,
                                               boolean continuousLayout) {
      return new VerticalMultiSplitPane(numChildren, continuousLayout);
   }

   private VerticalMultiSplitPane(int numChildren, boolean continuousLayout) {
      super.setLayout(new MigLayout(new LC().fill().insets("0")));

      extraSpace_.setMinimumSize(new Dimension(0, 0));
      extraSpace_.setPreferredSize(new Dimension(0, 0));

      for (int i = 0; i < numChildren; ++i) {
         final JSplitPane splitPane =
               new JSplitPane(JSplitPane.VERTICAL_SPLIT, continuousLayout);
         splitPane.setBorder(BorderFactory.createEmptyBorder());
         splitPane.setResizeWeight(0.0);
         if (i == 0) {
            super.add(splitPane, new CC().grow().push());
         }
         else {
            splitPanes_.get(i - 1).setBottomComponent(splitPane);
         }
         splitPanes_.add(splitPane);

         // When the user drags the divider, temporarily add a huge space at
         // the bottom/right so that the last (real) component can be resized,
         // and also so that the other components can be resized without
         // shrinking the last component.
         SplitPaneUI splitPanelUI = splitPane.getUI();
         // I hope it is safe to assume that the split panel UI inherits from
         // BasicSplitPanelUI. It is the case on OS X and Windows with our
         // usual look and feel.
         if (splitPanelUI instanceof BasicSplitPaneUI) {
            ((BasicSplitPaneUI) splitPanelUI).getDivider().
                  addMouseListener(new MouseAdapter() {
                     @Override
                     public void mouseEntered(MouseEvent e) {
                        if (debug) {
                           System.err.println("ENTERED");
                        }
                        if (!isTrackingDividerDrag_) {
                           prepareForDividerDrag(splitPane);
                        }
                        isUnprepareForDragPending_ = false;
                     }

                     @Override
                     public void mouseExited(MouseEvent e) {
                        if (debug) {
                           System.err.println("EXITED");
                        }
                        if (!isTrackingDividerDrag_) {
                           unprepareForDividerDrag();
                        }
                        else {
                           isUnprepareForDragPending_ = true;
                        }
                     }

                     @Override
                     public void mousePressed(MouseEvent e) {
                        if (debug) {
                           System.err.println("PRESSED");
                        }
                        startTrackingDividerDrag();
                     }

                     @Override
                     public void mouseReleased(MouseEvent e) {
                        if (debug) {
                           System.err.println("RELEASED");
                        }
                        finishTrackingDividerDrag();
                     }
                  });
         }
      }

      if (!splitPanes_.isEmpty()) {
         getLastSplitPane().setBottomComponent(extraSpace_);
      }
   }

   @Override
   public Dimension getPreferredSize() {
      Dimension prefSize = super.getPreferredSize();
      if (super.isPreferredSizeSet()) {
         return prefSize;
      }

      if (splitPanes_.isEmpty()) {
         return new Dimension(prefSize.width, 200);
      }

      int prefHeight = 0;
      for (JSplitPane splitPane : splitPanes_) {
         Component component = splitPane.getTopComponent();
         Dimension componentPrefSize = component.isValid() ?
               component.getSize() : component.getPreferredSize();
         prefHeight += componentPrefSize.height;
         prefHeight += splitPane.getDividerSize();
      }
      prefHeight += extraSpace_.getPreferredSize().height;

      return new Dimension(prefSize.width, prefHeight);
   }

   public void setComponentAtIndex(int index, JComponent child) {
      if (splitPanes_.size() > index) {
         splitPanes_.get(index).setTopComponent(child);
      }
   }

   public void setComponentResizeEnabled(int index, boolean enabled) {
      if (splitPanes_.size() > index) {
         splitPanes_.get(index).setEnabled(enabled);
      }
   }

   /**
    * Resize this multi-split pane so that every component is at its preferred
    * height.
    * <p>
    * Call this after changing the preferred size of any of the components.
    */
   public void resizeToFitPreferredSizes() {
      // Note that what we are doing is _not_ analogous to JSplitPane's
      // resetToPreferredSizes(). That method preserves the current size of the
      // JSplitPane, potentially shrinking the contained components.
      // We just want the components to have exactly their preferred height.

      // We set the size of the entire panel (which propagates to all split
      // panes) before moving the dividers to the right locations.
      List<JSplitPane> reversedSplitPanes =
            new ArrayList<JSplitPane>(splitPanes_);
      Collections.reverse(reversedSplitPanes);
      int totalHeight = 0;
      for (JSplitPane splitPane : reversedSplitPanes) {
         totalHeight += splitPane.getDividerSize();
         totalHeight += splitPane.getTopComponent().getPreferredSize().height;
      }
      setSize(getWidth(), totalHeight);
      if (!splitPanes_.isEmpty()) {
         splitPanes_.get(0).setSize(getWidth(), totalHeight);
      }

      for (JSplitPane splitPane : splitPanes_) {
         // Move divider to honor prferred size of top component
         splitPane.setDividerLocation(-1); // See JSplitPane Javadoc
      }

      revalidate();
   }

   private JSplitPane getLastSplitPane() {
      return splitPanes_.get(splitPanes_.size() - 1);
   }

   private void preferCurrentSizes() {
      for (JSplitPane splitPane : splitPanes_) {
         splitPane.getTopComponent().setPreferredSize(
               new Dimension(splitPane.getTopComponent().getSize()));
         if (debug) {
            System.err.println("SET PREF " + splitPane.getTopComponent().getPreferredSize());
         }
      }
   }

   private void prepareForDividerDrag(JSplitPane splitPane) {
      if (isPreparedForDividerDrag_) {
         return;
      }

      // Put ourself in a state where dragging the divider does not lead to
      // resizing any pane except for the one immediately above the divider
      // bing dragged. We accomplish this by inserting a lot of extra height
      // after the last divider (thus, all components below the dragged divider
      // will just slide down without shrinking).

      // Setting the preferred size of the "extra space" is enough to cause the
      // JSplitPane and MultiSplitPane to both resize. However, the JSplitPane
      // will compute the movable range of its divider before that resizing
      // takes place. So we need to proactively resize the split pane. Note
      // that it is too late to do this in mousePressed().

      final int HUGE_HEIGHT = 10000;
      extraSpace_.setPreferredSize(new Dimension(0, HUGE_HEIGHT));
      splitPane.setSize(splitPane.getWidth(),
            splitPane.getHeight() + HUGE_HEIGHT);
      VerticalMultiSplitPane.this.revalidate();
      isPreparedForDividerDrag_ = true;
   }

   private void unprepareForDividerDrag() {
      if (!isPreparedForDividerDrag_) {
         return;
      }

      extraSpace_.setPreferredSize(new Dimension(0, 0));
      VerticalMultiSplitPane.this.revalidate();
      isPreparedForDividerDrag_ = false;
   }

   private void startTrackingDividerDrag() {
      isTrackingDividerDrag_ = true;
   }

   private void finishTrackingDividerDrag() {
      if (!isTrackingDividerDrag_) {
         return;
      }

      // Without this, all the components are reset to their
      // preferred height upon horizontal resizing of the frame.
      // I have not yet determined why.
      preferCurrentSizes();

      isTrackingDividerDrag_ = false;

      // "mouseExited" may have happened before "mouseReleased"
      if (isUnprepareForDragPending_) {
         unprepareForDividerDrag();
         isUnprepareForDragPending_ = false;
      }
   }

   @Override // Scrollable
   public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
   }

   @Override // Scrollable
   public int getScrollableUnitIncrement(Rectangle visibleRect,
                                         int orientation, int direction) {
      return 10;
   }

   @Override // Scrollable
   public int getScrollableBlockIncrement(Rectangle visibleRect,
                                          int orientation, int direction) {
      if (orientation == SwingConstants.HORIZONTAL) {
         return 10; // Whatever...
      }

      if (splitPanes_.isEmpty()) {
         return 0;
      }

      // Jump to the top/left of the next component
      if (direction > 0) { // Scroll down
         int splitPanePos = 0;
         for (JSplitPane splitPane : splitPanes_) {
            if (visibleRect.y < splitPanePos) {
               return splitPanePos - visibleRect.y;
            }
            splitPanePos += splitPane.getTopComponent().getHeight() +
                  splitPane.getDividerSize();
         }
         return getHeight() - visibleRect.height - visibleRect.y;
      }
      else {
         int splitPanePos = splitPanes_.get(0).getHeight();
         List<JSplitPane> reversedSplitPanes =
               new ArrayList<JSplitPane>(splitPanes_);
         Collections.reverse(reversedSplitPanes);
         for (JSplitPane splitPane : reversedSplitPanes) {
            splitPanePos -= splitPane.getDividerSize() +
                  splitPane.getTopComponent().getHeight();
            if (visibleRect.y > splitPanePos) {
               return visibleRect.y - splitPanePos;
            }
         }
         return visibleRect.y - 0; // Should not be reached
      }
   }

   @Override // Scrollable
   public boolean getScrollableTracksViewportWidth() {
      return true;
   }

   @Override // Scrollable
   public boolean getScrollableTracksViewportHeight() {
      return false;
   }


   //
   // Test driver
   //

   public static void main(String[] argv) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            test();
         }
      });
   }

   private static JComponent makeTestLabel() {
      final JLabel label = new JLabel();
      label.setVerticalTextPosition(JLabel.TOP);
      label.setVerticalAlignment(JLabel.TOP);
      label.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            label.setText(String.format("%d", label.getHeight()));
            System.err.println("Resized: " + label.getHeight());
         }
      });
      return label;
   }

   private static void test() {
      final VerticalMultiSplitPane multiSplitPane =
            VerticalMultiSplitPane.create(3, true);
      multiSplitPane.debug = true;

      JComponent label = makeTestLabel();
      label.setPreferredSize(new Dimension(200, 200));
      multiSplitPane.setComponentAtIndex(0, label);

      label = makeTestLabel();
      label.setMinimumSize(new Dimension(200, 150));
      label.setPreferredSize(new Dimension(200, 250));
      label.setMaximumSize(new Dimension(500, 350));
      multiSplitPane.setComponentAtIndex(1, label);
      final JComponent middleLabel = label;

      label = makeTestLabel();
      label.setMinimumSize(new Dimension(200, 50));
      label.setPreferredSize(new Dimension(200, 150));
      label.setMaximumSize(new Dimension(500, 250));
      multiSplitPane.setComponentAtIndex(2, label);

      JButton button = new JButton("Change Preferred Size");
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            middleLabel.setPreferredSize(new Dimension(200, 200));
            multiSplitPane.resizeToFitPreferredSizes();
            multiSplitPane.revalidate();
            multiSplitPane.repaint();
         }
      });

      JScrollPane scrollPane = new JScrollPane(multiSplitPane,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setBorder(BorderFactory.createEmptyBorder());

      JFrame frame = new JFrame();
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setMinimumSize(new Dimension(240, 200));
      frame.setLayout(new MigLayout(new LC().fill().insets("0")));
      frame.add(button, new CC().wrap());
      frame.add(scrollPane, new CC().grow().push());

      frame.pack();
      frame.setVisible(true);
   }
}