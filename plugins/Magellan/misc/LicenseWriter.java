/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 *
 * @author Henry
 */
public class LicenseWriter {
   
   
   public static void main(String[] args) throws FileNotFoundException {
      String path = "C:/projects/micromanager1.4/plugins/Magellan/";
      for (File dir : new File(path).listFiles()) {
         if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
               if (f.getName().endsWith(".java")) {
                  String fileString = fileToString(f);
                  System.out.println(fileString);
                  String result = removeNetbeansDefault(fileString);
                   System.out.println(fileString);
               }
            }
         }
      }
      
   }
   
   //null if it doesn't match, chop off if it does
   public static String removeNetbeansDefault(String s) {
      String h1 = "/*\n * To change this license header, choose License Headers in Project Properties.\n * To change this template file, choose Tools | Templates\n"+
         " * and open the template in the editor.\n */";
      String h2 = "/*\n * To change this template, choose Tools | Templates\n * and open the template in the editor.\n */";
          
      if (s.startsWith(h1)) {
         System.out.println(s.substring(0, 110));
         System.out.println(h2);
         return s.replaceFirst(h1, "");
      } else if (s.startsWith(h2)) {
         return s.replaceFirst(h2, "");
      }
      return null;
   }
   
   public static String fileToString(File f) throws FileNotFoundException  {
    String s = "";
      Scanner scanner = new Scanner(f);
      scanner.useDelimiter("\n");
    while(scanner.hasNext()) {
       s += scanner.next() + "\n";
    }
    scanner.close();
    return s;
}
   
}
