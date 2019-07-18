/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.imagedisplay;

import java.awt.Graphics;
import org.micromanager.magellan.mmcloneclasses.graph.ContrastPanel;
import org.micromanager.magellan.mmcloneclasses.graph.Histograms;
import org.micromanager.magellan.mmcloneclasses.graph.MultiChannelHistograms;
import org.micromanager.magellan.mmcloneclasses.graph.SingleChannelHistogram;

/**
 *
 * @author henrypinkard
 */
public class ContrastPanelMagellanAdapter extends ContrastPanel {
   
   private VirtualAcquisitionDisplay currentDisplay_;
   private Histograms histograms_;
   
   public ContrastPanelMagellanAdapter() {
      super();
   }
   
   public void initialize(DisplayPlus display) {
      //setup for use with a single display
      currentDisplay_ = display;
      if (currentDisplay_.getNumChannels() == 1) {
         histograms_ = new SingleChannelHistogram(currentDisplay_, this);
      } else {
         histograms_ = new MultiChannelHistograms(currentDisplay_, this);
      }
      displayChanged(currentDisplay_, histograms_);
      imageChangedUpdate();
   }
   
   public void prepareForClose() {
      histograms_.prepareForClose();
   }
   
   public Histograms getHistograms() {
      return histograms_;
   }

   /*
    * called just before image is redrawn.  Calcs histogram and stats (and displays
    * if image is in active window), applies LUT to image.  Does NOT explicitly
    * call draw because this function should be only be called just before 
    * ImagePlus.draw or CompositieImage.draw runs as a result of the overriden 
    * methods in MMCompositeImage and MMImagePlus
    * We postpone metadata display updates slightly in case the image display
    * is changing rapidly, to ensure that we don't end up with a race condition
    * that causes us to display the wrong metadata.
    */
   public void imageChangedUpdate() { 
     this.imageChanged();
   }
   
   
   
}
