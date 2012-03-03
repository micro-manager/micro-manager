/////////////////////////////////////////////////////////////////////////////////
// FILE:          MMCoreJ.i
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCoreJ
//-----------------------------------------------------------------------------
// DESCRIPTION:   SWIG generator for the Java interface wrapper.
//              
// COPYRIGHT:     University of California, San Francisco, 2006,
//                All Rights reserved
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/07/2005
// 
// CVS:           $Id$
//

%module (directors="1") MMCoreJ
%feature("director") MMEventCallback;

%include std_string.i
%include std_vector.i
%include std_map.i
%include std_pair.i
%include "typemaps.i"

// output arguments
%apply double &OUTPUT { double &x_stage };
%apply double &OUTPUT { double &y_stage };
%apply int &OUTPUT { int &x };
%apply int &OUTPUT { int &y };
%apply int &OUTPUT { int &xSize };
%apply int &OUTPUT { int &ySize };


// Java typemap
// change deafult SWIG mapping of unsigned char* return values
// to byte[]
//
// Assumes that class has the following method defined:
// long GetImageBufferSize()
//


%typemap(jni) unsigned char*        "jbyteArray"
%typemap(jtype) unsigned char*      "byte[]"
%typemap(jstype) unsigned char*     "byte[]"
%typemap(out) unsigned char*
{
   long lSize = (arg1)->getImageBufferSize();
   
   // create a new byte[] object in Java
   jbyteArray data = JCALL1(NewByteArray, jenv, lSize);
   
   // copy pixels from the image buffer
   JCALL4(SetByteArrayRegion, jenv, data, 0, lSize, (jbyte*)result);

   $result = data;
}

// Map input argument: java byte[] -> C++ unsigned char *
%typemap(in) unsigned char*
{
   // Assume that we are sending an image to an SLM device, one byte per pixel (monochrome grayscale).
   
   long expectedLength = (arg1)->getSLMWidth(arg2) * (arg1)->getSLMHeight(arg2);
   long receivedLength = JCALL1(GetArrayLength, jenv, $input);
   
   if (receivedLength != expectedLength && receivedLength != expectedLength*4)
   {
      jclass excep = jenv->FindClass("java/lang/Exception");
      if (excep)
         jenv->ThrowNew(excep, "Image dimensions are wrong for this SLM.");
      return;
   }
   
   $1 = (unsigned char *) JCALL2(GetByteArrayElements, jenv, $input, 0);
}

%typemap(freearg) unsigned char* {
   // Allow the Java byte array to be garbage collected.
   JCALL3(ReleaseByteArrayElements, jenv, $input, (jbyte *) $1, JNI_ABORT); // JNI_ABORT = Don't alter the original array.
}

// change Java wrapper output mapping for unsigned char*
%typemap(javaout) unsigned char* {
    return $jnicall;
 }

%typemap(javain) unsigned char* "$javainput" 


 
// Java typemap
// change deafult SWIG mapping of void* return values
// to return CObject containing array of pixel values
//
// Assumes that class has the following methods defined:
// unsigned GetImageWidth()
// unsigned GetImageHeight()
// unsigned GetImageDepth()
// unsigned GetNumberOfComponents


