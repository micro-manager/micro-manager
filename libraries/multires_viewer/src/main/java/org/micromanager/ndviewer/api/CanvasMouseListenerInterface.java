package org.micromanager.ndviewer.api;

import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;

/**
 * Interface for all three types of mouse listeners in one. A custom object
 * implementing these can be added to the viewer canvas
 * 
 * @author henrypinkard
 */
public interface CanvasMouseListenerInterface extends MouseListener, MouseWheelListener, MouseMotionListener{
   
}
