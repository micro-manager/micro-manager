package org.micromanager.zprojector;


/**
 * Data class to store the parameters of the ZProjector plugin.
 */
public class ZProjectorData {
   final String projectionAxis_;
   final int firstFrame_;
   final int lastFrame_;
   final int projectionMethod_;

   /**
    * Constructor.
    *
    * @param projectionAxis The axis to project along.
    * @param firstFrame The first frame to project.
    * @param lastFrame The last frame to project.
    * @param projectionMethod The method to use for projection.
    */
   public ZProjectorData(final String projectionAxis,
                         final int firstFrame,
                         final int lastFrame,
                         final int projectionMethod) {
      projectionAxis_ = projectionAxis;
      firstFrame_ = firstFrame;
      lastFrame_ = lastFrame;
      projectionMethod_ = projectionMethod;
   }
}
