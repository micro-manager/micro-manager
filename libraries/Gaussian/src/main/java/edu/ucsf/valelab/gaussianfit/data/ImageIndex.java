/*
Copyright (c) 2010-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
*/

package edu.ucsf.valelab.gaussianfit.data;

/**
 * Holds the frame, slice, channel, and position index Can be used to identify to which image a
 * given spot belongs
 *
 * @author nico
 */
public class ImageIndex {

   private final int frame_;
   private final int slice_;
   private final int channel_;
   private final int position_;

   public ImageIndex(int frame, int slice, int channel, int position) {
      frame_ = frame;
      slice_ = slice;
      channel_ = channel;
      position_ = position;
   }


   @Override
   public boolean equals(Object test) {
      if (!(test instanceof ImageIndex)) {
         return false;
      }
      ImageIndex t = (ImageIndex) test;
      return t.frame_ == frame_ && t.slice_ == slice_ &&
            t.channel_ == channel_ && t.position_ == position_;
   }


   @Override
   public int hashCode() {
      int hash = frame_ + 256 * slice_ + 256 * 256 * channel_ +
            256 * 256 * 256 * position_;
      return hash;
   }
}
