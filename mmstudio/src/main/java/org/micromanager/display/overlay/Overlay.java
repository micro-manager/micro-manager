///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Display API
// -----------------------------------------------------------------------------
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

package org.micromanager.display.overlay;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JComponent;
import org.micromanager.PropertyMap;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;

/**
 * A graphic object displayed over images.
 *
 * <p>To implement a new overlay, you should extend {@link AbstractOverlay} rather than directly
 * implementing this interface.
 *
 * <p>Overlays are not thread safe. In a GUI, all methods must be called on the Swing/AWT event
 * dispatch thread. (This means that Overlay implementations do not need to worry about threading.)
 *
 * @see OverlayPlugin
 * @author Mark A. Tsuchida, based on earlier version by Chris Weisiger
 */
public interface Overlay {
  /**
   * Return a human-readable name for this overlay.
   *
   * @return the overlay title
   */
  String getTitle();

  /**
   * Paint the overlay to the given graphics context.
   *
   * @param graphicsContext the graphics context to paint with
   * @param screenRect the screen region displaying part or all of the image, in the graphics
   *     context's coordinates
   * @param displaySettings the current display settings of the display window
   * @param images the images on which the overlay is to be drawn (multiple images if in composite
   *     color mode)
   * @param primaryImage the currently selected image (if in composite color mode) or the single
   *     displayed image (otherwise)
   * @param imageViewPort the visible region of the image, in image coordinates
   */
  void paintOverlay(
      Graphics2D graphicsContext,
      Rectangle screenRect,
      DisplaySettings displaySettings,
      List<Image> images,
      Image primaryImage,
      Rectangle2D.Float imageViewPort);

  /**
   * Return the configuration UI component for this overlay.
   *
   * <p>The component should work well when laid out with a fixed width and height, determined based
   * on its minimum and maximum sizes. The width of the component may be changed in response to
   * layout changes, but the component must not actively resize itself. It is recommended that the
   * preferred width be no more than 480 pixels.
   *
   * <p>This method should return the same object every time it is called, throughout the lifetime
   * of this Overlay instance. If the overlay does not require any user configuration, {@code null}
   * may be returned.
   *
   * <p><strong>Important</strong>: The Overlay must not create any UI objects unless this method is
   * called, and still support all other methods. This is to support headless use of overlays.
   *
   * @return the configuration UI component, or null
   */
  JComponent getConfigurationComponent();

  /**
   * Return the user-customizable settings for this overlay.
   *
   * <p>The configuration should contain all parameters needed to reproduce the same overlay on
   * another display window.
   *
   * @return a property map containing current parameters of the overlay
   */
  PropertyMap getConfiguration();

  /**
   * Configure the overlay with the given parameters.
   *
   * @param config a property map previously returned by {@code getConfiguration} of an overlay of
   *     the same class.
   */
  void setConfiguration(PropertyMap config);

  /**
   * Return whether this overlay is shown.
   *
   * <p>Note: {@link AbstractOverlay} implements this method.
   *
   * @return true if overlay is visible
   */
  boolean isVisible();

  /**
   * Show or hide this overlay.
   *
   * <p>Note: {@link AbstractOverlay} implements this method.
   *
   * @param visible whether to show the overlay
   */
  void setVisible(boolean visible);

  /**
   * Add a listener.
   *
   * <p>Note: {@link AbstractOverlay} implements this method.
   *
   * @param listener the listener to add
   */
  void addOverlayListener(OverlayListener listener);

  /**
   * Remove a listener.
   *
   * <p>Note: {@link AbstractOverlay} implements this method.
   *
   * @param listener the listener to remove
   */
  void removeOverlayListener(OverlayListener listener);
}
