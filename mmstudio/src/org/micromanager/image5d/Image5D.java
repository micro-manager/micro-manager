package org.micromanager.image5d;

import java.awt.*;
import java.awt.image.*;
import java.lang.reflect.Array;


import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.macro.Interpreter;
import ij.measure.*;
import ij.process.*;
import ij.plugin.filter.*;

/*
 * Created on 26.03.2005
 *
 */

/** Extends ImagePlus to 5 dimensions: x, y, channel (color), slice (z), frame (t).
 * Frame is last to minimize reordering during potential acquisition of time series.
 * <p>
 * An Image5D has a current "position", i.e. a currently active channel, slice and frame.
 * The image belonging to this position is displayed. The stack returned by getStack() is
 * a stack containing all slices belonging to the current channel and frame. In this way the
 * Image5D is backward compatible to plugins, which work on image stacks.
 * <p>
 * When dealing with "position"s, indices range from 0 to dimensionSize-1
 * When dealing with "Channel", "Slice", "Frame", indices range from 1 to NChannels...
 * This is to have the same behaviour as ImageJ has for Slices.
 * 
 * The image data of an Image5D is stored in the imageStack (of type ij.ImageStack).
 * 
 * Calibration data is stored in the 
 * ImagePlus.calibration field (type ij.measure.Calibration), 
 * the chCalibration array (components of type ChannelCalibration) and in the
 * calibration5D field (of type Calibration5D) (TODO: will contain scaling of channel and
 * time axes)
 * 
 * The current display properties are stored in the array chDisplayProps (of type ChannelDisplayProperties).
 * 
 * Working copies of the data are copied to the "channelIPs" array and to the ImagePlus.stack.
 * To replace the "pixels" Object for the imageStack and the working copies, use the method: 
 * setPixels(Object pixels, int channel, int slice, int frame)
 * 
 * Also, the display settings and calibration functions are copied to the "channelIPs" array.
 * display settings and calibration functions can be brought to the same state by the methods:
 * storeChannelProperties(), restoreChannelProperties(), 
 * storeCurrenChannelProperties(), and restoreCurrentChannelProperties().
 *  
 * @author Joachim Walter
 *
 */
// TODO: E.g. handle color models (contrast value) and title

public class Image5D extends ImagePlus {
    // Initially there was the thought of extending this to more dimensions 
    // E.g. fluorescence lifetime as extra dimension?
    
   public static final String VERSION = "1.1.4";
    
   static final int nDefaultDimensions = 5;
	protected int nDimensions = nDefaultDimensions;
    
   private String[] dimensionLabels = {"x", "y", "ch", "z", "t"};
	
	protected boolean isInitialized;
	
	// imageStack: the array that contains references to the data of all slices, channels, frames
	protected ImageStack imageStack;
	protected int imageStackSize;
	
	private Object dummyImage;
	
	// current position in dimension from 0 to dimensionSize-1
	// (e.g. currentSlice = currentPosition[2]+1)
	protected int[] currentPosition = new int[nDimensions];

   // Image5D version of ImagePLus
   protected ChannelImagePlus[] channelImps = new ChannelImagePlus[1];

	// Array of ImageProcessors. One for each channel. 
	// The one of the current channel is always the current ip from getProcessor().
	protected ImageProcessor[] channelIPs = new ImageProcessor[1];
	
	// To handle different color models, contrast settings, ... for different channels
	protected int colorDimension = 2;
   private ChannelCalibration[] chCalibration;
	private ChannelDisplayProperties[] chDisplayProps;
    
   private ImageJ ij = IJ.getInstance();
	
	// For dealing with display AWT image img. (method getImage())
	int[] awtImagePixels;
	boolean newPixels;
	MemoryImageSource imageSource;
	ColorModel imageColorModel;
	Image awtImage;
	int[][] awtChannelPixels;
	byte[][] channelCMReds;
	byte[][] channelCMGreens;
	byte[][] channelCMBlues;
	
	protected int displayMode; //  ChannelControl.ONE_CHANNEL_GRAY, ONE_CHANNEL_COLOR, or OVERLAY
	protected boolean displayAllGray;	
	// grayColorModel is initialized to an 8-bit grayscale ImdexColorModel in the constructor.
	// It is used for every channel in every instance of Image5D.  
	static IndexColorModel grayColorModel;
	
	static final String outOfRange = "Argument out of range: ";

	/**
	 * @param title
	 * @param type
	 * @param dimensions: array containing width, height, nChannels, nSlices and nFrames.
	 * @param fill: If true, data is allocated for each combination of <ch, z, t> ("position"). 
	 * If false, only references to one and the same pixel array will be created. This is
	 * much faster, but changes to one position will apply to all positions.
	 */
	public Image5D(String title, int type, int[] dimensions, boolean fill) {
		this(title, createProcessorFromDims(type, dimensions));
				
		for (int i=2; i<nDimensions; ++i){
			expandDimension(i, dimensions[i], fill);
		}	
	}

	/** Same as Image5D(String title, int type, int[] dimensions, boolean fill), but the array dimensions[]
	 * replaced by the individual elements width, height, nChannels, nSlices and nFrames.
	 */
	public Image5D(String title, int type, int width, int height, int nChannels, int nSlices, int nFrames, boolean fill) {
			this(title, type, new int[] {width, height, nChannels, nSlices, nFrames}, fill);
		}	
	
	/**
	 * @param title
	 * @param ip
	 */
	public Image5D(String title, ImageProcessor ip) {
		this(title, createStackFromProcessor(ip));
	}

	/**
	 * Makes an Image5D from an ImageStack. 
	 * All other constructors of Image5D call this one. So changes that apply to all 
	 * constructors should go here.
	 * @param title: title of the image
	 * @param stack: stack containing the image data
	 */
	public Image5D(String title, ImageStack stack) {	
      this (title, stack, 1, 1, stack.getSize());
   }
		
      /*
		super(title, stack);
		if(IJ.versionLessThan("1.34p")) throw new IllegalArgumentException("too old ImageJ version");
		
		// Initialize Image5D:currentPosition, imageData reference.
		for (int i=0; i<nDimensions; i++) {
			currentPosition[i] = 0;
		}
		
		imageStack = stack;
		imageStackSize = getStackSize();

      chCalibration = new ChannelCalibration[1];
      chCalibration[0] = new ChannelCalibration();
        
      chDisplayProps = new ChannelDisplayProperties[1];
		chDisplayProps[0] = new ChannelDisplayProperties();
		chDisplayProps[0].setColorModel(ip.getColorModel());
		chDisplayProps[0].setMinValue(ip.getMin());
		chDisplayProps[0].setMaxValue(ip.getMax());
		chDisplayProps[0].setMinThreshold(ip.getMinThreshold());
		chDisplayProps[0].setMaxThreshold(ip.getMaxThreshold());
		chDisplayProps[0].setLutUpdateMode(ip.getLutUpdateMode());
		chDisplayProps[0].setDisplayedGray(false);
        
		
		//displayMode = ChannelControl.OVERLAY;
      displayMode = ChannelControl.ONE_CHANNEL_COLOR;
		displayAllGray = false;
		channelIPs[0] = getProcessor();
		
		grayColorModel = ChannelDisplayProperties.createModelFromColor(Color.white);
		
		imageStack.setColorModel(grayColorModel);
        
        setCalibration(super.getCalibration());
		
		isInitialized = true;
	}
   */

