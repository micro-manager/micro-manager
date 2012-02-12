///////////////////////////////////////////////////////////////////////////////
//FILE:          ProblemReportSender.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Karl Hoover, 2010
//COPYRIGHT:     University of California, San Francisco, 2010-2012
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.utils.HttpUtils;
import org.micromanager.utils.MMUUEncoder;
import org.micromanager.utils.ReportingUtils;

class ProblemReportSender extends Thread {

    private String status_;
    private CMMCore core_;
    String prepreamble_;

    public ProblemReportSender(String prepreamble, CMMCore c) {
        super("sender");
        status_ = "";
        core_ = c;
        prepreamble_ = prepreamble;
    }

    public String Status() {
        return status_;
    }

   @Override
    public void run() {
        status_ = "";
        // a handy, unique ID for the equipment
        String physicalAddress = "00-00-00-00-00-00";
        String cfgFile = MMStudioMainFrame.getInstance().getSysConfigFile();
        // is there a public way to get these keys??
        //mainPrefs_.get("sysconfig_file", cfgFile);
        String preamble = prepreamble_;
        if(0< preamble.length())
            preamble += "\n";
        preamble += "#";
        StrVector ss = core_.getMACAddresses();
        if (0 < ss.size()){
            String pa2 = ss.get(0);
            if(null != pa2){
                if( 0 <  pa2.length()){
                    physicalAddress = pa2;
                }
            }
        }
        preamble += ("MAC: " + physicalAddress + " ");
        try {

            preamble += "\n#Host: " + InetAddress.getLocalHost().getHostName() + " ";
        } catch (IOException e) {
        }
        preamble += ("\n#User: " + core_.getUserId() + "\n#configuration file: " + cfgFile + "\n");
        try {
            Reader in = new BufferedReader(new FileReader(cfgFile));
            StringBuilder sb = new StringBuilder();
            char[] tmpBuffer = new char[8192];
            int length;

            while ((length = in.read(tmpBuffer)) > 0) {
                sb.append(tmpBuffer, 0, length);
            }
            preamble += sb.toString();
            preamble += "\n";
        } catch (IOException e) {
        }
        String archPath = core_.saveLogArchiveWithPreamble(preamble, preamble.length());
        try {
            HttpUtils httpu = new HttpUtils();
            File archiveFile = new File(archPath);
            
            // contruct a filename for the archive which is extremely
            // likely to be unique as follows:
            // yyyyMMddHHmmss + timezone + MAC address
            String qualifiedArchiveFileName = "";
            try {
                SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
                qualifiedArchiveFileName += df.format(new Date());
                String shortTZName = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT);
                qualifiedArchiveFileName += shortTZName;
                qualifiedArchiveFileName += "_";
                qualifiedArchiveFileName += physicalAddress; 
            } catch (Throwable t) {
            }
            // try ensure valid and convenient UNIX file name
            qualifiedArchiveFileName.replace(' ', '_');
            qualifiedArchiveFileName.replace('*', '_');
            qualifiedArchiveFileName.replace('|', '_');
            qualifiedArchiveFileName.replace('>', '_');
            qualifiedArchiveFileName.replace('<', '_');
            qualifiedArchiveFileName.replace('(', '_');
            qualifiedArchiveFileName.replace(')', '_');
            qualifiedArchiveFileName.replace(':', '_');
            qualifiedArchiveFileName.replace(';', '_');
            qualifiedArchiveFileName += ".log";
            MMUUEncoder uuec = new MMUUEncoder();
            InputStream reader = new FileInputStream(archiveFile);
            // put the report in the tmp directory
            qualifiedArchiveFileName = System.getProperty("java.io.tmpdir") + qualifiedArchiveFileName;
            OutputStream writer = new FileOutputStream(qualifiedArchiveFileName);
            uuec.encodeBuffer(reader, writer);
            reader.close();
            writer.close();
            File fileToSend = new File(qualifiedArchiveFileName);
            try {
                URL url = new URL("http://valelab.ucsf.edu/~MM/upload_corelog.php");
                List flist = new ArrayList<File>();
                flist.add(fileToSend);
                // for each of a colleciton of files to send...
                for (Object o0 : flist) {
                    File f0 = (File) o0;
                    try {
                        httpu.upload(url, f0);
                    } catch (java.net.UnknownHostException e2) {
                        status_ = e2.toString();//, " log archive upload");
                    } catch (IOException e2) {
                        status_ = e2.toString();
                    } catch (SecurityException e2) {
                        status_ = e2.toString();
                    } catch (Exception e2) {
                        status_ = e2.toString();
                    }
                }
            } catch (MalformedURLException e2) {
                status_ = e2.toString();
            }
            if( !fileToSend.delete())
                ReportingUtils.logMessage("Couldn't delete temporary file " + qualifiedArchiveFileName );
            if(!archiveFile.delete())
                ReportingUtils.logMessage("Couldn't delete archive file " + archPath);
        } catch (IOException e2) {
            status_ = e2.toString();
        }
    }
    public String Send(){
       start();
       try {
            join();
       } catch (InterruptedException ex) {
           status_ = ex.toString();

       }
       return Status();
    }
}


