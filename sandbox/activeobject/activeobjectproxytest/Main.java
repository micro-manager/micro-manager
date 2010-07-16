/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package activeobjectproxytest;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author arthur
 */
public class Main {
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
         for (int i = 0; i < 60; i++) {
            addService.add(2 * i, 3 * i, callback);
         }
         long elapsed = System.currentTimeMillis() - startTime;
         System.out.println("All calls finished: " + elapsed + " ms elapsed");

         PingService ping1 = (PingService) ActiveObject.newInstance(new Ping());
         PingService ping2 = (PingService) ActiveObject.newInstance(new Ping());
         ping1.setPartner(ping2);
         ping2.setPartner(ping1);
      try {
         Thread.sleep(1000);
      } catch (InterruptedException ex) {
         Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
      }
         ping1.run();
      try {
         Thread.sleep(10000);
      } catch (InterruptedException ex) {
         Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
      }
         ping1._stop();
         ping2._stop();
   }

   private static interface AddService {

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
   }


   public interface PingService {
      public void setPartner(PingService partner);
      public void run();
      public void _stop();

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

      public void _stop() {
         
      }
   }


}
