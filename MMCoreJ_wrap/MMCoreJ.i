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


//
// Native library loading
//

%pragma(java) jniclassimports=%{
   import java.io.File;
   import java.util.ArrayList;
   import java.util.List;
   import java.net.URL;
   import java.net.URLDecoder;
%}

// Pull in the compile-time hard-coded paths (determined by Unix configure script)
%javaconst(1) LIBRARY_PATH;
%constant char *LIBRARY_PATH = MMCOREJ_LIBRARY_PATH;

%pragma(java) jniclasscode=%{
   private static class FijiPaths {
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
            if (bang >= 0)
               path = path.substring(0, bang);
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

      public static List<File> getPaths() {
         // Return these dirs:
         // $MMCOREJ_JAR_DIR
         // $MMCOREJ_JAR_DIR/..
         // $MMCOREJ_JAR_DIR/../mm/$PLATFORM
         // $MMCOREJ_JAR_DIR/../..               # Was used by classic Micro-Manager
         // $MMCOREJ_JAR_DIR/../../mm/$PLATFORM
         // XXX: Which one is used by OpenSPIM?

         final File jarDir = new File(getJarPath()).getParentFile();
         final File jarDirParent = jarDir.getParentFile();
         final File jarDirGrandParent = jarDirParent.getParentFile();

         final String fijiPlatform = getPlatformString();
         final File jarDirParentFiji = new File(new File(jarDirParent, "mm"), fijiPlatform);
         final File jarDirGrandParentFiji = new File(new File(jarDirGrandParent, "mm"), fijiPlatform);

         final List<File> searchPaths = new ArrayList<File>();
         searchPaths.add(jarDir);
         searchPaths.add(jarDirParent);
         searchPaths.add(jarDirParentFiji);
         searchPaths.add(jarDirGrandParent);
         searchPaths.add(jarDirGrandParentFiji);
         return searchPaths;
      }
   }

   private final static String MM_PROPERTY_MMCOREJ_LIB_PATH = "mmcorej.library.path";
   private final static String MM_PROPERTY_MMCOREJ_LIB_STDERR_LOG = "mmcorej.library.loading.stderr.log";
   private final static String NATIVE_LIBRARY_NAME = "MMCoreJ_wrap";

   private static void logLibraryLoading(String message) {
      boolean useStdErr = true;

      final String useStdErrProp = System.getProperty(MM_PROPERTY_MMCOREJ_LIB_STDERR_LOG, "0");
      if (useStdErrProp.equals("0") ||
            useStdErrProp.equalsIgnoreCase("false") ||
            useStdErrProp.equalsIgnoreCase("no")) {
         useStdErr = false;
      }

      if (useStdErr) {
         System.err.println("Load " + NATIVE_LIBRARY_NAME + ": " + message);
      }
   }

   private static File getPreferredLibraryPath() {
      final String path = System.getProperty(MM_PROPERTY_MMCOREJ_LIB_PATH);
      if (path != null && path.length() > 0)
         return new File(path);
      return null;
   }

   private static File getHardCodedLibraryPath() {
      final String path = MMCoreJConstants.LIBRARY_PATH;
      if (path != null && path.length() > 0)
         return new File(path);
      return null;
   }

   private static boolean isLinux() {
      return System.getProperty("os.name").toLowerCase().startsWith("linux");
   }

   // Return false if search should be continued
   private static boolean loadNamedNativeLibrary(File dirPath, String libraryName) {
      final String libraryPath = new File(dirPath, libraryName).getAbsolutePath();
      if (new File(libraryPath).exists()) {
         logLibraryLoading("Try loading: " + libraryPath);
         System.load(libraryPath);
         // We let exceptions (usually UnsatisfiedLinkError) propagate, because
         // it is a fatal error to have a library that exists but cannot be
         // loaded.
         logLibraryLoading("Successfully loaded: " + libraryPath);
         return true;
      }
      logLibraryLoading("Skipping nonexistent candidate: " + libraryPath);
      return false;
   }

   // Return false if search should be continued
   private static boolean loadNativeLibrary(File dirPath) {
      final String libraryName = System.mapLibraryName(NATIVE_LIBRARY_NAME);

      // On OS X, System.mapLibraryName() can return a name with a .dylib
      // suffix (since Java 7?). But our native library is expected to have a
      // .jnilib suffix (traditional on OS X). Try both to be safe.
      if (libraryName.endsWith(".dylib")) {
         final String altLibraryName = "lib" + NATIVE_LIBRARY_NAME + ".jnilib";
         boolean ret = loadNamedNativeLibrary(dirPath, altLibraryName);
         if (ret) {
            return true;
         }
      }

      return loadNamedNativeLibrary(dirPath, libraryName);
   }
      
   // Return false if search should be continued
   private static boolean checkIfAlreadyLoaded() {
      boolean loaded = true;
      try {
         CMMCore.noop();
      }
      catch (UnsatisfiedLinkError e) {
         loaded = false;
      }

      if (loaded) {
         // TODO If the library is already loaded, we still want to make sure
         // that it was loaded from the right copy. The Core now has this
         // information, so we should check it.
         logLibraryLoading("Already loaded");
         return true;
      }
      return false;
   }

   // Return false if search should be continued
   private static boolean loadFromPathSetBySystemProperty() {
      final File preferredPath = getPreferredLibraryPath();
      if (preferredPath != null) {
         logLibraryLoading("Try path given by " + MM_PROPERTY_MMCOREJ_LIB_PATH);
         loadNativeLibrary(preferredPath);
         return true;
      }
      return false;
   }

   // Return false if search should be continued
   private static boolean loadFromHardCodedPaths() {
      final List<File> searchPaths = new ArrayList<File>();

      // Some relative paths were hard-coded for running Micro-Manager in Fiji
      // and in the classic distribution (in which the native library is
      // located in the grand-parent directory of the directory containing the
      // MMCoreJ JAR). This also allows finding the native library in the same
      // directory as the JAR.
      searchPaths.addAll(FijiPaths.getPaths());

      // On Linux, also search a compile-time hard-coded path (TODO It is odd
      // that this is done only in the case of Linux. The build system should
      // be modified so that it can be enabled or disabled on any Unix.)
      if (isLinux()) {
         final File hardCodedPath = getHardCodedLibraryPath();
         if (hardCodedPath != null)
            searchPaths.add(hardCodedPath);
      }

      logLibraryLoading("Will search in hard-coded paths:");
      for (File path : searchPaths) {
         logLibraryLoading("  " + path.getPath());
      }

      for (File path : searchPaths) {
         if (loadNativeLibrary(path)) {
            return true;
         }
      }
      return false;
   }

   // Load the MMCoreJ_wrap native library.
   static {
      logLibraryLoading("Start loading...");
      if (!checkIfAlreadyLoaded()) {
         // The most reliable method for locating (the correct copy of)
         // MMCoreJ_wrap is to look in the single path given as a Java system
         // property. The launcher will typically set this property. If this
         // property is set, other paths will not be considered.
         if (!loadFromPathSetBySystemProperty()) {
            // However, if the system property is not set, we search in some
            // candidate directories in order.
            if (!loadFromHardCodedPaths()) {
               // Finally, if all else fails, try the system default mechanism,
               // which will use java.library.path. This is necessary for
               // backward compatibility, and it is also what people will
               // generally expect.
               logLibraryLoading("Falling back to loading using system default method");
               try {
                  System.loadLibrary(NATIVE_LIBRARY_NAME);
               }
               catch (UnsatisfiedLinkError e) {
                  logLibraryLoading("System default loading method failed");
               }
            }
         }
      }
   }

%} // jniclasscode (native library loading)



