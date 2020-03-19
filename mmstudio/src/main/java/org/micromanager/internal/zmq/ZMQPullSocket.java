/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.zmq;

import java.util.function.Function;
import org.json.JSONObject;
import org.zeromq.SocketType;

/**
 * Does not run on its own thread
 * @author henrypinkard
 */
public class ZMQPullSocket<T> extends ZMQSocketWrapper {
   
   Function<JSONObject, T> deserializationFunction_;

   public ZMQPullSocket(String name, Function<JSONObject, T> deserializationFunction) {
      super(SocketType.PULL);
      deserializationFunction_ = deserializationFunction;
   }

   @Override
   public void initialize(int port) {
      socket_ = context_.createSocket(type_);
      port_ = port;
      socket_.connect("tcp://127.0.0.1:" + port);
   }


   public T next() {
      return (T) ZMQUtil.deserialize(socket_.recv(), deserializationFunction_);
   }
}
