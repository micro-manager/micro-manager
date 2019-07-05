package org.micromanager.magellan.api;

import org.json.JSONException;
import org.json.JSONObject;
import org.zeromq.SocketType;

/**
 *
 * @author henrypinkard
 */
public class ZMQMasterServer extends ZMQServer {
   
   private ZMQCoreServer coreServer_ = null;
   private ZMQMagellanServer magellanServer_ = null;
   private ZMQMagellanAcquisitionServer magellanAcqServer_ = null;

   
   public ZMQMasterServer() {
      super(4827, "master", SocketType.REP);
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
      } else if (json.getString("command").equals("start-magellan")) { 
         if (magellanServer_ == null) {
            magellanServer_ = new ZMQMagellanServer();
         }
         if (magellanAcqServer_ == null) {
            magellanAcqServer_ = new ZMQMagellanAcquisitionServer();
         }
         JSONObject reply = new JSONObject();
         reply.put("reply", "success");
         return reply.toString().getBytes();
      }
      throw new RuntimeException("Unknown Command");
   }
   
}
