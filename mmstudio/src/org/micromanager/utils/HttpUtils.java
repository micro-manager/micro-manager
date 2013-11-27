
package org.micromanager.utils;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 *
 * @author karlhoover
 */
public class HttpUtils {

    public void upload(URL url, File f) throws Exception {
        final String boundary = HttpBoundaryString();
        HttpURLConnection anURLConnection = (HttpURLConnection) url.openConnection();
        anURLConnection.setDoOutput(true);
        anURLConnection.setDoInput(true);
        anURLConnection.setUseCaches(false);
        anURLConnection.setChunkedStreamingMode(1024);
        anURLConnection.setRequestMethod("POST");
        anURLConnection.setRequestProperty("Connection", "keep-alive");
        anURLConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        DataOutputStream httpOut = new DataOutputStream(anURLConnection.getOutputStream());


        String str = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + f.getName() + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        //+ "Content-Type: text/plain\r\n\r\n";

        httpOut.write(str.getBytes());

        FileInputStream uploadFileReader = new FileInputStream(f);
        int numBytesToRead = 1024;
        int availableBytesToRead;
        while ((availableBytesToRead = uploadFileReader.available()) > 0) {
            byte[] bufferBytesRead;
            bufferBytesRead = availableBytesToRead >= numBytesToRead ? new byte[numBytesToRead]
                    : new byte[availableBytesToRead];
            uploadFileReader.read(bufferBytesRead);
            httpOut.write(bufferBytesRead);
            httpOut.flush();
        }

        httpOut.write(("\n\r\n--" + boundary + "\r\n").getBytes());
        httpOut.write(("Content-Disposition: form-data; name=\"submit\"\r\n\r\nSubmit\r\n--" + boundary + "--\r\n").getBytes());
        httpOut.flush();
        httpOut.close();

        try{
            uploadFileReader.close();
        }catch(Exception e){
            ReportingUtils.logError(e);
        }

        // read & parse the response
        InputStream is = anURLConnection.getInputStream();
        StringBuilder response = new StringBuilder();
        byte[] respBuffer = new byte[4096];
        while (is.read(respBuffer) >= 0) {
            response.append(new String(respBuffer).trim());
        }
        is.close();
        System.out.println(response.toString());
    }

    private String HttpBoundaryString() {
        String possibleCharacters = "+-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        int length = 36;
        StringBuffer workingBuffer = new StringBuffer(length);
        String preAmble = "--Micro-ManagerReporter+0";
        workingBuffer.append(preAmble);
        try{
        length -= preAmble.length();
            for (int i = 0; i < length; i++) {
                int ioff = (int) (0.5 + Math.random() * possibleCharacters.length());
                workingBuffer.append(possibleCharacters.charAt(ioff));
            }
        } catch(Throwable t){
            System.out.println(t.getLocalizedMessage());
        }

        return workingBuffer.toString();
    }
}
