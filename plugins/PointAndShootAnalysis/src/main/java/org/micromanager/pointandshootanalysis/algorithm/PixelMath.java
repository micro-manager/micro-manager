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
import boofcv.struct.image.Planar;

/**
 * Collection of functions that could be part of boofcv.alg.misc.PixelMath
 * but aren't
 * 
 * 
 * @author Nico
 */
public class PixelMath {
   
   
   /**
	 * Computes the minimum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void minimumBand(Planar<GrayU8> input , GrayU8 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayU8[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				int minimum = Byte.MAX_VALUE & 0xFF;
				for( int i = 0; i < bands.length; i++ ) {
					if ( (bands[i].data[ indexInput ] & 0xFF) < minimum) {
                  minimum =  (bands[i].data[ indexInput ] & 0xFF);
               }
				}
				output.data[indexOutput] = (byte) minimum;
			}
		}
	}
   
   /**
	 * Computes the minimum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void minimumBand(Planar<GrayS8> input , GrayS8 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayS8[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				byte minimum = Byte.MAX_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
					if (bands[i].data[ indexInput ] < minimum) {
                  minimum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = minimum;
			}
		}
	}
   
   /**
	 * Computes the minimum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void minimumBand(Planar<GrayU16> input , GrayU16 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayU16[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				int minimum = Short.MAX_VALUE & 0xFFFF;
				for( int i = 0; i < bands.length; i++ ) {
					if ( (bands[i].data[ indexInput ] & 0xFFFF) < minimum) {
                  minimum = (bands[i].data[ indexInput ] & 0xFFFF);
               }
				}
				output.data[indexOutput] = (short) minimum;
			}
		}
	}

   
   /**
	 * Computes the average for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void minimumBand(Planar<GrayS16> input , GrayS16 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayS16[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				short minimum = Short.MAX_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
					if (bands[i].data[ indexInput ] < minimum) {
                  minimum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = minimum;
			}
		}
	}
   
   /**
	 * Computes the minimum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void minimumBand(Planar<GrayS32> input , GrayS32 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayS32[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				int minimum = Integer.MAX_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
               if (bands[i].data[ indexInput ] < minimum) {
                  minimum = bands[i].data[ indexInput ];
               }
            }
				output.data[indexOutput] = minimum;
			}
		}
	}

   
   /**
	 * Computes the minimum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void minimumBand(Planar<GrayS64> input , GrayS64 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayS64[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				long minimum = Long.MAX_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
               if (bands[i].data[ indexInput ] < minimum) {
                  minimum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = minimum;
			}
		}
	}

   
   /**
	 * Computes the minimum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void minimumBand(Planar<GrayF32> input , GrayF32 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayF32[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				float minimum = Float.MAX_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
               if (bands[i].data[ indexInput ] < minimum) {
                  minimum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = minimum;
			}
		}
	}
   
   /**
	 * Computes the minimum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing minimum pixel values
	 */
	public static void minimumBand(Planar<GrayF64> input , GrayF64 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayF64[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				double minimum = Double.MAX_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
               if (bands[i].data[ indexInput ] < minimum) {
                  minimum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = minimum;
			}
		}
	}

   /**
	 * Computes the maximum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void maximumBand(Planar<GrayU8> input , GrayU8 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayU8[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				int maximum = Byte.MIN_VALUE & 0xFF;
				for( int i = 0; i < bands.length; i++ ) {
					if ( (bands[i].data[ indexInput ] & 0xFF) > maximum) {
                  maximum =  (bands[i].data[ indexInput ] & 0xFF);
               }
				}
				output.data[indexOutput] = (byte) maximum;
			}
		}
	}
   
   /**
	 * Computes the maximum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void maximumBand(Planar<GrayS8> input , GrayS8 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayS8[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				byte maximum = Byte.MIN_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
					if (bands[i].data[ indexInput ] > maximum) {
                  maximum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = maximum;
			}
		}
	}
   
   /**
	 * Computes the maximum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void maximumBand(Planar<GrayU16> input , GrayU16 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayU16[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				int maximum = Short.MIN_VALUE & 0xFFFF;
				for( int i = 0; i < bands.length; i++ ) {
					if ( (bands[i].data[ indexInput ] & 0xFFFF) > maximum) {
                  maximum = (bands[i].data[ indexInput ] & 0xFFFF);
               }
				}
				output.data[indexOutput] = (short) maximum;
			}
		}
	}

   
   /**
	 * Computes the average for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void maximumBand(Planar<GrayS16> input , GrayS16 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayS16[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				short maximum = Short.MIN_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
					if (bands[i].data[ indexInput ] > maximum) {
                  maximum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = maximum;
			}
		}
	}
   
   /**
	 * Computes the maximum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void maximumBand(Planar<GrayS32> input , GrayS32 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayS32[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				int maximum = Integer.MIN_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
               if (bands[i].data[ indexInput ] > maximum) {
                  maximum = bands[i].data[ indexInput ];
               }
            }
				output.data[indexOutput] = maximum;
			}
		}
	}

   
   /**
	 * Computes the maximum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void maximumBand(Planar<GrayS64> input , GrayS64 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayS64[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				long maximum = Long.MIN_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
               if (bands[i].data[ indexInput ] > maximum) {
                  maximum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = maximum;
			}
		}
	}

   
   /**
	 * Computes the maximum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing average pixel values
	 */
	public static void maximumBand(Planar<GrayF32> input , GrayF32 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayF32[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				float maximum = Float.MIN_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
               if (bands[i].data[ indexInput ] > maximum) {
                  maximum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = maximum;
			}
		}
	}
   
   /**
	 * Computes the maximum for each pixel across all bands in the {@link Planar} image.
	 * 
	 * @param input Planar image
	 * @param output Gray scale image containing minimum pixel values
	 */
	public static void maximumBand(Planar<GrayF64> input , GrayF64 output ) {
		final int h = input.getHeight();
		final int w = input.getWidth();

		GrayF64[] bands = input.bands;
		
		for (int y = 0; y < h; y++) {
			int indexInput = input.getStartIndex() + y * input.getStride();
			int indexOutput = output.getStartIndex() + y * output.getStride();

			int indexEnd = indexInput+w;
			// for(int x = 0; x < w; x++ ) {
			for (; indexInput < indexEnd; indexInput++, indexOutput++ ) {
				double maximum = Double.MIN_VALUE;
				for( int i = 0; i < bands.length; i++ ) {
               if (bands[i].data[ indexInput ] > maximum) {
                  maximum = bands[i].data[ indexInput ];
               }
				}
				output.data[indexOutput] = maximum;
			}
		}
	}
   
}
