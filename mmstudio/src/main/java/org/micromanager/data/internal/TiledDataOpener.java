///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    Regents of the University of California, 2026
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

package org.micromanager.data.internal;

import java.util.Map;
import java.util.TreeMap;
import org.micromanager.Studio;
import org.micromanager.data.TiledDataOpenerPlugin;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Routes a path to a {@link TiledDataOpenerPlugin} when one recognizes it.
 *
 * <p>Tiled (pyramidal) datasets are not backed by a {@code Datastore} and so cannot travel
 * through {@code DataManager.loadData()}. Every place that opens data from disk therefore
 * consults this class first, and only falls back to the normal loading path when no opener
 * claims the path.
 *
 * <p>This must happen before Micro-Manager's own format detection runs. That detection begins
 * with {@code MultipageTiffReader.isMMMultipageTiff}, which throws when a directory contains
 * no TIFF files at all - which is exactly the case for an OME-Zarr dataset, and would
 * otherwise surface as a misleading "is this a Micro-Manager dataset?" error.
 */
public final class TiledDataOpener {

   /**
    * The outcome of offering a path to the registered openers.
    *
    * <p>"No opener wanted it" and "an opener took it and failed" have to be told apart: in the
    * first case the caller should carry on and try the normal loading path, in the second it
    * must stop, because the opener has already told the user what went wrong.
    */
   public static final class Result {
      private final boolean handled_;
      private final String datasetRoot_;

      private Result(boolean handled, String datasetRoot) {
         handled_ = handled;
         datasetRoot_ = datasetRoot;
      }

      /**
       * Whether an opener claimed this path, whether or not it succeeded.
       *
       * @return true if the caller should not attempt to open the data itself
       */
      public boolean wasHandled() {
         return handled_;
      }

      /**
       * The dataset root that was opened.
       *
       * <p>This is the path to record in the recently-opened files list. It can differ from the
       * path passed in, which may have pointed at a file inside the dataset.
       *
       * @return the opened dataset root, or null if nothing was opened
       */
      public String getDatasetRoot() {
         return datasetRoot_;
      }
   }

   private static final Result NOT_HANDLED = new Result(false, null);

   private TiledDataOpener() {
      // static utility - no instances
   }

   /**
    * Offer a path to every registered tiled-data opener, and let the first one that recognizes
    * it open the dataset.
    *
    * <p>Safe to call when no opener is installed, in which case the result simply reports that
    * the path was not handled. Never throws: an opener that fails unexpectedly is logged and
    * reported as handled-but-unopened, so the caller does not go on to produce a second,
    * confusing error about the same dataset.
    *
    * @param studio the Studio instance, used to find the openers
    * @param path   directory of a dataset, or a file within one
    * @return whether an opener claimed the path, and the dataset root if one was opened
    */
   public static Result tryOpen(Studio studio, String path) {
      if (studio == null || path == null) {
         return NOT_HANDLED;
      }
      // getTiledDataOpenerPlugins() returns a HashMap, whose iteration order is unspecified.
      // Sorting by class name means that when more than one opener recognizes a path, the same
      // one wins every time, rather than varying between runs.
      Map<String, TiledDataOpenerPlugin> openers =
            new TreeMap<>(studio.plugins().getTiledDataOpenerPlugins());
      for (TiledDataOpenerPlugin opener : openers.values()) {
         try {
            if (!opener.canOpen(path)) {
               continue;
            }
            return new Result(true, opener.open(path));
         } catch (RuntimeException e) {
            // The opener claimed the path, so stop here rather than falling through to the
            // standard loading path, which would fail again with a less relevant message.
            ReportingUtils.showError(e, "Failed to open tiled dataset at " + path);
            return new Result(true, null);
         }
      }
      return NOT_HANDLED;
   }
}
