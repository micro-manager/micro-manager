// Copyright (C) 2020 Contributors to the Micro-Manager project
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
package org.micromanager.display.internal.displaywindow.interfaces;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JFrame;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.display.internal.displaywindow.imagej.MMImageCanvas;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.internal.utils.MustCallOnEDT;

/**
 *
 * @author Nick Anthony (nickmanthony at hotmail.com)
 */
public interface ImageJBridgeParent {
    void canvasDidChangeSize();

    void canvasNeedsSwap();
    
    DisplayController getDisplayController();
    
    int getDisplayedAxisLength(String axis);

    List<Image> getDisplayedImages();
    
    /**
     * Not ideal, but the Image Exporter needs access to the canvas to
     * grab images
     *
     * @return
     */
    MMImageCanvas getIJImageCanvas();
    
    int getImageHeight();

    int getImageWidth();
    
    /**
     * Return the coords of selected channel among the displayed images.
     * @return coords of the selected channel
     */
    Coords getMMPrincipalDisplayedCoords();
    
    boolean isAxisDisplayed(String axis);
    
    /**
     * Notify the UI controller that a mouse event occurred on the image canvas.
     *
     * If {@code imageLocation} is null or empty, the indicator is hidden. The
     * {@code imageLocation} parameter can be a rectangle containing more than
     * one pixel, for example if the point comes from a zoomed-out canvas.
     *
     * @param e MouseEvent that occurred on the Canvas. Use its getId() function
     * to discover what kind of Mouse Event happened.
     * @param imageLocation the image coordinates of the pixel for which
     * information should be displayed (in image coordinates)
     * @param ijToolId ID of tool selected in ImageJ tool-bar
     */
    void mouseEventOnImage(final MouseEvent e, final Rectangle imageLocation, final int ijToolId);

    void mouseWheelMoved(MouseWheelEvent e);
    
    /**
     * a key was pressed on the display Canvas.  Post as an event.
     * Let the caller know if any of the event handlers consumed the keypress
     *
     * @param e KeyEvent generated by the key press on our canvas
     * @return true if action was taken by one of the handlers, false otherwise
     */
    boolean keyPressOnImageConsumed(KeyEvent e);
    
    void paintDidFinish();

    void paintOverlays(Graphics2D g, Rectangle destRect, Rectangle2D.Float viewPort);

    void selectionMayHaveChanged(final BoundsRectAndMask selection);
    
    /**
     * Callback for the ImageJ code.  Do not call directly.
     * Used to update the Micro-Manager code of the new zoom factor.
     *
     * @param factor Newly set Zoom factor.
     */
    void uiDidSetZoom(double factor);
    
    @MustCallOnEDT
    JFrame getFrame();
    
    @MustCallOnEDT
    void setVisible(boolean visible);
    
    @MustCallOnEDT
    void toFront();
}

