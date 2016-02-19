
package edu.valelab.gaussianfit;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Code downloaded from http://www.peterfranza.com/2008/09/26/little-endian-input-stream/
 * 
 * @author pfranza
 */
public class LittleEndianDataInputStream extends InputStream implements DataInput {
 
	public LittleEndianDataInputStream(InputStream in) {
		this.in = in;
		this.d = new DataInputStream(in);
		w = new byte[8];
	}
 
   @Override
	public int available() throws IOException {
		return d.available();
	}
 
 
   @Override
	public final short readShort() throws IOException
	{
		d.readFully(w, 0, 2);
		return (short)(
				(w[1]&0xff) << 8 |
				(w[0]&0xff));
	}
 
	/**
	 * Note, returns int even though it reads a short.
	 */
   @Override
	 public final int readUnsignedShort() throws IOException
	 {
		 d.readFully(w, 0, 2);
		 return (
				 (w[1]&0xff) << 8 |
				 (w[0]&0xff));
	 }
 
	 /**
	  * like DataInputStream.readChar except little endian.
	  */
   @Override
	 public final char readChar() throws IOException
	 {
		 d.readFully(w, 0, 2);
		 return (char) (
				 (w[1]&0xff) << 8 |
				 (w[0]&0xff));
	 }
 
	 /**
	  * like DataInputStream.readInt except little endian.
	  */
   @Override
	 public final int readInt() throws IOException
	 {
		 d.readFully(w, 0, 4);
		 return
		 (w[3])      << 24 |
		 (w[2]&0xff) << 16 |
		 (w[1]&0xff) <<  8 |
		 (w[0]&0xff);
	 }
 
	 /**
	  * like DataInputStream.readLong except little endian.
	  */
   @Override
	 public final long readLong() throws IOException
	 {
		 d.readFully(w, 0, 8);
		 return
		 (long)(w[7])      << 56 | 
		 (long)(w[6]&0xff) << 48 |
		 (long)(w[5]&0xff) << 40 |
		 (long)(w[4]&0xff) << 32 |
		 (long)(w[3]&0xff) << 24 |
		 (long)(w[2]&0xff) << 16 |
		 (long)(w[1]&0xff) <<  8 |
		 (long)(w[0]&0xff);
	 }
 
   @Override
	 public final float readFloat() throws IOException {
		 return Float.intBitsToFloat(readInt());
	 }
 
   @Override
	 public final double readDouble() throws IOException {
		 return Double.longBitsToDouble(readLong());
	 }
 
   @Override
	 public final int read(byte b[], int off, int len) throws IOException {
		 return in.read(b, off, len);
	 }
 
   @Override
	 public final void readFully(byte b[]) throws IOException {
		 d.readFully(b, 0, b.length);
	 }
 
   @Override
	 public final void readFully(byte b[], int off, int len) throws IOException {
		 d.readFully(b, off, len);
	 }
 
   @Override
	 public final int skipBytes(int n) throws IOException {
		 return d.skipBytes(n);
	 }
 
   @Override
	 public final boolean readBoolean() throws IOException {
		 return d.readBoolean();
	 }
 
   @Override
	 public final byte readByte() throws IOException {
		 return d.readByte();
	 }
 
   @Override
	 public int read() throws IOException {
		 return in.read();
	 }
 
   @Override
	 public final int readUnsignedByte() throws IOException {
		 return d.readUnsignedByte();
	 }
 
	 @Deprecated
   @Override
	 public final String readLine() throws IOException {
		 return d.readLine();
	 }
 
   @Override
	 public final String readUTF() throws IOException {
		 return d.readUTF();
	 }
 
   @Override
	 public final void close() throws IOException {
		 d.close();
	 }
 
	 private final DataInputStream d; // to get at high level readFully methods of
	 // DataInputStream
	 private final InputStream in; // to get at the low-level read methods of
	 // InputStream
	 private final byte w[]; // work array for buffering input
}
