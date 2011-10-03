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
// CVS:          $Id: MMConfigFileException.java 1281 2008-06-04 20:59:50Z nenad $
//
package org.micromanager.conf2;

/**
 * Configuration file I/O errors. 
 *
 */
public class MMConfigFileException extends Exception {
   private static final long serialVersionUID = 1L;
   private Throwable cause;
   
   public MMConfigFileException(final String message) {
      super(message);
  }

  public MMConfigFileException(final Throwable t) {
      super(t.getMessage());
      this.cause = t;
  }

  public Throwable getCause() {
      return this.cause;
  }
}
