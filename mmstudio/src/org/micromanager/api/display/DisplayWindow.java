package org.micromanager.api.display;

import com.google.common.eventbus.EventBus;

import ij.gui.ImageWindow;
import ij.ImagePlus;

import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.Window;
import java.util.List;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;

/**
 * A DisplayWindow is the interface to Micro-Manager's custom image display
 * windows.
 */
public interface DisplayWindow {
   /**
    * Display the image at the specified coordinates in the Datastore the
    * display is fronting.
    */
   public void setDisplayedImageTo(Coords coords);

   /**
    * Display the specified status string.
    */
   public void displayStatusString(String status);

   /**
    * Set the zoom level for this display. This may result in the canvas
    * changing size, and there is no guarantee that you will get precisely
    * the zoom level you request, as ImageJ quantizes magnification levels.
    */
   public void setMagnification(double magnification);

   /**
    * Retrieve the current zoom level for the display.
    */
   public double getMagnification();

   /**
    * Add an additional "mode button" to the display window, to show/hide
    * the provided Component when clicked.
    */
   public void addControlPanel(String label, Component widget);

   /**
    * Retrieve the Datastore backing this display.
    */
   public Datastore getDatastore();

   /**
    * Retrieve the DisplaySettings for this display.
    */
   public DisplaySettings getDisplaySettings();

   /**
    * Set new DisplaySettings for this display. This will post a
    * NewDisplaySettingsEvent to the display's EventBus.
    */
   public void setDisplaySettings(DisplaySettings settings);

   /**
    * Retrieve the Images currently being displayed (all channels).
    */
   public List<Image> getDisplayedImages();

   /**
    * Publish a RequestToCloseEvent, which may cause any relevant subscribers
    * to call forceClosed(), below. This is the recommended way to close
    * DisplayWindows, so that cleanup logic can be performed (e.g. saving of
    * unsaved data, or stopping acquisitions/live mode). The workflow is:
    * - User clicks close button
    * - This causes requestToClose() to be called
    * - "Owner" of the DisplayWindow determines if window can be closed
    * - Owner calls forceClosed(), below, if appropriate
    *
    * @return True if the window is closed, false if it remains open.
    */
   public boolean requestToClose();

   /**
    * Close the display, bypassing any logic that would normally occur when the
    * window is closed.
    */
   public void forceClosed();

   /**
    * Return true iff the window has been closed.
    */
   public boolean getIsClosed();

   /**
    * Turn fullscreen mode on or off. Note that, because of how Java's GUI
    * systems work, turning on fullscreen mode actually creates a new
    * DisplayWindow that takes over the monitor that this DisplayWindow is in,
    * and turning off fullscreen mode dismisses the new DisplayWindow.
    * TODO: effects of calling this on the wrong DisplayWindow are uncertain.
    */
   public void toggleFullScreen();

   /**
    * Return the GraphicsConfiguration for the monitor the DisplayWindow's
    * upper-left corner is in.
    */
   public GraphicsConfiguration getScreenConfig();

   /**
    * Raise the display to the front.
    */
   public void toFront();

   /**
    * If appropriate, return the ImageWindow that this display represents.
    * Otherwise return null. This is not necessarily the same java.awt.Window
    * that the DisplayWindow represents; MicroManager uses a dummy ImageWindow
    * to simplify communications with ImageJ. You probably should not need
    * to call this method.
    */
   public ImageWindow getImageWindow();

   /**
    * Provide a java.awt.Window representing this display; mostly useful for
    * positioning dialogs and the like.
    */
   public Window getAsWindow();

   /**
    * Register for access to the EventBus that the window uses for propagating
    * events. Note that this is different from the EventBus that the Datastore
    * uses; this EventBus is specifically for events related to the display.
    */
   public void registerForEvents(Object obj);

   /**
    * Unregister for events for this display. See documentation for
    * registerForEvents().
    */
   public void unregisterForEvents(Object obj);

   /**
    * Post the provided event object on the display's EventBus, so that objects
    * that have called registerForEvents(), above, can receive it.
    */
   public void postEvent(Object obj);

   /**
    * Return a reference to the display's EventBus.
    */
   public EventBus getDisplayBus();
}