// output arguments
%apply double &OUTPUT { double &x_stage };
%apply double &OUTPUT { double &y_stage };
%apply int &OUTPUT { int &x };
%apply int &OUTPUT { int &y };
%apply int &OUTPUT { int &xSize };
%apply int &OUTPUT { int &ySize };


// Java typemap
// change default SWIG mapping of unsigned char* return values
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


// Map input argument: java List<byte[]> -> C++ std::vector<unsigned char*>
%typemap(jni) std::vector<unsigned char*>        "jobject"
%typemap(jtype) std::vector<unsigned char*>      "java.util.List<byte[]>"
%typemap(jstype) std::vector<unsigned char*>     "java.util.List<byte[]>"
%typemap(in) std::vector<unsigned char*>
{
   // Assume that we are sending an image to an SLM device, one byte per pixel (monochrome grayscale).
   
   long expectedLength = (arg1)->getSLMWidth(arg2) * (arg1)->getSLMHeight(arg2);
   std::vector<unsigned char*> inputVector;
   jclass clazz = jenv->FindClass("java/util/List");
   jmethodID sizeMethodID = jenv->GetMethodID(clazz, "size", "()I");
   // get JNI ID for java.util.List.get(int i) method.
   // Because of type erasure we specify an "Object" return value,
   // but we expect a byte[] to be returned.
   jmethodID getMethodID = jenv->GetMethodID(clazz, "get", "(I)Ljava/lang/Object;");
   int listSize = jenv->CallIntMethod($input, sizeMethodID);
   
   for (int i = 0; i < listSize; ++i) {
     jbyteArray pixels = (jbyteArray) jenv->CallObjectMethod($input, getMethodID, i);
     long receivedLength = jenv->GetArrayLength(pixels);
   	 if (receivedLength != expectedLength && receivedLength != expectedLength*4)
	 {
	    jclass excep = jenv->FindClass("java/lang/Exception");
	     if (excep)
	        jenv->ThrowNew(excep, "Image dimensions are wrong for this SLM.");
	      return;
	  }
	  inputVector.push_back((unsigned char *) JCALL2(GetByteArrayElements, jenv, pixels, 0));
   }
   $1 = inputVector;
}

