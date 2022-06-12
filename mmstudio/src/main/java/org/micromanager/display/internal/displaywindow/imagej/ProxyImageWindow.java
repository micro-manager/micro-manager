///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015; Mark Tsuchida, 2016
//
// COPYRIGHT:    2015 Regents of the University of California
//               2015-2016 Open Imaging, Inc.
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
import ij.gui.StackWindow;

/**
 * A proxy object providing an interface between our image display system
 * and ImageJ's system.
 * <p>
 * ImageJ expects its images to be displayed in {@code ij.gui.ImageWindow} (and
 * its subclass {@code ij.gui.StackWindow}), which descend from
 * {@code java.awt.Frame}. Since MMStudio's image display windows are
 * {@code JFrame}s, it is not practical to get ImageJ to directly interact
 * with them. This class acts as an intermediary; it is never actually
 * displayed on screen and simply forwards the relevant requests from ImageJ
 * to MMStudio's display controller.
 *
 * @author Mark A. Tsuchida, based on earlier version by Chris Weisiger
 */
final class ProxyImageWindow extends StackWindow {
   private final ImageJBridge parent_;

   // Provide access to our parent ImageJBridge while calling StackWindow's
   // constructor.
   //
   // We do not want to actually show this window, since it's sole role is to
   // communicate with ImageJ. We block StackWindow's normal behavior of
   // showing itself in its constructor by overriding setVisible(), etc. and
   // instead showing our own display frame.
   //
   // However, StackWindow also calls getCanvas() from its constructor, when
   // our overrided getCanvas() would not normally have access to parent_
   // (since our part of the constructor hasn't executed yet).
   //
   // Instead, we store the parent ImageJBridge in a static field just for the
   // duration it is needed during construction.
   //
   // Guarded by monitor on ProxyImageWindow.class:
   private static ImageJBridge parentDuringConstruction_;

   /**
    * Create a proxy window to satisfy ImageJ's window management.
    *
    * @param parent the parent {@code ImageJBridge} object, which must be
    *               ready to provide a valid {@code ImagePlus} and {@code ImageCanvas}.
    * @return the new proxy window
    */
   static synchronized ProxyImageWindow create(ImageJBridge parent) {
      parentDuringConstruction_ = parent;
      try {
         return new ProxyImageWindow(parent);
      } finally {
         parentDuringConstruction_ = null;
      }
   }

   private ProxyImageWindow(ImageJBridge parent) {
      super(parent.getIJImagePlus());
      parent_ = parent;
   }

   private ImageJBridge getParentEvenDuringConstruction() {
      if (parent_ == null) {
         // We are still in the StackWindow constructor
         // (and therefore within synchronized (ProxyImageWindow.class))
         return parentDuringConstruction_;
      }
      return parent_;
   }

   // Redirect ImageJ's drawing to the real canvas owned by our parent.
   @Override
   public ImageCanvas getCanvas() {
      ImageCanvas canvas = getParentEvenDuringConstruction().getIJImageCanvas();
      if (canvas == null) {
         throw new NullPointerException(
               "Programming Error: ImageJLink returned null ImageCanvas");
      }
      return canvas;
   }

   // Redirect ImageJ's requests to bring the window forward
   @Override
   public void toFront() {
      getParentEvenDuringConstruction().ij2mmToFront();
   }

   // Redirect ImageJ's requests to show/hide the window
   @Override
   public void setVisible(boolean visible) {
      getParentEvenDuringConstruction().ij2mmSetVisible(visible);
   }

   @Override
   @Deprecated
   public void show() {
      setVisible(true);
   }

   @Override
   @Deprecated
   public void hide() {
      setVisible(false);
   }

   @Override
   public boolean validDimensions() {
      // Make ImageJ think that its image windows always have the correct
      // dimensions. This protects us from certain edge cases
      // (in ImagePlus.setStack()) that can cause ImageJ to decide it needs to
      // create a new StackWindow, which we definitely do not want.
      // Our displays do not need to be recreated when the number of channels,
      // slices, and time points change, so this is the right implementation.
      return true;
   }
}