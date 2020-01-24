package org.micromanager.internal.zmq;

import org.json.JSONObject;
import org.micromanager.magellan.acq.AcquisitionEvent;
import org.zeromq.SocketType;

/**
 *
 * @author henrypinkard
 */
//public class ZMQAcquisitionHookClient extends ZMQClient {
   
//   public ZMQAcquisitionHookClient() {
//      super(4829, "CMMCore", SocketType.REQ);
//   }
//   
//   public void executeHook(AcquisitionEvent event) {
//      //serialize Acquisition event
//      JSONObject json = event.toJSON();
//      //send message
//      socket_.send(json.toString());
//      //wait on reply even though there's no return to ensure that acquisition thread blocks
//      String reply = socket_.recvStr();
//      //TODO: check for exception
//   }
//   
   
//}
