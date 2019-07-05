/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.misc.Log;
import org.zeromq.SocketType;

/**
 * implments and abstract request reply server (ie the reply part)
 */
public abstract class ZMQServer extends ZMQSocketWrapper {

   private ExecutorService executor_;

   public ZMQServer(int port, String name, SocketType type) {
      super(port, name, type);
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
                     json.put("reply", "Exception");
                     json.put("message", e.getMessage());
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

}
