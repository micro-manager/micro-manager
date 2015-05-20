///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display;

import com.google.common.eventbus.EventBus;

import ij.gui.ImageWindow;
import ij.ImagePlus;

import java.awt.GraphicsConfiguration;
import java.awt.Window;
import java.util.List;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;

/**
 * A DisplayWindow is the interface to Micro-Manager's custom image display
 * windows.
 */
public interface DisplayWindow {
   /**
    * Display the image at the specified coordinates in the Datastore the
    * display is showing.
    * @param coords The coordinates of the image to be displayed.
    */
   public void setDisplayedImageTo(Coords coords);

   /**
    * Request that the display redraw its current image. You shouldn't need
    * to call this often; most changes that affect the display (for example,
    * changes to the display settings) automatically cause the image to be
    * redrawn.
    */
   public void requestRedraw();

   /**
    * Display the specified status string.
    * @param status String to display in the window.
    */
   public void displayStatusString(String status);

   /**
    * Set the zoom level for this display. This may result in the canvas
    * changing size, and there is no guarantee that you will get precisely
    * the zoom level you request, as ImageJ quantizes magnification levels.
    * @param magnification Magnification level to set.
    */
   public void setMagnification(double magnification);

   /**
    * Multiplay the current zoom level of the image canvas by the provided
    * factor. Thus for example a zoom of 2.0 will double the amount of screen
    * pixels dedicated to each camera pixel.
    * @param factor Amount to adjust zoom by.
    */
   public void adjustZoom(double factor);

   /**
    * Retrieve the current zoom level for the display.
    * @return the current magnification level.
    */
   public double getMagnification();

   /**
    * Retrieve the Datastore backing this display.
    * @return The Datastore backing this display.
    */
   public Datastore getDatastore();

   /**
    * Retrieve the DisplaySettings for this display.
    * @return The DisplaySettings for this display.
    */
   public DisplaySettings getDisplaySettings();

   /**
    * Set new DisplaySettings for this display. This will post a
    * NewDisplaySettingsEvent to the display's EventBus.
    * @param settings The new DisplaySettings to use.
    */
   public void setDisplaySettings(DisplaySettings settings);

   /**
    * Retrieve the Images currently being displayed (all channels).
    * @return Every image at the currently-displayed image coordinates modulo
    *         the channel coordinate (as all channels are represented).
    */
   public List<Image> getDisplayedImages();

   /**
    * Return the ImagePlus used to handle image display logic in ImageJ. This
    * may be a CompositeImage.
    * @return The ImagePlus used by the window.
    */
   public ImagePlus getImagePlus();

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
    * If you are the owner (e.g. you called DisplayManager.createDisplay()),
    * then you should have a class that is subscribed to the display's EventBus
    * and that is listening for the RequestToCloseEvent; otherwise, the
    * display cannot be closed. If you do not want this responsibility, you can
    * request that Micro-Manager manage a Datastore and its associated displays
    * for you via DisplayManager.manage().
    *
    * @return True if the window is closed, false if it remains open.
    */
   public boolean requestToClose();

   /**
    * Close the display, bypassing any logic that would normally occur when the
    * window is closed. Typically this is called by the "owner" of the display
    * after a RequestToCloseEvent has been posted and the owner has determined
    * that it is okay to remove the display.
    */
   public void forceClosed();

   /**
    * Return true iff the window has been closed.
    * @return whether the display has been closed.
    */
   public boolean getIsClosed();

   /**
    * Turn fullscreen mode on or off. Note that, because of how Java's GUI
    * systems work, turning on fullscreen mode actually creates a new
    * JFrame that takes over the monitor that this DisplayWindow is in,
    * and turning off fullscreen mode dismisses that JFrame.
    */
   public void toggleFullScreen();

   /**
    * Return the GraphicsConfiguration for the monitor the DisplayWindow's
    * upper-left corner is in.
    * @return the GraphicsConfiguration of the monitor this window is in.
    */
   public GraphicsConfiguration getScreenConfig();

   /**
    * Create a new DisplayWindow for the same Datastore as this DisplayWindow.
    * @return The newly-created DisplayWindow.
    */
   public DisplayWindow duplicate();

   /**
    * Raise the display to the front.
    */
   public void toFront();

   /**
    * If appropriate, return the ImageJ ImageWindow that this display
    * represents.  Otherwise return null. This is not necessarily the same
    * java.awt.Window that the DisplayWindow represents; MicroManager uses a
    * dummy ImageWindow to simplify communications with ImageJ. You probably
    * should not need to call this method.
    * @return an ImageWindow corresponding to this DisplayWindow.
    */
   public ImageWindow getImageWindow();

   /**
    * Provide a java.awt.Window representing this display; mostly useful for
    * positioning dialogs or if you need to move a display window around.
    * @return the DisplayWindow as a Window.
    */
   public Window getAsWindow();

   /**
    * Return the unique name of this display. Typically this will include the
    * display number and the name of the dataset; if no name is available, then
    * "MM image display" will be used instead. This string is displayed in
    * the inspector window, ImageJ's "Windows" menu, and a few other places.
    * @return a string labeling this window.
    */
   public String getName();

   /**
    * Add a custom extra string to the title of this display. The usual format
    * for the window title is "#num: name (magnification) (saved status)".
    * If you call this method, then the name will be replaced by your custom
    * string. If there is no name and this method has not been called, then "MM
    * image display" is used instead. You can call this method with a null
    * argument to revert to the default title.
    * @param title Custom title component to insert into the window's title.
    */
   public void setCustomTitle(String title);

   /**
    * Register for access to the EventBus that the window uses for propagating
    * events. Note that this is different from the EventBus that the Datastore
    * uses; this EventBus is specifically for events related to the display.
    * @param obj The object that wants to subscribe for events.
    */
   public void registerForEvents(Object obj);

   /**
    * Unregister for events for this display. See documentation for
    * registerForEvents().
    * @param obj The object that wants to no longer be subscribed for events.
    */
   public void unregisterForEvents(Object obj);

   /**
    * Post the provided event object on the display's EventBus, so that objects
    * that have called registerForEvents(), above, can receive it.
    * @param obj The event to post.
    */
   public void postEvent(Object obj);

   /**
    * Return a reference to the display's EventBus.
    * @return The display's EventBus.
    */
   public EventBus getDisplayBus();
}
