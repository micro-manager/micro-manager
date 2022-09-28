///////////////////////////////////////////////////////////////////////////////
//PROJECT:       PWS Plugin
//
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nick Anthony, 2021
//
// COPYRIGHT:    Northwestern University, 2021
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
//

package org.micromanager.sharpnessinspector;

import ij.process.ImageProcessor;
import java.awt.Rectangle;
import org.micromanager.data.Image;
import org.micromanager.imageprocessing.ImgSharpnessAnalysis;
import org.micromanager.internal.MMStudio;

/**
 *
 * @author Nick Anthony
 */
public class SharpnessEvaluator {
   private final ImgSharpnessAnalysis anl = new ImgSharpnessAnalysis();
   // In my experience Redondo works much better than other methods.
   private ImgSharpnessAnalysis.Method method_ = ImgSharpnessAnalysis.Method.Redondo;

   public void setMethod(ImgSharpnessAnalysis.Method method) {
      method_ = method;
      anl.setComputationMethod(ImgSharpnessAnalysis.Method.valueOf(method.name()));
   }
    
   public ImgSharpnessAnalysis.Method getMethod() {
      return method_;
   }
    
   public double evaluate(Image img, Rectangle r) {
      ImageProcessor proc = MMStudio.getInstance().data().getImageJConverter().createProcessor(img);
      proc.setRoi(r);
      proc = proc.crop();
      return anl.compute(proc);
   }
    
   /* Before using the ImgSharpnessAnalysis we used to do it ourselves here
    private double evaluateGradient(Image img, Rectangle r) {
        GrayF32 im = new GrayF32(r.width, r.height);
        for (int i=0; i<r.width; i++) {
            for (int j=0; j<r.height; j++) {
                long intensity = img.getIntensityAt(r.x + i, r.y + j);
                im.set(i, j, (int) intensity);
            }
        }
        GrayF32 blurred = BlurImageOps.gaussian(im, null, -1, 3, null);
        PixelMath.divide(blurred, ImageStatistics.mean(blurred), blurred); //Normalize?
        GrayF32 dx = new GrayF32(im.width, im.height);
        GrayF32 dy = new GrayF32(im.width, im.height);
        GImageDerivativeOps.gradient(DerivativeType.THREE, blurred, dx, dy, BorderType.EXTENDED);
        //Calculate magnitude of the gradient
        PixelMath.pow2(dx, dx);
        PixelMath.pow2(dy, dy);
        GrayF32 mag = new GrayF32(dx.width, dx.height);
        PixelMath.add(dx, dy, mag);
        PixelMath.sqrt(mag, mag);
        float[] arr = mag.getData();
        double[] dubArr = new double[arr.length];
        for (int i = 0; i < arr.length; i++) { // must convert from float[] to double[]
            dubArr[i] = arr[i];
        }
        return new Percentile().evaluate(dubArr, 95);
    } */
}
