/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.activeobjects;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.utils.JavaUtils;

/**
 *
 * @author arthur
 */
public class ActiveObjectTest {
   /**
    * @param args the command line arguments
    */

   public static void main(String[] args) {

         AddService addService = (AddService) ActiveObject.newInstance(new Adder());
         AddResultsCallback callback = new AddResultsCallback() {
         Integer x = new Integer(1);

            public void addResultsComputed(int x, int y, int sum) {
               System.out.println(x + " + " + y + " = " + sum);
            }
         };
         long startTime = System.currentTimeMillis();
         for (int i = 0; i <= 60; i++) {
            addService.add(2 * i, 3 * i, callback);
         }
         long elapsed = System.currentTimeMillis() - startTime;
         System.out.println("All calls finished: " + elapsed + " ms elapsed");

         PingService ping1 = (PingService) ActiveObject.newInstance(new Ping());
         PingService ping2 = (PingService) ActiveObject.newInstance(new Ping());
         ping1.setPartner(ping2);
         ping2.setPartner(ping1);
         JavaUtils.sleep(1000);
         System.out.println("running.");
         ping1.run();
         JavaUtils.sleep(10000);
         System.out.println("pausing pings...");
         ping1.pause();
         ping2.pause();
         JavaUtils.sleep(5000);
         System.out.println("resuming pings...");
         ping1.resume();
         ping2.resume();
         JavaUtils.sleep(5000);
         ping1.stop();
         ping2.stop();
         System.out.println("stopped pings");
   }

   private static interface AddService extends Activatable {

      public void add(int x, int y, AddResultsCallback callback);
   }

   private static interface AddResultsCallback {

      public void addResultsComputed(int x, int y, int sum);
   }

   public static class Adder implements AddService {

      public void add(int x, int y, AddResultsCallback callback) {
         int sum = x + y;
         try {
            Thread.sleep(1000);
         } catch (InterruptedException e) {
         }
         callback.addResultsComputed(x, y, sum);
      }

      public void pause() {
      }

      public void resume() {
      }

      public void stop() {
      }
   }


   public interface PingService extends Activatable {
      public void setPartner(PingService partner);
      public void run();
   }
   


   public static class Ping implements PingService {
      private PingService partner;

      public void setPartner(PingService partner) {
         this.partner = partner;
      }

      public void run() {
         System.out.println("Ping! " + Thread.currentThread());
         partner.run();
         try {
            Thread.sleep((int) (Math.random()*1000));
         } catch (InterruptedException ex) {

         }

      }

      public void stop() {
         System.out.println("Ack! I've been stopped!");
      }
      public void pause() {}
      public void resume() {}
   }


}
