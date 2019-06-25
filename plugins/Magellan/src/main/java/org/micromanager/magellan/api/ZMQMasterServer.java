/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.api;

import org.json.JSONException;
import org.json.JSONObject;
import static org.micromanager.magellan.api.ZMQServer.parseAPI;
import org.micromanager.magellan.main.Magellan;

/**
 *
 * @author henrypinkard
 */
public class ZMQMasterServer extends ZMQServer {
   
   private ZMQCoreServer coreServer_ = null;
   
   public ZMQMasterServer() {
      super(4827, "master");
   }
   
   protected byte[] parseAndExecuteCommand(String message) throws JSONException, NoSuchMethodException, IllegalAccessException {
      JSONObject json = new JSONObject(message);
      if (json.getString("command").equals("connect-master")) {
         JSONObject reply = new JSONObject();
         reply.put("reply", "success");
         return reply.toString().getBytes();
      } else if (json.getString("command").equals("start-core")) { 
         if (coreServer_ == null) {
            coreServer_ = new ZMQCoreServer();
         }
         JSONObject reply = new JSONObject();
         reply.put("reply", "success");
         return reply.toString().getBytes();
      }
      throw new RuntimeException("Unknown Command");
   }
   
}