    /**
     * Makes an Image5D from an ImageStack and dimension sizes.
     * All other constructors of Image5D eventually call this one. So changes that apply to all 
     * constructors should go here.
     * @param title: title of the image
     * @param stack: stack containing the image data. Changes first by channel, then by slice then by frame.
     * @param nChannels
     * @param nSlices
     * @param nFrames
     */
    public Image5D(String title, ImageStack stack, int nChannels, int nSlices, int nFrames) {    
        super(title, createZStackFromImageStack(stack, nChannels, nSlices, nFrames));
        
        if(IJ.versionLessThan("1.34p")) throw new IllegalArgumentException("ImageJ version too old");        
    
        // Set imageStack and its size for use by super.setDimensions().
        imageStack = stack;
        imageStackSize = stack.getSize();
        super.setDimensions(nChannels, nSlices, nFrames);
        
        // Initialize Image5D:currentPosition, imageData reference.
        for (int i=0; i<nDimensions; i++) {
            currentPosition[i] = 0;
        }

        // Create all necessary arrays for channel display handling.
        chCalibration = new ChannelCalibration[nChannels];
        for (int i=0; i<nChannels; i++) {
            chCalibration[i] = new ChannelCalibration();
        }
        
        chDisplayProps = new ChannelDisplayProperties[nChannels];
        for (int i=0; i<nChannels; i++) {
            chDisplayProps[i] = new ChannelDisplayProperties();
            chDisplayProps[i].setColorModel(ip.getColorModel());
            chDisplayProps[i].setMinValue(ip.getMin());
            chDisplayProps[i].setMaxValue(ip.getMax());
            chDisplayProps[i].setMinThreshold(ip.getMinThreshold());
            chDisplayProps[i].setMaxThreshold(ip.getMaxThreshold());
            chDisplayProps[i].setLutUpdateMode(ip.getLutUpdateMode());
            chDisplayProps[i].setDisplayedGray(false);
        }
        
        // Create channel ImageProcessor and ImagePlus Arrays
        ImageProcessor[] newChannelIPs = new ImageProcessor[nChannels];
        channelImps = new ChannelImagePlus[nChannels];
        for (int i=0; i<nChannels; ++i){             
            newChannelIPs[i] = createProcessorFromDims(getType(), new int[] {width, height, 1, 1, 1});
            newChannelIPs[i].setPixels(imageStack.getPixels(getCurrentSliceOffset()+i));
            newChannelIPs[i].setColorModel(chDisplayProps[i].getColorModel());
            newChannelIPs[i].setThreshold(chDisplayProps[i].getMinThreshold(), 
                    chDisplayProps[i].getMaxThreshold(), ImageProcessor.NO_LUT_UPDATE);
            newChannelIPs[i].setMinAndMax(chDisplayProps[i].getMinValue(), 
                    chDisplayProps[i].getMaxValue());
            channelImps[i] = new ChannelImagePlus("", newChannelIPs[i]);
        }
        channelIPs = newChannelIPs;
                              
        displayMode = ChannelControl.ONE_CHANNEL_COLOR;
        displayAllGray = false;
        //displayGrayInTiles = false;
        
        grayColorModel = ChannelDisplayProperties.createModelFromColor(Color.white);
        
        imageStack.setColorModel(grayColorModel);
        
        setCalibration(super.getCalibration());
        
        isInitialized = true;
    }

	/* Following: Constructors from super, which are not supported for Image5Ds.
	 */
		public Image5D() {
		    throw(new IllegalArgumentException("Empty constructor not supported."));
//			super();
//			for (int i=0; i<nDimensions; i++) {
//				dimensionSize[i] = 0;
//			}
		}
		
		public Image5D(String title, Image img) {
		    throw(new IllegalArgumentException("Constructor Image5D(String, Image) not supported."));
		}

		public Image5D(String pathOrURL) {
		    throw(new IllegalArgumentException("Constructor Image5D(String) not supported."));
		}
		
	

	/** Replaces the current stack, with the one specified. Set 'title' to null to leave the title unchanged. 
	Type ColorRGB is not permitted.
	If the Image5D is initialized, stack type and dimensions have to match to current type/dims.*/
	public void setStack(String title, ImageStack stack) {
		// Exception, if dimensions or data type don't match. 
		if (stack.getProcessor(1) instanceof ColorProcessor)
			throw new IllegalArgumentException("Cannot accept RGB stack for Image5D.");
		if (isInitialized) {
			if(!stack.getProcessor(1).getClass().equals(this.getProcessor().getClass())) 
				throw new IllegalArgumentException("Stack type does not match current Image5D type.");
			if (stack.getWidth() != width ||
				stack.getHeight() != height ||
				stack.getSize() != getNSlices()) {
				throw new IllegalArgumentException("Stack dimensions don't match current Image5D dimensions.");
			}
		}
		
		// Avoid messing up of image window (5D) by superclass, which doesn't know about 5D.
		ImageWindow tempWin = win;
		win = null;
			super.setStack(title, stack);
		win = tempWin;
		
		//  Handle fact that stack has actually changed: point to data in new stack.
		if (isInitialized) {
			int offs = getCurrentStackOffset()-1;
			int incr = getCurrentStackIncrement();
			int size = getNSlices();
			Object[] imageStackArray = imageStack.getImageArray();
			for(int i=0; i<size; ++i) {
			    imageStackArray[offs+i*incr] = stack.getPixels(i+1);
			}
			
			chDisplayProps[currentPosition[colorDimension]].setColorModel(stack.getColorModel());
			setSlice(currentPosition[3]+1);
		}
		
		updateAndRepaintWindow();
	}
	
    /** Updates the window controls of the Image5DWindow. Right now only the Channel selector*/
    public void updateWindowControls() {
        if (win!=null) {
            Image5DWindow iWin = (Image5DWindow)win;
            iWin.getChannelControl().updateChannelSelector();
        }
    }
    
	/** Causes the AWT image returned by getImage() to be fully recalculated. 
	 */
	public void updateImageAndDraw() {
		img = null;
		updateAndDraw();
	}
	
