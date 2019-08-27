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
public class ZMQMagellanServer extends ZMQServer {
   
   public ZMQMagellanServer() {
      super(4829, "MagellanAPI", SocketType.REP);
   }
   
   protected byte[] parseAndExecuteCommand(String message) throws JSONException, NoSuchMethodException, IllegalAccessException {
      JSONObject json = new JSONObject(message);
      if (json.getString("command").equals("send-MagellanAPI-api")) {
         return parseAPI(Magellan.getAPI().getClass());
      } else if (json.getString("command").equals("run-method")) {
         return runMethod(Magellan.getAPI(), json);
      }
      throw new RuntimeException("Unknown Command");
   }
      
}
