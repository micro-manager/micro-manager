/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.acq;

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.micromanager.magellan.misc.Log;

/**
 *
 * @author henrypinkard
 */
public class MagellanServer {

   private final ServerSocket socket_;
   private Socket client_;
   private InputStream inStream_;
   private OutputStream outStream_;
   private final ExecutorService executor_ = Executors.newSingleThreadExecutor((Runnable r)
           -> new Thread(r, "Acquisition control server"));

   public static void main(String[] args) throws IOException {
      new MagellanServer();
   }

   public MagellanServer() throws IOException {
      int port = 4827;
      socket_ = new ServerSocket(port);
      executor_.submit(listen());
   }

   private Runnable listen() {
      return new Runnable() {
         @Override
         public void run() {

            connectToClient();
            while (true) {
               //Read incoming messages
               try {
                  //first read the number of bytes of this message
                  if (inStream_.available() >= 4) {
                     byte[] buff = new byte[4];
                     inStream_.read(buff, 0, 4);
                     int numBytes = ByteBuffer.wrap(buff).order(ByteOrder.nativeOrder()).asIntBuffer().get(0);
                     //read message
                     byte[] message = new byte[numBytes];
                     ByteStreams.readFully(inStream_, message);
                     parseMessage(message);
                     //send back an answer
                     //TODO: maybe build a queue of message to send?
                     if (false) {
                        //nothing to send for now
                     } else {
                        //send a message alerting that there's nothing to send
                        ByteBuffer buffer = ByteBuffer.allocate(4);                        
                        buffer.order(ByteOrder.nativeOrder()).asIntBuffer().put(-1);
                        outStream_.write(buffer.array(), 0, 4);
                        outStream_.flush();
                     }
                  }
               } catch (IOException ex) {
                  ex.printStackTrace();
                  Log.log("Exception whule trying to read message from client");
                  Log.log(ex);
               }
            }
         }
      };
   }

   /**
    * Parse incoming message and take appropriate action
    */
   private void parseMessage(byte[] message) {
      System.out.println("Got message of length " + message.length);
      System.out.println(message[3]);
//      System.out.println(new String(message));
   }

   private void connectToClient() {
      try {
         client_ = socket_.accept();
         System.out.println("got connection on port 8080");
         inStream_ = client_.getInputStream();
         outStream_ = client_.getOutputStream();
      } catch (IOException ex) {
         Log.log(ex);
      }
   }

}
