///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
package org.micromanager.plugins.magellan.main;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import org.micromanager.plugins.magellan.gui.GUI;
import java.util.prefs.Preferences;
import mmcorej.CMMCore;
import org.micromanager.MMStudio;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Henry
 */
public class Magellan implements MMPlugin{

   private static final String VERSION = "1.0";
           
   public static final String menuName = "Micro-Magellan";
   public static final String tooltipDescription = "High throughout, automated micrscopy for spatiotemporal exploration";

   private static Preferences prefs_;
   private static ScriptInterface mmAPI_;
   private static GUI gui_;
   
   public Magellan() {
      //TODO: Remove, for affine debugging
       Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);
      try { 
         prefs.clear();
      } catch (BackingStoreException ex) {
         Logger.getLogger(Magellan.class.getName()).log(Level.SEVERE, null, ex);
      }
      
      
      
      if (gui_ == null) {
         prefs_ = Preferences.userNodeForPackage(Magellan.class);
         gui_ = new GUI(prefs_, VERSION);
      }
   }
   
   public static Preferences getPrefs() {
      return prefs_;
   }
   
   @Override
   public void dispose() {
   }

   @Override
   public void setApp(ScriptInterface si) {
      
   }

   @Override
   public void show() {      
      gui_.setVisible(true);
   }

   @Override
   public String getDescription() {
      return "Explore samples in space and time";
   }

   @Override
   public String getInfo() {
      return "";
   }

   @Override
   public String getVersion() {
      return VERSION;
   }

   @Override
   public String getCopyright() {
      return "Henry Pinkard UCSF 2014";
   }
   
   //methods for communicating with MM APIs
   public static CMMCore getCore() {
      return MMStudio.getInstance().getCore();
   }
   
   public static ScriptInterface getScriptInterface() {
      return (ScriptInterface) MMStudio.getInstance();
   }
   
   public static String getConfigFileName() {
      try {
         return MMStudio.getInstance().getSysConfigFile();
      } catch (Exception e) {
         //since this is not an API method
         return "";
      }    
   }
   
}
