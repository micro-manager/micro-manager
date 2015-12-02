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
 * windows. It is not expected that third-party code implement this interface.
 * If you want to provide your own custom display then you should implement
 * the org.micromanager.display.DataViewer interface instead.
 */
public interface DisplayWindow extends DataViewer {

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
    * Cause the display to autostretch each of its displayed channels, as if
    * the "auto once" button had been clicked in the Image Inspector Window.
    */
   public void autostretch();

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
    * Add a custom extra string to the title of this display. The usual format
    * for the window title is "#num: name (magnification) (saved status)".
    * If you call this method, then the name will be replaced by your custom
    * string. If there is no name and this method has not been called, then "MM
    * image display" is used instead. You can call this method with a null
    * argument to revert to the default title.
    * @param title Custom title component to insert into the window's title.
    */
   public void setCustomTitle(String title);
}
