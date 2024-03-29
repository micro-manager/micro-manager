/*
 *
 * Karl Bellve
 * Biomedical Imaging Group
 * University of Massachusetts Medical School
 * Karl.Bellve@umassmed.edu
 * http://big.umassmed.edu/
 *
 */

package edu.umassmed.big;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class I2I {

   private String[] history = new String[15];
   private String i2IFileName;
   private short min;
   private short max;
   private short xOrg;
   private short yOrg;
   private Integer x_;
   private Integer y_;
   private Integer z_;
   private Integer t_ = 1;
   private Integer dataSize;
   private Integer imageSize;
   private int fileEndian;
   private byte[] buffer = new byte[64];
   private int bufferedZ = 0;
   private RandomAccessFile out = null;
   private RandomAccessFile in = null;
   short[] data;

   /**
    * Creates a blank I2I image with dimensions X,Y,Z.
    *
    * @param x
    * @param y
    * @param z
    */
   public I2I(int x, int y, int z) {
      try {
         // creates a header file for a new I2I image
         initializeI2I(x, y, z, 0);
         data = new short[dataSize];
      } catch (Exception e) {
         System.err.println("Failed to create blank I2I: " + e.getMessage());
      }
   }

   /**
    * Creates an I2I 16bit image from a 16 bit image with dimensions X,Y,Z.
    *
    * @param image
    * @param x
    * @param y
    * @param z
    */
   public I2I(short[] image, int x, int y, int z) {
      try {
         // creates a header file for a new I2I image
         initializeI2I(x, y, z, 0);
         data = new short[dataSize];
         System.arraycopy(image, 0, data, 0, image.length);
      } catch (Exception e) {
         System.err.println("Failed to create I2I: " + e.getMessage());
      }
   }

   /**
    * Creates an I2I 16bit image from an 8 bit image with dimensions X,Y,Z.
    *
    * @param image
    * @param x
    * @param y
    * @param z
    */
   public I2I(byte[] image, int x, int y, int z) {
      try {
         // creates a header file for a new I2I image
         initializeI2I(x, y, z, 0);
         data = new short[dataSize];

         System.arraycopy(image, 0, data, 0, image.length);
      } catch (Exception e) {
         System.err.println("Failed to create I2I: " + e.getMessage());
      }
   }

   /**
    * Opens a buffered image stream to save images as acquired. This will keep the file open, so you
    * must call close()
    *
    * @param file path to save to
    * @param x position
    * @param y position
    * @param z position
    */
   public I2I(String file, int x, int y, int z) {

      try {
         i2IFileName = new String(file);

         System.out.println("opening " + i2IFileName);
         out = new RandomAccessFile(i2IFileName, "rw");

         // creates a header file for a new I2I image
         bufferedZ = 1;
         System.out.println("initialize " + i2IFileName);
         initializeI2I(x, y, z, 0);
         data = new short[dataSize];

         System.out.println("write header " + i2IFileName);
         // write the header
         writeHeader(out);
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   /**
    * Opens an existing I2I file
    *
    * @param file
    */
   public I2I(String file) {
      try {
         //in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
         i2IFileName = new String(file);
         in = null;
         in = new RandomAccessFile(i2IFileName, "r");
         try {
            // Are we an I2I file?
            int c = in.readByte();
            if (c == 'I') {
               // READ HEADER
               in.read(buffer, 0, 6);
               String x = new String(buffer, 0, 6);
               x_ = Integer.parseInt(x.trim());

               in.read(buffer, 0, 6);
               String y = new String(buffer, 0, 6);
               y_ = Integer.parseInt(y.trim());

               in.read(buffer, 0, 6);
               String z = new String(buffer, 0, 6);
               z_ = Integer.parseInt(z.trim());

               //in.skip(1); // skip space
               in.skipBytes(1);
               fileEndian = in.readByte();
               if (fileEndian == 'L') { // must swap bytes if data is little
                  // endian
                  min = swap(in.readShort());
                  max = swap(in.readShort());
                  xOrg = swap(in.readShort());
                  yOrg = swap(in.readShort());
                  setT(swap(in.readShort()));

               } else {
                  // Java is big endian, most significant byte first
                  min = in.readShort();
                  max = in.readShort();
                  xOrg = in.readShort();
                  yOrg = in.readShort();
                  setT(in.readShort());
               }

               // Load History file
               //in.skip(33);
               in.skipBytes(33);
               for (int i = 0; i < 15; i++) {
                  in.read(buffer, 0, 64);
                  history[i] = new String(buffer);
               }

               // READ DATA
               dataSize = getX() * getY() * getZ();
               data = new short[dataSize];

               System.out.println(i2IFileName + " Data Size: " + dataSize);
               for (int i = 0; i < dataSize; i++) {
                  if (fileEndian == 'B') {
                     data[i] = in.readShort();
                  } else {
                     data[i] = swap(in.readShort());
                  }
               }
            }
         } finally {
            in.close();
         }
      } catch (FileNotFoundException e) {
         e.printStackTrace();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   /**
    * Closes any open file streams
    */
   public void close() {
      try {
         if (out != null) {
            out.close();
         }
         out = null;
      } catch (IOException e) {
         e.printStackTrace();
      }

      try {
         if (in != null) {
            in.close();
         }
         in = null;
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   /**
    * Initializer method that sets up common parameters
    *
    * @param x
    * @param y
    * @param z
    * @param t
    */
   private void initializeI2I(int x, int y, int z, int t) {

      setX(x);
      setY(y);
      setZ(z);
      setT(t);
      min = 0;
      max = 0;
      xOrg = 0;
      yOrg = 0;
      // Are we buffering?
      if (bufferedZ > 0) {
         dataSize = getX() * getY() * bufferedZ;
      } else {
         dataSize = getX() * getY() * getZ();
      }

      imageSize = getX() * getY();
      Utils u = new Utils();
      history[0] = ("* Created on " + u.generateDate() + " by " + System.getenv("HOSTNAME"));
   }

   /**
    * Creates a new I2I image file
    *
    * @param file
    */
   public void saveImage(String file) {
      try {
         i2IFileName = new String(file);
         out = null;
         out = new RandomAccessFile(i2IFileName, "w");

         try {
            // write header
            System.out.println("Writing Header");
            writeHeader(out);
            System.out.println("Writing Image Data");
            // write data
            for (int x = 0; x < dataSize; x++) {
               out.writeShort(data[x]);
            }

         } finally {
            if (out != null) {
               out.close();
            }
         }
      } catch (FileNotFoundException e) {
         e.printStackTrace();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public short getX() {
      return (x_.shortValue());
   }

   public short getY() {
      return (y_.shortValue());
   }

   public short getZ() {
      return (z_.shortValue());
   }

   public short getT() {
      if (t_ < 1) {
         return (1);
      } else {
         return (t_.shortValue());
      }
   }

   public void setX(int x) {
      if (x < 0) {
         x = 1;
      }
      this.x_ = x;
      return;
   }

   public void setY(int y) {
      if (y < 0) {
         y = 1;
      }
      this.y_ = y;
      return;
   }

   /**
    * Sets the number of Z planes, or number of images if T = 0 or 1.
    *
    * @param z number of Z planes
    */
   public void setZ(int z) {
      if (z < 0) {
         z = 1;
      }
      this.z_ = z;
      return;
   }

   /**
    * Sets the number of time points. Divide Z/T to get the number of Z per time point
    *
    * @param t number of Time Points.
    */
   public void setT(int t) {
      if (t < 1) {
         t = 1;
      }
      if (t == 8224) {
         t = 1;
      }
      this.t_ = t;
      return;
   }

   /**
    * Returns entire data, but it might be buffered and just a single image.
    *
    * @return
    */
   public short[] getImage() {
      return (data); // pointer to all the data
   }

   /**
    * Returns a specific 2D image out of a 3D image. It creates a second array to store and return
    * the 2D image
    *
    * @param z
    * @return
    */
   public short[] getImage2D(int z) {
      if (bufferedZ > 1) {
         // perhaps retrieve that image from the file, but not implemented
         return Utils.getarray(data, 0, imageSize);
      } else {
         return (Utils.getarray(data, z * imageSize, imageSize));
      }

   }

   // return 1 3D plane
   public short[] getImage3D(int z) {
      return (data); // only one 3D time point, just return the entire array
   }


   /**
    * micromanager uses unsigned shorts, but we store as signed shorts.
    *
    * @param image
    * @param z     location to add 3D image
    */
   public void addUImage(short[] image, int z) {
      try {
         // expand array to add, unless it is buffered
         int value = 0;
         int foo;
         if (bufferedZ > 0) {
            foo = 0;
         } else {
            foo = x_ * y_ * z;
         }

         for (int x = 0; x < x_ * y_; x++) {
            value = (int) (char) image[x];
            data[foo + x] = (short) (value >> 1);  // divide by 2 to avoid out of bounds
         }
         // we need to purge image if we are buffering
         if (bufferedZ > 0) {
            writeImage(out, data, z);
         }

      } catch (Exception e) {
         System.err.println("Exception: " + e.getMessage());
      }
      return;
   }

   public void addImage(int[] image, int z) {
      // expand array to add
      try {
         int foo;
         if (bufferedZ > 0) {
            foo = 0;
         } else {
            foo = x_ * y_ * z;
         }

         for (int x = 0; x < x_ * y_; x++) {
            data[foo + x] = (short) (image[x]);
         }
         // we need to purge image if we are buffering
         if (bufferedZ > 0) {
            writeImage(out, data, z);
         }
      } catch (Exception e) {
         System.err.println("Exception: " + e.getMessage());
      }
      return;
   }

   public void addImage(short[] image, int z) {
      try {
         // expand array to add, unless it is buffered
         int foo;
         if (bufferedZ > 0) {
            foo = 0;
         } else {
            foo = x_ * y_ * z;
         }

         for (int x = 0; x < x_ * y_; x++) {
            data[foo + x] = image[x];
         }
         // we need to purge image if we are buffering
         if (bufferedZ > 0) {
            writeImage(out, data, z);
         }
      } catch (Exception e) {
         System.err.println("Exception: " + e.getMessage());

      }
      return;
   }

   public void addImage(byte[] image, int z) {
      try {
         // expand array to add, unless it is buffered
         int foo;
         if (bufferedZ > 0) {
            foo = 0;
         } else {
            foo = x_ * y_ * z;
         }

         for (int x = 0; x < x_ * y_; x++) {
            data[foo + x] = (short) image[x];
         }
         // we need to purge image if we are buffering
         if (bufferedZ > 0) {
            writeImage(out, data, z);
         }
      } catch (Exception e) {
         System.err.println("Exception: " + e.getMessage());
      }

      return;
   }

   public static short swap(short value) {
      int b1 = value & 0xff;
      int b2 = (value >> 8) & 0xff;

      return (short) (b1 << 8 | b2 << 0);
   }

   public short getMin() {
      return min;
   }

   public void setMin(short min) {
      if (min == 8224) {
         min = 0;
      }
      this.min = min;
   }

   public short getMax() {
      return max;
   }

   public void setMax(short max) {
      if (max == 8224) {
         max = 0;
      }
      this.max = max;
   }

   public short getxOrg() {
      return xOrg;
   }

   public void setxOrg(short xOrg) {
      if (xOrg == 8224) {
         xOrg = 0;
      }
      this.xOrg = xOrg;
   }

   public short getyOrg() {
      return yOrg;
   }

   public void setyOrg(short yOrg) {
      if (yOrg == 8224) {
         yOrg = 0;
      }
      this.yOrg = yOrg;
   }

   /**
    * Writes the first 1024 bytes to a file.
    *
    * @param out Already existing output file stream
    */
   private void writeHeader(RandomAccessFile out) {

      try {
         if (out != null) {
            out.seek(0);
            String header = String.format("I %5d %5d %5d B", getX(), getY(), getZ());
            out.writeBytes(header);
            out.writeShort(getMin());
            out.writeShort(getMax());
            out.writeShort(getxOrg());
            out.writeShort(getyOrg());
            out.writeShort(getT());

            for (int x = 0; x < 33; x++) {
               out.writeByte(32);
            }
            // write history
            System.out.println("Writing History");
            for (int x = 0; x < 15; x++) {
               if (history[x] != null) {
                  out.writeBytes(history[x]);
                  if (history[x].length() < 64) {
                     for (int y = history[x].length(); y < 64; y++) {
                        out.writeByte(32); // fill with spaces
                     }
                  }
               } else {
                  for (int y = 0; y < 64; y++) {
                     out.writeByte(32); // fill with spaces
                  }
               }
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   private void writeImage(RandomAccessFile out, short[] image, int z) {
      try {
         if (out != null) {
            out.seek((imageSize.intValue() * z * 2) + 1024);
            for (int x = 0; x < imageSize; x++) {
               out.writeShort(image[x]);
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

}
