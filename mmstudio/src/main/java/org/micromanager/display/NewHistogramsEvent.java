///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//               Open Imaging, Inc. 2015
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

package org.micromanager.display;

import java.util.ArrayList;

/**
 * This event is posted whenever new histogram data is calculated for the
 * display. It contains histogram data for a single channel. It is consumed
 * by the histograms widget in the Inspector window; thus, if you have a
 * custom DataViewer and you wish to update the histograms, you need to post
 * one of these events on your EventBus.
 */
public class NewHistogramsEvent {
   private final int channel_;
   private final ArrayList<HistogramData> datas_;

   /**
    * @param channel The channel index for which these new histograms are
    *        intended.
    * @param datas A list of HistogramDatas, one per component in the image(s)
    *        used to generate the histograms.
    */
   public NewHistogramsEvent(int channel, ArrayList<HistogramData> datas) {
      channel_ = channel;
      datas_ = datas;
   }

   /**
    * Return the channel for which new histograms have been calculated.
    * @return Channel for which new histograms were calculated
    */
   public int getChannel() {
      return channel_;
   }

   /**
    * Return the number of components in the image(s) used to generate the
    * histograms.
    * @return Number of Components (i.e. 1 for grey scale, 3 for RGB) in the image 
    * used to generate the histogram
    */
   public int getNumComponents() {
      return datas_.size();
   }

   /**
    * Retrieve the HistogramData corresponding to one component.
    * @param component Desired component index
    * @return Histogram for the requested component
    */
   public HistogramData getHistogram(int component) {
      return datas_.get(component);
   }
}
