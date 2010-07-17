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

         Adder adder = (Adder) ActiveObject.newInstance(Adder.class);

         Double sum = new Double(0);
         for (int j=0;j<100;++j) {
            sum += adder.getRandNumber();
         }
         System.out.println(sum);


         AddResultsCallback callback = new AddResultsCallback() {
         Integer x = new Integer(1);

            public void addResultsComputed(int x, int y, int sum) {
               System.out.println(x + " + " + y + " = " + sum);
            }
         };
         long startTime = System.currentTimeMillis();
         for (int i=0;i<10;++i)
            adder.add(2 * i, 3 * i, callback);
         long elapsed = System.currentTimeMillis() - startTime;
         System.out.println("All calls finished: " + elapsed + " ms elapsed");

        Ping ping1 = (Ping) ActiveObject.newInstance(Ping.class);
         Ping ping2 = (Ping) ActiveObject.newInstance(Ping.class);
         ping1.setPartner(ping2);
         ping2.setPartner(ping1);
         
         
         Sleep(1000);
         ping1.run();
         Sleep(1000);
         ping1._stop();
         ping2._stop();
   }


   public static interface AddResultsCallback {

      public void addResultsComputed(int x, int y, int sum);
   }

   public static class Adder {

      public void add(int x, int y, AddResultsCallback callback) {
         int sum = x + y;
         Sleep(1000);
         callback.addResultsComputed(x, y, sum);
      }

      public Double getRandNumber() {
         Sleep(1000);
         return new Double(Math.random());
      }
      
   }


   public static class Ping {
      private Ping partner;

      public void setPartner(Ping partner) {
         this.partner = partner;
      }
      
      public void run() {
         System.out.println("Ping! " + Thread.currentThread());
         partner.run();
         Sleep((int) (Math.random()*1000));
      }

      public void _stop() {
         
      }
   }

   public static void Sleep(int timeMs) {
      try {
         Thread.sleep(timeMs);
      } catch (InterruptedException ex) {
      }
   }

}
