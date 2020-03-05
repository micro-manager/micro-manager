/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.multiresviewer.api;

import org.micromanager.multiresviewer.DataViewCoords;
import org.micromanager.multiresviewer.overlay.Overlay;

/**
 *
 * @author henrypinkard
 */
public interface OverlayerPlugin {

   public void setShowScaleBar(boolean selected);

   public void shutdown();

   public Overlay createEasyPartsOfOverlay(DataViewCoords view_);

   public void redrawOverlay(DataViewCoords view_);
   
}
