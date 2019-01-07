/* 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.micromanager.pointandshootanalysis.algorithm;

import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayF64;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayS64;
import boofcv.struct.image.GrayS8;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.Planar;

/**
 * Collection of functions that could be in boofcv.alg.misc.GPixelMath but 
 * aren't
 * 
 * @author Nico
 */
public class GPixelMath {
   
   /**
	 * Computes the minimum for each pixel across all bands in the {@link Planar} image.
	 *
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static <T extends ImageGray<T>> void minimumBand(Planar<T> input, T output) {

		if( GrayU8.class == input.getBandType() ) {
			PixelMath.minimumBand((Planar<GrayU8>) input, (GrayU8) output);
		} else if( GrayS8.class == input.getBandType() ) {
			PixelMath.minimumBand((Planar<GrayS8>) input, (GrayS8) output);
		} else if( GrayU16.class == input.getBandType() ) {
			PixelMath.minimumBand((Planar<GrayU16>) input, (GrayU16) output);
		} else if( GrayS16.class == input.getBandType() ) {
			PixelMath.minimumBand((Planar<GrayS16>) input, (GrayS16) output);
		} else if( GrayS32.class == input.getBandType() ) {
			PixelMath.minimumBand((Planar<GrayS32>) input, (GrayS32) output);
		} else if( GrayS64.class == input.getBandType() ) {
			PixelMath.minimumBand((Planar<GrayS64>) input, (GrayS64) output);
		} else if( GrayF32.class == input.getBandType() ) {
			PixelMath.minimumBand((Planar<GrayF32>) input, (GrayF32) output);
		} else if( GrayF64.class == input.getBandType() ) {
			PixelMath.minimumBand((Planar<GrayF64>) input, (GrayF64) output);
		} else {
			throw new IllegalArgumentException("Unknown image Type: "+input.getBandType().getSimpleName());
		}
	}
   
    /**
	 * Computes the maximum for each pixel across all bands in the {@link Planar} image.
	 *
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static <T extends ImageGray<T>> void maximumBand(Planar<T> input, T output) {

		if( GrayU8.class == input.getBandType() ) {
			PixelMath.maximumBand((Planar<GrayU8>) input, (GrayU8) output);
		} else if( GrayS8.class == input.getBandType() ) {
			PixelMath.maximumBand((Planar<GrayS8>) input, (GrayS8) output);
		} else if( GrayU16.class == input.getBandType() ) {
			PixelMath.maximumBand((Planar<GrayU16>) input, (GrayU16) output);
		} else if( GrayS16.class == input.getBandType() ) {
			PixelMath.maximumBand((Planar<GrayS16>) input, (GrayS16) output);
		} else if( GrayS32.class == input.getBandType() ) {
			PixelMath.maximumBand((Planar<GrayS32>) input, (GrayS32) output);
		} else if( GrayS64.class == input.getBandType() ) {
			PixelMath.maximumBand((Planar<GrayS64>) input, (GrayS64) output);
		} else if( GrayF32.class == input.getBandType() ) {
			PixelMath.maximumBand((Planar<GrayF32>) input, (GrayF32) output);
		} else if( GrayF64.class == input.getBandType() ) {
			PixelMath.maximumBand((Planar<GrayF64>) input, (GrayF64) output);
		} else {
			throw new IllegalArgumentException("Unknown image Type: "+input.getBandType().getSimpleName());
		}
	}
   
}
