/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.valelab.gaussianfit.utils;

/**
 * Code from stackoverflow:
 * http://stackoverflow.com/questions/8797722/modified-bessel-functions-of-order-n
 * License unclear. Remove when an alternative is available
 *
 * @author Michael
 */
public class Besseli {

   /**
    * Functions that aren't part of standard libraries User: Michael Date:
    * 1/9/12 Time: 9:22 PM
    */
   public static final double ACC = 4.0;
   public static final double BIGNO = 1.0e10;
   public static final double BIGNI = 1.0e-10;

   public static final double bessi0(double x) {
      double answer;
      double ax = Math.abs(x);
      if (ax < 3.75) { // polynomial fit
         double y = x / 3.75;
         y *= y;
         answer = 1.0 + y * (3.5156229 + y * (3.0899424 + y * (1.2067492
                 + y * (0.2659732 + y * (0.360768e-1 + y * 0.45813e-2)))));
      } else {
         double y = 3.75 / ax;
         answer = 0.39894228 + y * (0.1328592e-1 + y * (0.225319e-2
                 + y * (-0.157565e-2 + y * (0.916281e-2 + y * (-0.2057706e-1
                 + y * (0.2635537e-1 + y * (-0.1647633e-1 + y * 0.392377e-2)))))));
         answer *= (Math.exp(ax) / Math.sqrt(ax));
      }
      return answer;
   }

   public static final double bessi1(double x) {
      double answer;
      double ax = Math.abs(x);
      if (ax < 3.75) { // polynomial fit
         double y = x / 3.75;
         y *= y;
         answer = ax * (0.5 + y * (0.87890594 + y * (0.51498869 + y * (0.15084934 + y * (0.2658733e-1 + y * (0.301532e-2 + y * 0.32411e-3))))));
      } else {
         double y = 3.75 / ax;
         answer = 0.2282967e-1 + y * (-0.2895312e-1 + y * (0.1787654e-1 - y * 0.420059e-2));
         answer = 0.39894228 + y * (-0.3988024e-1 + y * (-0.362018e-2 + y * (0.163801e-2 + y * (-0.1031555e-1 + y * answer))));
         answer *= (Math.exp(ax) / Math.sqrt(ax));
      }
      return answer;
   }

   public static final double bessi(int n, double x) {
      if (n == 0) {
         return bessi0(x);
      }
      if (n == 1) {
         return bessi1(x);
      }
      if (n < 2) {
         throw new IllegalArgumentException("Function order must be greater than 1");
      }
      if (x == 0.0) {
         return 0.0;
      } else {
         double tox = 2.0 / Math.abs(x);
         double ans = 0.0;
         double bip = 0.0;
         double bi = 1.0;
         for (int j = 2 * (n + (int) Math.sqrt(ACC * n)); j > 0; --j) {
            double bim = bip + j * tox * bi;
            bip = bi;
            bi = bim;
            if (Math.abs(bi) > BIGNO) {
               ans *= BIGNI;
               bi *= BIGNI;
               bip *= BIGNI;
            }
            if (j == n) {
               ans = bip;
            }
         }
         ans *= bessi0(x) / bi;
         return (((x < 0.0) && ((n % 2) == 0)) ? -ans : ans);
      }
   }
}
