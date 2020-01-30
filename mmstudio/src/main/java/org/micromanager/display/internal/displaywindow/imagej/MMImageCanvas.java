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

package org.micromanager.display.internal.displaywindow.imagej;

import ij.gui.ImageCanvas;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.SwingUtilities;
import org.micromanager.internal.utils.MustCallOnEDT;

/**
 * Our wrapped version of ImageJ's {@code ImageCanvas}.
 *
 * @author Mark A. Tsuchida, parts based on older version by Chris Weisiger
 */
public final class MMImageCanvas extends ImageCanvas
      implements MouseListener, MouseMotionListener, MouseWheelListener
{
   private final ImageJBridge parent_;

   private Dimension preferredSize_;

   @MustCallOnEDT
   static MMImageCanvas create(ImageJBridge parent) {
      final MMImageCanvas instance = new MMImageCanvas(parent);

      // Initialize min/max/pref sizes for the initial zoom ratio set by super
      instance.setMagnification(instance.getMagnification());

      // Listen for size changes; without this, we are unaware of resizing that
      // occurs due to window resizing
      instance.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            instance.handleComponentResized();
         }
      });

      // Listen for the addition of this canvas to its containing panel
      instance.addHierarchyListener(new HierarchyListener() {
         @Override
         public void hierarchyChanged(HierarchyEvent e) {
            instance.handleHierarchyChanged(e);
         }
      });
      
      instance.addMouseWheelListener(instance);

      return instance;
   }

   private MMImageCanvas(ImageJBridge parent) {
      super(parent.getIJImagePlus());
      parent_ = parent;
   }

   @Override
   public void paint(Graphics g) {
      // Really, this should be implemented using VolatileImage. Unfortunately,
      // ij.gui.ImageCanvas is not written in a way that allows us to easily
      // override paint() without reimplementing a whole bunch of stuff.

      // Let ImageJ draw the image, selection, zoom indicator, etc.
      super.paint(g);
      parent_.paintMMOverlays((Graphics2D) g, getWidth(), getHeight(), srcRect);
      parent_.ijPaintDidFinish();
   }

   @Override
   public Dimension getPreferredSize() {
      // ImageJ overrides this method, but we prefer the standard method of
      // calling setPreferredSize as necessary.
      if (preferredSize_ == null) {
         return new Dimension();
      }
      return new Dimension(preferredSize_);
   }
   
   @Override
   @Deprecated
   public Dimension preferredSize() {
      // Don't let this leak access to super just because it's deprecated
      return getPreferredSize();
   }

   @Override
   public void setPreferredSize(Dimension size) {
      // Implementation to match getPreferredSize
      preferredSize_ = new Dimension(size);
   }

   @Override
   public boolean isPreferredSizeSet() {
      // Implementation to match getPreferredSize
      return preferredSize_ != null;
   }

   @Override
   public void setSize(Dimension newSize) {
      // ImageJ forgets to override this overloaded version
      setSize(newSize.width, newSize.height);
   }
   
   /**
    * The ImageJ canvas knows best what its current size is,
    * Adjust our display to the current size
    */
   public void setSizeToCurrent() {
      setSize(super.dstWidth, super.dstHeight);
   }

   @Override
   public void setSize(int newWidth, int newHeight) {
      if (newWidth == getSize().width && newHeight == getSize().height) {
         return; // Prevent infivite update loop
      }

      super.setSize(newWidth, newHeight); // Does _not_ update source rect

      // The ImageCanvas constructor calls this method, so parent_ may be null
      if (parent_ != null) {
         setSourceRect(parent_.computeSourceRectForCanvasSize(
               getMagnification(), newWidth, newHeight, getSrcRect()));

         parent_.ij2mmCanvasDidChangeSize();
      }

      // We need to repaint even if shrinking, to correctly show the zoom
      // indicator
      repaint();
   }

   // Handle componentResized event
   private void handleComponentResized() {
      super.setSize(getWidth(), getHeight()); // Update dest rect
      setSourceRect(parent_.computeSourceRectForCanvasSize(
                  getMagnification(), getWidth(), getHeight(), getSrcRect()));

      // Keep the AWT-Swing mixture happy
      Container parent = getParent();
      if (parent != null) {
         Window window = SwingUtilities.getWindowAncestor(parent);
         if (window != null) {
            window.validate();
         }
      }

      repaint();
   }

   // Handle hierarchyChanged event
   private void handleHierarchyChanged(HierarchyEvent e) {
      if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
         if (getParent() != null) {
            handleComponentResized();
         }
      }
   }


   //
   // Internal handling of zoom and source rect
   //

   @Override
   public void setMagnification(double factor) {
      super.setMagnification(factor); // Does _not_ set the source rect

      // Update the component dimension limits and preference
      setMaximumSize(new Dimension(
            (int) Math.floor(parent_.getMMWidth() * getMagnification()),
            (int) Math.floor(parent_.getMMHeight() * getMagnification())));
      // Limit so that zoom indicator is not obscured, but no larger than the
      // scaled image
      Dimension maxSize = getMaximumSize();
      setMinimumSize(new Dimension(Math.min(84, getMaximumSize().width),
            Math.min(84, getMaximumSize().height)));
      // By default, the preferred size is to show the entire image. The object
      // owning this canvas may set a smaller preferred size, for example to
      // ensure the enclosing window fits in the screen.
      setPreferredSize(new Dimension(getMaximumSize()));

      parent_.ij2mmZoomDidChange(getMagnification());
   }

   @Override
   public void setSourceRect(Rectangle rect) {
      // ImageJ's implementation is broken (it will alter magnification)
      srcRect = new Rectangle(rect);
   }

   //
   // Reimplementation of ImageCanvas's zoom API
   //

   @Override
   public void fitToWindow() {
      // ImageJ's implementation of this method makes assumptions about the
      // window containing the canvas. In our case, we only need to fit to the
      // outer component (which should be a panel filling the canvas portion of
      // the window).
      // TODO Implement to set size and srcRect appropriately
      // (For now we don't support this.)
   }

   @Override
   public void setScaleToFit(boolean enable) {
      // Not supported

      // This is supposed to enable a mode in which the zoom automatically
      // tracks the window size so that the whole image remains visible. It
      // would not have made much sense with 1k-by-1k or lower res cameras,
      // but these days it might be worth supporting.
      // If we do support this, we should probably refactor this class so that
      // a pluggable strategy class can be used to handle the zoom-size-srcrect
      // correspondence; otherwise a huge mess will be generated.
   }

   @Override
   public boolean getScaleToFit() {
      return false; // Not supported
   }

   @Override
   public void zoomOut(int centerScreenX, int centerScreenY) {
      // ImageJ's implementation sets the dest size, source rect, and zoom in
      // different orders depending on current state, so cannot be fixed.
      // We implement our own zoom out.
      parent_.mm2ijZoomOut(); // TODO Take into account mouse position
   }

   /**
    * Zooms in by making the window bigger.  If it can't be made bigger, then
    * make the source rectangle smaller and center it on the position in the
    * canvas where the cursor was when zooming started
    * @param centerScreenX x position of the cursor when zooming started
    * @param centerScreenY y position of the cursor when zooming started
    */
   @Override
   public void zoomIn(int centerScreenX, int centerScreenY) {
      // ImageJ's implementation sets the dest size, source rect, and zoom in
      // different orders depending on current state, so cannot be fixed.
      // We implement our own zoom in.
      parent_.mm2ijZoomIn(centerScreenX, centerScreenY); // TODO Take into account mouse position
   }

   @Override
   public void zoom100Percent() {
      // ImageJ's implementation sets the dest size, source rect, and zoom in
      // different orders depending on current state, so cannot be fixed.
      // We implement our own zoom-to-100-percent.
      parent_.mm2ijSetZoom(1.0);
   }

   @Override
   public void unzoom() {
      // It's not clear if this method is relevant to Micro-Manager. Assume the
      // "original" zoom is always 100%.
      zoom100Percent();
   }


   //
   // Custom right-click popup menu
   //

   @Override
   protected void handlePopupMenu(MouseEvent e) {
      // TODO
   }


   //
   // Mouse event handling
   // (All events overrided for documentation, even where we just call super)
   //

   @Override
   public void mouseClicked(MouseEvent e) {
      parent_.ij2mmMouseClicked(e);
      super.mouseClicked(e); // Super delegates to the current tool
   }

   @Override
   public void mousePressed(MouseEvent e) {
      parent_.ij2mmMousePressed(e);
      super.mousePressed(e);
      // Super, roughly,
      // 1) Tries to stop playback animation if tool is not magnifier (but
      // this does not reach us since it goes through ImageWindow), then
      // returns
      // 2) Calls handlePopupMenu if right-clicked
      // 3) If space bar is down, pretend hand tool is selected
      // 4) May take special actions for overlays
      // 5) Starts tool-dependent actions (magnifier, hand, ROI, or custom)
   }

   @Override
   public void mouseReleased(MouseEvent e) {
      parent_.ij2mmMouseReleased(e);
      super.mouseReleased(e);
      // Super, roughly,
      // 1) May take special actions for overlays
      // 2) Allows tool to handle
      // 3) Removes the current ROI if we were constructing an ROI but its
      // bounds are zero
   }

   @Override
   public void mouseEntered(MouseEvent e) {
      parent_.ij2mmMouseEnteredCanvas(e);
      super.mouseEntered(e); // Super delegates to the current tool
   }

   @Override
   public void mouseExited(MouseEvent e) {
      parent_.ij2mmMouseExitedCanvas(e);
      super.mouseExited(e);
      // Super delegates to the current tool, or resets cursor and status
   }

   @Override
   public void mouseDragged(MouseEvent e) {
      parent_.ij2mmMouseDraggedOnCanvas(e);
      super.mouseDragged(e);
      // Super, roughly,
      // 1) Scrolls if current tool is hand
      // 2) Delegates to the current tool
      // 3) Handles dragging of ROI (to move)
   }

   @Override
   public void mouseMoved(MouseEvent e) {
      parent_.ij2mmMouseMovedOnCanvas(e);
      super.mouseMoved(e);
      // Super, roughly,
      // 1) Delegates to the current tool
      // 2) Handles ROI construction
      // 3) Notifies ImageWindow, which notifies ImagePlus, which shows
      // ImageJ status line with location and pixel value
   }
   
   @Override
   public void mouseWheelMoved(MouseWheelEvent e) {
      parent_.ij2mmMouseWheelMoved(e);   
   }

   @Override
   public void repaint() {
      // repaint is the easiest (and only practical) place to detect ImageJ
      // ROI change
      if (parent_ != null) {
         parent_.ij2mmRoiMayHaveChanged();
      }
      super.repaint();
   }

   @Override
   public void repaint(int x, int y, int w, int h) {
      if (parent_ != null) {
         parent_.ij2mmRoiMayHaveChanged();
      }
      super.repaint(x, y, w, h);
   }


}