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

import java.util.ArrayList;

import org.micromanager.display.internal.HistogramData;

/**
 * This event is posted whenever new histogram data is calculated for the
 * display. It contains histogram data for a single channel.
 */
public class NewHistogramsEvent {
   private int channel_;
   private ArrayList<HistogramData> datas_;
   public NewHistogramsEvent(int channel, ArrayList<HistogramData> datas) {
      channel_ = channel;
      datas_ = datas;
   }

   public int getChannel() {
      return channel_;
   }

   public int getNumComponents() {
      return datas_.size();
   }

   public HistogramData getHistogram(int component) {
      return datas_.get(component);
   }
}
