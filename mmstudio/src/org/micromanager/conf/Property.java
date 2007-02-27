///////////////////////////////////////////////////////////////////////////////
//FILE:          Property.java
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
 * Device property data structure.
 */
public class Property {
   public String name_;
   public String value_;
   public boolean readOnly_ = false;
   public boolean preInit_ = false;
   public String[] allowedValues_;
   
   public Property() {
      name_ = new String("Undefined");
      value_ = new String();
      allowedValues_ = new String[0];
   }
   public Property(String name, String value) {
      name_ = new String(name);
      value_ = new String(value);
      allowedValues_ = new String[0];
   }
   public Property(String name, String value, boolean preinit) {
      name_ = new String(name);
      value_ = new String(value);
      allowedValues_ = new String[0];
      preInit_ = preinit;
   }
}