%typemap(freearg) std::vector<unsigned char*> {
   // Allow the Java List to be garbage collected.
   // Not sure how to do that here -- may not be necessary.
   //JCALL3(ReleaseByteArrayElements, jenv, $input, (jbyte *) $1, JNI_ABORT); // JNI_ABORT = Don't alter the original array.
}

%typemap(javain) std::vector<unsigned char*> "$javainput" 

// Java typemap
// change default SWIG mapping of void* return values
// to return CObject containing array of pixel values
//
// Assumes that class has the following methods defined:
// unsigned GetImageWidth()
// unsigned GetImageHeight()

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
      if ((arg1)->getNumberOfComponents() == 1)
      {
         // create a new float[] object in Java
         jfloatArray data = JCALL1(NewFloatArray, jenv, lSize);
         if (data == 0)
         {
            jclass excep = jenv->FindClass("java/lang/OutOfMemoryError");
            if (excep)
               jenv->ThrowNew(excep, "The system ran out of memory!");

            $result = 0;
            return $result;
         }

         // copy pixels from the image buffer
         JCALL4(SetFloatArrayRegion, jenv, data, 0, lSize, (jfloat*)result);

         $result = data;
      }
      else
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
// change default SWIG mapping of void* return values
// to return CObject containing array of pixel values
//
// Assumes that class has the following methods defined:
// unsigned GetImageWidth()
// unsigned GetImageHeight()
// unsigned GetImageDepth()
// unsigned GetNumberOfComponents()


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
     jenv->ThrowNew(excep, $1.getFullMsg().c_str());
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

// We've translated exceptions to java.lang.Exception, so don't wrap the unused
// C++ exception classes.
%ignore CMMError;
%ignore MetadataKeyError;
%ignore MetadataIndexError;


