///////////////////////////////////////////////////////////////////////////////
// FILE:          PolygonPlugin.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MightexPolygon plugin
//-----------------------------------------------------------------------------
// DESCRIPTION:   Mightex Polygon400 plugin.
//                
// AUTHOR:        Wayne Liao, mightexsystem.com, 05/15/2015
//
// COPYRIGHT:     Mightex Systems, 2015
//
// LICENSE:       This file is distributed under the BSD license.
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

package org.micromanager.polygon;

import mmcorej.CMMCore;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Wayne
 */
public class PolygonPlugin implements org.micromanager.api.MMPlugin {
    public static String menuName = "Mightex Polygon Plugin";
    public static String tooltipDescription = "Plugin for Mightex Polygon400 device to control pattern projection";

    private ScriptInterface app_;
    private CMMCore core_;

    private PolygonForm form_;

    @Override
    public void dispose() {
    }

    @Override
    public void setApp(ScriptInterface app) {
        app_ = app;
        core_ = app.getMMCore();
   }

   @Override
   public void show() {
        if(core_.getSLMDevice().length()==0){
            Utility.LogMsg( "SLM device count = " + core_.getSLMDevice().length() );
            ReportingUtils.showMessage("Please load a Mightex Polygon400 device before using the MightexPolygon plugin.");
            return;
        }
        if(form_==null) {
            form_ = new PolygonForm(app_);
        }
        form_.setVisible(true);
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }

   @Override
   public String getInfo() {
      return "Info: Mightex Polygon Plugin";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "(C) 2014 Mightex Systems. This software is released under the BSD license";
   }
  
}
