package org.micromanager.internal.zmq;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import static org.micromanager.internal.zmq.ZMQServer.DEFAULT_MASTER_PORT_NUMBER;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

// Base class that wraps a ZMQ socket and implements type conversions as well 
// as the impicit JSON message syntax
public abstract class ZMQSocketWrapper {

   protected static ZContext context_;

   //map of port numbers to servers, each of which has its own thread and base class
   private static ConcurrentHashMap<Integer, ZMQSocketWrapper> portSocketMap_
           = new ConcurrentHashMap<Integer, ZMQSocketWrapper>();
   public static final int DEFAULT_MASTER_PORT_NUMBER = 4827;
//   public static int nextPort_ = DEFAULT_MASTER_PORT_NUMBER;

   protected SocketType type_;
   protected volatile ZMQ.Socket socket_;
   protected int port_;

   public ZMQSocketWrapper(SocketType type) {
      type_ = type;
      if (context_ == null) {
         context_ = new ZContext();
      }
      port_ = nextPortNumber(this);
//      System.out.println("port: " + port_ + "\t\t" + this);
      initialize(port_);
   }

   private static synchronized int nextPortNumber(ZMQSocketWrapper t) {
      int port = portSocketMap_.isEmpty() ? DEFAULT_MASTER_PORT_NUMBER : 
              Collections.max(portSocketMap_.keySet()) + 1;
//         int port = nextPort_;
//         nextPort_++;

      portSocketMap_.put(port, t);
      return port;
   }
   
   public int getPort() {
      return port_;
   }

   public abstract void initialize(int port);
   
   public void close() {
      socket_.close();
      portSocketMap_.remove(socket_);
   }

}
