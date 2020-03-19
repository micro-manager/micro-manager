package org.micromanager.internal.zmq;

import java.util.Collections;
import java.util.HashMap;
import org.json.JSONException;
import org.json.JSONObject;
import static org.micromanager.internal.zmq.ZMQServer.DEFAULT_MASTER_PORT_NUMBER;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

// Base class that wraps a ZMQ socket and implements type conversions as well 
// as the impicit JSON message syntax
public abstract class ZMQSocketWrapper {

   protected static ZContext context_;

   //map of port numbers to servers, each of which has its own thread and base class
   private static HashMap<Integer, ZMQSocketWrapper> portSocketMap_
           = new HashMap<Integer, ZMQSocketWrapper>();
   public static final int DEFAULT_MASTER_PORT_NUMBER = 4827;

   protected SocketType type_;
   protected volatile ZMQ.Socket socket_;
   protected int port_;

   public ZMQSocketWrapper(SocketType type) {
      type_ = type;
      if (context_ == null) {
         context_ = new ZContext();
      }
      port_ = nextPortNumber();
      portSocketMap_.put(port_, this);
      initialize(port_);
   }

   private int nextPortNumber() {
      return portSocketMap_.isEmpty() ? DEFAULT_MASTER_PORT_NUMBER : 
              Collections.max(portSocketMap_.keySet()) + 1;
   }
   
   public int getPort() {
      return port_;
   }

   public abstract void initialize(int port);

}
