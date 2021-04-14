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

import ij.ImagePlus;
import java.awt.Window;
import java.io.Closeable;
import java.util.List;
import org.micromanager.display.overlay.Overlay;


/**
 * A DisplayWindow is the interface to Micro-Manager's image display windows.
 *
 * It is not expected that third-party code implement this interface. If you
 * want to provide your own custom display then you should implement {@link
 * org.micromanager.display.DataViewer} instead.
 */
public interface DisplayWindow extends DataViewer, Closeable {
   /**
    * Display a custom string in the window.
    *
    * The string will remain displayed until this method is called again to
    * change it.
    *
    * Calls are ignored if the display has closed.
    *
    * This method can be called from any thread.
    *
    * @param status String to display in the window.
    */
   void displayStatusString(String status);

   /**
    * Get the current zoom ratio of the display.
    *
    * If the display has closed, the returned value is undefined.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    *
    * @return the current zoom ratio (1.0 = actual size)
    */
   double getZoom();

   /**
    * Obsolete equivalent of {@link #getZoom()}.
    *
    * @return the current zoom level
    * @deprecated use {@link #getZoom()} instead
    */
   @Deprecated
   double getMagnification();

   /**
    * Set the zoom ratio for this display.
    *
    * Calls are ignored if the display has closed.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    *
    * @param ratio zoom ratio to set (1.0 = actual size)
    */
   void setZoom(double ratio);

   /**
    * Obsolete equivalent of {@link #setZoom}.
    *
    * @param ratio
    * @deprecated use {@link #setZoom} instead
    */
   @Deprecated
   void setMagnification(double ratio);

   /**
    * Multiply the current zoom ratio of the image canvas by the provided
    * factor.
    *
    * This is equivalent to {@code setZoom(factor * getZoom())} if called on
    * the Swing/AWT event dispatch thread.
    *
    * Calls are ignored if the display has closed.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    *
    * @param factor the factor to zoom in by
    */
   void adjustZoom(double factor);

   /**
    * Perform a one-shot autoscaling of intensities.
    *
    * The intensity range of the currently displayed image is used to set the
    * intensity scaling range for all displayed channels. If continuous
    * autostretching was enabled, it is disabled.
    *
    * Calls are ignored if the display has closed.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    */
   void autostretch();

   /**
    * Add a graphical overlay.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    *
    * @param overlay the overlay to add
    * @throws NullPointerException if {@code overlay} is null
    * @see #removeOverlay
    * @see #getOverlays
    */
   void addOverlay(Overlay overlay);

   /**
    * Remove a graphical overlay.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    *
    * @param overlay the overlay to remove
    * @see #addOverlay
    */
   void removeOverlay(Overlay overlay);

   /**
    * Get the overlays attached to this display.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    *
    * @return the overlays
    * @see #addOverlay
    */
   List<Overlay> getOverlays();

   /**
    * Return the ImageJ {@code ImagePlus} object used by the display window.
    *
    * Note that the {@code ImagePlus} may be replaced with a new one during the
    * lifetime of the display window.
    *
    * It is recommended that this method be called on the Swing/AWT event
    * dispatch thread. If you call it from another thread, make sure that your
    * thread is not obstructing the event dispatch thread (i.e. the event
    * dispatch thread is not waiting for your thread to finish something).
    *
    * @return the {@code ImagePlus} used by the window, which may be a
    * {@code CompositeImage}, or {@code null} if none has been created or if
    * the display has closed.
    *
    * @deprecated Directly accessing the {@code ImagePlus} of an MMStudio
    * display window will generally result in very fragile code. Consider
    * accessing image data through {@link #getDataProvider}. For drawing
    * overlay graphics, see {@link #addOverlay}.
    */
   @Deprecated
   ImagePlus getImagePlus();

   /**
    * Close this display window, giving its owner a chance to cancel closing.
    *
    * For example, the owner may prompt the user to save the data associated
    * with this display. Special display windows may perform other actions.
    *
    * Calls are ignored if the display has already closed.
    *
    * @return true if the display is closed, or was already closed; false
    * if it remains open
    */
   boolean requestToClose();

   /**
    * Close this display window unconditionally.
    *
    * This will skip such actions as prompting the user to save the data.
    * Special display windows may skip other special actions.
    *
    * Calls are ignored if the display has already closed.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes. In particular, the display
    * may not have closed yet at the moment this method returns if called on
    * another thread.
    */
   @Override
   void close();

   /**
    * Enable or disable full screen mode.
    *
    * Calls are ignored if the display has closed.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    *
    * @param enable whether to enable or disable full screen mode
    */
   void setFullScreen(boolean enable);

   /**
    * Tell whether full screen mode is currently enabled.
    *
    * It is recommended that this method be called on the Swing/AWT event
    * dispatch thread. If you call it from another thread, make sure that your
    * thread is not obstructing the event dispatch thread (i.e. the event
    * dispatch thread is not waiting for your thread to finish something).
    *
    * @return whether full screen mode is enabled ({@code false} if the
    * display has closed
    */
   boolean isFullScreen();

   /**
    * Enable or disable full screen mode.
    *
    * Calls are ignored if the display has closed.
    *
    * @deprecated use {@link #setFullScreen} instead
    */
   @Deprecated
   void toggleFullScreen();

   /**
    * Create a new display window for the same data provider as this one.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    *
    * @return the new display window
    */
   DisplayWindow duplicate();

   /**
    * Raise this display window to the front.
    *
    * Calls are ignored if the display has closed.
    *
    * This method can be called from any thread. However, you should call it
    * from the Swing/AWT event dispatch thread if you want to correctly
    * synchronize with other user interface changes.
    */
   void toFront();

   /**
    * Get the {@code java.awt.Window} used for this image display.
    *
    * You should not access the contents of the returned window, but you can
    * set its on-screen position and size, etc.
    *
    * If the display is in full screen mode, this method returns the original
    * (hidden) window, not the window used for full-screen display.
    *
    * It is recommended that this method be called on the Swing/AWT event
    * dispatch thread. If you call it from another thread, make sure that your
    * thread is not obstructing the event dispatch thread (i.e. the event
    * dispatch thread is not waiting for your thread to finish something).
    *
    * @return the {@code java.awt.Window} used by this image display
    * @throws IllegalStateException if the display has closed
    */
   Window getWindow() throws IllegalStateException;

   /**
    * Get the {@code java.awt.Window} used for this image display.
    *
    * @return the {@code java.awt.Window} used by this image display, or
    * {@code null} if the display has been closed
    *
    * @deprecated use {@link #getWindow} instead
    */
   @Deprecated
   Window getAsWindow();

   /**
    * Add a custom extra string to the title of this display.
    *
    * The usual format for the window title is
    * "#number: name (zoom ratio) (saved status)".
    * If you call this method, then the name part will be replaced by your
    * custom title. You can call this method with a null argument to revert to
    * the default title, which is what {@link #getName} returns.
    *
    * Calls are ignored if the display has closed.
    *
    * This method can be called from any thread.
    *
    * @param title a custom window title, or {@code null} to revert to the
    * default
    */
   void setCustomTitle(String title);

   /**
    * DisplayWindows are not shown by default.  Call this function after
    * construction, and after attaching listeners as needed, to show the Window.
    */
   void show();
}