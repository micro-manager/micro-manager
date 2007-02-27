///////////////////////////////////////////////////////////////////////////////
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

%module MMCoreJ
%include std_string.i
%include std_vector.i
%include std_map.i
%include std_pair.i
%include "typemaps.i"

// output arguments
%apply double &OUTPUT { double &x };
%apply double &OUTPUT { double &y };

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

// change Java wrapper mapping for unsigned char*
%typemap(javaout) unsigned char* {
    return $jnicall;
 }
 
 
// Java typemap
// change deafult SWIG mapping of void* return values
// to return CObject containing array of pixel values
//
// Assumes that class has the following methods defined:
// unsigned GetImageWidth()
// unsigned GetImageHeight()
// unsigned GetImageDepth()
//
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
         jclass excep = jenv->FindClass("java/lang/Exception");
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
         jclass excep = jenv->FindClass("java/lang/Exception");
		 if (excep)
			jenv->ThrowNew(excep, "The system ran out of memory!");
		$result = 0;
		return $result;
	  }
  
      // copy pixels from the image buffer
      JCALL4(SetShortArrayRegion, jenv, data, 0, lSize, (jshort*)result);

      $result = data;
   }
   else
   {
      // don't know how to map
      // TODO: thow exception?
      $result = 0;
   }
}


//
// CMMError exception objects
//
%rename(eql) operator=;
%typemap(throws, throws="java.lang.Exception") CMMError {
   jclass excep = jenv->FindClass("java/lang/Exception");
   if (excep)
     jenv->ThrowNew(excep, $1.getMsg().c_str());
   return $null;
}

%typemap(javabase) CMMError "java.lang.Exception"

%typemap(javacode) CMMError %{
   public String getMessage() {
      return getMsg();
   }
%}

%pragma(java) jniclasscode=%{
  static {
    try {
        System.loadLibrary("MMCoreJ_wrap");
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
#include "../MMCore/MMCore.h"
%}


// intantiate STL mappings
namespace std {
    %template(CharVector)   vector<char>;
    %template(IntVector)    vector<int>;
    %template(LongVector)   vector<long>;
    %template(StrVector)    vector<string>;
    %template(pair_ss)      pair<string, string>;
    %template(StrMap)       map<string, string>;
}

%include "../MMDevice/MMDeviceConstants.h"
%include "../MMCore/Error.h"
%include "../MMCore/Configuration.h"
%include "../MMCore/MMCore.h"