	/** Replaces the AWT image, if any, with the one specified. 
	Throws an IllegalStateException if an error occurs 
	while loading the image. 
	Use of this method is untested. */
	public void setImage(Image img) {
		// Avoid messing up of image window (5D) by superclass, which doesn't know about 5D.
		ImageWindow tempWin = win;
		win = null;
			super.setImage(img);
		win = tempWin; 
		// Now tell the Image5DWindow (if any) to update itself.
	}

	/** Replaces the ImageProcessor, if any, with the one specified.
	Set 'title' to null to leave the image title unchanged. */
	// TODO: this method probably doesn't work like this. Think about it.
	public void setProcessor(String title, ImageProcessor ip) {
		// Avoid messing up of image window (5D) by superclass, which doesn't know about 5D.
		ImageWindow tempWin = win;
		win = null;
			super.setProcessor(title, ip);
		win = tempWin;
		// Now tell the Image5DWindow (if any) to update itself.
	}
	
	
	protected void setType(int type) {
// TODO Doesn't work properly, yet. Exceptions when changing type in GUI.
// Think about allowing a different type for each channel.	    

//    	if ((type<0) || (type>GRAY32))
//    		return;		
		super.setType(type);
	}
	
	public synchronized void setSlice(int index) {			
		setCurrentPosition(3, index-1);
	}


	
	public synchronized void setCurrentPosition(int dimension, int position) {
		if (dimension<0 || dimension>=nDimensions) 
			throw new IllegalArgumentException("Invalid dimension: "+dimension);
		if (position<0 || position>=(getDimensions()[dimension]))
			return;
		int[] tmpPos = new int[nDimensions];
		for (int i=0; i<nDimensions; ++i) {
			if(i==dimension) {
				tmpPos[i]=position;
			} else {
				tmpPos[i]=currentPosition[i];
			}
		}
		setCurrentPosition(tmpPos);
	}
	
	public synchronized void setCurrentPosition(int x, int y, int ch, int z, int t) {
		this.setCurrentPosition(new int[] {x, y, ch, z, t});
	}
	
	/** THE method to set the current Position in the Image5D. 
	 * All other setCurrentPosition methods with different signature call this one.
	 * @param position
	 */
	public synchronized void setCurrentPosition(int[] position) {
		if (position.length<nDimensions) 
			throw new IllegalArgumentException("Position array is smaller than number of dimensions.");
		int[] dimensions = getDimensions();
		for(int i=0; i<nDimensions; i++) {
			if (position[i]<0)
				position[i]=0;
			else if (position[i]>=(dimensions[i]))
				position[i]=(dimensions[i])-1;
		}
        
        storeCurrentChannelProperties();
        
		int oldChannel = currentPosition[colorDimension];
		int newChannel = position[colorDimension];
		boolean channelChanged = (oldChannel != newChannel);
		boolean stackChanged = channelChanged | (currentPosition[4] != position[4]);

		for(int i=0; i<nDimensions; i++) {
			currentPosition[i] = position[i];
		}
				
		// Change Stack if necessary
		if (stackChanged) {
			ImageStack newStack = new ImageStack(width, height, chDisplayProps[position[colorDimension]].getColorModel());
			int offs = getCurrentStackOffset();
			int incr = getCurrentStackIncrement();
			for (int i=0; i<getNSlices(); ++i) {
				newStack.addSlice(imageStack.getSliceLabel(offs+i*incr), imageStack.getPixels(offs+i*incr));
			}
			// Avoid messing up of image window (5D) by superclass, which doesn't know about 5D.
			ImageWindow tempWin = win;
			win = null;
				super.setStack(null, newStack);
			win = tempWin;			
		}

		// Update channelIPs
		channelIPs[currentPosition[2]] = getProcessor();
		for (int i=0; i<getNChannels(); ++i) {
			if(i!=currentPosition[2]) {
				channelIPs[i].setPixels(imageStack.getPixels(getCurrentSliceOffset()+i));
			}
		}
		
		restoreCurrentChannelProperties();

		// Call super.setSlice(), but avoid messing up of image window (5D) by superclass, 
		// which doesn't know about 5D. super.setSlice() calls updateAndRepaintWindow().
		newPixels = true;
		ImageWindow tempWin = win;
		win = null;
			super.setSlice(currentPosition[3]+1);
		win = tempWin; 	
		
		if (win!=null){
			((Image5DWindow)win).updateSliceSelector();
		}
		if (IJ.spaceBarDown() && (getType()==GRAY16||getType()==GRAY32)) {
			ip.resetMinAndMax();
			IJ.showStatus((currentPosition[3]+1)+": min="+ip.getMin()+", max="+ip.getMax());
		}

		newPixels = true;
		updateAndRepaintWindow();
	}
	
	public int getNDimensions() {
		return nDimensions;
	}
    
    public String getDimensionLabel(int dimension) {
        if (dimension<0 || dimension >= nDimensions)
            throw new IllegalArgumentException("Invalid Dimension: "+dimension);
        return dimensionLabels[dimension];
    }
    
    /** Returns the size of a dimension. Dimensions go from 0 to 4 
     * (width, height, nChannels, nSlices, nFrames). 
     */
    public int getDimensionSize(int dimension) {
        if (dimension<0 || dimension>4) {
            return 0;
        }
        switch (dimension) {
        case 0: return getWidth();
        case 1: return getHeight();
        case 2: return getNChannels();
        case 3: return getNSlices();
        case 4: return getNFrames();
        default: return 0;
        }
    }

	/** Returns the full size of the ImageStack, 
	 *  not just that of the current channel/frame.
	 *  Overrides method in ImagePlus.
	 */
	public int getImageStackSize() {
		return imageStackSize;
	}
	
	/** Returns a reference to the imageStack that contains all channels, slices and frames.
	 */
	public ImageStack getImageStack() {
		return imageStack;
	}
	
	/** Returns Image5D-specific metadata to be saved in TIFF-file.
	 */
	public String getI5DMetaData() {
        // Took another way to save metadata.
	    return "";
	}
    
    public FileInfo getFileInfo() {
        FileInfo fi = super.getFileInfo();
        // changes to fileinfo
        
        fi.nImages = getImageStackSize();
        
        if (fi.nImages>1)
            fi.pixels = getImageStack().getImageArray();
        
        return fi;
    }
	
	
	/** Returns a reference to the image data of the given channel, slice and frame:
	 *  An object of type byte-array, short-array or float-array.
	 */	
	public Object getPixels(int channel, int slice, int frame) {
	    if (channel<1 || channel>getNChannels() || slice<1 || slice>getNSlices() ||
	            frame<1 || frame>getNFrames()) {
	        throw new IllegalArgumentException(outOfRange+ "<"+channel+", "+slice +", "+frame +">");
	    }
	    return imageStack.getPixels(getImageStackIndex(channel, slice, frame));
	}

