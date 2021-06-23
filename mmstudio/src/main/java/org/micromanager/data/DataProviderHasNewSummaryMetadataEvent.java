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
 * This class signifies that new summary metadata has been set for a 
 * DataProvider.
 *
 * The default implementation of this Event posts on the DataProvider
 * event bus.  Subscribe using {@link DataProvider#registerForEvents(Object)}.
 */
public interface DataProviderHasNewSummaryMetadataEvent extends MMEvent {

   /**
    * @return New summary metadata of the DataProvider.
    */
   SummaryMetadata getSummaryMetadata();
}
