///////////////////////////////////////////////////////////////////////////////
// FILE:          MMCorePy.i
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCorePy
//-----------------------------------------------------------------------------
// DESCRIPTION:   SWIG generator for the Python interface wrapper.
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
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 2009.08.11
//                based on the java wrapper code by
//                Nenad Amodaj, nenad@amodaj.com, 06/07/2005
// 
// CVS:           $Id: MMCorePy.i 2178 2009-02-27 13:08:53Z nenad $
//





%module (directors="1") MMCorePy
%feature("director") MMEventCallback;

%include std_string.i
%include std_vector.i
%include std_map.i
%include std_pair.i
%include "typemaps.i"


%{
#define SWIG_FILE_WITH_INIT
%}

%init %{
import_array();
%}

%{
#include "arrayobject.h"
#include "string.h"
%}


// output arguments
%apply double &OUTPUT { double &x };
%apply double &OUTPUT { double &y };
%apply int &OUTPUT { int &x };
%apply int &OUTPUT { int &y };
%apply int &OUTPUT { int &xSize };
%apply int &OUTPUT { int &ySize };


%typemap(out) void*
{
   npy_intp dims[2];
   dims[0] = (arg1)->getImageHeight();
   dims[1] = (arg1)->getImageWidth();
   //unsigned numChannels = (arg1)->getNumberOfComponents();

   if ((arg1)->getBytesPerPixel() == 1)
   {
	  // create new numpy array object
      PyObject * numpyArray = PyArray_SimpleNew(2, dims, NPY_UINT8);

      // copy the data to the numpy array data buffer.
      memcpy(PyArray_DATA((PyArrayObject *) numpyArray), result, dims[0]*dims[1]);

      $result = numpyArray;
   }
   else if ((arg1)->getBytesPerPixel() == 2)
   {
	  // create new numpy array object
      PyObject * numpyArray = PyArray_SimpleNew(2, dims, NPY_UINT16);

      // copy the data to the numpy array data buffer.
      memcpy(PyArray_DATA((PyArrayObject *) numpyArray), result, dims[0]*dims[1]*2);

      $result = numpyArray;
   }
   else if ((arg1)->getBytesPerPixel() == 4)
   {
	  // create new numpy array object
      PyObject * numpyArray = PyArray_SimpleNew(2, dims, NPY_UINT32);

      // copy the data to the numpy array data buffer.
      memcpy(PyArray_DATA((PyArrayObject *) numpyArray), result, dims[0]*dims[1]*4);

      $result = numpyArray;
   }
   else
   {
      // don't know how to map
      // TODO: thow exception?
      $result = 0;
   }
}


%typemap(out) unsigned int*
{
   //Here we assume we are getting RGBA (32 bits).
   npy_intp dims[3];
   dims[0] = (arg1)->getImageHeight();
   dims[1] = (arg1)->getImageWidth();
   dims[2] = 3; // RGB
   unsigned numChannels = (arg1)->getNumberOfComponents();
   unsigned char * pyBuf;
   unsigned char * coreBuf = (unsigned char *) result;
   
   if ((arg1)->getBytesPerPixel() == 4 && numChannels == 1)
   {

	  // create new numpy array object
      PyObject * numpyArray = PyArray_SimpleNew(3, dims, NPY_UINT8);

	  // get a pointer to the data buffer
	  pyBuf = (unsigned char *) PyArray_DATA((PyArrayObject *) numpyArray);

	  // copy R,G,B but leave out A in RGBA to return a WxHx3-dimensional array

	  long pixelCount = dims[0] * dims[1];         
	  
	  for (long i=0; i<pixelCount; ++i)
	  {
	     *pyBuf++ = *coreBuf++; //R
	     *pyBuf++ = *coreBuf++; //G
	     *pyBuf++ = *coreBuf++; //B

	     ++coreBuf; // Skip the empty byte
	  }
	  
	  // Return the numpy array object

      $result = numpyArray;

   }
   else
   {
      // don't know how to map
      // TODO: thow exception?
      $result = 0;
   }
}



%{
#define SWIG_FILE_WITH_INIT
#include "../MMDevice/MMDeviceConstants.h"
#include "../MMCore/Error.h"
#include "../MMCore/Configuration.h"
#include "../MMDevice/ImageMetadata.h"
#include "../MMCore/MMEventCallback.h"
#include "../MMCore/MMCore.h"
%}

// Extend exception objects to return the exception object message in python.
// __str__ method gets printed in the traceback, so it should contain the core error message string.

%extend CMMError {
  std::string const message;
  
  std::string __getitem__(int n) {
	return $self->getMsg();
  }
  
  std::string __str__() {
    return $self->getMsg();
  }

}

%extend MetadataKeyError {
  std::string const message;
  
  std::string __getitem__(int n) {
	return $self->getMsg();
  }
  
  std::string __str__() {
    return $self->getMsg();
  }

}


%extend MetadataIndexError {
  std::string const message;
  
  std::string __getitem__(int n) {
	return $self->getMsg();
  }
  
  std::string __str__() {
    return $self->getMsg();
  }

}


%{
std::string const CMMError_message_get(CMMError * err) {
	return err->getMsg();
}

std::string const MetadataKeyError_message_get(MetadataKeyError * err) {
	return err->getMsg();
}

std::string const MetadataIndexError_message_get(MetadataIndexError * err) {
	return err->getMsg();
}

%}




// instantiate STL mappings
namespace std {
    %template(CharVector)   vector<char>;
    %template(LongVector)   vector<long>;
    %template(StrVector)    vector<string>;
    %template(pair_ss)      pair<string, string>;
    %template(StrMap)       map<string, string>;
}


%include "../MMDevice/MMDeviceConstants.h"
%include "../MMCore/Error.h"
%include "../MMCore/Configuration.h"
%include "../MMCore/MMCore.h"
%include "../MMDevice/ImageMetadata.h"
%include "../MMCore/MMEventCallback.h"
