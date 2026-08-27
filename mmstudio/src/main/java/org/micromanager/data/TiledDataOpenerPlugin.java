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

package org.micromanager.data;

import org.micromanager.MMPlugin;

/**
 * Opens a tiled (pyramidal) dataset in a viewer of its own choosing.
 *
 * <p>Micro-Manager's built-in data loading path ({@link DataManager#loadData}) produces a
 * {@link Datastore} shown in the standard display window. That model does not fit the
 * pyramidal storage formats (OME-Zarr, OME-BigTIFF, tiled NDTiff), which are viewed in the
 * TiledDataViewer and are not backed by a {@code Datastore} at all.
 *
 * <p>Those formats live in libraries that are built <em>after</em> mmstudio, so mmstudio
 * cannot call them directly. This interface is the bridge: mmstudio asks each registered
 * opener whether it recognizes a path, and hands the work to the first one that does. When no
 * opener is installed, the File menu and drag-and-drop behave exactly as they did before this
 * interface existed.
 *
 * <p>Implementations are discovered through the usual SciJava mechanism, by annotating the
 * class with {@code @Plugin(type = TiledDataOpenerPlugin.class)}.
 */
public interface TiledDataOpenerPlugin extends MMPlugin {
   /**
    * Indicate whether this opener can handle the dataset at the given path.
    *
    * <p>The path may be the dataset directory itself or a file inside it, since the file
    * chooser and drag-and-drop both let the user pick either. Implementations are expected to
    * resolve the dataset root themselves.
    *
    * <p>Called before Micro-Manager attempts its own format detection, so this method should
    * avoid expensive work where it can, and must not show any UI.
    *
    * @param path directory of a dataset, or a file within one
    * @return true if {@link #open} should be called with this path
    */
   boolean canOpen(String path);

   /**
    * Open the dataset and show a viewer for it.
    *
    * <p>Called on a background thread, never on the EDT. Implementations report their own
    * failures to the user (for instance through {@code studio.logs().showError()}) and return
    * null; Micro-Manager will not attempt to open the data by any other means afterwards.
    *
    * @param path the same path that {@link #canOpen} accepted
    * @return the dataset root that was opened, so that it can be recorded in the list of
    *     recently-opened files, or null if the dataset could not be opened
    */
   String open(String path);
}
