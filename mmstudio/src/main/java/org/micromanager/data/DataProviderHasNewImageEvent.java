///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

import org.micromanager.MMEvent;

/**
 * This class signifies that an image has been added to a DataProvider.
 *
 * The default implementation of this Event posts on the DataProvider
 * event bus.  Subscribe using {@link DataProvider#registerForEvents(Object)}.
 */
public interface DataProviderHasNewImageEvent extends MMEvent {
   /**
    * Provides the newly-added image.
    * @return the Image that was just added to the DataProvider.
    */
   Image getImage();

   /**
    * @return the Coords for the Image; identical to getImage().getCoords().
    */
   Coords getCoords();

   /**
    * Provides the DataProvider this image was added to; potentially useful for
    * code that listens to events from multiple DataProviders.
    * @return the DataProvider this image was added to.
    */
   DataProvider getDataProvider();
}