%typemap(jni) void*        "jobject"
%typemap(jtype) void*      "Object"
%typemap(jstype) void*     "Object"
%typemap(javaout) void* {
   return $jnicall;
}
%typemap(out) void*
{
   long lSize = (arg1)->getImageWidth() * (arg1)->getImageHeight();
   
   if ((arg1)->getBytesPerPixel() == 1)
   {
      // create a new byte[] object in Java
      jbyteArray data = JCALL1(NewByteArray, jenv, lSize);
      if (data == 0)
      {
         jclass excep = jenv->FindClass("java/lang/OutOfMemoryError");
         if (excep)
            jenv->ThrowNew(excep, "The system ran out of memory!");

         $result = 0;
         return $result;
      }
   
      // copy pixels from the image buffer
      JCALL4(SetByteArrayRegion, jenv, data, 0, lSize, (jbyte*)result);

      $result = data;
   }
   else if ((arg1)->getBytesPerPixel() == 2)
   {
      // create a new short[] object in Java
      jshortArray data = JCALL1(NewShortArray, jenv, lSize);
      if (data == 0)
      {
         jclass excep = jenv->FindClass("java/lang/OutOfMemoryError");
         if (excep)
            jenv->ThrowNew(excep, "The system ran out of memory!");
         $result = 0;
         return $result;
      }
  
      // copy pixels from the image buffer
      JCALL4(SetShortArrayRegion, jenv, data, 0, lSize, (jshort*)result);

      $result = data;
   }
   else if ((arg1)->getBytesPerPixel() == 4)
   {
      // create a new byte[] object in Java
      jbyteArray data = JCALL1(NewByteArray, jenv, lSize * 4);
      if (data == 0)
      {
         jclass excep = jenv->FindClass("java/lang/OutOfMemoryError");
         if (excep)
            jenv->ThrowNew(excep, "The system ran out of memory!");

         $result = 0;
         return $result;
      }
   
      // copy pixels from the image buffer
      JCALL4(SetByteArrayRegion, jenv, data, 0, lSize * 4, (jbyte*)result);

      $result = data;
   }
   else if ((arg1)->getBytesPerPixel() == 8)
   {
      // create a new short[] object in Java
      jshortArray data = JCALL1(NewShortArray, jenv, lSize * 4);
      if (data == 0)
      {
         jclass excep = jenv->FindClass("java/lang/OutOfMemoryError");
         if (excep)
            jenv->ThrowNew(excep, "The system ran out of memory!");
         $result = 0;
         return $result;
      }
  
      // copy pixels from the image buffer
      JCALL4(SetShortArrayRegion, jenv, data, 0, lSize * 4, (jshort*)result);

      $result = data;
   }

   else
   {
      // don't know how to map
      // TODO: throw exception?
      $result = 0;
   }
}

// Java typemap
// change deafult SWIG mapping of void* return values
// to return CObject containing array of pixel values
//
// Assumes that class has the following methods defined:
// unsigned GetImageWidth()
// unsigned GetImageHeight()
// unsigned GetImageDepth()
// unsigned GetNumberOfComponents


%typemap(jni) unsigned int* "jobject"
%typemap(jtype) unsigned int*      "Object"
%typemap(jstype) unsigned int*     "Object"
%typemap(javaout) unsigned int* {
   return $jnicall;
}
%typemap(out) unsigned int*
{
   long lSize = (arg1)->getImageWidth() * (arg1)->getImageHeight();
   unsigned numComponents = (arg1)->getNumberOfComponents();
   
   if ((arg1)->getBytesPerPixel() == 1 && numComponents == 4)
   {
      // assuming RGB32 format
      // create a new int[] object in Java
      jintArray data = JCALL1(NewIntArray, jenv, lSize);
      if (data == 0)
      {
         jclass excep = jenv->FindClass("java/lang/OutOfMemoryError");
         if (excep)
            jenv->ThrowNew(excep, "The system ran out of memory!");
         $result = 0;
         return $result;
      }
  
      // copy pixels from the image buffer
      JCALL4(SetIntArrayRegion, jenv, data, 0, lSize, (jint*)result);

      $result = data;
   }
   else
   {
      // don't know how to map
      // TODO: thow exception?
      $result = 0;
   }
}


