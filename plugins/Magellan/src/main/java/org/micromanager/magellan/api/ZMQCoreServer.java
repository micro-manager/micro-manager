/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.api;

import org.json.JSONException;
import org.json.JSONObject;
import static org.micromanager.magellan.api.ZMQSocketWrapper.parseAPI;
import org.micromanager.magellan.main.Magellan;
import org.zeromq.SocketType;

/**
 *
 * @author henrypinkard
 */
public class ZMQCoreServer extends ZMQServer {
   
   public ZMQCoreServer() {
      super(4828, "CMMCore", SocketType.REP);
   }
   
   
   protected byte[] parseAndExecuteCommand(String message) throws JSONException, NoSuchMethodException, IllegalAccessException {
      JSONObject json = new JSONObject(message);
      if (json.getString("command").equals("send-CMMCore-api")) {
         Class coreClass = Magellan.getCore().getClass();
         return parseAPI(coreClass);
      } else if (json.getString("command").equals("run-method")) {
         return runMethod(Magellan.getCore(), json);
      }
      throw new RuntimeException("Unknown Command");
   }
      
}
