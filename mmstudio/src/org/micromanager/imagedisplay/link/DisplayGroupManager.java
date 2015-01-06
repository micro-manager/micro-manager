package org.micromanager.imagedisplay.link;

import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;

import org.micromanager.api.display.DisplayWindow;
import org.micromanager.api.events.NewDisplayEvent;

import org.micromanager.MMStudio;

import org.micromanager.utils.ReportingUtils;

/**
 * This class is responsible for tracking groupings of DisplayWindows and
 * handling synchronization of linked DisplaySettings across them.
 */
public class DisplayGroupManager {
   /**
    * This maps the Class of a DisplaySettingsEvent to the list of
    * DisplayWindows that currently care about that event.
    */
   private HashMap<Class<?>, ArrayList<DisplayWindow>> classToDisplays_;

   public DisplayGroupManager(MMStudio studio) {
      studio.registerForEvents(this);
   }

   /**
    * A new display has arrived; register for its events.
    */
   @Subscribe
   public void onNewDisplayEvent(NewDisplayEvent event) {
      event.getDisplayWindow().registerForEvents(this);
   }

   /**
    * A LinkButton has been clicked.
    */
   @Subscribe
   public void onLinkButtonEvent(LinkButtonEvent event) {
      ReportingUtils.logError("Link button clicked: " + event.getSettingsLinker() + ", " + event.getDisplay() + ", " + event.getIsLinked());
   }
}
