///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//package org.micromanager.internal.zmq;
//
//import org.micromanager.Studio;
//import org.zeromq.SocketType;
//
///**
// *
// * @author henrypinkard
// */
//public class ZMQClient extends ZMQSocketWrapper{
//
//   public ZMQClient(Studio studio, SocketType type) {
//      super(studio, type);
//   }
//   
//   /**
//    * send a command from a Java client to a python server and wait for response
//    *
//    * @param request Command to be send through the port
//    * @return response from the Python side
//    */
//   protected Object sendRequest(String request) {
//      socket_.send(request);
//      byte[] reply = socket_.recv();
//      return deserialize(reply);
//   }
//
//   @Override
//   public void initialize(int port) {
//      
//   }
//
//}
