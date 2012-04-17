//
// Two-photon plugin module for micro-manager
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//                
// AUTHOR:        Nenad Amodaj

package com.imaging100x.twophoton;

public class TwoPhotonException extends Exception {
   private static final long serialVersionUID = 1L;
   private Throwable cause;

   /**
    * Constructs an exception object with an explanatory message.
    * @param message - the reason for the exception.
    */
   public TwoPhotonException(String message) {
      super("TwoPhoton error: " + message);
   }

   public TwoPhotonException(Throwable t) {
      super(t.getMessage());
      this.cause = t;
   }

   public Throwable getCause() {
      return this.cause;
   }

}
