package org.micromanager.imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.gui.ImageCanvas;
import ij.gui.StackWindow;
import ij.ImagePlus;
import ij.WindowManager;

import java.util.concurrent.locks.ReentrantLock;

import org.micromanager.api.data.Coords;
import org.micromanager.api.display.NewImagePlusEvent;
import org.micromanager.imagedisplay.events.StackPositionChangedEvent;
import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.ReportingUtils;

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
public class DummyImageWindow extends StackWindow {
   private static DefaultDisplayWindow staticMaster_;
   private static ReentrantLock masterLock_ = new ReentrantLock();
   /**
    * We have a problem. StackWindows automatically try to draw themselves,
    * and raise themselves to the front, as soon as they are created. We
    * override getCanvas() and toFront() to prevent this (and to redirect
    * future calls), instead returning our master's canvas and moving our
    * master to the front. However, the first calls to getCanvas() and
    * toFront() happen in the StackWindow constructor, before we have set
    * master_, so these calls will fail.  Instead, we code them to access a
    * static DefaultDisplayWindow instance which we set in this method, with
    * appropriate locks to ensure only one DummyImageWindow can be made at a
    * time. Once the constructor is completed, the static master is no longer
    * needed, since the DummyImageWindow keeps its own copy around.  Naturally,
    * this is a gigantic hack.
    */
   public static DummyImageWindow makeWindow(ImagePlus plus,
         DefaultDisplayWindow master, EventBus displayBus) {
      masterLock_.lock();
      staticMaster_ = master;
      DummyImageWindow result = new DummyImageWindow(plus, master, displayBus);
      masterLock_.unlock();
      return result;
   }

   private DefaultDisplayWindow master_;
   public DummyImageWindow(ImagePlus plus, DefaultDisplayWindow master,
         EventBus displayBus) {
      super(plus);
      imp = plus;
      master_ = master;
      plus.setWindow(this);
      setVisible(false);
      displayBus.register(this);
   }

   /**
    * Instead of drawing to our canvas, draw to our master's.
    */
   public ImageCanvas getCanvas() {
      if (master_ == null) { // I.e. we're still in the StackWindow constructor
         return staticMaster_.getCanvas();
      }
      return master_.getCanvas();
   }

   /** We should never be visible; instead raise our master. This has
    * similar issues as getCanvas(), above.
    */
   @Override
   public void toFront() {
      if (master_ == null) {
         staticMaster_.toFront();
      }
      else {
         master_.toFront();
      }
   }

   @Override
   public void dispose() {
      WindowManager.removeWindow(this);
      super.dispose();
   }

   /**
    * Never display us; instead display our master.
    */
   @Override
   @SuppressWarnings("deprecation")
   public void show() {
      if (master_ == null) {
         staticMaster_.show();
      }
      else {
         master_.show();
      }
   }

   public DefaultDisplayWindow getMaster() {
      return master_;
   }

   /**
    * We need to update our scrollers whenever the stack changes position,
    * so that anything on ImageJ's side that cares about e.g. changing channel
    * can be notified.
    */
   @Subscribe
   public void onStackChanged(StackPositionChangedEvent event) {
      Coords coords = event.getCoords();
      try {
         if (cSelector != null && coords.getPositionAt("channel") != -1) {
            cSelector.setValue(coords.getPositionAt("channel"));
         }
         if (zSelector != null && coords.getPositionAt("z") != -1) {
            zSelector.setValue(coords.getPositionAt("z"));
         }
         if (tSelector != null && coords.getPositionAt("time") != -1) {
            tSelector.setValue(coords.getPositionAt("time"));
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't update Dummy's selectors to " + coords);
      }
   }

   /**
    * The ImagePlus has changed; we need to recreate various links that
    * ImageJ depends on.
    */
   @Subscribe
   public void onNewImagePlus(NewImagePlusEvent event) {
      // Remove, and then later re-add ourselves to the window manager, to
      // force it to refresh its cache of what our ImagePlus object is.
      // Otherwise it won't be able to find us via our ImagePlus later.
      WindowManager.removeWindow(this);
      imp = event.getImagePlus();
      imp.setWindow(this);
      // Ensure nobody's keeping around a reference to our canvas.
      CanvasPaintPending.removeAllPaintPending(ic);
      ic = new ImageCanvas(imp);
      WindowManager.addWindow(this);
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      try {
         dispose();
      }
      catch (NullPointerException e) {
         ReportingUtils.logError(e, "NullPointerException in ImageJ code while disposing of dummy image window");
      }
   }
}
