///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// -----------------------------------------------------------------------------
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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.HashMap;

/**
 * This class does a rigid grid layout that can be sparse (i.e. have gaps) and can have entities
 * take up multiple cells.
 */
public final class SparseGridLayout implements LayoutManager2 {
  private HashMap<Rectangle, Component> rectToComponent_;
  // Size of a cell, in pixels.
  private int cellWidth_;
  private int cellHeight_;
  // Max length along each axis of the grid.
  private int largestX_ = 0;
  private int largestY_ = 0;

  public SparseGridLayout(int cellWidth, int cellHeight) {
    cellWidth_ = cellWidth;
    cellHeight_ = cellHeight;
    rectToComponent_ = new HashMap<Rectangle, Component>();
  }

  @Override
  public void addLayoutComponent(Component comp, Object constraints) {
    Rectangle rect = (Rectangle) constraints;
    // Update our known max dimensions.
    largestX_ = Math.max(largestX_, rect.x + rect.width);
    largestY_ = Math.max(largestY_, rect.y + rect.height);
    rectToComponent_.put(rect, comp);
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {}

  @Override
  public void removeLayoutComponent(Component comp) {
    for (Rectangle r : rectToComponent_.keySet()) {
      if (comp == rectToComponent_.get(r)) {
        rectToComponent_.remove(r);
        break;
      }
    }
    // Recalculate max dimensions.
    largestX_ = 0;
    largestY_ = 0;
    for (Rectangle r : rectToComponent_.keySet()) {
      addLayoutComponent(rectToComponent_.get(r), r);
    }
  }

  @Override
  public Dimension preferredLayoutSize(Container container) {
    return new Dimension(largestX_ * cellWidth_, largestY_ * cellHeight_);
  }

  @Override
  public Dimension maximumLayoutSize(Container container) {
    return preferredLayoutSize(container);
  }

  @Override
  public Dimension minimumLayoutSize(Container container) {
    return preferredLayoutSize(container);
  }

  @Override
  public void layoutContainer(Container container) {
    for (Rectangle r : rectToComponent_.keySet()) {
      Component comp = rectToComponent_.get(r);
      // Horizontally and vertically center the component.
      Dimension size = comp.getPreferredSize();
      int slopWidth = Math.max(0, (r.width * cellWidth_ - size.width) / 2);
      int slopHeight = Math.max(0, (r.height * cellHeight_ - size.height) / 2);
      int targetWidth = Math.min(size.width, r.width * cellWidth_);
      int targetHeight = Math.min(size.height, r.height * cellHeight_);
      Rectangle bounds =
          new Rectangle(
              r.x * cellWidth_ + slopWidth,
              r.y * cellHeight_ + slopHeight,
              targetWidth,
              targetHeight);
      comp.setBounds(bounds);
    }
  }

  @Override
  public float getLayoutAlignmentX(Container container) {
    return 0.5f;
  }

  @Override
  public float getLayoutAlignmentY(Container container) {
    return 0.5f;
  }

  @Override
  public void invalidateLayout(Container target) {}
}
