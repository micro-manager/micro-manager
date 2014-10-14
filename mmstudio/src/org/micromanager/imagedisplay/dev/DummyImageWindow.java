package org.micromanager.imagedisplay.dev;

import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.ImagePlus;
import ij.WindowManager;

/**
 * This class exists to provide an interface between our image display system
 * and ImageJ's system. ImageJ has methods for tracking which windows are open,
 * but they only work with its ImageWindow and StackWindow objects. We used to
 * implement our display windows as subclasses of ImageWindow, but that became
 * increasingly problematic as we shifted more towards using Swing (as
 * ImageWindows are descended from Frame, not JFrame). Wrestling with layout
 * errors, display bugs, etc. became increasingly burdensome. Nowadays we
 * create one of these DummyImageWindows and stash it out of the way, while our
 * own entirely-custom display window handles the actual display logic. The
 * DummyImageWindow mostly redirects ImageJ queries to the actual window.
 */
public class DummyImageWindow extends ImageWindow {
   private DefaultDisplayWindow master_;
   public DummyImageWindow(ImagePlus plus, DefaultDisplayWindow master) {
      // Use the constructor that only provides a title, to avoid flashing
      // up a new window and then having it immediately disappear (as all
      // other available constructors do).
      super(master.getTitle());
      imp = plus;
      master_ = master;
      plus.setWindow(this);
      setVisible(false);
      WindowManager.addWindow(this);
   }

   public ImageCanvas getCanvas() {
      return master_.getCanvas();
   }

   // We should never be visible; instead raise our master.
   @Override
   public void toFront() {
      master_.toFront();
   }

   public DefaultDisplayWindow getMaster() {
      return master_;
   }
}
