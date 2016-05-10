/*
* ASI_CRISP_V2.java
* Micro Manager Plugin for ASIs CRISP Autofocus
* Based on Nico Stuurman's original ASI CRISP Control plugin.
* Modified by Vikram Kopuri, ASI
* Last Updated 1/14/2015
* Changelog
* 2.0
* First Draft
* 2.01
* Bugfix , Pause refresh during Log Cal to avoid getting error
* 2.02
* Shows Offset and Sum Values
 */
package com.asiimaging.CRISPv2;//ASI_CRISP_V2;//

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import java.awt.event.WindowEvent;
/**
 *
 * @author Vik
 */

public class ASI_CRISP_V2 implements org.micromanager.api.MMPlugin {

   public static String menuName = "ASI CRISP V2[Beta]";
   public static String tooltipDescription = "Interface for ASIs CRISP Autofocus ";
   private CMMCore core_;
   private ScriptInterface gui_;
   private ASI_CRISP_Frame myFrame_;
   
    @Override
    public void dispose() {
        if (myFrame_ != null)
         myFrame_.safePrefs();
    }

    @Override
    public void setApp(ScriptInterface app) {
          gui_ =  app;
            core_ = app.getMMCore();
                  if (myFrame_ != null) {
                WindowEvent wev = new WindowEvent(myFrame_, WindowEvent.WINDOW_CLOSING);
                myFrame_.dispatchEvent(wev);
                myFrame_ = null;
                }
            if (myFrame_ == null) {
         try {
            myFrame_ = new ASI_CRISP_Frame(gui_);
            gui_.addMMBackgroundListener(myFrame_);
         } catch (Exception e) {
            e.printStackTrace();
            return;
         }
      }
      myFrame_.setVisible(true);
    }

    @Override
    public void show() {
       String ig = "ASI CRISP Control";
    }

    @Override
    public String getDescription() {
    return "Description: Interface for ASIs CRISP Autofocus. Written by ASI";    
    }

    @Override
    public String getInfo() {
    return "Info: ASI CRISP V2";
    }

    @Override
    public String getVersion() {
        return"2.02" ;
                }

    @Override
    public String getCopyright() {
    return "(C) 2014 ASI";
    }
    
}
