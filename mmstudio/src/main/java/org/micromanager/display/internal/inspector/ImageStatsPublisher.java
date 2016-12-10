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

package org.micromanager.display.internal.inspector;

import org.micromanager.EventPublisher;
import org.micromanager.display.internal.imagestats.ImagesAndStats;

/**
 * An object (usually data viewer) that computes and makes available histograms
 * and stats for sets of images.
 * <p>
 * TODO: Move this into the API (need to move ImagesAndStats into API first).
 * <p>
 * Classes implementing this interface must post {@link NewImageStatsEvent} on
 * the Swing/EDT event dispatch thread when it switches to a new set of images.
 *
 * @author Mark A. Tsuchida
 */
public interface ImageStatsPublisher extends EventPublisher {
   public static class NewImageStatsEvent {
      private final ImagesAndStats stats_;

      public NewImageStatsEvent create(ImagesAndStats stats) {
         return new NewImageStatsEvent(stats);
      }

      private NewImageStatsEvent(ImagesAndStats stats) {
         stats_ = stats;
      }

      ImagesAndStats getImagesAndStats() {
         return stats_;
      }
   }

   ImagesAndStats getCurrentImagesAndStats();
}