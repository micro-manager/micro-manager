///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIdiSPIM.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     ASIdiSPIM plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013-2015
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

import com.google.common.eventbus.Subscribe;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.asidispim.api.ASIdiSPIMException;
import org.micromanager.asidispim.api.ASIdiSPIMImplementation;
import org.micromanager.asidispim.api.ASIdiSPIMInterface;
import org.micromanager.events.ShutdownCommencingEvent;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/** @author nico */
@Plugin(type = MenuPlugin.class)
public class ASIdiSPIM implements MenuPlugin, SciJavaPlugin {
  public static final boolean OSPIM = false;
  public static final String MENUNAME = "ASI " + (OSPIM ? "oSPIM" : "diSPIM");
  public static final String RMINAME = "ASIdiSPIM_API";
  public static final String TOOLTIPDESCRIPTION = "Control the " + MENUNAME;
  public static final Color BORDERCOLOR = Color.gray;

  private Studio gui_;
  private static ASIdiSPIMFrame myFrame_ = null;
  private static Registry registry_ = null;

  public static ASIdiSPIMFrame getFrame() {
    return myFrame_;
  }

  @Override
  public String getVersion() {
    return "0.4";
  }

  @Override
  public String getCopyright() {
    return "University of California and Applied Scientific Precision(ASI), 2013-2017";
  }

  @Override
  public String getSubMenu() {
    return "Beta";
  }

  @Override
  public void onPluginSelected() {
    // close frame before re-load if already open
    // if frame has been opened and then closed (myFrame != null) but it won't be displayable
    if (myFrame_ != null && myFrame_.isDisplayable()) {
      WindowEvent wev = new WindowEvent(myFrame_, WindowEvent.WINDOW_CLOSING);
      myFrame_.dispatchEvent(wev);
    }
    // create brand new instance of plugin frame every time
    try {
      myFrame_ = new ASIdiSPIMFrame(gui_);
      gui_.events().registerForEvents(myFrame_);
    } catch (ASIdiSPIMException e) {
      gui_.logs().showError(e);
    }
    myFrame_.setVisible(true);

    // create and publish object to allow remote invocation of API methods
    // see documentation of Java RMI
    try {
      ASIdiSPIMInterface api = new ASIdiSPIMImplementation();
      ASIdiSPIMInterface stub = (ASIdiSPIMInterface) UnicastRemoteObject.exportObject(api, 0);
      // Bind the remote object's stub in the registry
      registry_ =
          LocateRegistry.createRegistry(
              Registry.REGISTRY_PORT); // maybe we need getRegistry() instead?
      registry_.rebind(RMINAME, stub);
    } catch (RemoteException ex) {
      throw new RuntimeException("Error registering API for RMI access", ex);
    }
  }

  @Override
  public void setContext(Studio studio) {
    gui_ = studio;
    gui_.events().registerForEvents(this);
  }

  @Override
  public String getName() {
    return MENUNAME;
  }

  @Override
  public String getHelpText() {
    return TOOLTIPDESCRIPTION;
  }

  @Subscribe
  public void closeRequested(ShutdownCommencingEvent sce) {
    if (!sce.isCanceled() && myFrame_ != null) {
      myFrame_.dispose();
    }
  }
}
