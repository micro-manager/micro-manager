/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.plugins.magellan.propsandcovariants;

import java.io.File;
import java.io.FileNotFoundException;
import java.rmi.activation.ActivationSystem;
import java.util.Arrays;
import java.util.Scanner;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.MatrixUtils;


public class LaserPredNet {


   Array2DRowRealMatrix W1_, B1_, W2_, B2_;
   public static LaserPredNet singleton_;
      
   public LaserPredNet(String filename) throws FileNotFoundException {
      readModel(filename);
      singleton_ = this;
   }
   
 public byte[] forwardPass(double[][] x) {
     double[] ones = new double[x.length];
     Arrays.fill(ones, 1.0);
     Array2DRowRealMatrix onesMat = new Array2DRowRealMatrix(ones);
      //assume x is properly normalized
      new Array2DRowRealMatrix(x[0]);
      Array2DRowRealMatrix xMat = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(x);
      Array2DRowRealMatrix h = xMat.multiply(W1_).add(onesMat.multiply(B1_));
      relu(h);
      Array2DRowRealMatrix z = (Array2DRowRealMatrix) h.multiply(W2_.transpose()).add(onesMat.multiply(B2_));
      byte[] powers = new byte[z.getRowDimension()*z.getColumnDimension()];
      for (int i = 0; i < powers.length; i++) {
         powers[i] = (byte) Math.max(0, Math.min(255, z.getEntry(i, 0)));
      }
      return powers;  
   }
   
  private static void relu(Array2DRowRealMatrix activations) {      
      for (int r = 0; r < activations.getRowDimension(); r++) {
         for (int c = 0; c < activations.getColumnDimension(); c++) {
            if (activations.getEntry(r, c) < 0) {
               activations.setEntry(r, c, 0.0);
            }
         }
      }
   }
 
   private void readModel(String filename) throws FileNotFoundException {
      Scanner s = new Scanner(new File(filename));
      double[][] w1 = new double[12][30];
      double[][] b1 = new double[1][30];
      double[][] w2 = new double[1][30];
      double[][] b2 = new double[1][1];
      double[][] var = null;      
      int index = 0;
      int matCount = 0;
      while(s.hasNext()) {
         String line = s.nextLine();
         if (line.toLowerCase().startsWith("fc")) {
            //new variable
            if (matCount == 0) {
               var = w1;
            } else if (matCount == 1) {
               var = b1;
            } else if (matCount == 2) {
               var = w2;
            } else {
               var = b2;
            }
            matCount++;
            index = 0;
         } else {
            String[] entries = line.split(",");
            for (int i = 0; i < entries.length; i++) {
               var[index / var[0].length][index % var[0].length] = Double.parseDouble(entries[i]);
    
               index++;
            }
         }    
      }
      //convert model to Apache commons matrices
      W1_ = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(w1);
      B1_ = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(b1);
      W2_ = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(w2);
      B2_ = (Array2DRowRealMatrix) MatrixUtils.createRealMatrix(b2);              
   }
   
      
}