%typemap(javaimports) CMMCore %{
   import org.json.JSONObject;
   import java.awt.geom.Point2D;
   import java.awt.Rectangle;
   import java.util.ArrayList;
   import java.util.List;
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
      int numComponents = (int) getNumberOfComponents();
      switch (depth) {
         case 1:
            return "GRAY8";
         case 2:
            return "GRAY16";
         case 4: {
            if (numComponents == 1)
               return "GRAY32";
            else
               return "RGB32";
            }
         case 8:
            return "RGB64";
     }
     return "";
   }

   private String getMultiCameraChannel(JSONObject tags, int cameraChannelIndex) {
	  try {
	  String camera = tags.getString("Core-Camera");
	  String physCamKey = camera + "-Physical Camera " + (1 + cameraChannelIndex);
	  if (tags.has(physCamKey)) {
		 try {
			return tags.getString(physCamKey);
		 } catch (Exception e2) {
			return null;
		 }
	  } else {
		 return null;
	  }
	 } catch (Exception e) {
	   return null;
	 }

   }

   private TaggedImage createTaggedImage(Object pixels, Metadata md, int cameraChannelIndex) throws java.lang.Exception {
      TaggedImage image = createTaggedImage(pixels, md);
      JSONObject tags = image.tags;
      
      if (!tags.has("CameraChannelIndex")) {
         tags.put("CameraChannelIndex", cameraChannelIndex);
         tags.put("ChannelIndex", cameraChannelIndex);
      }
      if (!tags.has("Camera")) {
         String physicalCamera = getMultiCameraChannel(tags, cameraChannelIndex);
         if (physicalCamera != null) {
            tags.put("Camera", physicalCamera);
            tags.put("Channel",physicalCamera);
         }
      }
      return image;
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
      tags.put("PixelSizeUm", getPixelSizeUm(true));
      tags.put("ROI", getROITag());
      tags.put("Width", getImageWidth());
      tags.put("Height", getImageHeight());
      tags.put("PixelType", getPixelType());
      tags.put("Frame", 0);
      tags.put("FrameIndex", 0);
      tags.put("Position", "Default");
      tags.put("PositionIndex", 0);
      tags.put("Slice", 0);
      tags.put("SliceIndex", 0);
      String channel = getCurrentConfigFromCache(getPropertyFromCache("Core","ChannelGroup"));
      if ((channel == null) || (channel.length() == 0)) {
         channel = "Default";
      }
      tags.put("Channel", channel);
      tags.put("ChannelIndex", 0);


      try {
         tags.put("Binning", getProperty(getCameraDevice(), "Binning"));
      } catch (Exception ex) {}
      
      return new TaggedImage(pixels, tags);	
   }

   public TaggedImage getTaggedImage(int cameraChannelIndex) throws java.lang.Exception {
      Metadata md = new Metadata();
      Object pixels = getImage(cameraChannelIndex);
      return createTaggedImage(pixels, md, cameraChannelIndex);
   }

   public TaggedImage getTaggedImage() throws java.lang.Exception {
      return getTaggedImage(0);
   }

   public TaggedImage getLastTaggedImage(int cameraChannelIndex) throws java.lang.Exception {
      Metadata md = new Metadata();
      Object pixels = getLastImageMD(cameraChannelIndex, 0, md);
      return createTaggedImage(pixels, md, cameraChannelIndex);
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
      return createTaggedImage(pixels, md, cameraChannelIndex);
   }

   public TaggedImage popNextTaggedImage() throws java.lang.Exception {
      return popNextTaggedImage(0);
   }

   // convenience functions follow
   
   /*
    * Convenience function. Returns the ROI of the current camera in a java.awt.Rectangle.
    */
   public Rectangle getROI() throws java.lang.Exception {
      // ROI values are given as x,y,w,h in individual one-member arrays (pointers in C++):
      int[][] a = new int[4][1];
      getROI(a[0], a[1], a[2], a[3]);
      return new Rectangle(a[0][0], a[1][0], a[2][0], a[3][0]);
   }
   
    /*
    * Convenience function. Returns the ROI of specified camera in a java.awt.Rectangle.
    */
   public Rectangle getROI(String label) throws java.lang.Exception {
      // ROI values are given as x,y,w,h in individual one-member arrays (pointers in C++):
      int[][] a = new int[4][1];
      getROI(label, a[0], a[1], a[2], a[3]);
      return new Rectangle(a[0][0], a[1][0], a[2][0], a[3][0]);
   }

   /*
    * Convenience function: returns multiple ROIs of the current camera as a
    * list of java.awt.Rectangles.
    */
   public List<Rectangle> getMultiROI() throws java.lang.Exception {
      UnsignedVector xs = new UnsignedVector();
      UnsignedVector ys = new UnsignedVector();
      UnsignedVector widths = new UnsignedVector();
      UnsignedVector heights = new UnsignedVector();
      getMultiROI(xs, ys, widths, heights);
      ArrayList<Rectangle> result = new ArrayList<Rectangle>();
      for (int i = 0; i < xs.size(); ++i) {
         Rectangle r = new Rectangle((int) xs.get(i), (int) ys.get(i),
               (int) widths.get(i), (int) heights.get(i));
         result.add(r);
      }
      return result;
   }

   /*
    * Convenience function: convert incoming list of Rectangles into vectors
    * of ints to set multiple ROIs.
    */
   public void setMultiROI(List<Rectangle> rects) throws java.lang.Exception {
      UnsignedVector xs = new UnsignedVector();
      UnsignedVector ys = new UnsignedVector();
      UnsignedVector widths = new UnsignedVector();
      UnsignedVector heights = new UnsignedVector();
      for (Rectangle r : rects) {
         xs.add(r.x);
         ys.add(r.y);
         widths.add(r.width);
         heights.add(r.height);
      }
      setMultiROI(xs, ys, widths, heights);
   }

   /* 
    * Convenience function. Returns the current x,y position of the stage in a Point2D.Double.
    */
   public Point2D.Double getXYStagePosition(String stage) throws java.lang.Exception {
      // stage position is given as x,y in individual one-member arrays (pointers in C++):
      double p[][] = new double[2][1];
      getXYPosition(stage, p[0], p[1]);
      return new Point2D.Double(p[0][0], p[1][0]);
   }

   /**
    * Convenience function: returns the current XY position of the current
    * XY stage device as a Point2D.Double.
    */
   public Point2D.Double getXYStagePosition() throws java.lang.Exception {
      double x[] = new double[1];
      double y[] = new double[1];
      getXYPosition(x, y);
      return new Point2D.Double(x[0], y[0]);
   }
   
   /* 
    * Convenience function. Returns the current x,y position of the galvo in a Point2D.Double.
    */
   public Point2D.Double getGalvoPosition(String galvoDevice) throws java.lang.Exception {
      // stage position is given as x,y in individual one-member arrays (pointers in C++):
      double p[][] = new double[2][1];
      getGalvoPosition(galvoDevice, p[0], p[1]);
      return new Point2D.Double(p[0][0], p[1][0]);
   }
