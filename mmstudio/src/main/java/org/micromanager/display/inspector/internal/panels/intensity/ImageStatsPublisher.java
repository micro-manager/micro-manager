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

package org.micromanager.display.inspector.internal.panels.intensity;

import org.micromanager.EventPublisher;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.internal.utils.MustCallOnEDT;

/**
 * An object (usually data viewer) that computes and makes available histograms
 * and stats for sets of images.
 *
 * <p>TODO: Move this into the API (need to move ImagesAndStats into API first).
 * But first, consider a little more how this should be factored: should it be
 * a "Histogrammable" interface? What about other aspects of display-related
 * image stats (LUT, 8-bit image)? Also, does the event (or stats object) need
 * to include the display settings and/or ROI at the time of the stats compute?</p>
 *
 * <p>Classes implementing this interface must post {@link ImageStatsChangedEvent} on
 * the Swing/EDT event dispatch thread when it switches to a new set of images.
 * Such a class is also responsible for _not_ posting {@code ImageStatsChangedEvent}s
 * containing stats that are no longer valid (e.g. because compute settings
 * changed).</p>
 *
 * @author Mark A. Tsuchida
 */
public interface ImageStatsPublisher extends EventPublisher {

   /**
    * Event signalling that Image Statistics changed.
    */
   final class ImageStatsChangedEvent {
      private final ImagesAndStats stats_;

      public static ImageStatsChangedEvent create(ImagesAndStats stats) {
         return new ImageStatsChangedEvent(stats);
      }

      private ImageStatsChangedEvent(ImagesAndStats stats) {
         stats_ = stats;
      }

      public ImagesAndStats getImagesAndStats() {
         return stats_;
      }
   }

   @MustCallOnEDT
   ImagesAndStats getCurrentImagesAndStats();
}