    /** Assigns a pixel array to the current position,
     * @param pixels
     */
    public void setPixels(Object pixels) {
        if(isInitialized && pixels != null) {           
            if ( !pixels.getClass().equals(imageStack.getPixels(getCurrentImageStackIndex()).getClass()) ) 
                return;
            if (width*height != Array.getLength(pixels)) {
                throw(new IllegalArgumentException("Array size does not match image dimensions."));
            }
            
            imageStack.setPixels(pixels, getCurrentImageStackIndex());
            
            channelIPs[currentPosition[2]].setPixels(pixels);

            super.getStack().setPixels(pixels, getCurrentPosition(3)+1);
        }
        
    }
    
    /** Assigns a pixel array to the specified position,
     * @param pixels
     */
    public void setPixels(Object pixels, int channel, int slice, int frame) {
        if(isInitialized && pixels != null) {           
            if ( !pixels.getClass().equals(imageStack.getPixels(getImageStackIndex(channel, slice, frame)).getClass()) ) 
                throw(new IllegalArgumentException("Invalid pixels array."));
            if (width*height != Array.getLength(pixels)) {
                throw(new IllegalArgumentException("Array size does not match image dimensions."));
            }
            checkChannel(channel);
            checkSlice(slice);
            checkFrame(frame);

            imageStack.setPixels(pixels, getImageStackIndex(channel, slice, frame));

            if (slice==getCurrentSlice() && frame==getCurrentFrame())
                channelIPs[channel-1].setPixels(pixels);
            if (channel==getCurrentChannel() && frame==getCurrentFrame())
                super.getStack().setPixels(pixels, slice);
        }
        
    }

    /** Creates an empty pixels-array of size width*height and type of this image, 
     * and returns a reference to it.*/
    public Object createEmptyPixels() {
        Object pixelsArray = null;
        switch(getType()) {
            case ImagePlus.GRAY8:
                pixelsArray = new byte[getWidth()*getHeight()];
                break;
            case ImagePlus.GRAY16:
                pixelsArray = new short[getWidth()*getHeight()];
                break;
            case ImagePlus.GRAY32:
                pixelsArray = new float[getWidth()*getHeight()];
                break;
        }
        return pixelsArray;
    }
	
	public int getColorDimension() {
		return colorDimension;
	}	

	/** Returns the index in the imageStack corresponding to the current position.
	 */
	public int getCurrentImageStackIndex() {
		return (currentPosition[4]*getNChannels()*getNSlices() 
				+ currentPosition[3]*getNChannels() + currentPosition[2] + 1);
	}
	
	/** Returns the index in the imageStack corresponding to the specified position.
	 */
	public int getImageStackIndex(int channel, int slice, int frame) {
		return ((frame-1)*getNChannels()*getNSlices() 
				+ (slice-1)*getNChannels() + (channel-1) + 1);
	}	
	
	public int[] getCurrentPosition() {
		int[] pos = new int[nDimensions];
		System.arraycopy(currentPosition, 0, pos, 0, nDimensions);
		return pos;
	}
	
	public int getCurrentPosition(int dimension) {
		return currentPosition[dimension];
	}	
	
	public int getCurrentChannel(){
		return currentPosition[2]+1;
	}
	public int getCurrentSlice(){
		return currentPosition[3]+1;
	}
	public int getCurrentFrame(){
		return currentPosition[4]+1;
	}

	/** Changes the color model of a channel to this one.
	 * @param channel
	 * @param model
	 */
	public void setChannelColorModel(int channel, ColorModel model) {
		checkChannel(channel);
		if (! (model instanceof IndexColorModel)) 
			throw new IllegalArgumentException("Only accepting IndexColorModels");
        storeChannelProperties(channel);
		chDisplayProps[channel-1].setColorModel(model);
		restoreChannelProperties(channel);
	}
    
    /** Changes the min- and max-value of a channel to these ones.
     * @param channel
     * @param min value
     * @param max value
     */
    public void setChannelMinMax(int channel, double minValue, double maxValue) {
        checkChannel(channel);
        storeChannelProperties(channel);
        chDisplayProps[channel-1].setMinValue(minValue);
        chDisplayProps[channel-1].setMaxValue(maxValue);
        restoreChannelProperties(channel);
    }
//	/** Returns, whether the given channel is selected to be displayed in grayscale.
//	 * 
//	 * @param channel
//	 * @return
//	 */
//	public boolean isChannelGray(int channel) {
//		checkChannel(channel);
//		return cChannelProps[channel-1].isDisplayedGray();
//	}
//	public void setChannelGray(int channel, boolean displayGray) {
//		checkChannel(channel);
//		
//		ChannelDisplayProperties props = cChannelProps[channel-1];
//		ImageProcessor channelIP = channelIPs[channel-1];
//		
//		// only do something if there is a change of state
//		if ((props.isDisplayedGray() == displayGray))
//			return;
//		
//		storeChannelProperties(channel);
//		props.setDisplayedGray(displayGray);
//		if (displayGray) {
//			channelIP.setColorModel(grayColorModel);
//		} else {
//			channelIP.setColorModel(props.getColorModel());			
//		}
//		restoreChannelProperties(channel);
//		
//		updateImageAndDraw();			
//	}

	
    public int getDisplayMode() {
        return displayMode;
    }
    
    /** Sets the displayMode of the Image5D to one of 
     * ChannelControl.ONE_CHANNEL_GRAY, ONE_CHANNEL_COLOR or OVERLAY
     * and updates the ChannelControl of the Image5DWindow. 
     * This is the method to be called from external code, not the one in ChannelControl.*/
    public void setDisplayMode(int displayMode) {
        if (this.displayMode == displayMode)
            return;
        
		for (int i=1; i<=getNChannels(); ++i) {
			storeChannelProperties(i);
		}       

        this.displayMode = displayMode;
        if (displayMode == ChannelControl.ONE_CHANNEL_GRAY) {
            displayAllGray = true;
        } else if ((displayMode == ChannelControl.ONE_CHANNEL_COLOR) ||
                (displayMode == ChannelControl.OVERLAY)) {
            displayAllGray = false;
        }
        
		for (int i=1; i<=getNChannels(); ++i) {
			restoreChannelProperties(i);	
		}    
        
        Image5DWindow win = (Image5DWindow)getWindow();
        if (win!=null) {
            win.getChannelControl().setDisplayMode(displayMode);
        }
        
		updateImageAndDraw();
    }
 
    
    public boolean isDisplayedInOverlay(int channel) {
        checkChannel(channel);
        return chDisplayProps[channel-1].isDisplayedInOverlay();
    }     
    public void setDisplayedInOverlay(int channel, boolean displayed) {
        checkChannel(channel);   
        
		for (int i=1; i<=getNChannels(); ++i) {
			storeChannelProperties(i);
		}        
		chDisplayProps[channel-1].setDisplayedInOverlay(displayed);		
		for (int i=1; i<=getNChannels(); ++i) {
			restoreChannelProperties(i);	
		}   
    }
    
