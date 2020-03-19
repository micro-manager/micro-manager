/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.zmq;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.micromanager.internal.zmq.ZMQSocketWrapper.context_;
import org.zeromq.SocketType;

/**
 *
 * @author henrypinkard
 */
public class ZMQPushSocket<T> extends ZMQSocketWrapper {
  
   private ExecutorService executor_;

   //Constructor for server the base class that runs on its own thread
   public ZMQPushSocket(int port, String name) {
      super(SocketType.PULL);
   }

   @Override
   public void initialize(int port) {
      executor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "ZMQ Pusher " ));
      executor_.submit(() -> {
         socket_ = context_.createSocket(type_);
         port_ = port;
         socket_.bind("tcp://127.0.0.1:" + port);
      });
   }

   /**
    * Serialize the object and send it out to any pulling sockets
    *
    * @param o
    */
   public Future push(T o) {
      return executor_.submit(() -> {
         socket_.send(ZMQUtil.serialize(o));
      });
   }

}
