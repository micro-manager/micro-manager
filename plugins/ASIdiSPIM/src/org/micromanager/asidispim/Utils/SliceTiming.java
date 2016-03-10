///////////////////////////////////////////////////////////////////////////////
//FILE:          SliceTiming.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2014
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

package org.micromanager.asidispim.Utils;


/**
 * Associative container or "plain old data structure" for slice timing information.
 * Public elements so they can be get/set directly, like C/C++ struct
 * Note that this container doesn't work with collections (https://www.artima.com/lejava/articles/equality.html)
 * @author Jon
 *
 */
public class SliceTiming {
   public float scanDelay;
   public int scanNum;
   public float scanPeriod;
   public float laserDelay;
   public float laserDuration;
   public float cameraDelay;
   public float cameraDuration;
   public float cameraExposure;  // used to set exposure in Micro-Manager, not the controller timing
   public float sliceDuration;   // depends on first 7 values by formula, up to users to keep updated

   /**
    * Chooses some reasonable defaults (may not be controller defaults).
    */
   public SliceTiming() {
      scanDelay = 0;
      scanNum = 1;
      scanPeriod = 10;
      laserDelay = 0;
      laserDuration = 1;
      cameraDelay = 0;
      cameraDuration = 1;
      cameraExposure = 1;
      sliceDuration = 2;
   }
   
   /**
    * copy constructor (unused?)
    */
   public SliceTiming(SliceTiming orig) {
      scanDelay = orig.scanDelay;
      scanNum = orig.scanNum;
      scanPeriod = orig.scanPeriod;
      laserDelay = orig.laserDelay;
      laserDuration = orig.laserDuration;
      cameraDelay = orig.cameraDelay;
      cameraDuration = orig.cameraDuration;
      cameraExposure = orig.cameraExposure;
      sliceDuration = orig.sliceDuration;
   }
   
   @Override
   public boolean equals(Object obj) {
      if ((obj instanceof SliceTiming)) {
         SliceTiming s = (SliceTiming) obj;
         return(scanDelay == s.scanDelay
               && scanNum == s.scanNum
               && scanPeriod == s.scanPeriod
               && laserDelay == s.laserDelay
               && laserDuration == s.laserDuration
               && cameraDelay == s.cameraDelay
               && cameraDuration == s.cameraDuration
               && cameraExposure == s.cameraExposure
               && sliceDuration == s.sliceDuration);
      } else {
         return false;
      }
      
      
   }

}
