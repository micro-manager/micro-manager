///////////////////////////////////////////////////////////////////////////////
//FILE:          ChannelSpec.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 10, 2005
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
// CVS:          $Id: ChannelSpec.java 12081 2013-11-06 21:27:25Z nico $
//
package org.micromanager.acqj.api;

import java.util.HashMap;

/**
 * Settings corresponding to a single channel that are hardcoded into acq engine,
 * plus generic key value pairs for including optional stuff
 */
public class ChannelSetting {
    
   final public String group_;
   final public String config_; // Configuration setting name
   final public String name_; 
   public double exposure_; // ms
   public double offset_;
   public boolean use_ = true;
//   final public boolean uniqueEvent_;
   //Generic key value pairs for non essential properties associated with channels (e.g. display settings)
   private final HashMap<String, Object> props_ = new HashMap<String, Object>();

   public ChannelSetting(String group, String config, String name, boolean uniqueEvent ) {       
      group_ = group;
      config_ = config;
      name_ = name;
//      uniqueEvent_ = uniqueEvent; // true for only first on multichannel camera
   }
   
   public Object getProperty(String key) {
      return props_.get(key);
   }
   
   public void setProperty(String key, Object value) {
      props_.put(key, value);
   }
   

}