    public boolean isDisplayedGray(int channel) {
        checkChannel(channel);
        return chDisplayProps[channel-1].isDisplayedGray();
    }     
    public void setDisplayedGray(int channel, boolean displayedGray) {
        checkChannel(channel);   
        
        for (int i=1; i<=getNChannels(); ++i) {
            storeChannelProperties(i);
        }         
        chDisplayProps[channel-1].setDisplayedGray(displayedGray);        
        for (int i=1; i<=getNChannels(); ++i) {
            restoreChannelProperties(i);    
        }   
    }

	/** true: all channels are displayed in gray*/   
	public boolean isDisplayAllGray() {
		return displayAllGray;
	}


    public void show(String statusMessage) {
        if ((IJ.macroRunning() && ij==null) || Interpreter.isBatchMode()) {
            WindowManager.setTempCurrentImage(this);
            Interpreter.addBatchModeImage(this);
            return;
        }
        
        IJ.showStatus(statusMessage);
        new Image5DWindow(this);
    }
    
	public Image getImage() {
		if (img == null) {
		    updateImage();
		}
		return img;	
	}
	
	/** ImageCanvas.paint() calls this method when the
	 * ImageProcessor has generated a new image.		
	 * Calculates the AWT Image displayed in the Image5DWindow. 
	 * For ONE_CHANNEL_GRAY and ONE_CHANNEL_COLOR this uses the method from 
	 * ImageProcessor. 
	 * For OVERLAY this method has to calculate a lot. 
	 */
	public void updateImage() {

		int imageSize = width*height;
		int nChannels = getNChannels();
		int redValue, greenValue, blueValue;
	
		int displayMode = -1;
		if (win!=null)
			displayMode = ((Image5DWindow)win).getDisplayMode();


		if ((displayMode == ChannelControl.ONE_CHANNEL_GRAY) || 
				(displayMode == ChannelControl.ONE_CHANNEL_COLOR)) {
         
			img = ip.createImage();

			
		} else if (displayMode == ChannelControl.OVERLAY) {	
			// Lengthy calculation of overlay image from the time when there was no 
			// ImageProcessor.getCurrentColorModel() method.
		    // Actually faster than with ImageProcessor.getCurrentColorModel()!!
			if (awtImagePixels == null || awtImagePixels.length != imageSize) {
				awtImagePixels = new int[imageSize];
				newPixels = true;
			}
				
			// Build imagePixels array from AWT images of each channel.
			if (awtChannelPixels==null || awtChannelPixels.length!=nChannels || awtChannelPixels[0].length!=imageSize) {
				awtChannelPixels = new int[nChannels][];
				for (int i=0; i<nChannels; ++i) {
					awtChannelPixels[i] = new int[imageSize];
				}
			}			
			for (int i=0; i<nChannels; ++i) {
			    if (!chDisplayProps[i].isDisplayedInOverlay())
			        continue;
				PixelGrabber pg = new PixelGrabber(channelIPs[i].createImage(), 0, 0, width, height, awtChannelPixels[i], 0, width);
				try {
					pg.grabPixels();
				}
				catch (InterruptedException e){};
			}
			for (int i=0; i<imageSize; ++i) {
				redValue=0; greenValue=0; blueValue=0;
				for (int j=0; j<nChannels; ++j) {
				    if (!chDisplayProps[j].isDisplayedInOverlay())
				        continue;
					redValue += (awtChannelPixels[j][i]>>16)&0xFF;
					greenValue += (awtChannelPixels[j][i]>>8)&0xFF;
					blueValue += (awtChannelPixels[j][i])&0xFF; 
					if (redValue>255)redValue		= 255;
					if (greenValue>255)greenValue	= 255;
					if (blueValue>255)blueValue		= 255;
				}
				awtImagePixels[i] = (redValue<<16) | (greenValue<<8) | (blueValue); 			
			}			
			// taken and modified from ByteProcessor.createImage()
			if (img == null && awtImage!=null)
				img = awtImage;
				
			if (imageSource==null) {
				imageColorModel = new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF);
				imageSource = new MemoryImageSource(width, height, imageColorModel, awtImagePixels, 0, width);
				imageSource.setAnimated(true);
				imageSource.setFullBufferUpdates(true);
				awtImage = Toolkit.getDefaultToolkit().createImage(imageSource);
				newPixels = false;
			} else if (newPixels){
				imageSource.newPixels(awtImagePixels, imageColorModel, 0, width);
				newPixels = false;
			} else {
				imageSource.newPixels();
			}  

		} // end of calculating in overlay mode
	
	}	
	
