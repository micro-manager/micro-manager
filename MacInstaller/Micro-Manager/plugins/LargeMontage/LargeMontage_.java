/**
* Copyright (c) 2005-2007 Vilppu Tuominen (vtuo@iki.fi)
* University of Tampere, Institute of Medical Technology
*
* http://iki.fi/vtuo/software/largemontage/
*
* This program is free software; you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation; either version 2 of the License, or (at your
* option) any later version.
*
* This program is distributed in the hope that it will be useful, but
* WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* General Public License for more details.
*
* You should have received a copy of the GNU General Public License along
* with this program; if not, write to the Free Software Foundation, Inc.,
* 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
*
*/

import ij.IJ;
import ij.ImageJ;
import ij.Macro;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.io.SaveDialog;
import ij.plugin.PlugIn;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Iterator;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import javax.media.jai.BorderExtender;
import javax.media.jai.Histogram;
import javax.media.jai.JAI;
import javax.media.jai.KernelJAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.ROIShape;
import javax.media.jai.RenderedOp;
import javax.media.jai.TiledImage;
import javax.media.jai.operator.MosaicDescriptor;

import com.sun.media.imageio.plugins.jpeg2000.J2KImageWriteParam;
import com.sun.media.imageio.plugins.tiff.TIFFImageWriteParam;
import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageEncoder;
import com.sun.media.jai.codec.PNMEncodeParam;

/**
 * Program's main class, which includes almost all the functionality. See the
 * program's homepage for documentation.
 */
public class LargeMontage_ implements PlugIn {
    
    /** This plugin/program was started from ImageJ? */
    private boolean startedFromIJ = false;
    
    /** Use verbose output mode? */
    private boolean verbose = false;

    /** Use a predefined SWS file for source image count? */
    private boolean sws = false;
    
    /** Number of rows in the montage. */
    private int montageRows = 0;
    
    /** Number of columns in the montage. */
    private int montageCols = 0;
    
    /** Source image count. */
    private int sourceImageCount = 0;
    
    /** Source images' width (px). */
    private int width = 0;
    
    /** Source images' height (px). */
    private int height = 0;
    
    /** Source images' bits per pixel (bpp). */
    private int bpp = 0;

    /** Edge overlapping (X axis, px). */
    private int overlapX = 0;
    
    /** Edge overlapping (Y axis, px). */
    private int overlapY = 0;

    /** Fixed shift amount (X axis, px). */
    private int shiftX = 0;
    
    /** Fixed shift amount (Y axis, px). */
    private int shiftY = 0;    
    
    /**
    * Overlap threshold percent, i.e. how many percent can user defined
    * overlaps vary.
    */
    private float overlapThres = 0.40F;
    
    /** Tile width (px), used only with JPEG2000. */
    private int tileWidth = 0;
    
    /** Tile height (px), used only with JPEG2000. */
    private int tileHeight = 0;    

    /** Use registration? */
    private boolean registration = false;
    
    /** Registration options, if registration is used. */
    private String regOptions = "";
    
    /** Registration's source file. */
    private File regSource = null;
    
    /** Registration's target file. */
    private File regTarget = null;
    
    /** Final output file. */
    private File finalOutput = null;
    
    /** Final output file's format (either PNM, TIFF or JPEG2000). */
    private String finalFormat = "";
        
    /**
    * Use "snake-like" row ordering? e.g.:
    * 
    *       Snake       |     Regular
    *                   |
    *   1   2   3   4   |   1   2   3   4      
    *   8   7   6   5   |   5   6   7   8
    *   9  10  11  12   |   9  10  11  12
    *      ..  ..  13   |  13  ..  ..
    */
    private boolean snakeRows = false;

    /** Unsharp masking level (gain: level/10), 0 if no sharpening is done. */
    private int sharpen = 0;
    
    /** Seam smoothing for every source image in the montage, 0 if none. */
    private int smoothen = 0;
    
    /** Source data, used in convolve/smoothing operation. */
    private BufferedImage convSource = null;
    
    /** Target data, used in convolve/smoothing operation. */
    private BufferedImage convTarget = null;
    
    /** A help variable, used in horizontal convolving. */
    private MapImage prevConvSource = null;
    
    /**
    * JPEG2000 compression level (rate is 1 : level*4).
    * 0 if no compression is done (lossless).
    */
    private int compress = 0;
    
    /**
    * Use special source image numbering? e.g.:
    * 
    * IMAGExxxyyy.JPG, where xxx is the column number and
    *                        yyy is the row number.
    *
    * Base image must always begin with 000000 number and
    * the number mask's size must always be 6 characters.
    */
    private boolean specialSrcNumbering = false;

    /**
    * ImageJ's instance - used if registration is used and the program has been
    * started from command line.
    */
    private ImageJ IJInstance = null;
    
    /** The output montage represented with a "map". */
    private MapImage [][] sourceMap = null;
    
    /** Base file, which acts as a sample for the rest of the source images. */
    private File baseFile = null;
    
    /** Base file's path. */
    private String baseFilePath = "";
    
    /** Base file's prefix, e.g. IMG0001.JPG -> IMG */
    private String baseFilePrefix = "";
    
    /** Base file's suffix, e.g. IMG0001.JPG -> .JPG */
    private String baseFileSuffix = "";
    
    /** Base file's number, e.g. IMG0001.JPG -> 0001 */
    private String baseFileNumber = "";
    
    /** Base file's "name", e.g. IMG0001.JPG -> IMG0001 */
    private String baseFileJustName = "";
    
    /**
    * Base file's numbering begins with 0 (or 1)?
    * If special source numbering is used, then the base file's number must
    * be 000000. If "normally" incrementing numbering is being used, then 0
    * or 1 as the first (last) number is acceptable, e.g. 001 or 000.
    */
    private boolean baseFileStartZero = false;    

    /** Whole montage's cumulative X correction. */
    private int montageXcorr = 0;
    
    /** Whole montage's cumulative Y correction. */
    private int montageYcorr = 0;
    
    // ------------------------------------------------------------------------
    
    /** Default constructor. */
    public LargeMontage_() {
        
        this.regSource = new File("source.jpg");
        this.regTarget = new File("target.jpg");
        
    }
    
    /** Constructor, which is used when starting the program from console. */
    public LargeMontage_(String[] args) {
        
        this();
        
        this.startedFromIJ = false;
        
    	// if asked for help, print out help information and quit
    	if ( args.length == 0           || 
    		 args[0].equals( "-help" )  ||
    		 args[0].equals( "--help" ) ||            		 
    		 args[0].equals( "/help" )  ||
    		 args[0].equals( "-?" )     ||            		 
    		 args[0].equals( "/?" ) ) {
    		
    		verbose = true;
    		printMessage( "Usage: java LargeMontage_ [-options] [args...]" );
    		printMessage( "" );
    		printMessage( "where options are:" );
    		printMessage( "" );
    		printCmdLineSyntax();
    		printMessage( "" );
    		printMessage( "Version: " + Constants.VERSION );
    		System.exit( 0 );
    		
    	}   
        
        if ( !processOptions( args ) ) {        	
            
        	System.out.println();
            System.out.println("Invalid command line arguments.");
            System.out.println();
            System.out.println("Usage: java LargeMontage_ [-options] [args...]");
            System.out.println();
            System.out.println("where options are:");
            System.out.println();
            printCmdLineSyntax();
            System.out.println();
            System.out.println("(Hint: you can't use registration with less than 2 px overlappings.)");
            System.out.println();
            System.out.println("Version: " + Constants.VERSION);            
            
            System.exit( 1 );
            
        }
        
        if ( !startedFromIJ && registration ) {

                // try to fetch the ImageJ's path from the classpath
            	String cp = System.getProperty( "java.class.path" );
            	int idx1 = cp.indexOf( "\\ij.jar" );
            	if ( idx1 < 0 ) {
            		idx1 = cp.indexOf( "/ij.jar" );
            		if ( idx1 < 0 ) {
            			System.err.println(
            				"Cannot find ImageJ's ij.jar from the classpath; " +
            				"cannot find the plugin directory!"
            			);
            			System.exit( 1 );
            		}
            	}
            	String separator = System.getProperty( "path.separator" );
            	int idx2 = cp.lastIndexOf( separator, idx1 );
            	String IJDir = cp.substring( idx2 + 1, idx1 );
            	
                // set the ImageJ's plugin directory's path 
            	System.getProperties().setProperty( "plugins.dir", IJDir );

            	// create a new ImageJ-instance.
            	this.IJInstance = new ImageJ(null);
            
        }

    }

    // ------------------------------------------------------------------------ 
    