%}


%{
#include "../MMDevice/MMDeviceConstants.h"
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
	

	%typemap(javaimports) vector<unsigned> %{
		import java.lang.Iterable;
		import java.util.Iterator;
		import java.util.NoSuchElementException;
		import java.lang.UnsupportedOperationException;
	%}

   %typemap(javainterfaces) vector<unsigned> %{ Iterable<Long>%}

   %typemap(javacode) vector<unsigned> %{
      public Iterator<Long> iterator() {
         return new Iterator<Long>() {

            private int i_=0;

            public boolean hasNext() {
               return (i_<size());
            }

            public Long next() throws NoSuchElementException {
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

      public Long[] toArray() {
         if (0==size())
            return new Long[0];

         Long ints[] = new Long[(int) size()];
         for (int i=0; i<size(); ++i) {
            ints[i] = get(i);
         }
         return ints;
      }
   %}




    %template(CharVector)   vector<char>;
    %template(LongVector)   vector<long>;
    %template(DoubleVector) vector<double>;
    %template(StrVector)    vector<string>;
    %template(BooleanVector)    vector<bool>;
    %template(UnsignedVector) vector<unsigned>;
    %template(pair_ss)      pair<string, string>;
    %template(StrMap)       map<string, string>;




}


%include "../MMDevice/MMDeviceConstants.h"
%include "../MMCore/Configuration.h"
%include "../MMCore/MMCore.h"
%include "../MMDevice/ImageMetadata.h"
%include "../MMCore/MMEventCallback.h"