%typemap(jni) imgRGB32 "jintArray"
%typemap(jtype) imgRGB32      "int[]"
%typemap(jstype) imgRGB32     "int[]"
%typemap(javain) imgRGB32     "$javainput"
%typemap(in) imgRGB32
{
   // Assume that we are sending an image to an SLM device, one int (four bytes) per pixel.
   
   if  ((arg1)->getSLMBytesPerPixel(arg2) != 4)
   {
      jclass excep = jenv->FindClass("java/lang/Exception");
      if (excep)
         jenv->ThrowNew(excep, "32-bit array received but not expected for this SLM.");
      return;
   }
   
   long expectedLength = (arg1)->getSLMWidth(arg2) * (arg1)->getSLMHeight(arg2);
   long receivedLength = JCALL1(GetArrayLength, jenv, (jarray) $input);
   
   if (receivedLength != expectedLength)
   {
      jclass excep = jenv->FindClass("java/lang/Exception");
      if (excep)
         jenv->ThrowNew(excep, "Image dimensions are wrong for this SLM.");
      return;
   }
   
   $1 = (imgRGB32) JCALL2(GetIntArrayElements, jenv, (jintArray) $input, 0);
}

%typemap(freearg) imgRGB32 {
   // Allow the Java int array to be garbage collected.
   JCALL3(ReleaseIntArrayElements, jenv, $input, (jint *) $1, JNI_ABORT); // JNI_ABORT = Don't alter the original array.
}


//
// Map all exception objects coming from C++ level
// generic Java Exception
//
%rename(eql) operator=;

// CMMError used by MMCore
%typemap(throws, throws="java.lang.Exception") CMMError {
   jclass excep = jenv->FindClass("java/lang/Exception");
   if (excep)
     jenv->ThrowNew(excep, $1.getMsg().c_str());
   return $null;
}

// MetadataKeyError used by Metadata class
%typemap(throws, throws="java.lang.Exception") MetadataKeyError {
   jclass excep = jenv->FindClass("java/lang/Exception");
   if (excep)
     jenv->ThrowNew(excep, $1.getMsg().c_str());
   return $null;
}

// MetadataIndexError used by Metadata class
%typemap(throws, throws="java.lang.Exception") MetadataIndexError {
   jclass excep = jenv->FindClass("java/lang/Exception");
   if (excep)
     jenv->ThrowNew(excep, $1.getMsg().c_str());
   return $null;
}

%typemap(javabase) CMMError "java.lang.Exception"
//%typemap(javabase) MetadataKeyError "java.lang.Exception"
//%typemap(javabase) MetadataIndexError "java.lang.Exception"

%typemap(javaimports) CMMCore %{
   import org.json.JSONObject;
%}

%typemap(javacode) CMMCore %{
   private JSONObject metadataToMap(Metadata md) {
      JSONObject tags = new JSONObject();
      for (String key:md.GetKeys()) {
         try {
            tags.put(key, md.GetSingleTag(key).GetValue());
         } catch (Exception e) {} 
      }
      return tags;
   }

   private String getROITag() throws java.lang.Exception {
      String roi = "";
      int [] x = new int[1];
      int [] y = new int[1];
      int [] xSize = new int[1];
      int [] ySize = new int[1];
      getROI(x, y, xSize, ySize);
      roi += x[0] + "-" + y[0] + "-" + xSize[0] + "-" + ySize[0];
      return roi;
   }

   private String getPixelType() {
     int depth = (int) getBytesPerPixel();
     switch (depth) {
         case 1:
            return "GRAY8";
         case 2:
            return "GRAY16";
         case 4:
            return "RGB32";
         case 8:
            return "RGB64";
     }
     return "";
   }

   private TaggedImage createTaggedImage(Object pixels, Metadata md) throws java.lang.Exception {
      JSONObject tags = metadataToMap(md);
      PropertySetting setting;
      Configuration config = getSystemStateCache();
      for (int i = 0; i < config.size(); ++i) {
         setting = config.getSetting(i);
         String key = setting.getDeviceLabel() + "-" + setting.getPropertyName();
         String value = setting.getPropertyValue();
          tags.put(key, value);
      }
      tags.put("BitDepth", getImageBitDepth());
      tags.put("PixelSizeUm", getPixelSizeUm());
      tags.put("ROI", getROITag());
      tags.put("Width", getImageWidth());
      tags.put("Height", getImageHeight());
      tags.put("PixelType", getPixelType());
      try {
         tags.put("Binning", getProperty(getCameraDevice(), "Binning"));
      } catch (Exception ex) {}
      return new TaggedImage(pixels, tags);	
   }

   public TaggedImage getTaggedImage(int cameraChannelIndex) throws java.lang.Exception {
      Metadata md = new Metadata();
      Object pixels = getImage(cameraChannelIndex);
      return createTaggedImage(pixels, md);
   }

   public TaggedImage getTaggedImage() throws java.lang.Exception {
      return getTaggedImage(0);
   }

   public TaggedImage getLastTaggedImage(int cameraChannelIndex) throws java.lang.Exception {
      Metadata md = new Metadata();
      Object pixels = getLastImageMD(cameraChannelIndex, 0, md);
      return createTaggedImage(pixels, md);
   }
   
   public TaggedImage getLastTaggedImage() throws java.lang.Exception {
      return getLastTaggedImage(0);
   }

   public TaggedImage getNBeforeLastTaggedImage(long n) throws java.lang.Exception {
      Metadata md = new Metadata();
      Object pixels = getNBeforeLastImageMD(n, md);
      return createTaggedImage(pixels, md);
   }

   public TaggedImage popNextTaggedImage(int cameraChannelIndex) throws java.lang.Exception {
      Metadata md = new Metadata();
      Object pixels = popNextImageMD(cameraChannelIndex, 0, md);
      return createTaggedImage(pixels, md);
   }

   public TaggedImage popNextTaggedImage() throws java.lang.Exception {
      return popNextTaggedImage(0);
   }

%}