//	/** Draws the image. If there is a ROI, its
//	outline is also displayed.  Does nothing if there
//	is no window associated with this image (i.e. show()
//	has not been called).*/
//	public void draw(){
//		if (win!=null)
//			win.getCanvas().repaint();
//	}


    public void setDefaultColors() {
        int nChannels = getNChannels();
        float colorIncr = 1f/(float)nChannels;
        
        for (int c=1; c<=nChannels; c++) {
            setChannelColorModel(c, ChannelDisplayProperties.
                    createModelFromColor(Color.getHSBColor(colorIncr*(c-1), 1f, 1f)));            
        }       
    }
    
    public void setDefaultChannelNames() {
        int nChannels = getNChannels();

        for (int c=1; c<=nChannels; c++) {
            getChannelCalibration(c).setLabel("Ch-"+c);          
        }
    }
	
    /** Sets the image arrays to null to help the garbage collector
    do its job. In addition to ImagePlus.flush() also sets the imageStack to null.
    Does nothing if the image is locked or a
    setIgnoreFlush(true) call has been made. */
    public synchronized void flush() {
        super.flush();
        if (locked)
            return;
        // ignoreFlush is not visible, so find out, whether the arrays of stack have 
        // been set to null. If yes, ignoreFlush was false. Else return.
        ImageStack stack = getStack();
        if (stack!=null) {
            Object[] arrays = stack.getImageArray();
            if (arrays!=null && arrays[0]!=null)
                    return;
        }
          
        // Set data arrays particular to Image5D to null.
        Object[] imageStackArrays = imageStack.getImageArray();
        if (imageStackArrays!=null)
            for (int i=0; i<imageStackArrays.length; i++)
                imageStackArrays[i] = null;
        
        if (channelIPs!=null)
            for (int i=0; i<channelIPs.length; i++)
                channelIPs[i]=null;
        
        if (chDisplayProps!= null)
            for (int i=0; i<chDisplayProps.length; i++)
                chDisplayProps[i]=null;
        
        if (chCalibration!= null)
            for (int i=0; i<chCalibration.length; i++)
                chCalibration[i]=null;

        if  (awtChannelPixels != null)
            for (int i=0; i<awtChannelPixels.length; ++i) 
                awtChannelPixels[i] = null;

        if (awtImagePixels != null)
           awtImagePixels = null;

        if (dummyImage != null)
           dummyImage = null;

        System.gc();
    }

    /** Copies the active channel to the ImageJ clipboard, if only one channel is displayed.
	 * In OVERLAY mode copy the currently displayed image as an RGB image.
	 */
    public void copy(boolean cut) {
        if (displayMode != ChannelControl.OVERLAY) {
            super.copy(cut);
        } else {
            // Copy RGB image to clipboard when in overlay mode. Don't cut.
            if (cut)
                return;
            
            // make sure the clipboard is not null and get reference to it
            super.copy(false);
            ImagePlus imgClip = getClipboard();
            
            // imagePixels: pixels of displayed AWT Image
            ColorProcessor clipProcessor =  new ColorProcessor(width, height, awtImagePixels);

    		Roi roi = getRoi();
    		if (roi!=null && !roi.isArea()) {
    			IJ.error("Cut/Copy", "The Cut and Copy commands require\n"
    				+"an area selection, or no selection.");
    			return;
    		}
    		
    		String msg = (cut)?"Cut":"Copy";
    		IJ.showStatus(msg+ "ing...");
    		
    		if (roi != null) {
    		    clipProcessor.setRoi((Roi)roi.clone());
    		}
    		ColorProcessor clipProcessor2 =  (ColorProcessor)clipProcessor.crop();


    		Roi roi2 = null;	
    		if (roi!=null && roi.getType()!=Roi.RECTANGLE) {
    			roi2 = (Roi)roi.clone();
    			Rectangle r = roi.getBounds();
    			if (r.x<0 || r.y<0) {
    				roi2.setLocation(Math.min(r.x,0), Math.min(r.y,0));
    			}
    		}

    		imgClip.setProcessor("", clipProcessor2);
    		if (roi2!=null) imgClip.setRoi(roi2);

    		int bytesPerPixel = 4;    		
    		IJ.showStatus(msg + ": " + (imgClip.getWidth()*imgClip.getHeight()*bytesPerPixel)/1024 + "k");    
        }
    }
    
    
	/**
	 * Expands Image5D to larger size in one dimension. Does nothing, if newSize <= current size.
	 * Works only for dimensions >=2 (i.e. channel, slice, frame) at present.
	 * @param dimension
	 * @param newSize
	 * @param fill: if true, create black image for each position, 
	 * 		if false, link a common dummy image to each position
	 */	
	synchronized protected void expandDimension(int dimension, int newSize, boolean fill) {
		if (dimension<2 || dimension>nDimensions)
			throw new IllegalArgumentException("Invalid dimension: "+dimension);
		
		int[] dimensionSize = getDimensions();
		int oldSize = dimensionSize[dimension];
		if (oldSize >= newSize) 
			return;

		// Get size of dimensions and allocate new imageData object. 
		dimensionSize[dimension]=newSize;
		int dataSize = 1, lowerDimSize=1, higherDimSize=1;
		for (int i=2; i<nDimensions; ++i) {
			dataSize *= dimensionSize[i];
			if (i<dimension)
				lowerDimSize *= dimensionSize[i];
			else if (i>dimension) 
				higherDimSize *= dimensionSize[i];
		}

		int type = getType(), imageSize = dimensionSize[0]*dimensionSize[1];
		
		// creates "dummy" image if needed
		if (!fill && dummyImage==null) {
			switch(type) {
				case GRAY8:
					dummyImage = new byte[imageSize];
					break;
				case GRAY16:
					dummyImage = new short[imageSize];
					break;
				case GRAY32:
					dummyImage = new float[imageSize];
					break;
			}
		}
		
		ImageStack newImageStack = new ImageStack(width, height, grayColorModel);		
		// copy data references from old ImageStack to new ImageStack
		for (int highIndex=0; highIndex<higherDimSize; ++highIndex) {
			int baseIndexOld = highIndex*lowerDimSize*oldSize+1;
//			int baseIndexNew = highIndex*lowerDimSize*newSize+1;
			
			for (int oldIndex=baseIndexOld; oldIndex<baseIndexOld+lowerDimSize*oldSize; ++oldIndex) {
			    newImageStack.addSlice(imageStack.getSliceLabel(oldIndex), imageStack.getPixels(oldIndex));
			}

			if (fill) {
				for (int newIndex=lowerDimSize*oldSize; newIndex<lowerDimSize*newSize; ++newIndex) {
					switch(type) {
					case GRAY8:
						newImageStack.addSlice("", new byte[imageSize]);
						break;
					case GRAY16:
						newImageStack.addSlice("", new short[imageSize]);
						break;
					case GRAY32:
						newImageStack.addSlice("", new float[imageSize]);
					}
				}
			} else {			
				for (int newIndex=lowerDimSize*oldSize; newIndex<lowerDimSize*newSize; ++newIndex) {
					newImageStack.addSlice("", dummyImage);
				}
			}
		}
		
		imageStack = newImageStack;

		// update imageStackSize
		imageStackSize = 1;
		for (int i=2; i<nDimensions; ++i) {
			imageStackSize *= dimensionSize[i];
		}
		
		// update dimension sizes		
		super.setDimensions(dimensionSize[2], dimensionSize[3], dimensionSize[4]);		
		
		// copy references to colorModels, and min/max-arrays and fill new ones.
		if(dimension == colorDimension) {
            // Copy ChannelCalibrations
            ChannelCalibration[] newChCalibration = new ChannelCalibration[newSize];
            System.arraycopy(chCalibration, 0, newChCalibration, 0, oldSize);
            for (int i=oldSize; i<newSize; ++i){
                newChCalibration[i] = new ChannelCalibration();
            }
            chCalibration = newChCalibration;
            
            // Copy ChannelDisplayProperties
			ChannelDisplayProperties[] newChDisplayProps = new ChannelDisplayProperties[newSize];
			System.arraycopy(chDisplayProps, 0, newChDisplayProps, 0, oldSize);
			for (int i=oldSize; i<newSize; ++i){
				newChDisplayProps[i] = new ChannelDisplayProperties();
			}
			chDisplayProps = newChDisplayProps;
			
			// Expand ImageProcessor Array
			ImageProcessor[] newChannelIPs = new ImageProcessor[newSize];
			System.arraycopy(channelIPs, 0, newChannelIPs, 0, oldSize);
			for (int i=oldSize; i<newSize; ++i){
				newChannelIPs[i] = createProcessorFromDims(getType(), new int[] {width, height, 1, 1, 1});
				newChannelIPs[i].setPixels(imageStack.getPixels(getCurrentSliceOffset()+i));
				newChannelIPs[i].setColorModel(chDisplayProps[i].getColorModel());
				newChannelIPs[i].setThreshold(chDisplayProps[i].getMinThreshold(), 
                        chDisplayProps[i].getMaxThreshold(), ImageProcessor.NO_LUT_UPDATE);
				newChannelIPs[i].setMinAndMax(chDisplayProps[i].getMinValue(), 
                        chDisplayProps[i].getMaxValue());
			}
			channelIPs = newChannelIPs;
		}
		
		// set new Stack if necessary (has to come after super.setDimensions())
		if (dimension == 3) {
			ImageStack newStack = new ImageStack(width, height, chDisplayProps[currentPosition[colorDimension]].getColorModel());
			int offs = getCurrentStackOffset();
			int incr = getCurrentStackIncrement();
			for (int i=0; i<dimensionSize[3]; ++i) {
				newStack.addSlice(imageStack.getSliceLabel(offs+i*incr), imageStack.getPixels(offs+i*incr));
			}
			this.setStack(null, newStack);
		}
		this.setSlice(currentPosition[3]+1);  // Also calls updateSliceSelector of Image5DWindow.			
	}


	/**
	 * Make setDimensions non-functional, so that no one messes up stack dimensions in the GUI.
	 */
	public void setDimensions(int nChannels, int nSlices, int nFrames) {
		return;
	}
	

