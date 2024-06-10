package org.micromanager.sharpest;

import org.micromanager.imageprocessing.ImgSharpnessAnalysis;

/**
 * Data class to store the parameters of the Sharpest plugin.
 */
public class SharpestData {
   final ImgSharpnessAnalysis.Method sharpnessMethod_;
   final boolean showGraph_;
   final int nrPlanes_;

   /**
    * Constructor.
    *
    * @param sharpnessMethod The method to use for sharpness analysis.
    * @param showGraph Whether to show the sharpness graph.
    * @param nrPlanes The number of planes to produce.  Should be an odd number, i.e. 1 will produce
    *                 only the sharpest frame, 3 the sharpest plus the two adjacent frames.
    */
   public SharpestData(final ImgSharpnessAnalysis.Method sharpnessMethod,
                       final boolean showGraph,
                       final int nrPlanes) {
      sharpnessMethod_ = sharpnessMethod;
      showGraph_ = showGraph;
      nrPlanes_ = nrPlanes;
   }
}
