package org.micromanager.magellan.api;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.acq.MagellanAcquisitionsManager;
import static org.micromanager.magellan.api.ZMQSocketWrapper.parseAPI;
import org.micromanager.magellan.main.Magellan;
import org.zeromq.SocketType;

/**
 *
 * @author henrypinkard
 */
public class ZMQMagellanAcquisitionServer extends ZMQServer {
   
   public ZMQMagellanAcquisitionServer() {
      super(4830, "MagellanAcquisition", SocketType.REP);
   }
   
   protected byte[] parseAndExecuteCommand(String message) throws JSONException, NoSuchMethodException, IllegalAccessException {
      JSONObject json = new JSONObject(message);
      if (json.getString("command").equals("send-MagellanAcquisitionAPI-api")) {
         return parseAPI(Magellan.getAPI().getAcquisitions().get(0).getClass());
      } else if (json.getString("command").equals("run-method")) {
         String uuid = json.getString("UUID");
         MagellanAcquisitionAPI acq = MagellanAcquisitionsManager.getInstance().getAcquisitionByUUID(uuid);
         return runMethod(acq, json);
      }
      throw new RuntimeException("Unknown Command");
   }
      
}