//	 instance utility methods
	protected int getCurrentSliceOffset() {
		return (currentPosition[4]*getNSlices()*getNChannels() + currentPosition[3]*getNChannels() + 1);
	}
	protected int getCurrentStackOffset() {
		return (currentPosition[4]*getNSlices()*getNChannels() + currentPosition[2] + 1);
	}
	protected int getCurrentStackIncrement() {
		return getNChannels();
	}

    // 1<=channel<=NChannels
    protected void checkChannel(int channel) {
        if(channel<1 || channel> getNChannels())
            throw new IllegalArgumentException("Invalid channel: "+channel);        
    }   
    // 1<=channel<=getNSlices
    protected void checkSlice(int slice) {
        if(slice<1 || slice> getNSlices())
            throw new IllegalArgumentException("Invalid slice: "+slice);        
    }   
    // 1<=channel<=getNFrames
    protected void checkFrame(int frame) {
        if(frame<1 || frame> getNFrames())
            throw new IllegalArgumentException("Invalid frame: "+frame);        
    }

    
    /** Get reference to ChannelDisplayProperties item of a channel.*/
    public ChannelCalibration getChannelCalibration(int channel) {
        checkChannel(channel);
        return chCalibration[channel-1];
    }    
    
    /** Sets the ChannelDisplayProperties item of a channel.*/
    public void setChannelCalibration(int channel, ChannelCalibration cal) {
        checkChannel(channel);
        chCalibration[channel-1] = cal;
    }
    
    /** Get reference to ChannelDisplayProperties item of a channel.*/
    public ChannelDisplayProperties getChannelDisplayProperties(int channel) {
        checkChannel(channel);
        return chDisplayProps[channel-1];
    }    
    
    /** Sets the ChannelDisplayProperties item of a channel.*/
    public void setChannelDisplayProperties(int channel, ChannelDisplayProperties props) {
        checkChannel(channel);
        chDisplayProps[channel-1] = props;
    }
    
    
    // Store Calibration and Display Properties of current channel ImageProcessor 
    // to chCalibration chDisplayProps arrays.
    public void storeCurrentChannelProperties() {
        int channel = getCurrentChannel();
        storeChannelProperties(channel);
        
        // Density calibration properties
        ChannelCalibration chCal = chCalibration[channel-1];        
        if (getGlobalCalibration() == null) {
            Calibration cal = getCalibration();
            chCal.setFunction(cal.getFunction(), cal.getCoefficients(), cal.getValueUnit(), cal.zeroClip());
        }
    }
    
    // Restore Calibration and Display Properties from chCalibration chDisplayProps arrays  
    // to current channel ImageProcessor.
    public void restoreCurrentChannelProperties() {
        int channel = getCurrentChannel();
        restoreChannelProperties(channel);
    
        // Density calibration properties
        ChannelCalibration chCal = chCalibration[channel-1];  
        if (getGlobalCalibration() == null) {
            Calibration cal = getCalibration();
            cal.setFunction(chCal.getFunction(), chCal.getCoefficients(), chCal.getValueUnit(), chCal.isZeroClip());
        }
    }
    
    public void storeChannelProperties(int channel) {
		checkChannel(channel);
		
		ChannelDisplayProperties props = chDisplayProps[channel-1];
		ImageProcessor channelIP = channelIPs[channel-1];
		
        // Display properties
		if (!props.isDisplayedGray() && !displayAllGray) {
			props.setColorModel(channelIP.getColorModel());
		}
		props.setMinValue(channelIP.getMin());
		props.setMaxValue(channelIP.getMax());
		props.setMinThreshold(channelIP.getMinThreshold());
		props.setMaxThreshold(channelIP.getMaxThreshold());		
		props.setLutUpdateMode(channelIP.getLutUpdateMode());
        // TODO: handling of calibration function?
	}
	
    public void restoreChannelProperties(int channel) {
		checkChannel(channel);
		
		ChannelDisplayProperties props = chDisplayProps[channel-1];
		ImageProcessor channelIP = channelIPs[channel-1];
		
		if (props.isDisplayedGray() || displayAllGray) {
			channelIP.setColorModel(grayColorModel);
			if (channel == getCurrentChannel())
				getStack().setColorModel(grayColorModel);
		} else {
			channelIP.setColorModel(props.getColorModel());
			if (channel == getCurrentChannel())
				getStack().setColorModel(props.getColorModel());
		}
		channelIP.setMinAndMax(props.getMinValue(), props.getMaxValue());
		if(props.getMinThreshold() != ImageProcessor.NO_THRESHOLD) {
			channelIP.setThreshold(props.getMinThreshold(), props.getMaxThreshold(), props.getLutUpdateMode()); 
		}		      
        // TODO: handling of calibration function?
	}

    public ImageProcessor getProcessor(int channel) {
        checkChannel(channel);
        return channelIPs[channel-1];
    }
	
   public Image5D duplicate() {
       String newTitle = WindowManager.makeUniqueName(getTitle());
       ImagePlus impOrig = new ImagePlus(newTitle, imageStack);
       ImagePlus impCopy = (new ij.plugin.filter.Duplicater()).duplicateStack(impOrig, newTitle);
       ImageStack stackCopy = impCopy.getStack();
       
       Image5D i5d = new Image5D(newTitle, stackCopy, getNChannels(), getNSlices(), getNFrames());
       
       // Copy the calibration data.
       i5d.setCalibration(getCalibration().copy());
       
       // Copy the arrays for channel display handling.
       for (int i=0; i<getNChannels(); i++) {
           i5d.setCurrentPosition(0, 0, i, 0, 0);
           i5d.chCalibration[i] = chCalibration[i].copy();
           i5d.chDisplayProps[i] = chDisplayProps[i].copy();
           
           i5d.restoreCurrentChannelProperties();
       }
                    
       // Move to current positions.
       i5d.setCurrentPosition(currentPosition);

       return i5d;
   }

   /*
    * This functions splits the two halfs of an Image5D into two channels in a new Image5D
    */
    public Image5D split() {
       String newTitle = WindowManager.makeUniqueName(getTitle());
       ImagePlus impOrig = new ImagePlus(newTitle, imageStack);
       // get new stack widths
       ImageStack stack = impOrig.getStack();
       int newwidth=getWidth()/2;
       int height=getHeight();
       ColorModel cm=createLut().getColorModel();

       // copy the left and right to  new stacks
       ImageStack newStack=new ImageStack(newwidth, height, cm);
       String slicename="slice_";
       for (int n=1; n<=stack.getSize();n++) {
           ImageProcessor theslice=stack.getProcessor(n);
           theslice.setRoi(0,0,newwidth,height);
           newStack.addSlice(slicename+n,theslice.crop());
           theslice.setRoi(newwidth,0,newwidth,height);
           newStack.addSlice(slicename+n,theslice.crop());
        }
       /*
        slicename="slice_";
        for (int n=1; n<=stack.getSize();n++) {
           ImageProcessor theslice=stack.getProcessor(n);
           theslice.setRoi(newwidth,0,newwidth,height);
           newStack.addSlice(slicename+n,theslice.crop());
        }
        */
        System.out.println(getNSlices() + ", " + getNFrames() + ", " + newStack.getSize() + ", " + stack.getSize()); 
        Image5D i5d = new Image5D(newTitle, newStack, 2, getNSlices(), getNFrames());
       
        // Copy the calibration data.
        i5d.setCalibration(getCalibration().copy());
       
        // Copy the arrays for channel display handling.
        i5d.setCurrentPosition(0, 0, 0, 0, 0);
        i5d.chCalibration[0] = chCalibration[0].copy();
        i5d.chDisplayProps[0] = chDisplayProps[0].copy();
        i5d.chCalibration[0].setLabel(chCalibration[0].getLabel() + "-Left");
        i5d.setCurrentPosition(0, 0, 1, 0, 0);
        i5d.chCalibration[1] = chCalibration[0].copy();
        i5d.chDisplayProps[1] = chDisplayProps[0].copy();
        i5d.chCalibration[1].setLabel(chCalibration[0].getLabel() + "-Right");
        i5d.restoreCurrentChannelProperties();
                    
         // Move to current positions.
         i5d.setCurrentPosition(currentPosition);

         return i5d;
   }

   public Image5D crop() {
       // we can not crop the original, since this will crash Image5D
       // Make a copy, crop, show in Image5D and delete original
       String newTitle = WindowManager.makeUniqueName(getTitle());
       ImagePlus impOrig = new ImagePlus(newTitle, imageStack);
       Roi roi = this.getRoi();
       if (roi == null)
          throw new IllegalArgumentException("No ROI selected");
       ImagePlus impCrop = (new ij.plugin.filter.Duplicater()).duplicateStack(impOrig, newTitle);
       impCrop.setRoi(roi);
       PlugInFilter crop = new ij.plugin.filter.Resizer();
       crop.setup("crop", impCrop);
       crop.run(impCrop.getProcessor());
       ImageStack stackCopy = impCrop.getStack();
       
       Image5D i5d = new Image5D(newTitle, stackCopy, getNChannels(), getNSlices(), getNFrames());
       
       // Copy the calibration data.
       i5d.setCalibration(getCalibration().copy());
       
       // Copy the arrays for channel display handling.
       for (int i=0; i<getNChannels(); i++) {
           i5d.setCurrentPosition(0, 0, i, 0, 0);
           i5d.chCalibration[i] = chCalibration[i].copy();
           i5d.chDisplayProps[i] = chDisplayProps[i].copy();
           i5d.restoreCurrentChannelProperties();
       }
                    
       // i5d.setDisplayGrayInTiles(displayGrayInTiles);
       // i5d.setDisplayMode(displayMode);

       // Move to current position.
       i5d.setCurrentPosition(currentPosition);

       return i5d;
   }

