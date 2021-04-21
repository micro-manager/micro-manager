///////////////////////////////////////////////////////////////////////////////
// FILE:          ShadingExcaption.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MultiChannelShading plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2014
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

package org.micromanager.multichannelshading;

/** @author nico */
public class ShadingException extends Exception {
  private static final long serialVersionUID = 1982469123029387598L;

  public ShadingException(String msg) {
    super(msg);
  }
}
