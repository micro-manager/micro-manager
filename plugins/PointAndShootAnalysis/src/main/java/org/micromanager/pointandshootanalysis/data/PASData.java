 ///////////////////////////////////////////////////////////////////////////////
 //FILE:          PASData.java     
 //PROJECT:       PointAndShootAnalysis
 //-----------------------------------------------------------------------------
 //
 // AUTHOR:       Nico Stuurman
 //
 // COPYRIGHT:    University of California, San Francisco 2018
 //
 // LICENSE:      This file is distributed under the BSD license.
 //               License text is included with the source distribution.
 //
 //               This file is distributed in the hope that it will be useful,
 //               but WITHOUT ANY WARRANTY; without even the implied warranty
 //               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 //
 //               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 //               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 //               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.pointandshootanalysis.data;

import java.awt.Point;
import java.time.Instant;

/**
 * Note PAS is short for "Point And Shoot"
 * 
 * @author nico
 */
public class PASData {

   private final Instant pasClicked_; 
   private final Instant tsOfFrameBeforePas_;  
   private final int framePasClicked_; 
   private final Point pasIntended_; 
   private final Point pasActual_;   
   private final int[] pasFrames_; 

   public static class Builder {

      private Instant pasClicked_;
      private Instant tsOfFrameBeforePas_;
      private int framePasClicked_;
      private Point pasIntended_;
      private Point pasActual_;
      private int[] pasFrames_;
      
      private Builder copy(Instant pasClicked,
              Instant tsOfFrameBeforePas,
              int framePasClicked,
              Point pasIntended,
              Point pasActual,
              int[] pasFrames) {
         pasClicked_ = pasClicked;
         tsOfFrameBeforePas_ = tsOfFrameBeforePas;
         framePasClicked_ = framePasClicked;
         pasIntended_ = pasIntended;
         pasActual_ = pasActual;
         pasFrames_ = pasFrames;
         return this;
      }
      
      public Builder pasClicked(Instant inst) { pasClicked_ = inst; return this; }
      public Builder tsOfFrameBeforePas(Instant inst) { tsOfFrameBeforePas_ = inst; return this;}
      public Builder framePasClicked(int f) { framePasClicked_ = f; return this;}
      public Builder pasIntended(Point p) {pasIntended_ = p; return this;}
      public Builder pasActual(Point p) {pasActual_ = p; return this;}
      public Builder pasFrames(int[] pasFrames) { pasFrames_ = pasFrames; return this;}
      
      public PASData build() {
         return new PASData(pasClicked_,
                 tsOfFrameBeforePas_,
                 framePasClicked_,
                 pasIntended_,
                 pasActual_,
                 pasFrames_);
      }

   }
   
   public static Builder builder() { return new Builder(); }

   /**
    * PAS is short for "Point And Shoot"
    * 
    * 
    * @param pasClicked Instant when PAS was Clicked (as recorded by the Projector plugin
    * @param tsOfFrameBeforePas Instant of frame during which PAS was clicked
    * @param framePasClicked frame number when point and shoot was clicked
    *                        0-based (as in MM, unlike ImageJ)
    * @param pasIntended X Y coordinates (in pixels) where Point And Shoot was aimed
    * @param pasActual X Y coordinates (in pixels where Point And Shoot actually happened
    * @param pasFrames Frames during which the bleach laser was on
    *                         0-based (as in MM, unlike ImageJ)
    */
   private PASData(Instant pasClicked,
           Instant tsOfFrameBeforePas,
           int framePasClicked,
           Point pasIntended,
           Point pasActual,
           int[] pasFrames) {
      pasClicked_ = pasClicked;
      tsOfFrameBeforePas_ = tsOfFrameBeforePas;
      framePasClicked_ = framePasClicked;
      pasIntended_ = pasIntended;
      pasActual_ = pasActual;
      pasFrames_ = pasFrames;
   }
   
   public Instant pasClicked() {return pasClicked_;}
   public Instant tsOfFrameBeforePas() {return tsOfFrameBeforePas_;}
   public int framePasClicked() {return framePasClicked_;}
   public Point pasIntended() {return pasIntended_;}
   public Point pasActual() { return pasActual_;}
   public int[] pasFrames() { return pasFrames_;}
   
   public Builder copyBuilder() {
      Builder b = new Builder();
      b.copy (pasClicked_, tsOfFrameBeforePas_, framePasClicked_, pasIntended_,
              pasActual_, pasFrames_);
      return b;
   }

}
