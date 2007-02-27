///////////////////////////////////////////////////////////////////////////////
//FILE:          MMConfigFileException.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id$
//
package org.micromanager.conf;

/**
 * Configuration file I/O errors. 
 *
 */
public class MMConfigFileException extends Exception {
   private Throwable cause;
   
   public MMConfigFileException(String message) {
      super(message);
  }

  public MMConfigFileException(Throwable t) {
      super(t.getMessage());
      this.cause = t;
  }

  public Throwable getCause() {
      return this.cause;
  }
}
