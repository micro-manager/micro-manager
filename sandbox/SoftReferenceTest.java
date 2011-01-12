import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Arthur Edelstein, arthuredelstein@gmail.com, 2011.01.04
 * UCSF
 * 
 * This program lets you test the effects of different JVM command line
 * settings on SoftReferences. We create 1 MB objects every 100 ms
 * and hold them in SoftReferences.
 * 
 * Example run command:
 * java  -Xmx1000M -server -XX:SoftRefLRUPolicyMSPerMB=1000000000000000 SoftReferenceTest 800 100
 * 1 GB maximum heap, server mode, only garbage collect SoftRefs when necessary, allocate 800 1-MB objects, 100 ms sleeps
 */
public class SoftReferenceTest {

   /**
    * @param args the command line arguments
    */
   public static void main(String[] args) {
      final int MBsize = 1024*1024;
      final double MB_d = (double) MBsize;
      Runtime r = Runtime.getRuntime();
      List<SoftReference> myList = new ArrayList<SoftReference>();
      int n = Integer.parseInt(args[0]);
      int dt = Integer.parseInt(args[1]);
      long t0 = System.currentTimeMillis();
      long t;
      for (int i = 0; ;) {
         if (i<n) {
            SoftReference<byte[]> x = new SoftReference<byte []>(new byte[MBsize]);
            myList.add(x);
            ++i;
         }
         t = System.currentTimeMillis() - t0;
         try {
            Thread.sleep(dt);
         } catch (InterruptedException ex) {}
         System.out.format("t: %.2f\tallocated: %.2f\tused: %.2f\theap: %.2f\tmax: %.2f\n", t/(double) 1000., (double) i, (r.totalMemory()-r.freeMemory())/MB_d, r.totalMemory() / MB_d, r.maxMemory() / MB_d);
      }
   }
}
