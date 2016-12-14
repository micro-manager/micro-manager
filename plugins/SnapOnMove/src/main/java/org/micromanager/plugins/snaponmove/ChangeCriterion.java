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

import org.micromanager.PropertyMap;
import org.micromanager.PropertyMap.PropertyMapBuilder;
import org.micromanager.plugins.snaponmove.MonitoredItem.XYMonitoredItem;
import org.micromanager.plugins.snaponmove.MonitoredItem.ZMonitoredItem;
import org.micromanager.plugins.snaponmove.MonitoredValue.MonitoredFloatValue;
import org.micromanager.plugins.snaponmove.MonitoredValue.MonitoredXYValue;

/**
 * Criterion for detecting a value change for a monitored item.
 *
 * Instances specify the item to be monitored and when to consider its value
 * to have changed.
 */
abstract class ChangeCriterion {
   private final boolean requiresPolling_;

   protected ChangeCriterion(boolean requiresPolling) {
      requiresPolling_ = requiresPolling;
   }

   static ChangeCriterion createZDistanceCriterion(String stageLabel, double threshold, boolean requiresPolling) {
      return new ZDistanceCriterion(stageLabel, threshold, requiresPolling);
   }

   static ChangeCriterion createXYDistanceCriterion(String stageLabel, double threshold, boolean requiresPolling) {
      return new XYDistanceCriterion(stageLabel, threshold, requiresPolling);
   }

   boolean requiresPolling() {
      return requiresPolling_;
   }

   abstract MonitoredItem getMonitoredItem();
   abstract boolean testForChange(MonitoredValue oldValue, MonitoredValue newValue);

   private static final String SER_CLASS = "Class";
   private static final String SER_REQUIRES_POLLING = "Requires poling";
   private static final String SER_MONITORED_ITEM = "Monitored item";
   private static final String SER_THRESHOLD = "Threshold";

   void serialize(PropertyMapBuilder pmb) {
      pmb.putString(SER_CLASS, this.getClass().getSimpleName());
      pmb.putBoolean(SER_REQUIRES_POLLING, requiresPolling_);
   }

   static ChangeCriterion deserialize(PropertyMap pm) {
      String theClass = pm.getString(SER_CLASS, null);
      String itemStr = pm.getString(SER_MONITORED_ITEM, null);
      if (theClass == null || itemStr == null) {
         return null;
      }
      MonitoredItem item = MonitoredItem.fromString(itemStr);
      if (item == null || item.getDeviceLabel() == null) {
         return null;
      }

      if (theClass.equals("ZDistanceCriterion")) {
         if (!(item instanceof ZMonitoredItem)) {
            return null; // inconsistent
         }
         return createZDistanceCriterion(item.getDeviceLabel(),
               pm.getDouble(SER_THRESHOLD, 0.1),
               pm.getBoolean(SER_REQUIRES_POLLING, false));
      }
      else if (theClass.equals("XYDistanceCriterion")) {
         if (!(item instanceof XYMonitoredItem)) {
            return null;
         }
         return createXYDistanceCriterion(item.getDeviceLabel(),
               pm.getDouble(SER_THRESHOLD, 0.1),
               pm.getBoolean(SER_REQUIRES_POLLING, false));
      }
      return null;
   }

   protected String requiresPollingStringSuffix() {
      return requiresPolling_ ? " (poll)" : "";
   }

   static class ZDistanceCriterion extends ChangeCriterion {
      private final MonitoredItem item_;
      private final double threshold_;

      private ZDistanceCriterion(String stageLabel, double threshold, boolean requiresPolling) {
         super(requiresPolling);
         item_ = MonitoredItem.createZItem(stageLabel);
         threshold_ = threshold;
      }

      @Override
      public String toString() {
         return "Focus \"" + item_.getDeviceLabel() + "\" has moved " +
               threshold_ + " um" + requiresPollingStringSuffix();
      }

      @Override
      MonitoredItem getMonitoredItem() {
         return item_;
      }

      double getDistanceThresholdUm() {
         return threshold_;
      }

      @Override
      boolean testForChange(MonitoredValue oldValue, MonitoredValue newValue) {
         if (oldValue == null || newValue == null) {
            throw new NullPointerException(); // Programming error
         }
         if (oldValue instanceof MonitoredFloatValue &&
               newValue instanceof MonitoredFloatValue)
         {
            double diff = ((MonitoredFloatValue) newValue).getValue() -
                  ((MonitoredFloatValue) oldValue).getValue();
            return Math.abs(diff) >= threshold_;
         }
         else {
            throw new IllegalArgumentException(); // Programming error
         }
      }

      @Override
      void serialize(PropertyMapBuilder pmb) {
         super.serialize(pmb);
         pmb.putString(SER_MONITORED_ITEM, item_.toString());
         pmb.putDouble(SER_THRESHOLD, threshold_);
      }
   }

   static class XYDistanceCriterion extends ChangeCriterion {
      private final MonitoredItem item_;
      private final double threshold_;

      private XYDistanceCriterion(String stageLabel, double threshold, boolean requiresPolling) {
         super(requiresPolling);
         item_ = MonitoredItem.createXYItem(stageLabel);
         threshold_ = threshold;
      }

      @Override
      public String toString() {
         return "XY stage \"" + item_.getDeviceLabel() + "\" has moved " +
               threshold_ + " um" + requiresPollingStringSuffix();
      }

      @Override
      MonitoredItem getMonitoredItem() {
         return item_;
      }

      double getDistanceThresholdUm() {
         return threshold_;
      }

      @Override
      boolean testForChange(MonitoredValue oldValue, MonitoredValue newValue) {
         if (oldValue == null || newValue == null) {
            throw new NullPointerException(); // Programming error
         }
         if (oldValue instanceof MonitoredXYValue &&
               newValue instanceof MonitoredXYValue)
         {
            double x0 = ((MonitoredXYValue) oldValue).getX();
            double y0 = ((MonitoredXYValue) oldValue).getY();
            double x1 = ((MonitoredXYValue) newValue).getX();
            double y1 = ((MonitoredXYValue) newValue).getY();
            double dist = Math.sqrt((x1 - x0) * (x1 - x0) +
                  (y1 - y0) * (y1 - y0));
            return dist >= threshold_;
         }
         else {
            throw new IllegalArgumentException(); // Programming error
         }
      }

      @Override
      void serialize(PropertyMapBuilder pmb) {
         super.serialize(pmb);
         pmb.putString(SER_MONITORED_ITEM, item_.toString());
         pmb.putDouble(SER_THRESHOLD, threshold_);
      }
   }
}
