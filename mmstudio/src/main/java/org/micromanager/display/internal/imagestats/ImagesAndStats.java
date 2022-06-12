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
 * @author Mark A. Tsuchida
 */
public final class ImagesAndStats {
   private final long statsSequenceNumber_;
   private final ImageStatsRequest request_;
   private final ImageStatsRequest originalRequest_; // Used to interpret stats_
   private final List<ImageStats> stats_;


   public static ImagesAndStats create(long sequenceNumber,
                                       ImageStatsRequest input,
                                       ImageStats... stats) {
      return new ImagesAndStats(sequenceNumber, input, stats);
   }

   private ImagesAndStats(long sequenceNumber, ImageStatsRequest input,
                          ImageStats... stats) {
      this(sequenceNumber, input, input, stats);
   }

   private ImagesAndStats(long sequenceNumber, ImageStatsRequest request,
                          ImageStatsRequest originalInput, ImageStats... stats) {
      statsSequenceNumber_ = sequenceNumber;
      request_ = request;
      originalRequest_ = originalInput;
      stats_ = new ArrayList<>(Arrays.asList(stats));
   }

   public ImagesAndStats copyForRequest(ImageStatsRequest request) {
      return new ImagesAndStats(statsSequenceNumber_, request, request_,
            stats_.toArray(new ImageStats[] {}));
   }

   // Return serial number given to real stats (can be used to determine if
   // stats are newer than previously seen)
   public long getStatsSequenceNumber() {
      return statsSequenceNumber_;
   }

   public ImageStatsRequest getRequest() {
      return request_;
   }

   public List<ImageStats> getResult() {
      return new ArrayList<>(stats_);
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