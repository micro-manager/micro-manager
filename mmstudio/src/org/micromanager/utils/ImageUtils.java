package org.micromanager.utils;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Point;

import mmcorej.CMMCore;

public class ImageUtils {

	public static int BppToImageType(long Bpp) {
		int BppInt = (int) Bpp;
		switch (BppInt) {
		case 1:
			return ImagePlus.GRAY8;
		case 2:
			return ImagePlus.GRAY16;
		case 4:
			return ImagePlus.COLOR_RGB;
		}
		return 0;
	}
	
	public static int getImageProcessorType(ImageProcessor proc) {
		if (proc instanceof ByteProcessor) {
			return ImagePlus.GRAY8;
		}
		
		if (proc instanceof ShortProcessor) {
			return ImagePlus.GRAY16;
		}
		
		if (proc instanceof ColorProcessor) {
			return ImagePlus.COLOR_RGB;
		}
		
		return -1;
	}
	
	public static ImageProcessor makeProcessor(CMMCore core) {
		return makeProcessor(core, null);
	}
	
	public static ImageProcessor makeProcessor(CMMCore core, Object imgArray) {
		int w = (int) core.getImageWidth();
		int h = (int) core.getImageHeight();
		int Bpp = (int) core.getBytesPerPixel();
		int type;
		switch (Bpp) {
		case 1:
			type = ImagePlus.GRAY8;
			break;
		case 2:
			type = ImagePlus.GRAY16;
			break;
		case 4:
			type = ImagePlus.COLOR_RGB;
			break;
		default:
			type = 0;
		}
		return makeProcessor(type,w,h,imgArray);
	}
	

	

	
	public static ImageProcessor makeProcessor(int type, int w, int h, Object imgArray) {
		if (imgArray==null) {
			return makeProcessor(type, w, h);
		} else {
			switch (type) {
			case ImagePlus.GRAY8:
				return new ByteProcessor(w,h,(byte []) imgArray,null);
			case ImagePlus.GRAY16:
				return new ShortProcessor(w,h,(short []) imgArray,null);
			case ImagePlus.COLOR_RGB:
				return new ColorProcessor(w,h,(int []) imgArray);
			default:
				return null;
			}
		}
	}

	public static ImageProcessor makeProcessor(int type, int w, int h) {
		if (type == ImagePlus.GRAY8)
			return new ByteProcessor(w,h);
		else if (type == ImagePlus.GRAY16)
			return new ShortProcessor(w,h);
		else if (type == ImagePlus.COLOR_RGB)
			return new ColorProcessor(w,h);
		else
			return null;
	}
	
	/*
	 * Finds the position of the maximum pixel value.
	 */
	public static Point findMaxPixel(ImagePlus img) {
		ImageProcessor proc = img.getProcessor();
		float [] pix = (float[]) proc.getPixels();
		int width = img.getWidth();
		double max = 0;
		int imax = -1;
		for (int i=0;i<pix.length;i++) {
			if (pix[i]>max) {
				max = pix[i];
				imax = i;
			}
		}
		int y = imax / width;
		int x = imax % width;
		return new Point(x,y);
	}

    public static Point findMaxPixel(ShortProcessor proc) {
		int width = proc.getWidth();
        short [] pix = (short []) proc.getPixels();

		char max = 0;
		int imax = 0;
        char pixChar;

		for (int i=0;i<pix.length;++i) {
            pixChar = (char) pix[i];
			if (pixChar>max) {
				max = pixChar;
				imax = i;
			}
		}

		int y = imax / width;
		int x = imax % width;
		return new Point(x,y);
	}


    public static byte [] get8BitData(Object bytesAsObject) {
        return (byte []) bytesAsObject;
    }

    public static short [] get16BitData(Object shortsAsObject) {
        return (short []) shortsAsObject;
    }

    public static int [] get32BitData(Object intsAsObject) {
        return (int []) intsAsObject;
    }
}