// static utility methods.
	/** Called from constructor Image5D(String title, ImageProcessor ip) in call to this().
	 * 	Checks if ip is null and creates a stack from it.
	 */
	static private ImageStack createStackFromProcessor(ImageProcessor ip) {
		if (ip == null)
			throw new IllegalArgumentException("ImageProcessor is null.");
		ImageStack is = new ImageStack(ip.getWidth(), ip.getHeight(), ip.getColorModel());
		is.addSlice("", ip);
		return is;
	}

	/** Called from constructor Image5D(String title, int type, int[] dimensions, boolean fill) 
	 * 	in call to super(). Checks the number of dimensions and creates an ImageProcessor.
	 */
	static protected ImageProcessor createProcessorFromDims(int type, int[] dimensionSizes) {
		if (dimensionSizes.length!=nDefaultDimensions)
			throw new IllegalArgumentException("Invalid number of dimensions.");
		ImageProcessor ip;
		switch (type) {
			case GRAY8:
				ip = (ImageProcessor) new ByteProcessor(dimensionSizes[0], dimensionSizes[1]);
				break;
			case GRAY16:
				ip = (ImageProcessor) new ShortProcessor(dimensionSizes[0], dimensionSizes[1]);
				break;
			case GRAY32:
				ip = (ImageProcessor) new FloatProcessor(dimensionSizes[0], dimensionSizes[1]);
				break;
			default:
				ip = null;
				throw new IllegalArgumentException("Invalid data type.");
		}
		return ip;
	}

    /** Called from constructor Image5D(String title, ImageStack stack, int nChannels, int nSlices, int nFrames)
     *  in call to super(). Checks the dimensions and creates the z-stack at first channel/frame.    
     */     
    static protected ImageStack createZStackFromImageStack(ImageStack imageStack, int nChannels, int nSlices, int nFrames) {
        if(imageStack==null) throw new IllegalArgumentException("ImageStack is null");
        if (nChannels<1 | nSlices<1 | nFrames<1) throw new IllegalArgumentException("Stack dimensions must be >=1.");
        if(nChannels*nSlices*nFrames!=imageStack.getSize()) throw new IllegalArgumentException("Dimensions don't match ImageStack size.");
         
        ImageStack stack = new ImageStack(imageStack.getWidth(), imageStack.getHeight());
        for(int i=0; i<nSlices; i++) {
            stack.addSlice(imageStack.getSliceLabel(nChannels*i+1), imageStack.getPixels(nChannels*i+1));  
        }
        return stack;
    }

}
	
