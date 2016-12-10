// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.display.internal.imagestats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Mark A. Tsuchida
 */
public final class ImagesAndStats implements BoundedPriorityElement {
   private final ImageStatsRequest request_;
   private final ImageStatsRequest originalRequest_; // Used to interpret stats_
   private final List<ImageStats> stats_;

   static class ImageStats {
      private final List<ComponentStats> componentStats_;

      static ImageStats create(ComponentStats[] componentStats) {
         return new ImageStats(componentStats);
      }

      private ImageStats(ComponentStats[] componentStats) {
         componentStats_ = new ArrayList(Arrays.asList(componentStats));
      }

      int getNumberOfComponents() {
         return componentStats_.size();
      }

      ComponentStats getComponentStats(int component) {
         return componentStats_.get(component);
      }
   }

   public static ImagesAndStats create(ImageStatsRequest input,
         ImageStats[] stats)
   {
      return new ImagesAndStats(input, stats);
   }

   private ImagesAndStats(ImageStatsRequest input, ImageStats[] stats)
   {
      this(input, input, stats);
   }

   private ImagesAndStats(ImageStatsRequest request,
         ImageStatsRequest originalInput, ImageStats[] stats) {
      request_ = request;
      originalRequest_ = originalInput;
      stats_ = new ArrayList(Arrays.asList(stats));
   }

   public ImagesAndStats copyForRequest(ImageStatsRequest request) {
      return new ImagesAndStats(request, request_,
            stats_.toArray(new ImageStats[]{}));
   }

   @Override
   public int getPriority() {
      return request_.getPriority();
   }

   public ImageStatsRequest getRequest() {
      return request_;
   }

   /**
    * Returns whether the stats were computed from the nominal request wrapped
    * in this object (as opposed to adopted or recycled from another request).
    *
    * @return true if the stats were computed from the request returned by
    * {@code getRequest}
    */
   public boolean isRealStats() {
      return request_ == originalRequest_;
   }
}