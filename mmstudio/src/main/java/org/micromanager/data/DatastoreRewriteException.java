///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    Open Imaging, Inc 2016
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

/**
 * An unchecked exception thrown when an attempt is made to overwrite data in
 * a non-rewritable datastore.
 */
public class DatastoreRewriteException extends UnsupportedOperationException {
   public static final String ERROR_MESSAGE =
           "An attempt was made to overwrite data in a non-rewriteable Datastore";

   public DatastoreRewriteException() {
      super(ERROR_MESSAGE);
   }
}
