/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

/**
 *
 * @author Henry
 */
public class MagelUtils {

   public static int[] getIndices(String imageLabel) {
      int[] ind = new int[4];
      String[] s = imageLabel.split("_");
      for (int i = 0; i < 4; i++) {
         ind[i] = Integer.parseInt(s[i]);
      }
      return ind;
   }
}
