///////////////////////////////////////////////////////////////////////////////
//FILE:           HCSException.java
//PROJECT:        Micro-Manage
//SUBSYSTEM:      high content screening
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June 9, 2008
//
//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008
//                
//LICENSE:        This file is distributed under the GPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.hcs;

public class HCSException extends Exception {
   private static final long serialVersionUID = -8829247065013272369L;
   private Throwable cause;

   /**
    * Constructs a MMAcqDataException with an explanatory message.
    * @param message - the reason for the exception.
    */
   public HCSException(String message) {
      super(message);
   }

   public HCSException(Throwable t) {
      super(t.getMessage());
      this.cause = t;
   }

   @Override
   public Throwable getCause() {
      return this.cause;
   }
}
