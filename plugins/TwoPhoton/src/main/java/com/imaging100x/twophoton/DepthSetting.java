//
// Two-photon plugin module for micro-manager
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//                
// AUTHOR:        Nenad Amodaj

package com.imaging100x.twophoton;

public class DepthSetting implements Comparable<DepthSetting> {
   public double eomVolts1_;
   public double eomVolts2_;
   public PMTSetting pmts[];
   public double deltaZ;
   public double z;
   
   public DepthSetting() {
      eomVolts1_ = 0.0;
      eomVolts2_ = 0.0;
      deltaZ = 0.0;
      z = 0.0;
      pmts = new PMTSetting[0]; 
   }
   
   public void resizePMT(int size) {
      pmts = new PMTSetting[size];
      for (int i=0; i<size; i++)
         pmts[i] = new PMTSetting();
   }

   public int compareTo(DepthSetting ds) {
      if (ds.z > z)
         return 1;
      else if (ds.z < z)
         return -1;
         
      return 0;
   }

}
