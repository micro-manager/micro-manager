package org.micromanager.imagedisplay.link;

import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.micromanager.api.display.DisplayManager;
import org.micromanager.api.display.DisplayWindow;
import org.micromanager.api.events.NewDisplayEvent;

import org.micromanager.imagedisplay.DisplayDestroyedEvent;

import org.micromanager.MMStudio;

import org.micromanager.utils.ReportingUtils;

/**
 * This class is responsible for tracking groupings of DisplayWindows and
 * handling synchronization of linked DisplaySettings across them.
 */
public class DisplayGroupManager {
   private MMStudio studio_;
   private ArrayList<DisplayWindow> displays_;

   public DisplayGroupManager(MMStudio studio) {
      displays_ = new ArrayList<DisplayWindow>();
      studio_ = studio;
      // So we can be notified of newly-created displays.
      studio_.registerForEvents(this);
   }

   /**
    * A new display has arrived; register for its events.
    */
   @Subscribe
   public void onNewDisplayEvent(NewDisplayEvent event) {
      event.getDisplayWindow().registerForEvents(this);
      displays_.add(event.getDisplayWindow());
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      displays_.remove(event.getDisplay());
   }

   /**
    * A LinkButton has been clicked. Ensure other DisplayWindows get their
    * corresponding LinkButtons updated.
    */
   @Subscribe
   public void onLinkButtonEvent(LinkButtonEvent event) {
      try {
         // Find other displays for this datastore and update their
         // corresponding link buttons.
         DisplayWindow sourceDisplay = event.getDisplay();
         RemoteLinkEvent notifyEvent = new RemoteLinkEvent(event.getLinker(),
               event.getIsLinked());
         for (DisplayWindow display : displays_) {
            if (display == sourceDisplay ||
                  display.getDatastore() != sourceDisplay.getDatastore()) {
               // No need to notify this one.
               continue;
            }
            display.postEvent(notifyEvent);
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't redistribute LinkButtonEvent");
      }
   }
}
