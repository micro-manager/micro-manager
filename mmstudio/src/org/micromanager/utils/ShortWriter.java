/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 *
 * @author Arthur
 */
public class ShortWriter {

    final RandomAccessFile stream_;
    final FileChannel channel_;
    ByteBuffer buffer_;

    public ShortWriter(File file) throws FileNotFoundException, IOException {
        stream_ = new RandomAccessFile(file, "rw");
        channel_ = stream_.getChannel();
        buffer_ = createBuffer();
    }

    private ByteBuffer createBuffer() throws IOException {
        return channel_.map(FileChannel.MapMode.READ_WRITE, channel_.position(), Integer.MAX_VALUE);
    }
    
    public void write(short[] shorts) throws IOException {
        int byteCount = 2 * shorts.length;
        if ((buffer_.position() + byteCount) >= Integer.MAX_VALUE) {
            buffer_ = createBuffer();
        }
        buffer_.asShortBuffer().put(shorts);
        //   for (short s : shorts) {
        //       buffer_.putShort(s);
        //   }
    }

    public void close() throws IOException {
        channel_.truncate(channel_.position());
        channel_.close();
        stream_.close();
    }
}
