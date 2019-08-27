/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.imagedisplay;

import javax.swing.JScrollPane;
import org.micromanager.magellan.mmcloneclasses.graph.ContrastPanel;
import org.micromanager.magellan.mmcloneclasses.graph.Histograms;
import org.micromanager.magellan.mmcloneclasses.graph.MultiChannelHistograms;

/**
 *
 * @author henrypinkard
 */
public class ContrastPanelMagellanAdapter extends ContrastPanel {
   
   private MagellanDisplay currentDisplay_;
   private MultiChannelHistograms histograms_;
   
   public ContrastPanelMagellanAdapter() {
      super();
   }
   
   public void initialize(MagellanDisplay display) {
      //setup for use with a single display
      currentDisplay_ = display;
      histograms_ = new MultiChannelHistograms(currentDisplay_, this, display.getDisplaySettings());
      displayChanged(currentDisplay_, histograms_);
      imageChangedUpdate();
   }
   
   public int getNumChannels() {
      return histograms_.getNumChannels();
   }
   
   public JScrollPane getContrastPanelScrollPane() {
      return histDisplayScrollPane_;
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
