package org.micromanager.internal.zmq;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mmcorej.CMMCore;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.magellan.api.MagellanAPI;
import org.micromanager.magellan.main.Magellan;
import org.zeromq.SocketType;

/**
 * implments request reply server (ie the reply part)
 */
public class ZMQServer extends ZMQSocketWrapper {

   private ExecutorService executor_;
   public static final int DEFAULT_PORT_NUMBER = 4827;
   public static final Map<Class, Integer> PORT_NUMBERS;

   static {
      PORT_NUMBERS = new HashMap<>();
      PORT_NUMBERS.put(CMMCore.class, 4828);
      PORT_NUMBERS.put(MagellanAPI.class, 4829);
   }

   private static ZMQServer coreServer_ = null;
   private static ZMQServer magellanServer_ = null;

   public ZMQServer(Studio studio, Class clazz) {
      super(studio, clazz, SocketType.REP);  
   }

   @Override
   protected void initialize(int port) {
      executor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "ZMQ Server " + name_));
      executor_.submit(() -> {
         socket_ = context_.createSocket(type_);
         socket_.bind("tcp://127.0.0.1:" + port);
         
         while (true) {
            String message = socket_.recvStr();
            byte[] reply = null;
            try {
               reply = parseAndExecuteCommand(message);
            } catch (Exception e) {
               try {
                  JSONObject json = new JSONObject();
                  json.put("type", "exception");
                  json.put("value", e.getMessage());
                  reply = json.toString().getBytes();
                  studio_.logs().logError(e);
               } catch (JSONException ex) {
                  //This wont happen
               }
            }
            socket_.send(reply);
         }
      });
   }

   @Override
   protected byte[] parseAndExecuteCommand(String message) throws Exception {
      JSONObject json = new JSONObject(message);

      switch (json.getString("command")) {
         case "connect":
            //   Hard coded startup commands that have corresponding methods in pygellan
            // These will startup a new server if needed to avoid cross blocking between
            // different APIs
            String server = json.getString("server");
            switch (server) {
               case "master": {
                  JSONObject reply = new JSONObject();
                  reply.put("reply", "success");
                  reply.put("version", Magellan.VERSION);
                  return reply.toString().getBytes();
               }
               case "core": {
                  if (coreServer_ == null) {
                     coreServer_ = new ZMQServer(studio_, CMMCore.class);
                  }
                  JSONObject reply = new JSONObject();
                  reply.put("reply", "success");
                  this.serialize(Magellan.getCore(), reply);
                  return reply.toString().getBytes();

               }
               case "magellan": {
                  if (magellanServer_ == null) {
                     magellanServer_ = new ZMQServer(studio_, MagellanAPI.class);
                  }
                  JSONObject reply = new JSONObject();
                  reply.put("reply", "success");
                  this.serialize(Magellan.getAPI(), reply);
                  return reply.toString().getBytes();
               }
               default:
                  break;
            }
            break;
         case "run-method": {
            String hashCode = json.getString("hash-code");
            Object target = EXTERNAL_OBJECTS.get(hashCode);
            return runMethod(target, json);
         }
         case "destructor": {
            String hashCode = json.getString("hash-code");
            //TODO this is defined in superclass, maybe it would be good to merge these?
            EXTERNAL_OBJECTS.remove(hashCode);
            JSONObject reply = new JSONObject();

            reply.put("reply", "success");
            this.serialize(Magellan.getAPI(), reply);
            return reply.toString().getBytes();
         }
         default:
            break;
      }
      throw new RuntimeException("Unknown Command");
   }

   @Override
   protected int getPort(Class clazz) {
      if (clazz == null) {
         return DEFAULT_PORT_NUMBER;
      }
      if (PORT_NUMBERS.containsKey(clazz)) {
         return PORT_NUMBERS.get(clazz);
      }
      //TODO: inherit port number?
      return DEFAULT_PORT_NUMBER;
   }

}
