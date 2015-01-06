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
   private ArrayList<SettingsLinker> linkers_;
   private HashMap<DisplayWindow, ArrayList<SettingsLinker>> displayToLinkers_;

   public DisplayGroupManager(MMStudio studio) {
      displayToLinkers_ = new HashMap<DisplayWindow, ArrayList<SettingsLinker>>();
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
      displayToLinkers_.put(event.getDisplayWindow(),
            new ArrayList<SettingsLinker>());
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      displayToLinkers_.remove(event.getDisplay());
   }

   /**
    * A new LinkButton has been created; we need to start tracking its linker.
    */
   @Subscribe
   public void onNewLinkButton(LinkButtonCreatedEvent event) {
      displayToLinkers_.get(event.getButton().getDisplay()).add(event.getLinker());
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
         for (DisplayWindow display : displayToLinkers_.keySet()) {
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

   /**
    * One DisplayWindow has changed settings; check if others care.
    * TODO: it'd be nice if we didn't notify linkers for the DisplayWindow
    * that sourced the event, but currently we have no way of knowing.
    */
   @Subscribe
   public void onDisplaySettingsChange(DisplaySettingsEvent event) {
      for (DisplayWindow display : displayToLinkers_.keySet()) {
         for (SettingsLinker linker : displayToLinkers_.get(display)) {
            List<Class<?>> classes = linker.getRelevantEventClasses();
            for (Class<?> eventClass : classes) {
               if (eventClass.isInstance(event) &&
                     linker.getShouldApplyChanges(event)) {
                  linker.applyChange(event);
               }
            }
         }
      }
   }
}
