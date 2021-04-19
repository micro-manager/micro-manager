///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2017
//
// COPYRIGHT:    University of California, San Francisco, 2017
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
 * This event posts when the DataStore gets a new name, i.e. when
 * {@link Datastore#setName(String)} is called
 *
 * The default implementation of this Event posts on the Datastore
 * event bus.  Subscribe using {@link DataProvider#registerForEvents(Object)}.
 */
public interface DataProviderHasNewNameEvent extends MMEvent {

   /**
    * @return The new name of the DataProvider.
    */
   String getNewName();
}
