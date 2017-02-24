///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIMException.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2014
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

package org.micromanager.asidispim.api;

/**
 *
 * @author nico
 */
public class ASIdiSPIMException extends Exception {
   private static final long serialVersionUID = -8472385639461107821L;
   private Throwable cause;
   private static final String MSG_PREFIX = "Error in ASIdiSPIM Interface: ";

   public ASIdiSPIMException(String message) {
       super(MSG_PREFIX + message);
   }

   public ASIdiSPIMException(Throwable t) {
       super(MSG_PREFIX + t.getMessage());
       this.cause = t;
   }
   
   public ASIdiSPIMException(Throwable t, String message) {
       super(MSG_PREFIX + t.getMessage() + "-" + message);
       this.cause = t;
   }

   @Override
   public Throwable getCause() {
       return this.cause;
   }

}