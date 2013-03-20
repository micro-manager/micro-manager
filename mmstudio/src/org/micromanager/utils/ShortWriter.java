/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Arthur
 */
public class ShortWriter {

    final RandomAccessFile stream_;
    final FileChannel channel_;
    final File file_;
    ShortBuffer shortBuffer_ = null;
    ByteBuffer byteBuffer_ = null;
    long writePosition_; 

    public ShortWriter(File file) throws IOException {
        file_ = file;
        stream_ = new RandomAccessFile(file_, "rw");
        channel_ = stream_.getChannel();
        writePosition_ = 0;
        newBuffer();
    }

    private void newBuffer() throws IOException {
        byteBuffer_ = channel_.map(FileChannel.MapMode.READ_WRITE, channel_.position(), Integer.MAX_VALUE);
        shortBuffer_ = byteBuffer_.asShortBuffer();
    }
    
    public void write(short[] shorts) throws IOException {
        if ((shortBuffer_.position() +   shorts.length) >= Integer.MAX_VALUE/2) {
            newBuffer();
        }
        shortBuffer_.put(shorts);
        writePosition_ += shorts.length;
        //System.out.println(shortBuffer_.position());
        //   for (short s : shorts) {
        //       buffer_.putShort(s);
        //   }
    }

    public void close() throws IOException {
          /*     
        for (int i=0; i<100 || succeeded; ++i) {
            try {
                System.gc();
                channel_.truncate(writePosition_);
                succeeded = true;
            } catch (Exception e) {
            System.out.println("failed.");
            }
        }
        * */
        unmap(byteBuffer_);
        channel_.truncate(2 * writePosition_);
               stream_.close();
        //truncate();
    }
    
    private void unmap(ByteBuffer buffer) {
          if (buffer instanceof sun.nio.ch.DirectBuffer) {
            sun.misc.Cleaner cleaner = ((sun.nio.ch.DirectBuffer) buffer).cleaner();
            cleaner.clean();
          }
    }
    
    private void truncate() throws IOException {
        final FileOutputStream fileOutputStream = new FileOutputStream(file_, true);
        final FileChannel channel = fileOutputStream.getChannel();
        channel.truncate(writePosition_);
        channel.close();
        fileOutputStream.close();
    }
}
