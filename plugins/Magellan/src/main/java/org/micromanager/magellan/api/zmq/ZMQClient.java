/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.api.zmq;

import static org.micromanager.magellan.api.zmq.ZMQSocketWrapper.context_;
import org.zeromq.SocketType;

/**
 * Base class for implementing ZMQ client behavior
 */
//public class ZMQClient extends ZMQSocketWrapper {
//
//   public ZMQClient(int port, String name, SocketType type) {
//      super(port, name, type);
//   }
//
//   protected void initialize(int port) {
//      socket_ = context_.createSocket(type_);
//      socket_.connect("tcp://127.0.0.1:" + port);
//   }
//
//   @Override
//   protected byte[] parseAndExecuteCommand(String message) throws Exception {
//      //nothing to do here
//      throw new RuntimeException("MEthod not valid for ZMQ clients");
//   }
//
//}