%typemap(javacode) CMMError %{
   public String getMessage() {
      return getMsg();
   }
%}

%typemap(javacode) MetadataKeyError %{
   public String getMessage() {
      return getMsg();
   }
%}

%typemap(javacode) MetadataIndexError %{
   public String getMessage() {
      return getMsg();
   }
%}

%pragma(java) jniclassimports=%{
   import java.io.File;

   import java.util.ArrayList;
   import java.util.List;
   import java.net.URL;
   import java.net.URLDecoder;
%}

%pragma(java) jniclasscode=%{

  private static String URLtoFilePath(URL url) throws Exception {
    // We need to get rid of multiple protocols (jar: and file:)
    // and end up with an file path correct on every platform.
    // The following lines seem to work, though it's ugly:
	String url1 = URLDecoder.decode(url.getPath(), "UTF-8");
	String url2 = URLDecoder.decode(new URL(url1).getPath(), "UTF-8");
	return new File(url2).getAbsolutePath();
  }

  private static String getJarPath() {
    String classFile = "/mmcorej/CMMCore.class";
    try {
		String path = URLtoFilePath(CMMCore.class.getResource(classFile));
		int bang = path.indexOf('!');
		if (bang > 0)
			path = path.substring(0, bang);
		System.out.println("MMCoreJ.jar path = " + path);
		return path;
	} catch (Exception e) {
		return "";
	}
  }

  private static String getPlatformString() {
    String osName = System.getProperty("os.name");
    String osArch = System.getProperty("os.arch");
    return osName.startsWith("Mac") ? "macosx" :
      (osName.startsWith("Win") ? "win" : osName.toLowerCase()) +
      (osArch.indexOf("64") < 0 ? "32" : "64");
  }

  public static void loadLibrary(List<File> searchPaths, String name) {
    String libraryName = System.mapLibraryName(name);
    for (File path : searchPaths)
        if (new File(path, libraryName).exists()) {
            System.load(new File(path, libraryName).getAbsolutePath());
            return;
        }
    System.loadLibrary(name);
  }

  static {
    List<File> searchPaths = new ArrayList<File>();
    File directory = new File(getJarPath()).getParentFile();
    searchPaths.add(directory);
    directory = directory.getParentFile();
    searchPaths.add(directory);
    String platform = getPlatformString();
    File directoryMM = new File(new File(directory, "mm"), platform);
    searchPaths.add(directoryMM);
    directory = directory.getParentFile();
    searchPaths.add(directory);
    directoryMM = new File(new File(directory, "mm"), platform);
    searchPaths.add(directoryMM);
    // on Linux use the LSB-defined library paths
    if (platform.startsWith("linux"))
    {
        searchPaths.add(new File("/usr/local/lib/micro-manager"));
        searchPaths.add(new File("/usr/lib/micro-manager"));
    }
    
	try {
	    loadLibrary(searchPaths, "MMCoreJ_wrap");
        for (File path : searchPaths) {
          System.out.println(path.getAbsolutePath());
          CMMCore.addSearchPath(path.getAbsolutePath());
          }
    } catch (UnsatisfiedLinkError e) {
        System.err.println("Native code library failed to load. \n" + e);
        // do not exit here, loadLibrary does not work on all platforms in the same way,
        // perhaps the library is already loaded.
        //System.exit(1);
    }
  }
%}

