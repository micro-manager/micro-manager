package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.awt.Component;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.NewImageEvent;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.MMImagePlus;
import org.micromanager.utils.ReportingUtils;

/**
 * This class exists for one reason only: if we try to create a display (and
 * corresponding ImageJ objects) for a Datastore that does not have any actual
 * images yet, then we get horrific display bugs and exceptions. So this
 * class attaches to the Datastore, waits for an image to arrive, and *then*
 * creates a DefaultDisplayWindow. This does make things tricky for anyone who
 * wants to create and then maintain access to the DisplayWindow; best advice
 * is to make use of Datastore.getDisplays().
 */
public class DisplayStarter {
   private static int titleID = 0;

   private Datastore store_;
   private EventBus displayBus_;
   private DefaultDisplayWindow window_;
   private Component customControls_;

   /**
    * @param customControls Additional controls that will be displayed
    * beneath the scrollbars in the display window. May be null.
    */
   public DisplayStarter(Datastore store, Component customControls) {
      store_ = store;
      store_.registerForEvents(this, 100);
      customControls_ = customControls;
      displayBus_ = new EventBus();
      displayBus_.register(this);
      // Delay generating our UI until we have at least one image, because
      // otherwise ImageJ gets badly confused.
      if (store_.getNumImages() > 0) {
         makeWindowAndIJObjects();
      }
   }

   private void makeWindowAndIJObjects() {
      MMVirtualStack stack = new MMVirtualStack(store_);
      MMImagePlus plus = new MMImagePlus(displayBus_);
      stack.setImagePlus(plus);
      plus.setStack(generateImagePlusName(), stack);
      plus.setOpenAsHyperStack(true);
      window_ = new DefaultDisplayWindow(store_, stack, plus, displayBus_,
            customControls_);
   }

   /**
    * Datastore has received a new image. We need to create the window if it
    * doesn't already exist.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      try {
         if (window_ == null) {
            // Now we have some images with which to set up our display, so
            // we can make it, give it the new image, and then end our
            // responsibilities.
            makeWindowAndIJObjects();
            window_.receiveNewImage(event.getImage());
            store_.unregisterForEvents(this);
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't process new image");
      }
   }

   /**
    * Generate a unique name for our ImagePlus object.
    */
   private static String generateImagePlusName() {
      titleID++;
      return String.format("MM dataset %d", titleID);
   }
}
