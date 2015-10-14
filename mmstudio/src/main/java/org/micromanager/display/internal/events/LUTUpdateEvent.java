///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
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

package org.micromanager.display.internal.events;

/**
 * This event is used to ensure that the different histogram controls apply
 * their LUTs to the image.
 */
public class LUTUpdateEvent {
   // Each of these may be null, which signifies to the recipient that they
   // should use their own values instead.
   private Integer[] mins_;
   private Integer[] maxes_;
   private Double gamma_;

   public LUTUpdateEvent(Integer[] mins, Integer[] maxes, Double gamma) {
      mins_ = mins;
      maxes_ = maxes;
      gamma_ = gamma;
   }

   public Integer[] getMins() {
      return mins_;
   }

   public Integer[] getMaxes() {
      return maxes_;
   }

   public Double getGamma() {
      return gamma_;
   }
}