%{
#include "../MMDevice/MMDeviceConstants.h"
#include "../MMCore/Error.h"
#include "../MMCore/Configuration.h"
#include "../MMDevice/ImageMetadata.h"
#include "../MMCore/MMEventCallback.h"
#include "../MMCore/MMCore.h"
%}


// instantiate STL mappings

namespace std {

	%typemap(javaimports) vector<string> %{
		import java.lang.Iterable;
		import java.util.Iterator;
		import java.util.NoSuchElementException;
		import java.lang.UnsupportedOperationException;
	%}
	
	%typemap(javainterfaces) vector<string> %{ Iterable<String>%}
	
	%typemap(javacode) vector<string> %{
	
		public Iterator<String> iterator() {
			return new Iterator<String>() {
			
				private int i_=0;
			
				public boolean hasNext() {
					return (i_<size());
				}
				
				public String next() throws NoSuchElementException {
					if (hasNext()) {
						++i_;
						return get(i_-1);
					} else {
					throw new NoSuchElementException();
					}
				}
					
				public void remove() throws UnsupportedOperationException {
					throw new UnsupportedOperationException();
				}		
			};
		}
		
		public String[] toArray() {
			if (0==size())
				return new String[0];
			
			String strs[] = new String[(int) size()];
			for (int i=0; i<size(); ++i) {
				strs[i] = get(i);
			}
			return strs;
		}
		
	%}
	
   

	%typemap(javaimports) vector<bool> %{
		import java.lang.Iterable;
		import java.util.Iterator;
		import java.util.NoSuchElementException;
		import java.lang.UnsupportedOperationException;
	%}
	
	%typemap(javainterfaces) vector<bool> %{ Iterable<Boolean>%}
	
	%typemap(javacode) vector<bool> %{
	
		public Iterator<Boolean> iterator() {
			return new Iterator<Boolean>() {
			
				private int i_=0;
			
				public boolean hasNext() {
					return (i_<size());
				}
				
				public Boolean next() throws NoSuchElementException {
					if (hasNext()) {
						++i_;
						return get(i_-1);
					} else {
					throw new NoSuchElementException();
					}
				}
					
				public void remove() throws UnsupportedOperationException {
					throw new UnsupportedOperationException();
				}		
			};
		}
		
		public Boolean[] toArray() {
			if (0==size())
				return new Boolean[0];
			
			Boolean strs[] = new Boolean[(int) size()];
			for (int i=0; i<size(); ++i) {
				strs[i] = get(i);
			}
			return strs;
		}
		
	%}
	





    %template(CharVector)   vector<char>;
    %template(LongVector)   vector<long>;
    %template(DoubleVector) vector<double>;
    %template(StrVector)    vector<string>;
    %template(BooleanVector)    vector<bool>;
    %template(pair_ss)      pair<string, string>;
    %template(StrMap)       map<string, string>;





}


%include "../MMDevice/MMDeviceConstants.h"
%include "../MMCore/Error.h"
%include "../MMCore/Configuration.h"
%include "../MMCore/MMCore.h"
%include "../MMDevice/ImageMetadata.h"
%include "../MMCore/MMEventCallback.h"

