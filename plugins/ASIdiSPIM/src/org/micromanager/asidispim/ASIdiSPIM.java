///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIM.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim;

import java.awt.Color;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.api.ASIdiSPIMImplementation;
import org.micromanager.asidispim.api.ASIdiSPIMInterface;
import org.micromanager.utils.ReportingUtils;


public class ASIdiSPIM implements MMPlugin {
   
   // to create a duplicate plugin that uses different preferences (and has 2 menu entries):
   // - create a copy of this file under a different name and change the class name to match the file name
   // - add a distinguishing strung to the menuName, e.g. + " 2"
   // - change the second parameter in the call to new ASIdiSPIMFrame() to true to use a different preference node
   
   public static final boolean oSPIM = false;
   public static final boolean SCOPE = false;
   public static final boolean singleView = (oSPIM || SCOPE);  // true for SCOPE and oSPIM (and possibly other situations?)
   public static final boolean doubleXYZ = false;
   public final static String versionString = " 20250528";
   
   public final static String menuName = "ASI " 
         + (SCOPE ? "SCOPE" : (oSPIM ? "oSPIM" : "diSPIM") ) 
         + (doubleXYZ ? " double" : "") 
         + (SCOPE ? versionString : "")
         ;
   public final static String rmiName = "ASIdiSPIM_API";
   public final static String tooltipDescription = "Control the " + menuName;
   public final static Color borderColor = Color.gray;
   
   private ScriptInterface gui_;
   private static ASIdiSPIMFrame myFrame_ = null;
   private static Registry registry_ = null;

   @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;
      // close frame before re-load if already open
      // if frame has been opened and then closed (myFrame != null) but it won't be displayable 
      if (myFrame_ != null && myFrame_.isDisplayable()) {
         WindowEvent wev = new WindowEvent(myFrame_, WindowEvent.WINDOW_CLOSING);
         myFrame_.dispatchEvent(wev);
      }
      // create brand new instance of plugin frame every time
      try {
         myFrame_ = new ASIdiSPIMFrame(gui_, false);  // make 2nd parameter true to save preferences in alternate location
         myFrame_.setBackground(gui_.getBackgroundColor());
         gui_.addMMListener(myFrame_);
         gui_.addMMBackgroundListener(myFrame_);
      } catch (Exception e) {
         gui_.showError(e);
      }
      myFrame_.setVisible(true);
     
      // create and publish object to allow remote invocation of API methods
      // see documentation of Java RMI
      try {
         ASIdiSPIMInterface api = new ASIdiSPIMImplementation();
         ASIdiSPIMInterface stub = (ASIdiSPIMInterface) UnicastRemoteObject.exportObject(api, 0);
         // Bind the remote object's stub in the registry
         registry_ = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);  // maybe we need getRegistry() instead?
         registry_.rebind(rmiName, stub);
      } catch (RemoteException ex) {
         throw new RuntimeException("Error registering API for RMI access", ex);
      }
      
      ReportingUtils.logDebugMessage("finished initializing plugin " + getInfo() + " version " + getVersion());
   }
   
   public static ASIdiSPIMFrame getFrame() {
      return myFrame_;
   }
   
   /**
   * The main app calls this method to remove the module window
   */
   @Override
   public void dispose() {
      if (myFrame_ != null)
         myFrame_.dispose();
   }

   @Override
   public void show() {
      @SuppressWarnings("unused")
      String ig = menuName;
   }

   @Override
   public String getInfo () {
      return menuName;
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }

   @Override
   public String getVersion() {
      return "0.3";
   }

   @Override
   public String getCopyright() {
      return "University of California and Applied Scientific Instrumentation (ASI), 2013-2016";
   }
}

