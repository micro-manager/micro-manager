/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.zmq;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mmcorej.CMMCore;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.acq.MagellanAcquisitionsManager;
import org.micromanager.magellan.api.MagellanAPI;
import org.micromanager.magellan.api.MagellanAcquisitionAPI;
import static org.micromanager.internal.zmq.ZMQSocketWrapper.parseAPI;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.Log;
import org.zeromq.SocketType;

/**
 * implments request reply server (ie the reply part)
 */
public class ZMQServer extends ZMQSocketWrapper {

   private ExecutorService executor_;
   public static final int DEFAULT_PORT_NUMBER = 4827;
   public static final HashMap<Class, Integer> PORT_NUMBERS;

   static {
      PORT_NUMBERS = new HashMap<Class, Integer>();
      PORT_NUMBERS.put(CMMCore.class, 4828);
      PORT_NUMBERS.put(MagellanAPI.class, 4829);
//      PORT_NUMBERS.put(MagellanAcquisitionAPI.class, 4830);
   }

   private static ZMQServer coreServer_ = null;
   private static ZMQServer magellanServer_ = null;
//   private static ZMQServer magellanAcqServer_ = null;

   public ZMQServer(Class clazz) {
      super(clazz, SocketType.REP);
   }

   protected void initialize(int port) {
      executor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "ZMQ Server " + name_));
      executor_.submit(new Runnable() {
         @Override
         public void run() {
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
                     e.printStackTrace();
                     Log.log(e.getMessage());
                  } catch (JSONException ex) {
                     //This wont happen
                  }
               }
               socket_.send(reply);
            }
         }
      });
   }

   @Override
   protected byte[] parseAndExecuteCommand(String message) throws Exception {
      JSONObject json = new JSONObject(message);

      if (json.getString("command").equals("connect")) {
         ////   Hard coded startup commands that have corresponding methods in pygellan
         // These will startup a new server if needed to avoid cross blocking between 
         //different APIs
         String server = json.getString("server");
         if (server.equals("master")) {
            JSONObject reply = new JSONObject();
            reply.put("reply", "success");
            reply.put("version", Magellan.VERSION);
            return reply.toString().getBytes();
         } else if (server.equals("core")) {
            if (coreServer_ == null) {
               coreServer_ = new ZMQServer(CMMCore.class);
            }
            JSONObject reply = new JSONObject();
            reply.put("reply", "success");
            this.serialize(Magellan.getCore(), reply);
            return reply.toString().getBytes();

         } else if (server.equals("magellan")) {
            if (magellanServer_ == null) {
               magellanServer_ = new ZMQServer(MagellanAPI.class);
            }
//            if (magellanAcqServer_ == null) {
//               magellanAcqServer_ = new ZMQServer(MagellanAcquisitionAPI.class, SocketType.REP);
//            }
            JSONObject reply = new JSONObject();
            reply.put("reply", "success");
            this.serialize(Magellan.getAPI(), reply);
            return reply.toString().getBytes();
         }
      } else if (json.getString("command").equals("run-method")) {
         String hashCode = json.getString("hash-code");
         Object target = externalObjects_.get(hashCode);
         return runMethod(target, json);
      } else if (json.getString("command").equals("destructor")) {
         String hashCode = json.getString("hash-code");
         //TODO this is defined in superclass, maybe it would be good to merge these?
         externalObjects_.remove(hashCode);
         JSONObject reply = new JSONObject();

         reply.put("reply", "success");
         this.serialize(Magellan.getAPI(), reply);
         return reply.toString().getBytes();
      }
      throw new RuntimeException("Unknown Command");
   }

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
