/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package softreferencetest;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author arthur
 */
public class SoftReferenceTest {

   /**
    * @param args the command line arguments
    */
   public static void main(String[] args) {
      Runtime r = Runtime.getRuntime();
      List myList = new ArrayList();
      for (int i = 0; i < 1000; ++i) {
         SoftReference x = new SoftReference(new byte[1000000]);
         myList.add(x);
         try {
            Thread.sleep(100);
         } catch (InterruptedException ex) {}
         System.out.println("free:" + r.freeMemory() / 1024. / 1024.
                 + " total: " + r.totalMemory() / 1024. / 1024.
                 + " max: " + r.maxMemory() / 1024. / 1024.);
      }
   }
}
