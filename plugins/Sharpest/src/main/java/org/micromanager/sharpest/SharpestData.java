package org.micromanager.sharpest;

import org.micromanager.imageprocessing.ImgSharpnessAnalysis;

/**
 * Data class to store the parameters of the Sharpest plugin.
 */
public class SharpestData {
   final ImgSharpnessAnalysis.Method sharpnessMethod_;
   final boolean showGraph_;
   final int nrPlanes_;
   final boolean sharpenAllChannels_;
   final String channel_;

   /**
    * Constructor.
    *
    * @param sharpnessMethod The method to use for sharpness analysis.
    * @param showGraph Whether to show the sharpness graph.
    * @param nrPlanes The number of planes to produce.  Should be an odd number, i.e. 1 will produce
    *                 only the sharpest frame, 3 the sharpest plus the two adjacent frames.
    * @param sharpenAllChannels Whether to select the sharpest frame from all channels or only the
    *                           selected channel.
    * @param channel Channel to identify the sharpest plane (and use that plane from all channels).
    */
   public SharpestData(final ImgSharpnessAnalysis.Method sharpnessMethod,
                       final boolean showGraph,
                       final int nrPlanes,
                       final boolean sharpenAllChannels,
                       final String channel) {
      sharpnessMethod_ = sharpnessMethod;
      showGraph_ = showGraph;
      nrPlanes_ = nrPlanes;
      sharpenAllChannels_ = sharpenAllChannels;
      channel_ = channel;
   }
}
