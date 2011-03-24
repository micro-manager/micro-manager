/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author Johannes Schindelin
 */
public class MMUUEncoder {
        protected int maxBytesPerLine = 45;

        public void encodeBuffer(InputStream in, OutputStream out) throws IOException {
                out.write("begin 664 raw.bin\n".getBytes());
                byte[] buffer = new byte[maxBytesPerLine * 4 / 3];
                int counter = 0;
                for (;;) {
                        if (counter == maxBytesPerLine * 4 / 3) {
                                writeWithLength(out, buffer, counter);
                                counter = 0;
                        }

                        int value1 = in.read();
                        if (value1 < 0)
                                break;
                        int value2 = in.read(), value3 = in.read();
                        int packed = ((value1 & 0xff) << 16) |
                                ((value2 & 0xff) << 8) |
                                (value3 & 0xff);
                        buffer[counter++] = encode((packed >> 18) & 0x3f);
                        buffer[counter++] = encode((packed >> 12) & 0x3f);
                        if (value2 < 0)
                                break;
                        buffer[counter++] = encode((packed >> 6) & 0x3f);
                        if (value3 < 0)
                                break;
                        buffer[counter++] = encode(packed & 0x3f);
                }
                if (counter > 0)
                        writeWithLength(out, buffer, counter);
                out.write("`\nend\n".getBytes());
        }

        public static void writeWithLength(OutputStream out, byte[] buffer, int counter) throws IOException {
                out.write(encode(counter * 3 / 4));
                out.write(buffer, 0, counter);
                out.write('\n');
        }

        public static byte encode(int value) {
                return (byte)(value == 0 ? 96 : 32 + value);
        }

}
