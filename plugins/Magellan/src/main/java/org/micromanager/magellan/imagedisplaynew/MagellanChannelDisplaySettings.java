/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.imagedisplaynew;

import java.awt.Color;

 public class MagellanChannelDisplaySettings {
   
   public int contrastMin=0, contrastMax=65535;
   public double gamma = 1.0;
   public Color color = Color.white;
   public boolean active;
   
   public MagellanChannelDisplaySettings() {
      active = true;
      color = Color.white;
      gamma = 1.0;
      contrastMin = 0;
      contrastMax = 65535;
   }
   
   public MagellanChannelDisplaySettings(boolean a, Color c, double g, int minn, int maxx) {
      active = a;
      color = c;
      gamma = g;
      contrastMin = minn;
      contrastMax = maxx;
   }
   

}
