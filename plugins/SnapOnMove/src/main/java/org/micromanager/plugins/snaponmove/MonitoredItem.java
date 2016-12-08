// Snap-on-Move Preview for Micro-Manager
//
// Author: Mark A. Tsuchida
//
// Copyright (C) 2016 Open Imaging, Inc.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation
// and/or other materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its
// contributors may be used to endorse or promote products derived from this
// software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package org.micromanager.plugins.snaponmove;

import java.awt.geom.Point2D;
import mmcorej.CMMCore;
import org.micromanager.plugins.snaponmove.MonitoredValue.MonitoredFloatValue;
import org.micromanager.plugins.snaponmove.MonitoredValue.MonitoredXYValue;

/**
 * Immutable descriptor of an item to be monitored.
 */
abstract class MonitoredItem {
   private final String deviceLabel_;

   class DeviceError extends Exception {
      public DeviceError(String msg) {
         super(msg);
      }
   }

   private MonitoredItem(String deviceLabel) {
      deviceLabel_ = deviceLabel;
   }
   
   String getDeviceLabel() {
      return deviceLabel_;
   }

   /**
    * Return a string uniquely representing the item.
    *
    * (This string representation is used for equality and hash computation.)
    *
    * @return string representation of the item
    */
   @Override
   public String toString() {
      return deviceLabel_;
   }

   @Override
   public boolean equals(Object other) {
      if (other == null) {
         return false;
      }
      if (!(other instanceof MonitoredItem)) {
         return false;
      }
      return toString().equals(other.toString());
   }

   @Override
   public int hashCode() {
      return toString().hashCode();
   }

   /**
    * Poll the hardware for the current value of this item.
    * @param core the Core instance
    * @return the retrieved value
    */
   abstract MonitoredValue poll(CMMCore core) throws DeviceError;

   static MonitoredItem createZItem(String label) {
      return new ZMonitoredItem(label);
   }

   static MonitoredItem createXYItem(String label) {
      return new XYMonitoredItem(label);
   }

   static class ZMonitoredItem extends MonitoredItem {
      private ZMonitoredItem(String label) {
         super(label);
      }

      @Override
      public String toString() {
         return super.toString() + ",,ZPOS";
      }

      @Override
      public MonitoredFloatValue poll(CMMCore core) throws DeviceError {
         try {
            return new MonitoredFloatValue(core.getPosition(getDeviceLabel()));
         }
         catch (Exception deviceError) {
            throw new DeviceError(deviceError.getMessage());
         }
      }
   }

   static class XYMonitoredItem extends MonitoredItem {
      private XYMonitoredItem(String label) {
         super(label);
      }

      @Override
      public String toString() {
         return super.toString() + ",,XYPOS";
      }

      @Override
      public MonitoredXYValue poll(CMMCore core) throws DeviceError {
         try {
            Point2D.Double xy = core.getXYStagePosition(getDeviceLabel());
            return new MonitoredXYValue(xy.x, xy.y);
         }
         catch (Exception deviceError) {
            throw new DeviceError(deviceError.getMessage());
         }
      }
   }
}
