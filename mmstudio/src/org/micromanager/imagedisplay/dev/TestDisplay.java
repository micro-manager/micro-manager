package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.NewImageEvent;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.MMImagePlus;

/**
 * This class largely serves as a holding area for when we need to create a
 * new display, but our Datastore does not necessarily have any images to
 * show us yet.
 */
public class TestDisplay {
   private Datastore store_;
   private EventBus displayBus_;
   private DefaultDisplayWindow window_;
   
   public TestDisplay(Datastore store) {
      store_ = store;
      store_.registerForEvents(this, 100);
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
      plus.setStack("foo", stack);
      plus.setOpenAsHyperStack(true);
      window_ = new DefaultDisplayWindow(store_, stack, plus, displayBus_);
   }

   /**
    * Datastore has received a new image. We need to create the window if it
    * doesn't already exist.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      if (window_ == null) {
         // Now we have some images with which to set up our display.
         makeWindowAndIJObjects();
      }
      window_.receiveNewImage(event.getImage());
   }
}