    /**
    * PlugIn-interface's method (ImageJ's entrance).
    */
    public void run(String arg) {
        
        this.startedFromIJ = true;
        boolean calledFromMacro = false;
        
    	// check whether macro parameters are given
        String argBuf = Macro.getOptions();
        if ( argBuf != null ) {
        	
        	// yes..
        	calledFromMacro = true;
        	
        } else {
    		
    		// no..

    		// Start the construction of the main dialog.
    		OpenDialog od = new OpenDialog("Specify base image file", "");
    		String input = od.getDirectory() + od.getFileName();

    		SaveDialog sd = new SaveDialog("Output file (PBM/PGM/PPM, TIFF or JP2)", "montage", ".ppm");
    		String output = sd.getDirectory() + sd.getFileName();

    		if (od.getFileName() == null) {
    			input = "";
    		}
    		if (sd.getFileName() == null) {
    			output = "";
    		}

    		GenericDialog gd = new GenericDialog( "LargeMontage (" + Constants.VERSION + ")" );

    		gd.addStringField("Base image file", input, 20);        
    		gd.addStringField("Output file", output, 20);
    		gd.addNumericField("Rows", 0.0D, 0, 3, "");
    		gd.addNumericField("Columns", 0.0D, 0, 3, "");
    		gd.addNumericField("Horizontal overlap (X)", 0.0D, 0, 3, "px");
    		gd.addNumericField("Vertical overlap (Y) *", 0.0D, 0, 3, "px");
    		gd.addNumericField("Fixed_X shift", 0.0D, 0, 3, "px");
    		gd.addNumericField("Fixed_Y shift", 0.0D, 0, 3, "px");

    		String [] choices1 = new String [] {
    				"No", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" };

    		gd.addChoice("Unsharp masking", choices1, "No");

    		String [] choices2 = new String [] {
    				"No", "1", "2", "3" };

    		gd.addChoice("Seam smoothing", choices2, "No");
    		gd.addChoice("Compression **", choices1, "No");

    		CheckBoxPanel cbp = new CheckBoxPanel();
    		gd.addPanel( cbp );

    		gd.addMessage("  * approximate if registration is used\n" +
    		"** used only with JPEG2000");

    		gd.showDialog();

    		// Start parsing the input.

    		input = gd.getNextString();
    		input = "\"" + input + "\"";
    		argBuf = "-base " + input + " ";

    		output = gd.getNextString();
    		output = "\"" + output + "\"";
    		argBuf += "-output " + output + " ";

    		argBuf += "-rows " + (int) gd.getNextNumber() + " ";
    		argBuf += "-cols " + (int) gd.getNextNumber() + " ";
    		argBuf += "-ox " + (int) gd.getNextNumber() + " ";   
    		argBuf += "-oy " + (int) gd.getNextNumber() + " ";
    		argBuf += "-sx " + (int) gd.getNextNumber() + " ";   
    		argBuf += "-sy " + (int) gd.getNextNumber() + " ";

    		String selection = gd.getNextChoice();
    		if (!selection.equals("No")) {
    			argBuf += "-sharpen " + selection + " ";
    		}

    		selection = gd.getNextChoice();
    		if (!selection.equals("No")) {
    			argBuf += "-smoothen " + selection + " ";
    		}

    		selection = gd.getNextChoice();
    		if (!selection.equals("No")) {            
    			argBuf += "-compress " + selection + " ";
    		}

    		if ( cbp.yOverlapChecked() ) {
    			regOptions += Constants.Y_OVERLAP_TAG;
    		}

    		if ( cbp.xShiftChecked() ) {
    			regOptions += Constants.X_SHIFT_TAG;
    		}

    		if ( cbp.yShiftChecked() ) {
    			regOptions += Constants.Y_SHIFT_TAG;
    		}

    		// create the registration parameter string
    		if ( regOptions.length() > 0 ) {
    			argBuf += "-reg " + regOptions + " ";
    			regOptions = "";
    		}       

    		if ( cbp.snakeChecked() ) {
    			argBuf += "-snake ";    
    		}

    		if ( cbp.specialChecked() ) {
    			argBuf += "-special ";  
    		}

    		if ( cbp.swsChecked() ) {
    			argBuf += "-sws ";  
    		}

    		if ( cbp.verboseChecked() ) {
    			argBuf += "-verbose ";
    		}        

    		if ( gd.wasCanceled() ) {
    			return;
    		}

    		if( gd.invalidNumber() ) {
    			IJ.error( "Invalid number entered." );
    			return;
    		}

    	}

        long start = getTimestamp();
    	
        // transform the input into a "command line argument" style string
        // (same input checking is also done for command line args)
        StringTokenizer st = new StringTokenizer(argBuf);
        int length = st.countTokens();       
        String args [] = new String[length];        
        for ( int i = 0; i < length; i++ ) {
            args[i] = st.nextToken();
        }        
    	
    	// if asked for help, print out help information and quit
    	if ( args[0].equals( "-help" )  ||
    		 args[0].equals( "--help" ) ||            		 
    		 args[0].equals( "/help" )  ||
    		 args[0].equals( "-?" )     ||            		 
    		 args[0].equals( "/?" ) ) {
    		
    		verbose = true;
    		printMessage( "Usage:" );
    		printMessage( "" );
    		printCmdLineSyntax();
    		printMessage( "" );
    		return;
    		
    	}           
        
        // Process the options.
        if ( !processOptions( args ) ) {
        	if ( calledFromMacro ) {
        		verbose = true;
        		printMessage( "Invalid macro parameters." );
        		printMessage( "" );
        		printMessage( "Usage:" );
        		printCmdLineSyntax();
        		printMessage( "" );
        		printMessage( "Hints: " );
        		printMessage( "  * On Windows-based environments, be sure to use \\\\ as a path separator." );
        		printMessage( "  * You can't use registration with less than 2 px overlappings." );        		
        	} else {
        		IJ.error( "Invalid options." );
        	} 
            return;
        }
                
        try {
            
            // Call to the program's main execution method.
            execute();
            
        }
        catch (IOException e) {
            IJ.error("Error: " + e.toString());
            return;
        }
        
        long duration = getTimestamp() - start;
        IJ.showStatus("Completed. (Duration: " + duration/1000 + " s)");
        
    }

    // ------------------------------------------------------------------------ 
    
    /** Main method (command line entrance).*/
    public static void main(String[] args) {

        // Instantiate new LargeMontage_.
        LargeMontage_ lm = new LargeMontage_(args);
        
        try {
            
            // Call to the program's main execution method.
            lm.execute();
            
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit( 1 );
        }
        
        System.exit( 0 );

    }
    
    // ------------------------------------------------------------------------ 
    
    /**
    * Removes temporarily used TIFF-files from "tiff" directory.
    */
    public void removeTempTIFFfiles() {

        IJ.showStatus("Removing temporary TIFF-files..");
        
        File tiffDir = new File("tiff");
                
        if (tiffDir.exists()) {
            
            boolean error = false;
            
            for (int i = 0; i < montageRows; i++) {
                for (int j = 0; j < montageCols; j++) {
                
                    if (!sourceMap[i][j].getFile().delete()) {
                        error = true;
                    }               
                        
                }                                
            }
            
            if (error) {
                IJ.log("Cannot delete all temporary TIFF-files from directory " +
                    "\"tiff\". You have to delete them manually.");
            }

            
            File [] files = tiffDir.listFiles();
            
            if (files.length == 0) {
                tiffDir.delete();
            }
            
        }
        
    }
    
    // ------------------------------------------------------------------------
            
    /**
    * Program's/plugin's main execution method. First preprocesses source
    * images and performs selected additional operations (e.g. convolving
    * and unsharp masking) and then calculates and defines their correct
    * position in in the montage. Finally starts the actual construction
    * of the montage by writing to the output file.
    *
    * @throws IOException if an error with file handling occurs.
    */
    public void execute() throws IOException {
        
        JAI.disableDefaultTileCache();
                
        // Check whether directory "tiff" already exists.
        File tiffDir = new File("tiff");
        if (!tiffDir.exists()) {
            // If not, create it.
            if (!tiffDir.mkdir()) {
                IJ.error("Cannot create temporary directory \"tiff\".");
                return;
            }
        }

        // Source image's position in the montage.
        int xTrans = 0;
        int yTrans = 0;
        
        // (Possibly) adjusted Y overlap with registration. Default is fixed value.
        int adjOverlapY = overlapY;
        
        // Maximum positive X correction (if X shift is used).
        int maxXcorr = 0;
  
        // Current row and col information.
        int row = 0;
        int col = 1;        
        
        // Current image's number.
        String number = baseFileNumber;

        long start = getTimestamp();
                
        // Read in the base image (which acts as a sample for the rest of the images).
        processBaseImage();
        
        // Print used options.
        printOptions();
                
        printMessage("Preprocessing images..");        
        IJ.showStatus("Preprocessing images..");
        
        // Calculate whole montage's Y shift, if registration is used.
        if ( registration && regOptions.indexOf( Constants.Y_SHIFT_TAG ) != -1 ) {
            shiftY = calcShiftY(baseFileNumber, row);            
        }
        
        printMessage( "Montage's Y shift: " + shiftY + " px" );
        
        // Then the rest of the source images.
        for (int i = 1; i < sourceImageCount; i++) {
            
            IJ.showStatus("Preprocessing images..");
            IJ.showProgress( (i / (double) sourceImageCount) );
            
            if ( i % montageCols == 0 ) {
                
                // We have processed current row.
                
                // Next row..
                col = 0;
                row++;

            }
            
            // Get current file's number.
            number = getSrcNum(number, row, col);
            
            // Defining of the current row's Y overlap..
            if ( i % montageCols == 0 ) {
                
                // Is registration being used?
                if ( registration &&
                    (regOptions.indexOf( Constants.Y_OVERLAP_TAG ) != -1 ||
                     regOptions.indexOf( Constants.X_SHIFT_TAG ) != -1) ) {
                    
                    // Yes..perform registration calculations.
                    printMessage("Performing registration calculations for row " + (row+1) + "..");
                    
                    int [] adjustments = calcVertOverlap(number, row);
                    
                    if ( regOptions.indexOf( Constants.X_SHIFT_TAG ) != -1 ) {                    
                        shiftX = adjustments[0];
                    }
                    
                    if ( regOptions.indexOf( Constants.Y_OVERLAP_TAG ) != -1 ) {
                        adjOverlapY = adjustments[1];
                    }
                    
                    IJ.showProgress( (i / (double) sourceImageCount) );                  
                    
                }
                
                printMessage( (row+1) + ". row's Y overlap: " + adjOverlapY + " px" );
                printMessage( (row+1) + ". row's X shift: " + shiftX + " px" );
                
            }
                        
            String file = "tiff/" + baseFilePrefix + number + ".tif";
            sourceMap[row][col] = new MapImage(new File(file), null, 0, 0);
            
            printMessage("Preprocessing " + baseFilePrefix + number + baseFileSuffix + "..");
            
            // Are we preprocessing the last source image?
            if ( i == (sourceImageCount-1) ) {

                // Yes..
                preprocessTIFF(number, row, col, adjOverlapY, shiftX, shiftY, true);

            }
            else {

                // No..
                preprocessTIFF(number, row, col, adjOverlapY, shiftX, shiftY, false);

            }
            
            // Calculation of source file's new position in the montage..

            if ( i % montageCols == 0 ) {
                
                // We are processing the first image of the current row.               
                    
                yTrans = height * row - adjOverlapY - montageYcorr;
                xTrans = shiftX + montageXcorr;

                montageYcorr += adjOverlapY;
                montageXcorr += shiftX;
                
                if ( montageXcorr > maxXcorr ) {
                    maxXcorr = montageXcorr;
                }                               

            }
            else {
                
                // We are somewhere in the middle of the current row.
                    
                xTrans += width - overlapX;
                yTrans += shiftY;
                
            }

            sourceMap[row][col].setXtrans(xTrans);
            sourceMap[row][col].setYtrans(yTrans);
            
            col++;
            
        } // for

        // If the Y shift is positive, add it to the montageYcorr.
        if (shiftY > 0) {
            montageYcorr -= shiftY * (montageCols-1); 
        }

        if (maxXcorr <= 0) {
            montageXcorr = 0;
        }
        else {
            montageXcorr = maxXcorr;
        }
        
        // Begin to build the rendering chain:
        // (read -> translate -> mosaic -> write)
            
        // Source vector for the mosaic-operation, containing all the source images.
        Vector<Object> renderedOps = new Vector<Object>();

        // Used FileImageInputStreams.
        Vector<Object> readStreams = new Vector<Object>();

        // First, add the base image to the chain.
        ParameterBlockJAI baseReadParams =
            new ParameterBlockJAI("ImageRead", "rendered");
        FileImageInputStream baseStream = new FileImageInputStream(sourceMap[0][0].getFile());
        readStreams.addElement(baseStream);
        baseReadParams.setParameter("Input", baseStream);
        RenderedOp baseOp = JAI.create("ImageRead", baseReadParams);
        sourceMap[0][0].setOp(baseOp);
        renderedOps.addElement(baseOp);

        number = baseFileNumber;
        row = 0;
        col = 1;

        xTrans = 0;
        yTrans = 0;

        // Then the rest of the source images.
        for (int i = 1; i < sourceImageCount; i++) {
            
            if ( i % montageCols == 0 ) {

                // We have processed current row.

                // Next row..
                col = 0;
                row++;

            }
            
            File file = sourceMap[row][col].getFile();
            
            // Image's position information in the montage/mosaic.
            xTrans = (int) sourceMap[row][col].getXtrans();
            yTrans = (int) sourceMap[row][col].getYtrans();
            
            FileImageInputStream fiis = new FileImageInputStream(file);
            readStreams.addElement(fiis);
              
            // Source image's reading operation. 
            ParameterBlockJAI readParams =
                new ParameterBlockJAI("ImageRead", "rendered");
            readParams.setParameter("Input", fiis);
            RenderedOp op = JAI.create("ImageRead", readParams);            

            // If the new position would be negative (in x or y -axis)
            // -> perform a suitable crop-operation.
            if (xTrans < 0 || yTrans < 0) {
                
                float absX = 0.0F;
                float absY = 0.0F;
                
                if (xTrans < 0) {
                    absX = (float) -xTrans;
                }
                if (yTrans < 0) {
                    absY = (float) -yTrans;
                }
            
                ParameterBlockJAI cropParams =
                    new ParameterBlockJAI("Crop", "rendered");
            
                cropParams.addSource(op);
                cropParams.setParameter("x", absX); 
                cropParams.setParameter("y", absY);
                cropParams.setParameter("width", width - absX);
                cropParams.setParameter("height", height - absY);
            
                op = JAI.create("Crop", cropParams);
            
            }
        
            // Translate source images to correct places in the mosaic.
            ParameterBlockJAI transParams =
                new ParameterBlockJAI("Translate", "rendered");
            transParams.addSource(op);
            transParams.setParameter("xTrans", (float) xTrans);
            transParams.setParameter("yTrans", (float) yTrans);
            
            op = JAI.create("Translate", transParams);
            
            sourceMap[row][col].setOp(op);
            renderedOps.addElement(op);

            col++;
                    
        } // for
        
        // The (one and only) mosaic-operation.
        ParameterBlockJAI mosParams =
            new ParameterBlockJAI("Mosaic", "rendered");
        mosParams.setSources(renderedOps);
        mosParams.setParameter("mosaicType", MosaicDescriptor.MOSAIC_TYPE_OVERLAY);
        
        // Set the default background value to white.
        int numBands = sourceMap[0][0].getOp().getColorModel().getNumColorComponents();
        double [] backgroundValues = new double [numBands];
        for (int j = 0; j < numBands; j++) {
            backgroundValues[j] = 255.0D;
        }        
        mosParams.setParameter("backgroundValues", backgroundValues);
        
        RenderedOp finalImage = JAI.create("Mosaic", mosParams);
                
        long duration = getTimestamp() - start;
        printMessage("Preprocess duration: " + duration + " ms");       
        
        IJ.showStatus("Constructing the montage..");
        start = getTimestamp();    
        
        int sizePxWidth =
            width * montageCols - ( (montageCols-1) * overlapX ) + montageXcorr;
        
        int sizePxHeight = height * montageRows - montageYcorr;
        
        printMessage("Montage's width: " + sizePxWidth + " px");
        printMessage("Montage's height: " + sizePxHeight + " px");

        // Writing to the output file starts the rendering of the chain.
        writeMontage(finalImage, sizePxWidth, sizePxHeight);
        
        duration = getTimestamp() - start;
        printMessage("Montage construction duration: " + duration + " ms");

        IJ.showStatus("Cleaning temporary files..");
        printMessage("Cleaning temporary files..");
        
        // Remove temporary registration files.
        removeTempRegFiles();
        
        if (!startedFromIJ && registration) {
            
            // Quit ImageJ.
            IJInstance.quit();
            
            // Destroy the unnecessary IJ-pref-file generated.
            File pref = new File("IJ_Prefs.txt");
            pref.delete();
            
        }

        Enumeration streams = readStreams.elements();
        while (streams.hasMoreElements()) {
            FileImageInputStream fiis = (FileImageInputStream) streams.nextElement();
            fiis.close();
        }   
    
        removeTempTIFFfiles();
        
        IJ.showProgress(1.00D);
        printMessage("Completed.");
        
    }
    
    // ------------------------------------------------------------------------
    
    /**
    * Calculates row's vertical overlapping (Y overlap) and the (possible)
    * X shift with ImageJ's TurboReg registration-plugin.
    *
    * The calculation process goes as follows:
    *   - source image is the current row's first image and the target is the
    *     image above the source
    *   - when two sources are found, we calculate a specific ROI from both of
    *     them and from that area, we calculate std. deviation on each band by
    *     using the histogram-operation. Then we calculate the mean of all
    *     bands' std.deviations. By using this mean value, we can identify if
    *     the selected areas have registrable image data. If they do, we
    *     perform the TurboReg-registration process and get the appropriate
    *     Y overlap value. If they don't, we move on to right, to the next
    *     column.
    *
    * @param number Source image number (used as a mask).
    * @param row    Row number, for which the overlap is calculated.
    *    
    * @return Row's Y overlap and X shift in an array. Returns zeros
    *         if row is 0 (i.e. the first row of the montage). Returns the
    *         fixed <code>overlapY</code> if no registrable image data is
    *         found.
    *   
    * @throws IOException if an error with file handling occurs. 
    */
    private int[] calcVertOverlap(String number, int row) throws IOException {
        
        int [] results = {0, overlapY};
        
        // If current row is the first row in the montage..
        if ( row == 0 ) {
            int [] zeros = {0,0};
            return zeros;
        }
        
        int col = 0;
        
        int adjOverlapY = (int) (overlapY * (1.00F + overlapThres));
        
        removeTempRegFiles();
        
        boolean done = false;        

        while (!done) {
            
            String srcNumber = getSrcNum(number, row-1, col);
            String tgtNumber = getSrcNum(number, row, col);
            
            String srcPath = baseFilePath + baseFilePrefix + srcNumber + baseFileSuffix;
            String tgtPath = baseFilePath + baseFilePrefix + tgtNumber + baseFileSuffix;
            
            File [] files = { new File(srcPath),
                              new File(tgtPath) };
            
            // Wait (if necessary) for the target file.
            waitUntilReady(files[1]);
            
            Rectangle [] rois = { new Rectangle( 0, height-adjOverlapY-1, width, adjOverlapY ),
                                  new Rectangle( 0, 0, width, adjOverlapY ) };
                                   
            if ( containsRegistrableData(files, rois) ) {

                // Transform both images to grayscale (TurboReg needs it).
                transformToGrayJPG(files[0], regSource);
                transformToGrayJPG(files[1], regTarget);
                                        
                // Perform the registration calculation.
                int [] adjustments = calcRegistration(false, 1);

                if ( adjustments[1] != 0 ) {
                    
                    results[0] = adjustments[0];
                    results[1] = adjustments[1];
                    
                    done = true;
                    
                }
                
            }
            
            // Mark the calculation done if we have reached the last column.
            if ( (col+1) == montageCols ) {

                done = true;
                
            }
            else {
           
                col++;
                
            }
                                        
        }
        
        return results;

    }
    
    // ------------------------------------------------------------------------
    
    /**
    *
    * @param number Source image number (used as a mask).
    * @param row    Row number, for which the Y shift is calculated.
    *    
    * @return 
    *   
    * @throws IOException if an error with file handling occurs. 
    */    
    private int calcShiftY(String number, int row) throws IOException {
        
        // If montage has only one column, Y shift isn't calculated.
        if ( montageCols == 1 ) {
            return 0;
        }
        
        int col = 0;
        int shiftY = 0;
        
        int adjOverlapX = (int) (overlapX * (1.00F + overlapThres));
        
        removeTempRegFiles();
        
        printMessage("Calculating montage's Y shift..");

        boolean done = false;

        while (!done) {
            
            String srcNumber = getSrcNum(number, row, col);
            String tgtNumber = "";

            tgtNumber = getSrcNum(number, row, col+1);
            
            String srcPath = baseFilePath + baseFilePrefix + srcNumber + baseFileSuffix;
            String tgtPath = baseFilePath + baseFilePrefix + tgtNumber + baseFileSuffix;
            
            File [] files = new File[2];

            files[0] = new File(srcPath);
            files[1] = new File(tgtPath);
            
            // Wait (if necessary) for the target file.
            waitUntilReady(files[1]);
            
            Rectangle [] rois = { new Rectangle( width-adjOverlapX-1, 0, adjOverlapX, height ),
                                  new Rectangle( 0, 0, adjOverlapX, height ) };
                                   
            if ( containsRegistrableData(files, rois) ) {

                // Transform both images to grayscale (TurboReg needs it).
                transformToGrayJPG(files[0], regSource);
                transformToGrayJPG(files[1], regTarget);
                                        
                // Perform the registration calculation.
                int [] adjustments = calcRegistration(true, 1);
                shiftY = adjustments[1];
                
                done = true;
                
            }
            
            if ( (col+2) == montageCols && (row+1) == montageRows ) {
                
                // We have reached the end of the row and the row is the final row..
                
                done = true;
                
            }                        
            else if ( (col+2) == montageCols ) {
                
                // Skip to next row if we have reached the end of the current row.
                
                col = 0;
                row++;
                
            }
            else {
               
                col++;

            }
                                        
        }       
        
        return shiftY;
        
    }    
    
    // ------------------------------------------------------------------------
    
    /**
    * Checks whether the array of files (with specified ROIs) contains registrable
    * image data or not. This checking is done by calculating each band's standard
    * deviation and then calculating the mean of these standard deviations. If the
    * standard deviations' mean is big enough, then we can assume that the image
    * contains registrable data.
    *
    * @param files An array of image files.
    * @param rois  ROIs pointing to same indices in <code>files</code> array.
    *    
    * @return <code>true</code> if all argument files' ROIs contains registrable
    *         image data, <code>false</code> otherwise.
    *   
    * @throws IOException if an error with file handling occurs. 
    */    
    private boolean containsRegistrableData(File [] files, Rectangle [] rois)
        throws IOException {
            
        boolean contains = false;

        for (int i = 0; i < files.length; i++) {
        
            FileImageInputStream fiis = new FileImageInputStream(files[i]);

            ParameterBlockJAI readParams =
                new ParameterBlockJAI("ImageRead", "rendered");
            readParams.setParameter("Input", fiis);                
            RenderedOp readOp = JAI.create("ImageRead", readParams);
            
            ROIShape roi = new ROIShape(rois[i]);

            ParameterBlockJAI histParams =
                new ParameterBlockJAI("Histogram", "rendered");
            histParams.addSource(readOp);
            histParams.setParameter("roi", roi);                
            RenderedOp histOp = JAI.create("Histogram", histParams);
            
            Histogram h = (Histogram) histOp.getProperty("histogram");
            
            // Calculate each band's standard deviation.
            double stdDevMean = 0.0D;
            for (int j = 0; j < h.getNumBands(); j++) {
                double [] stdDevs = h.getStandardDeviation();
                stdDevMean += stdDevs[j];
            }
            
            // Calculate the standard deviations' mean.
            stdDevMean /= h.getNumBands();
            
            // printMessage("Calculated stdDevMean: " + stdDevMean);
        
            // Contains registrable image data?
            if (stdDevMean > 12.0D) {
                
                // Yes..selected area's pixels' values vary enough.
                
                // If all images contain registrable data..
                if (i == (files.length-1) ) {

                    printMessage("Found a registrable image pair: " +
                        files[0].getName() + " and " + files[1].getName() + ".");

                    contains = true;                        
                    
                }
                
            }
            else {
                
                // No..selected area contains (mostly) "one colored" information.
                
                break;
                
            }
            
            fiis.close();
        
        } // for
            
        return contains;
    
    }    
    
    // ------------------------------------------------------------------------
    
    /**
    * Returns one image's source number at specific place in the montage        
    * in appropriate (String) form.
    * 
    * @param zeroMask String which length is used as a mask of zeros.
    * @param row      Row number (row-1).
    * @param col      Column number (col-1).
    *
    * @return Wanted source number in appropriate (String) form.
    */
    private String getSrcNum(String zeroMask, int row, int col) {
        
        String number = "";
        
        // Special source file numbering?
        if (specialSrcNumbering) {
            
            // Yes..
                
            number = "000000";
            
            String tmp = Integer.toString(row);
            String rowNum = number.substring(3,6-tmp.length());
            rowNum = rowNum + tmp;
            number = number.replaceFirst(".{3}$", rowNum);
                        
            tmp = Integer.toString(col);
            String colNum = number.substring(0,3-tmp.length());
            colNum = colNum + tmp;
            number = number.replaceFirst("^.{3}", colNum);
            
        }
        else {
            
            // No..
                
            int num = 0;
            
            // Snake-like row ordering?
            if (snakeRows) {
                
                // Is current row even or odd?
                if ( (row+1) % 2 != 0 ) {
                    
                    // Odd..
                        
                    num = (row+1-1) * montageCols + (col+1);
                    
                }
                else {
                    
                    // Even..
                    
                    num = (row+1) * montageCols - col;
                    
                }               
                
            }
            else {
            
                num = (row+1-1) * montageCols + (col+1);
                
            }
            
            if (baseFileStartZero) {
                num--;              
            }
            
            number = String.valueOf(num);
            
            while (number.length() < zeroMask.length()) {               
                number = "0" + number;
            }
            
        }
        
        return number;

    }
    
    // ------------------------------------------------------------------------     
    
    /**
    * Processes the user defined options. The parameter array can be either
    * straight from command line or transformed from the ImageJ's dialog input.
    *
    * @param args An array of options.
    * 
    * @return <code>true</code> if options were correct.
    *         <code>false</code> otherwise.
    */
    private boolean processOptions( String[] args ) {
    
        boolean correct = true;
        
        try {
        
            // Iterate the array through.
            for (int i = 0; i < args.length; i++) {
                     
                if (args[i].equals("-base")) {
                    
                    if (args[i+1].matches("^-.*$")) {
                        correct = false;
                    }
                    
                    this.baseFile = new File( args[i+1].replace( "\"", "" ) );
                    
                    if (!baseFile.exists()) {
                        correct = false;
                    }
                    else {

                        if (baseFile.isDirectory()) {
                            
                            // Assume that the "special" src numbering is being used..
                            
                            File [] files = baseFile.listFiles();
                            
                            for (int j = 0; j < files.length; j++) {
                                
                                if ( files[j].getName().lastIndexOf("000000") != -1 ) {
                                                                
                                    this.baseFile = files[j];
                                    break;                                  
                                    
                                }
                                
                            }
                            
                            // If baseFile is still a dir, i.e. no "000000" ending file is found..
                            if (baseFile.isDirectory()) {
                                correct = false;
                            }
                        
                        }
                        
                        this.baseFilePath = baseFile.getCanonicalPath();
                        
                        int index = baseFilePath.lastIndexOf(baseFile.getName());
                        baseFilePath = baseFilePath.substring(0,index);
                        
                        String filename = baseFile.getName(); 
                        index = filename.lastIndexOf(".");
                        this.baseFileJustName = filename.substring(0,index);

                        this.baseFileSuffix = filename.substring(index,filename.length());

                        String [] splits = baseFileJustName.split("\\d+$");
                        
                        if (splits.length > 0) {
                            this.baseFilePrefix = splits[0];
                        }
                        
                        index = baseFilePrefix.length();
                        this.baseFileNumber =
                            baseFileJustName.substring(index,baseFileJustName.length());

                        if ( baseFileNumber.matches(".*1$") ) {
                            this.baseFileStartZero = false;
                        }
                        else if ( baseFileNumber.matches(".*0$") ) {
                            this.baseFileStartZero = true;
                        }
                        else {
                            correct = false;
                        }
                        
                    }
                    
                }
                else if (args[i].equals("-output")) {
                    
                    if (args[i+1].matches("^-.*$")) {
                        correct = false;
                    }
                                        
                    this.finalOutput = new File( args[i+1].replace( "\"", "" ) );

                    // Check the format of the output file.
                    
                    String format = "PNM";
                    
                    String name = finalOutput.getName();
                    int dotIndex = name.lastIndexOf(".");
                    
                    if (dotIndex != -1) {
                    
                        format = name.substring(dotIndex+1,name.length());
                        format = format.toUpperCase();
                        
                        if ( format.equals("JP2") || format.equals("J2K") ) {
                            format = "JPEG2000";
                        }
                        else if ( format.equals("TIF") || format.equals("TIFF") ) {
                            format = "TIFF";
                        }
                        else if ( format.equals("PBM") || format.equals("PGM") ||
                                  format.equals("PPM") || format.equals("PNM") ) {
                            format = "PNM";
                        }
                        else {
                            printMessage("Unknown file format: " + format + ", using PNM instead..");
                            format = "PNM";   
                        }
                    
                    }
                    
                    this.finalFormat = format;
                    
                }
                else if (args[i].equals("-rows")) {
                    this.montageRows = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("-cols")) {
                    this.montageCols = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("-ox")) {
                    this.overlapX = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("-oy")) {
                    this.overlapY = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("-sx")) {
                    this.shiftX = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("-sy")) {
                    this.shiftY = Integer.parseInt(args[i+1]);
                }                
                else if (args[i].equals("-reg")) {
                    
                    this.registration = true;

                    if ( (i+1) < args.length && !args[i+1].matches("^-.*$") ) {
                        
                        if ( args[i+1].indexOf( Constants.Y_OVERLAP_TAG ) != -1 ) {
                            this.regOptions += Constants.Y_OVERLAP_TAG;              
                        }
                        if ( args[i+1].indexOf( Constants.X_SHIFT_TAG ) != -1 ) {
                            regOptions += Constants.X_SHIFT_TAG;                          
                        }
                        if ( args[i+1].indexOf( Constants.Y_SHIFT_TAG ) != -1 ) {
                            regOptions += Constants.Y_SHIFT_TAG;
                        }
                        
                        if (regOptions.length() == 0) {
                            regOptions = Constants.Y_OVERLAP_TAG + Constants.X_SHIFT_TAG + Constants.Y_SHIFT_TAG;
                        }

                    }
                    else {
                        regOptions = Constants.Y_OVERLAP_TAG + Constants.X_SHIFT_TAG + Constants.Y_SHIFT_TAG;
                    }                

                }
                else if (args[i].equals("-compress")) {
                    this.compress = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("-sharpen")) {
                    this.sharpen = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("-smoothen")) {
                    this.smoothen = Integer.parseInt(args[i+1]);
                }
                else if (args[i].equals("-snake")) {
                    this.snakeRows = true;
                }
                else if (args[i].equals("-special")) {
                    this.specialSrcNumbering = true;
                }                
                else if (args[i].equals("-sws")) {
                    this.sws = true;
                }    
                else if (args[i].equals("-verbose")) {
                    this.verbose = true;
                }                
            
            } // for
            
        }
        catch (NumberFormatException e) {
            correct = false;
        }
        catch (ArrayIndexOutOfBoundsException e) {
            correct = false;
        }
        catch (StringIndexOutOfBoundsException e) {
            // Win-systems: comes from suffix's case-difference, e.g. .JPG and .jpg.
            correct = false;
        }
        catch (IOException e) {
            correct = false;
        }
        
        // Perform additional checkings
        if ( baseFile == null ||
             (specialSrcNumbering && !baseFileJustName.matches("^.*000000$")) ||
             montageRows < 0 ||
             montageCols < 0 ||
             sharpen < 0 ||
             sharpen > 10 ||
             smoothen < 0 ||
             smoothen > 3 ||
             overlapX < 0 ||
             overlapY < 0 ||
             ( (overlapY < 2 || overlapX < 2) && registration ) ) {
             
             correct = false;

        }
        
        // return false in case of an error
        if ( !correct ) {
            return false;           
        }
        
        if (specialSrcNumbering) {
        	
        	int bfjnLen = baseFileJustName.length();
        	
        	// if we don't have at least six numbers -> return
        	if ( bfjnLen < 6 ) {
        	    return false;
        	}
        	
        	// the base file number has to be 000000
        	if ( !baseFileJustName.endsWith( "000000" ) ) {
        		return false;
        	}
            
            this.baseFileNumber = "000000";
        	
            this.baseFilePrefix = baseFileJustName.substring( 0, bfjnLen - 6 );
            
            // Get montage's dimension from the last source file in the directory.
            File path = new File(baseFilePath);
            String [] files = path.list();
            
            int max = 0;
            String maxNum = "";
            int bfpLength = baseFilePrefix.length();
            
            for (int i = 0; i < files.length; i++) {
                
                int idx = files[i].indexOf(baseFilePrefix);
                if ( idx >= 0 &&
                     files[i].length() == bfpLength+6+baseFileSuffix.length() ) {
                                    
                    String numStr = files[i].substring(bfpLength, bfpLength+6);
                    int num = Integer.parseInt(numStr);
                    
                    if (num > max) {
                        max = num;
                        maxNum = numStr;
                    }                   
                    
                }
                
            }
            
            this.montageCols = 1 + Integer.parseInt( maxNum.substring(0,3) );
            this.montageRows = 1 + Integer.parseInt( maxNum.substring(3,6) );
            
        } else if ( sws ) {
            
            // Try to get montage's dimension from a predefined .sws -file.
            
            File baseDir = new File(baseFilePath);
            String parent = baseDir.getParent();
            File def = new File(parent + "/" + baseDir.getName() + ".sws");
            
            if (!def.exists()) {
                printMessage("Didn't find " + def.getName() + " -definition " +
                    "file, using user defined row and column values..");
            } else {
                
                try {
                
                    FileInputStream fis = new FileInputStream(def);
                    InputStreamReader isr = new InputStreamReader(fis);
                    BufferedReader br = new BufferedReader(isr);
                    
                    String line = "";
                    String x = "XFields=";
                    String y = "YFields=";
                    String method = "Method=";
                    
                    while( (line = br.readLine()) != null ) {
                        
                        int idx = -1;
                        String tmpStr;
                        
                        // columns
                        idx = line.indexOf( x );                        
                        if ( idx == 0 ) {                
                            tmpStr = line.substring( x.length(), line.length() );
                            this.montageCols = Integer.parseInt( tmpStr );
                            continue;
                        }
                        
                        // rows
                        idx = line.indexOf( y );
                        if ( idx == 0 ) {
                            tmpStr = line.substring( y.length(), line.length() );
                            this.montageRows = Integer.parseInt( tmpStr );
                            continue;
                        }

                        // snake- / comb-like row ordering
                        idx = line.indexOf( method );
                        if ( idx == 0 ) {
                            tmpStr = line.substring( method.length(), line.length() );
                            this.snakeRows = ( Integer.parseInt( tmpStr ) == 0 ) ? true : false;
                            continue;
                        }
                        
                        
                    } // while
                        
                    fis.close();
                        
                }
                catch (IOException e) {
                    IJ.error("Error processing .sws -file");
                    return false;
                }
                    
            }
            
        }        
        
        // Check if the output file is missing or is in fact a directory..
        if (finalOutput == null) {
 
            this.finalOutput = new File(baseFilePath + baseFilePrefix + ".ppm");
            this.finalFormat = "PNM";
        
        } else if (finalOutput.isDirectory()) {
            
            // Use PNM (PPM) format by default..
            
            try {
                
                String path = finalOutput.getCanonicalPath();
                path += "/";

                this.finalOutput =
                    new File(path + baseFilePrefix + ".ppm");
                
                this.finalFormat = "PNM";
                
            }
            catch (IOException e) {
                IJ.error("Error processing output directory");
                return false;
            }
            
        }                    
        
        this.sourceImageCount = montageRows * montageCols;        
        this.sourceMap = new MapImage[montageRows][montageCols];

        if (sourceImageCount < 2) {
            return false;
        }
        
        return true;
        
    }
    
    // ------------------------------------------------------------------------
    
    /**
    * Processing of the base image, which acts as a sample for the rest of the
    * source images. For example the image width and height is obtained from
    * the base image (it has to be the same for every other source image).
    * Also the suitable tile width and height is assigned here.
    *
    * @throws IOException if an error with file handling occurs. 
    */
    private void processBaseImage() throws IOException {
        
        IJ.showStatus("Preprocessing base-image..");
        printMessage("Preprocessing base-image " + baseFile.getName() + "..");

        FileImageInputStream fiis = new FileImageInputStream(baseFile);
        
        ParameterBlockJAI readParams =
            new ParameterBlockJAI("ImageRead", "rendered");
        readParams.setParameter("Input", fiis);
                
        RenderedOp op = JAI.create("ImageRead", readParams);
        
        this.width = op.getWidth();
        this.height = op.getHeight();
        this.bpp = op.getColorModel().getPixelSize();
        
        fiis.close();
        
        // If output file format is JPEG2000, we use tiling..
        if ( finalFormat.equals("JPEG2000") ) { 
        
            if (width >= 512) {
                this.tileWidth = 512;
            }
            else if (width >= 256 && width < 512) {
                this.tileWidth = 256;
            }
            else if (width >= 128 && width < 256) {
                this.tileWidth = 128;
            }
            else {
                this.tileWidth = 64;            
            }
            
            if (height >= 512) {
                this.tileHeight = 512;
            }
            else if (height >= 256 && height < 512) {
                this.tileHeight = 256;
            }
            else if (height >= 128 && height < 256) {
                this.tileHeight = 128;
            }
            else {
                this.tileHeight = 64;   
            }
            
        }
        else {
            
            // Otherwise, no tiling is used.
            this.tileWidth = 0;
            this.tileHeight = 0;
            
        }
        
        String baseTIFF = "tiff/" + baseFilePrefix + baseFileNumber + ".tif";
        
        sourceMap[0][0] = new MapImage(new File(baseTIFF), null, 0, 0);
        
        preprocessTIFF(baseFileNumber, 0, 0, 0, 0, 0, false);
        
    }
    
    // ------------------------------------------------------------------------
    
    /**
    * Preprocessing of each source image file. This process includes the
    * transformation to intermediate TIFF-format. Additionally, if user has
    * selected, we perform unsharp masking and/or convolve blur -operations.
    * If current source image is not yet accessible, the current thread waits
    * for it.
    *
    * @param number Source image's number.
    * @param row Current row.
    * @param col Current column.
    * @param adjOverlapY Current row's (possibly) adjusted Y overlap.
    * @param shiftX Current row's X axis shifting (px).
    * @param shiftY Current col's Y shift.
    * @param lastImage <code>true</code> if we are processing the last
    *                  image of the montage.
    *
    * @throws IOException if an error with file handling occurs.  
    */
    private void preprocessTIFF(String number, int row, int col,
                                int adjOverlapY, int shiftX,
                                int shiftY, boolean lastImage)
                                throws IOException {
        
        adjOverlapY = Math.abs(adjOverlapY);
        
        String input = baseFilePath + baseFilePrefix + number + baseFileSuffix;
        String output = "tiff/" + baseFilePrefix + number + ".tif";
        
        File inputFile = new File(input);
        File outputFile = new File(output);

        waitUntilReady(inputFile);
        
        FileImageInputStream fiis = new FileImageInputStream(inputFile);        
        
        ParameterBlockJAI readParams =
            new ParameterBlockJAI("ImageRead", "rendered");
        readParams.setParameter("Input", fiis);
        RenderedOp op = JAI.create("ImageRead", readParams);
        
        // Perform unsharp masking, if selected.
        if (sharpen > 0) {
            BufferedImage image = op.getAsBufferedImage();
            // Extend the image by adding 1px "paddings" to every edge.
            Raster img = image.getData();
            WritableRaster extended =
                img.createCompatibleWritableRaster(width+2, height+2);
            extended.setRect(1,1,img);
            
            // Set the paddings' pixel data to the same as their neighbours.
            for (int i = 0; i < (width+2); i++) {
                int [] px1 = extended.getPixel(i, 1, (int[]) null);
                int [] px2 = extended.getPixel(i, height, (int[]) null);
                extended.setPixel(i, 0, px1);               
                extended.setPixel(i, height+1, px2);
            }
            for (int i = 0; i < (height+2); i++) {
                int [] px1 = extended.getPixel(1, i, (int[]) null);
                int [] px2 = extended.getPixel(width, i, (int[]) null);
                extended.setPixel(0, i, px1);
                extended.setPixel(width+1, i, px2);
            }
            
            image = new BufferedImage(image.getColorModel(), extended, false, null);
            
            // Unsharp masking (in which the extra padding is needed, because of
            // the used 3x3 kernel).
            ParameterBlockJAI sharpParams =
                new ParameterBlockJAI("UnsharpMask", "rendered");

            sharpParams.addSource(image);
            //sharpParams.setParameter("kernel", ); // Default matrix: 3x3 average
            sharpParams.setParameter("gain", (float) (sharpen) / 10.0F );

            RenderedOp unsharpOp = JAI.create("UnsharpMask", sharpParams);            
            
            // Crop "off" the added padding.
            ParameterBlockJAI cropParams =
                new ParameterBlockJAI("Crop", "rendered");
            
            cropParams.addSource(unsharpOp);
            cropParams.setParameter("x", 1.0F); 
            cropParams.setParameter("y", 1.0F);
            cropParams.setParameter("width", (float) width );
            cropParams.setParameter("height", (float) height );
            
            op = JAI.create("Crop", cropParams);
        
        }
        
        // Perform convolving (with a "blur"-kernel), if selected.
        if (smoothen > 0) {
            
            BufferedImage image = op.getAsBufferedImage();
            
            // If we are processing the base image, no convolving is done.
            if ( number.equals(baseFileNumber) ) {
                
                this.prevConvSource = sourceMap[0][0];
                this.convTarget = image;
                
            }
            else {
                
                convSource = convTarget;
                convTarget = image;
                
                if ( col > 0 ) {
                    
                    // --- Vertical seam smoothing ------------------------
                        
                    float [] matrix = { 0.00F, 0.00F, 0.00F,
                                        0.30F, 0.40F, 0.30F,
                                        0.00F, 0.00F, 0.00F };                      
                                        
                    KernelJAI kernel = new KernelJAI(3, 3, matrix);                                          
                    
                    Raster sourceEdge = null;
                    Raster targetEdge = null;
                        
                    if (shiftY < 0) {
                        
                        sourceEdge =
                            convSource.getData(new Rectangle( width - 6, 0, 6, height + shiftY ));
                        targetEdge =
                            convTarget.getData(new Rectangle( overlapX, -shiftY, 6, height + shiftY ));
                        
                    }
                    else {
                        
                        sourceEdge =
                            convSource.getData(new Rectangle( width - 6, shiftY, 6, height - shiftY ));
                        targetEdge =
                            convTarget.getData(new Rectangle( overlapX, 0, 6, height - shiftY ));                          
                        
                    }
                    
                    WritableRaster ser =
                        Raster.createWritableRaster(sourceEdge.getSampleModel(),
                                                    sourceEdge.getDataBuffer(), new Point(0,0) );
                    WritableRaster ter =
                        Raster.createWritableRaster(targetEdge.getSampleModel(),
                                                    targetEdge.getDataBuffer(), new Point(0,0) );

                    WritableRaster joined =
                        sourceEdge.createCompatibleWritableRaster(12, height - Math.abs(shiftY));
    
                    joined.setRect(0,0,ser);
                    joined.setRect(6,0,ter);
                    
                    BufferedImage convImg =
                        new BufferedImage(convSource.getColorModel(), joined, false, null);
                    
                    ParameterBlockJAI b = new ParameterBlockJAI("Border", "rendered");
                    b.addSource(convImg);
                    b.setParameter("topPad", 1 + (smoothen-1));
                    b.setParameter("bottomPad", 1 + (smoothen-1));
                    b.setParameter("type", BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                    RenderedOp borderOp = JAI.create("Border", b);
                    
                    ParameterBlockJAI c = new ParameterBlockJAI("Convolve", "rendered");
                    c.addSource(borderOp);
                    c.setParameter("kernel", kernel);
                    RenderedOp convOp = JAI.create("Convolve", c);
                    
                    for (int i = 0; i < (smoothen-1); i++) {
                    
                        ParameterBlockJAI c2 = new ParameterBlockJAI("Convolve", "rendered");
                        c2.addSource(convOp);
                        c2.setParameter("kernel", kernel);
                        convOp = JAI.create("Convolve", c2);
                    
                    }
                                    
                    Raster sr = convOp.getData(new Rectangle( 4, 0, 2, height - Math.abs(shiftY) ));
                    Raster tr = convOp.getData(new Rectangle( 6, 0, 2, height - Math.abs(shiftY) ));
                    
                    Raster srTrans = null;
                    Raster trTrans = null;
                        
                    if (shiftY < 0) {
                        
                        srTrans = sr.createTranslatedChild( width - 2, 0 );
                        trTrans = tr.createTranslatedChild( overlapX, -shiftY );
                        
                    }
                    else {
                        
                        srTrans = sr.createTranslatedChild( width - 2, shiftY );
                        trTrans = tr.createTranslatedChild( overlapX, 0 );       
                                           
                    }                           
                    
                    convSource.setData(srTrans);
                    convTarget.setData(trTrans);
                    
                }
                
                if ( row > 0 ) {
                
                    // --- Horizontal seam smoothing --------------------------------
                        
                	float [] matrix = { 0.00F, 0.30F, 0.00F,
                                        0.00F, 0.40F, 0.00F,
                                        0.00F, 0.30F, 0.00F };

                    KernelJAI kernel = new KernelJAI(3, 3, matrix);
                    
                    BufferedImage prevRowSource = null;
                    
                    if (prevConvSource == sourceMap[row-1][col]) {
                        
                        prevRowSource = convSource;                                             
                        
                    }
                    else {
                        
                        FileImageInputStream fiis2 =
                            new FileImageInputStream(sourceMap[row-1][col].getFile());
                        
                        readParams = new ParameterBlockJAI("ImageRead", "rendered");
                        readParams.setParameter("Input", fiis2);
                
                        RenderedOp readOp = JAI.create("ImageRead", readParams);
                    
                        prevRowSource = readOp.getAsBufferedImage();
                        
                        fiis2.close();
                        
                    }
                    
                    Raster sourceEdge = null;
                    Raster targetEdge = null;

                    if (shiftX < 0) {
                        
                        sourceEdge = prevRowSource.getData(
                            new Rectangle( 0, height - 6, width + shiftX, 6 ));
                        targetEdge = convTarget.getData(
                            new Rectangle( -shiftX, adjOverlapY, width + shiftX, 6 ));                    
                        
                    }
                    else {
                        
                        sourceEdge = prevRowSource.getData(
                            new Rectangle( shiftX, height - 6, width - shiftX, 6 ));
                        targetEdge = convTarget.getData(
                            new Rectangle( 0, adjOverlapY, width - shiftX, 6 ));
                        
                    }
                    
                    WritableRaster ser =
                        Raster.createWritableRaster(sourceEdge.getSampleModel(),
                                                    sourceEdge.getDataBuffer(), new Point(0,0) );
                    WritableRaster ter =
                        Raster.createWritableRaster(targetEdge.getSampleModel(),
                                                    targetEdge.getDataBuffer(), new Point(0,0) );
                    
                    WritableRaster joined =
                        sourceEdge.createCompatibleWritableRaster(width - Math.abs(shiftX), 12);
  
                    joined.setRect(0,0,ser);
                    joined.setRect(0,6,ter);

                    BufferedImage convImg =
                        new BufferedImage(convTarget.getColorModel(), joined, false, null);
                    
                    ParameterBlockJAI b = new ParameterBlockJAI("Border", "rendered");
                    b.addSource(convImg);
                    b.setParameter("leftPad", 1 + (smoothen-1));
                    b.setParameter("rightPad", 1 + (smoothen-1));
                    b.setParameter("type", BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                    RenderedOp borderOp = JAI.create("Border", b);
                    
                    ParameterBlockJAI c = new ParameterBlockJAI("Convolve", "rendered");
                    c.addSource(borderOp);
                    c.setParameter("kernel", kernel);
                    RenderedOp convOp = JAI.create("Convolve", c);
                    
                    for (int i = 0; i < (smoothen-1); i++) {
                    
                        ParameterBlockJAI c2 = new ParameterBlockJAI("Convolve", "rendered");
                        c2.addSource(convOp);
                        c2.setParameter("kernel", kernel);
                        convOp = JAI.create("Convolve", c2);
                    
                    }

                    Raster sr = convOp.getData(new Rectangle( 0, 4, width - Math.abs(shiftX), 2 ));
                    Raster tr = convOp.getData(new Rectangle( 0, 6, width - Math.abs(shiftX), 2 ));

                    Raster srTrans = null;
                    Raster trTrans = null;

                    if (shiftX < 0) {
                        srTrans = sr.createTranslatedChild( 0, height - 2);
                        trTrans = tr.createTranslatedChild( -shiftX, adjOverlapY );
                    }
                    else {
                        srTrans = sr.createTranslatedChild( shiftX, height - 2);
                        trTrans = tr.createTranslatedChild( 0, adjOverlapY );
                    }
                      
                    prevRowSource.setData(srTrans);
                    convTarget.setData(trTrans);
                    
                    if (prevConvSource != sourceMap[row-1][col]) {
                        writeToTIFF(prevRowSource, sourceMap[row-1][col].getFile());
                    }
                    
                }
                
                writeToTIFF(convSource, prevConvSource.getFile());
                
                prevConvSource = sourceMap[row][col];
                
                if (lastImage) {
                    printMessage("Final image of the montage..");
                    writeToTIFF(convTarget, sourceMap[row][col].getFile());
                }
                
            }
            
        }
        else {
            
            BufferedImage image = op.getAsBufferedImage();
            writeToTIFF(image, outputFile);   
            
        }
        
        fiis.close();
        
    }
    
    // ------------------------------------------------------------------------
        
    /**
    * Sets the current thread to sleep if specified <code>File</code> is not
    * accessible.
    *
    * Testing the image's accessibility is done in two ways: first one is targeted
    * to Windows-environments and it tries to open a simple input stream of the
    * file and if an exception occurs, we know the file isn't accessible yet. The
    * other way is targeted to Unix-like environments, with more tolerable file
    * system, and it measures the file size difference between a small amount of
    * time (e.g. 10ms). If the file's size is same before and after a small pause,
    * we can assume that no writing is done to the file at the moment.
    *
    * @param file A <code>File</code> that is being waited.
    */ 
    private void waitUntilReady(File file) {
        
        boolean ready = false;
        boolean showedMsg = false;
        
        while (!ready) {
            
            if (file.canRead()) {
                
                try {
                    
                    FileInputStream fis = new FileInputStream(file);

                    long firstMark = file.length();
                    Thread.sleep(10);
                    long secondMark = file.length();
                    
                    ready = (firstMark == secondMark) ? true : false;
                    
                    fis.close();
                    
                }
                catch (FileNotFoundException e) {
                    ready = false;
                }
                catch (IOException e) {
                    ready = false;
                }
                catch (InterruptedException e) { }
                
            }

            if (!ready) {
                
                IJ.showStatus("Waiting for " + file.getName() + "..");
                
                if (!showedMsg) {
                    printMessage("Waiting for " + file.getName() + "..");
                    showedMsg = true;
                }
                
                try { Thread.sleep(1000); }
                catch (InterruptedException e) {}
                
            }
            
        }
        
    }

    // ------------------------------------------------------------------------ 
    
    /**
    * Removes temporary TIFF-files from "tiff" directory.
    *
    * @return <code>true</code> if removing is successful.
    *         <code>false</code> otherwise.
    */
    private boolean removeTempRegFiles() {
        
        if (regSource.exists()) {
            
            if (!regSource.delete()) {
                printMessage("Error in deleting temporary " + regSource.getName() +
                    "-file. \nYou have to delete it manually.");
                return false;
            }
            
        }
        
        if (regTarget.exists()) {

            if (!regTarget.delete()) {
                printMessage("Error in deleting temporary " + regTarget.getName() +
                    "-file. \nYou have to delete it manually.");
                return false;
            }

        }
        
        return true;
        
    }
    
    // ------------------------------------------------------------------------
    
    /**
    * A help-method, which returns current timestamp.
    *
    * @return The current time as UTC milliseconds from the epoch.
    */
    private long getTimestamp() {
        
        return (Calendar.getInstance().getTimeInMillis());
        
    }
    
    // ------------------------------------------------------------------------
    
    /**
    * A help-method, which prints out the user defined (and other) options.
    */
    private void printOptions() {

        printMessage("--- DETAILS -------------------------------------------------------------------");
    	printMessage("Base file: " + baseFile.toString());
        printMessage("Output file: " + finalOutput.toString());
        printMessage("Rows: " + montageRows);
        printMessage("Columns: " + montageCols);
        printMessage("Source image count: " + sourceImageCount);
        printMessage("Image width: " + width + " px");
        printMessage("Image height: " + height + " px");
        printMessage("Bpp: " + bpp);
        if ( finalFormat.equals( "JPEG2000" ) ) {
            printMessage("JP2 tile width: " + tileWidth + " px");
            printMessage("JP2 tile height: " + tileHeight + " px");
            printMessage("Compress level: " + compress);        
        }
        printMessage("X overlap: " + overlapX + " px" );
        printMessage("Y overlap: " + overlapY + " px" );
        printMessage("Fixed X shift: " + shiftX + " px" );
        printMessage("Fixed Y shift: " + shiftY + " px" );        
        printMessage("Registration: " + ( registration ? "yes" : "no" ) );
        if ( registration ) {
        	if ( regOptions.indexOf( Constants.Y_OVERLAP_TAG ) != -1 ) {
        		printMessage( "  * calculate Y overlap" );
        	}
        	if ( regOptions.indexOf( Constants.X_SHIFT_TAG ) != -1 ) {
        		printMessage( "  * calculate X shift" );
        	}
        	if ( regOptions.indexOf( Constants.Y_SHIFT_TAG ) != -1 ) {
        		printMessage( "  * calculate Y shift" );
        	}
        }
        printMessage("Sharpen level: " + sharpen);
        printMessage("Smoothen level: " + smoothen);
        printMessage("Snake-like rows: " + ( snakeRows ? "yes" : "no" ) );
        printMessage(
        		"Special src numbering: " +
        		( specialSrcNumbering ? "yes" : "no" )
        );
        printMessage("-------------------------------------------------------------------------------");
        
    }
    
    // ------------------------------------------------------------------------ 

    /**
    * Print the command line syntax
    *
    **/
    private void printCmdLineSyntax() {
    	
        printMessage("  -base <file>    First source image file");
        printMessage("  -output <file>  Output file (PBM/PGM/PPM, TIFF or JP2)");
        printMessage("  -rows <n>       Rows");
        printMessage("  -cols <n>       Columns");   
        printMessage("  -ox <n>         X overlap (horizontal), px");
        printMessage("  -oy <n>         Y overlap (vertical), px");                                   
        printMessage("  -sx <n>         Fixed X shift, px");
        printMessage("  -sy <n>         Fixed Y shift, px");
        printMessage("  -reg <"+Constants.Y_OVERLAP_TAG+"+"+Constants.X_SHIFT_TAG+"+"+Constants.Y_SHIFT_TAG+">    Use registration, can be a combination of:");
        printMessage("                    ("+Constants.Y_OVERLAP_TAG+" = Y overlap)");
        printMessage("                    ("+Constants.X_SHIFT_TAG+" = X shift)");
        printMessage("                    ("+Constants.Y_SHIFT_TAG+" = Y shift)");
        printMessage("  -sharpen <n>    Use unsharp masking (1-10)");
        printMessage("  -smoothen <n>   Smoothen montage's seams (1-3)");
        printMessage("  -compress <n>   Compression level (1-10), only with JP2");
        printMessage("  -snake          Use snake-like row ordering");
        printMessage("  -special        Use special source image numbering");
        printMessage("  -sws            Use a predefined SWS file");
        printMessage("  -verbose        Use verbose output mode");            
        printMessage("  -help           Prints out this message");
    	
    }
    
    // ------------------------------------------------------------------------ 

    /**
    * A help-method, which prints out text in suitable way. E.g. to ImageJ's
    * log-window or to standard output stream.
    *
    * @param msg The text to be shown.
    */
    private void printMessage(String msg) {
        
        if (verbose) {
            
            if (startedFromIJ) {
                IJ.log(msg);                
            }
            else {
                System.out.println(msg);                
            }           
            
        }
                
    }
        
    // ------------------------------------------------------------------------ 
    
    /**
    * Calculates two images overlap by using the ImageJ's TurboReg plugin.
    * 
    * Registration process goes as follows for horizontal (similar applies to
    * vertical, but with different crop areas):
    *   -crop a region from source image's bottom part with width at maximum and
    *    height at <code>overlapY</code> adjusted with predefined threshold,
    *    <code>overlapThres</code>. The same area is cropped from target image,
    *    but the area is on the top part of the image
    *   -define one landmark for each image, used in registration
    *   -perform registration calculation with TurboReg
    *   -if calculated overlap is "mildly" beyond the threshold, the calculation
    *    is done again, but with different landmark positions. The registration
    *    can be done maximum three times and if the overlap is still "mildly"
    *    beyond the threshold, we accept it as it is and return it
    *   -if calculated overlap is beyond the limit set by the threshold, we can
    *    assume that the registration has failed and we set the overlap to the
    *    fixed value and return it
    *
    * @param vertical Is vertical registration beings used?
    * @param passNum  Recursion round, which defines the new landmark positioning.
    * 
    * @return An <code>int</code> array, which holds the new overlap values.
    */
    private int [] calcRegistration(boolean vertical, int passNum) {
                                        
        int divider = 0;
        
        // Depending on the pass number / recursion round, we define new divider
        // that affects the positioning of the images' landmarks.
        switch (passNum) {
            
            case 1:  divider = 2; break;
            case 2:  divider = 3; break;
            case 3:  divider = 4; break;
            default: divider = 2; break;
                
        }
        
        int [] defaults = {0,0};
  
        int adjOverlapX = (int) (overlapX * (1.00F + overlapThres));
        int adjOverlapY = (int) (overlapY * (1.00F + overlapThres));
        
        String trOptions = "";
        
        if (!vertical) {
            
            // Horizontal edge registration
            
            /*
            *  |------------|
            *  |            |
            *  |   source   | 
            *  |            |
            *  |------------|
            *    ^ ^ ^ ^ ^  
            *    v v v v v  
            *  |------------|
            *  |            |
            *  |   target   | 
            *  |            |
            *  |------------|
            */          
            
            trOptions =
                "-align "
                + "-file " + regSource.getAbsolutePath() + " "
                + "0 " + (height - adjOverlapY - 1) + " " + (width - 1) + " " + (height - 1) + " "
                + "-file " + regTarget.getAbsolutePath() + " "
                + "0 0 " + (width - 1) + " " + (adjOverlapY - 1) + " "
                + "-translation "
                + (width/divider - 1) + " " + (height - (overlapY/2) - 1) + " "
                + (width/divider - 1) + " " + ( (overlapY/2) - 1) + " "
                + "-hideOutput";
            
        }
        else {
            
            // Vertical edge registration
            
            /*
            *  |------------|           |------------|
            *  |            | <--   --> |            |
            *  |   source   | <--   --> |   target   | 
            *  |            | <--   --> |            |
            *  |------------|           |------------|
            */
                        
            trOptions =
                "-align "
                + "-file " + regSource.getAbsolutePath() + " "
                + (width - adjOverlapX - 1) + " 0 " + (width - 1) + " " + (height - 1) + " "
                + "-file " + regTarget.getAbsolutePath() + " "
                + "0 0 " + (adjOverlapX - 1) + " " + (height - 1) + " "
                + "-translation "
                + (width - (overlapX/2) - 1) + " " + (height/divider - 1) + " "
                + ( (overlapX/2) - 1) + " " + (height/divider - 1) + " "
                + "-hideOutput";
            
        }
        
        Object turboReg = IJ.runPlugIn("TurboReg_", trOptions);
            
        if (turboReg == null) {
            
            IJ.error("Error opening TurboReg-plugin.\n" +
                "Using default overlap values..");
            
            registration = false;

            return defaults;
            
        }
        
        double sourceX0 = 0.0D;
        double sourceY0 = 0.0D;
        double targetX0 = 0.0D;
        double targetY0 = 0.0D;
        
        try {
        
            Method method = turboReg.getClass().getMethod("getSourcePoints", (Class[]) null);
            double[][] sourcePoints = (double[][]) method.invoke(turboReg, (Object[]) null);
        
            method = turboReg.getClass().getMethod("getTargetPoints", (Class[]) null);
            double[][] targetPoints = (double[][]) method.invoke(turboReg, (Object[]) null);

            sourceX0 = sourcePoints[0][0];
            sourceY0 = sourcePoints[0][1];
        
            targetX0 = targetPoints[0][0];
            targetY0 = targetPoints[0][1];
            
        }
        catch (NoSuchMethodException e) {
            IJ.error("Registration error:\n" + e.toString());
            return defaults;
        }
        catch (IllegalAccessException e) {
            IJ.error("Registration error:\n" + e.toString());
            return defaults;
        }
        catch (InvocationTargetException e) {
            IJ.error("Registration error:\n" + e.toString());
            return defaults;
        }

        // X & Y -translations (px)
        int dx = (int) Math.round(targetX0 - sourceX0); 
        int dy = (int) Math.round(targetY0 - sourceY0);
        
        int adjustX = 0;
        int adjustY = 0;
        
        // Proper corrections
        if (vertical) {
            adjustX = width - Math.abs(dx);
            adjustY = -dy;
        }
        else {
            adjustX = -dx;
            adjustY = height - Math.abs(dy);
        }

        // If adjust value goes "mildly" beyond the Y overlap, try
        // the registration again with (max.) three different landmarks.
        if (!vertical && passNum < 3) {

            if ( adjustY < (int) (overlapY * (1.00F - overlapThres/2)) ||
                 adjustY > (int) (overlapY * (1.00F + overlapThres/2)) ) {
                
                // Call this method recursively with a different pass number.
                return calcRegistration(vertical, passNum+1);
                
            }
            
        }
        
        // If adjustments are beyond the overlap threshold -limit, we can assume
        // that source and/or target images didn't contain adjustable information
        // or the registration process was incorret.
        if ( !vertical &&
             adjustY < (int) (overlapY * (1.00F - overlapThres)) ||
             adjustY > (int) (overlapY * (1.00F + overlapThres)) ) {

            printMessage("Registration value out of range.\n" +
                "Using default overlap..");

            adjustY = overlapY;
            
        }
        
        int [] adjustments = {adjustX, adjustY};
        
        return adjustments;
    
    }

    // ------------------------------------------------------------------------
    
    /**
    * Transforms a specific input file into a grayscale image and writes it to
    * a specific output JPG-file.
    *
    * @param inputFile A <code>File</code> to be transformed.
    * @param outputFile An output <code>File</code> (JPG).
    *
    * @throws IOException if an error with file handling occurs. 
    */
    private void transformToGrayJPG (File inputFile, File outputFile)
        throws IOException {

        FileImageInputStream fiis = new FileImageInputStream(inputFile);
        FileImageOutputStream fios = new FileImageOutputStream(outputFile);
    
        ParameterBlockJAI readParams = 
            new ParameterBlockJAI("ImageRead", "rendered");
        readParams.setParameter("Input", fiis);
        RenderedOp op = JAI.create("ImageRead", readParams);
        
        TiledImage image = new TiledImage(op, true);
        
        // Converto to grayscale if needed (i.e. RGB color model is used).
        if (bpp == 24) {
        
            // Used grayscale matrix.
            double [][] matrix = { {0.114D, 0.587D, 0.299D, 0.0D} };
            
            ParameterBlockJAI bcParams =
                new ParameterBlockJAI("BandCombine", "rendered");
            bcParams.addSource(image);
            bcParams.setParameter("matrix", matrix);
            
            op = JAI.create("BandCombine", bcParams);
        
        }

        JPEGImageWriteParam jiwp = new JPEGImageWriteParam(Locale.ENGLISH);
        jiwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jiwp.setCompressionQuality(1.00F);
        
        ParameterBlockJAI writeParams =
            new ParameterBlockJAI("ImageWrite", "rendered");
        
        if (bpp == 24) {
            writeParams.addSource(op);
        }
        else {
            writeParams.addSource(image);
        }
        
        writeParams.setParameter("Output", fios);
        writeParams.setParameter("Format", "JPG");
        writeParams.setParameter("WriteParam", jiwp);
        
        JAI.create("ImageWrite", writeParams);
        
        fiis.close();
        
        fios.flush();
        fios.close();

    }
    
    // ------------------------------------------------------------------------
        
    /**
    * Writes a <code>RenderedImage</code> to a specified <code>File</code>
    * in TIFF format. The TIFF-formatting of the source images is only a
    * temporary, intermediate phase. The TIFF file is always uncompressed
    * and untiled, except tiled if JPEG2000 output format is being used.
    *
    * @param image A source <code>RenderedImage</code>.
    * @param outputFile An output <code>File</code> (TIFF).
    *       
    * @throws IOException if an error with file handling occurs. 
    */    
    private void writeToTIFF (RenderedImage image, File outputFile)
        throws IOException {
        
        // Check if corresponding file already exists.
        if (outputFile.exists()) {
            
            // If yes, delete it.
            if (!outputFile.delete()) {
                throw new IOException("Error in deleting temporary TIFF file..");
            }
            
        }
        
        FileImageOutputStream fios = new FileImageOutputStream(outputFile);
        
        TIFFImageWriteParam tiwp = new TIFFImageWriteParam(Locale.ENGLISH); 
        tiwp.setCompressionMode(ImageWriteParam.MODE_DISABLED);

        if ( finalFormat.equals("JPEG2000") ) {         
            tiwp.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
            tiwp.setTiling(tileWidth,tileHeight,0,0);
        }
        else {
            tiwp.setTilingMode(ImageWriteParam.MODE_DISABLED);          
        }
        
        ParameterBlockJAI writeParams =
            new ParameterBlockJAI("ImageWrite", "rendered");
        writeParams.addSource(image);
        writeParams.setParameter("Output", fios);
        writeParams.setParameter("Format", "TIFF");
        writeParams.setParameter("WriteParam", tiwp);
        
        JAI.create("ImageWrite", writeParams);
        
        fios.close();
        
    }   

    // ------------------------------------------------------------------------

    /**
    * Writes a <code>RenderedOp</code> to the specified <code>finalOutput</code>
    * file (TIFF, JP2 or PBM/PGM/PPM). If output format is TIFF, then it will be
    * uncompressed, untiled and stripped. If it's JPEG2000, then it will be
    * tiled and compressed, if user has selected compression to be used. If it's
    * PNM-based (PBM/PGM/PPM), then it will be uncompressed, untiled and written
    * in binary.
    *
    * @param op A source <code>RenderedOp</code>.
    * @param sizePxWidth Montage's width in pixels.
    * @param sizePxHeight Montage's height in pixels.       
    *       
    * @throws IOException if an error with file handling occurs. 
    */    
    private void writeMontage(RenderedOp op, int sizePxWidth, int sizePxHeight)
        throws IOException {

        // Check if finalOutput-file already exists.
        if (finalOutput.exists()) {
            
            // If yes, delete it.
            if (!finalOutput.delete()) {
                throw new IOException("Error in deleting " + finalOutput.toString() + "..");
            }
            
        }
        
        long sizePx = (long) sizePxWidth * (long) sizePxHeight;

        printMessage("Output image size: " + sizePx + " px");

        FileOutputStream fos = new FileOutputStream(finalOutput);
        
        J2KImageWriteParam j2kiwp = null;
        TIFFImageWriteParam tiffiwp = null;
        PNMEncodeParam pnmep = null;
        
        if ( finalFormat.equals("JPEG2000") ) {
        
            j2kiwp = new J2KImageWriteParam();
            j2kiwp.setProgressionType("res-pos");
            j2kiwp.setLossless( true );
            j2kiwp.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
            j2kiwp.setTiling(tileWidth,tileHeight,0,0);
            
        } else if ( finalFormat.equals("PNM") ) {
            
            pnmep = new PNMEncodeParam();
            pnmep.setRaw(true);
            
        } else {
            
            tiffiwp = new TIFFImageWriteParam(Locale.ENGLISH); 
            tiffiwp.setCompressionMode(ImageWriteParam.MODE_DISABLED);
            tiffiwp.setTilingMode(ImageWriteParam.MODE_DISABLED);           
        
        }
        
        if ( finalFormat.equals("JPEG2000") && compress > 0 ) {
                    
            // Compress rate, in bits per pixel (bpp).
            double divider = compress * 4.0D;
            double compressRate = bpp / divider;            
     
            j2kiwp.setLossless(false);
            j2kiwp.setEncodingRate(compressRate);
            
        }

        printMessage("Writing to file " + finalOutput.getName() + "..");
        
        // If final format is PNM-based, we use different writing..
        if ( finalFormat.equals("PNM") ) {
            
            IJ.showProgress(1.00D);

            ImageEncoder encoder = ImageCodec.createImageEncoder("PNM", fos, pnmep);
            if (encoder == null) {
                throw new IOException("Cannot open a PNM-encoder..");
            }

            encoder.encode(op);
            
        } else {

            ParameterBlockJAI writeParams =
                new ParameterBlockJAI("ImageWrite", "rendered");
            writeParams.addSource(op);
            writeParams.setParameter("Output", fos);
            writeParams.setParameter("Format", finalFormat);
            
            if ( finalFormat.equals("JPEG2000") ) {
                writeParams.setParameter("WriteParam", j2kiwp);
            }
            else {
                writeParams.setParameter("WriteParam", tiffiwp);
            }

            // There's should be two writers present and the first one should
            // be a native version, which is used here.
            Iterator iter = ImageIO.getImageWritersByFormatName(finalFormat);
            ImageWriter iw = null;
            if (iter.hasNext()) {
                iw = (ImageWriter) iter.next();
            }
            else {
                throw new IOException("Cannot open a " + finalFormat + " writer..");
            }
            writeParams.setParameter("Writer", iw);

            EventListener [] listeners = new EventListener[1];
            ProgressListener pl = new ProgressListener(verbose);
            listeners[0] = pl;
            writeParams.setParameter("Listeners", listeners);

            JAI.create("ImageWrite", writeParams);
            
        }
        
        fos.flush();
        fos.close();
        
    }
    
}
