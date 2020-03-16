/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.api;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.util.HashMap;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.overlay.Overlay;

/**
 * Interface for a plugin to draw customized overlays on the image window.
 * Register it using the setOverlayPlugin method in {@link NDViewer}
 */
public interface OverlayerPlugin {

   /**
    * Called whenever the overlay needs to be drawn. Once it is ready, it should
    * be passed to the viewer by calling {@link NDViewer.setOverlay}
    * 
    * @param defaultOverlay The base overlay which should be added onto
    * @param displayImageSize 
    * @param downsampleFactor
    * @param g
    * @param axes
    * @param magnification
    * @param viewOffset
    * @throws InterruptedException 
    */
   public void drawOverlay(Overlay defaultOverlay, Point2D.Double displayImageSize, 
           double downsampleFactor, Graphics g, HashMap<String, Integer> axes, 
           double magnification, Point2D.Double viewOffset) throws InterruptedException;
   
   
   
}
