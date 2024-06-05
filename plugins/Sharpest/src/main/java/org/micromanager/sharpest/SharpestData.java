package org.micromanager.sharpest;

import org.micromanager.imageprocessing.ImgSharpnessAnalysis;

/**
 * Data class to store the parameters of the ZProjector plugin.
 */
public class SharpestData {
   final String projectionAxis_;
   final int firstFrame_;
   final int lastFrame_;
   final int projectionMethod_;
   final ImgSharpnessAnalysis.Method sharpnessMethod_;
   final boolean showGraph_;

   /**
    * Constructor.
    *
    * @param projectionAxis The axis to project along.
    * @param firstFrame The first frame to project.
    * @param lastFrame The last frame to project.
    * @param projectionMethod The method to use for projection.
    * @param sharpnessMethod The method to use for sharpness analysis.
    * @param showGraph Whether to show the sharpness graph.
    */
   public SharpestData(final String projectionAxis,
                         final int firstFrame,
                         final int lastFrame,
                         final int projectionMethod,
                         final ImgSharpnessAnalysis.Method sharpnessMethod,
                         final boolean showGraph) {
      projectionAxis_ = projectionAxis;
      firstFrame_ = firstFrame;
      lastFrame_ = lastFrame;
      projectionMethod_ = projectionMethod;
      sharpnessMethod_ = sharpnessMethod;
      showGraph_ = showGraph;
   }
}
