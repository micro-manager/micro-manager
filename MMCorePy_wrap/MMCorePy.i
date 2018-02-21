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
%feature("autodoc", "3");

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
#define NPY_NO_DEPRECATED_API NPY_1_7_API_VERSION
#include "numpy/arrayobject.h"
#include "string.h"
%}

%typemap(out) void*
{
   npy_intp dims[2];
   dims[0] = (arg1)->getImageHeight();
   dims[1] = (arg1)->getImageWidth();
   npy_intp pixelCount = dims[0] * dims[1];

   if ((arg1)->getBytesPerPixel() == 1)
   {
      PyObject * numpyArray = PyArray_SimpleNew(2, dims, NPY_UINT8);
      memcpy(PyArray_DATA((PyArrayObject *) numpyArray), result, pixelCount);
      $result = numpyArray;
   }
   else if ((arg1)->getBytesPerPixel() == 2)
   {
      PyObject * numpyArray = PyArray_SimpleNew(2, dims, NPY_UINT16);
      memcpy(PyArray_DATA((PyArrayObject *) numpyArray), result, pixelCount * 2);
      $result = numpyArray;
   }
   else if ((arg1)->getBytesPerPixel() == 4)
   {
      PyObject * numpyArray = PyArray_SimpleNew(2, dims, NPY_UINT32);
      memcpy(PyArray_DATA((PyArrayObject *) numpyArray), result, pixelCount * 4);
      $result = numpyArray;
   }
   else if ((arg1)->getBytesPerPixel() == 8)
   {
      PyObject * numpyArray = PyArray_SimpleNew(2, dims, NPY_UINT64);
      memcpy(PyArray_DATA((PyArrayObject *) numpyArray), result, pixelCount * 8);
      $result = numpyArray;
   }
   else
   {
      // don't know how to map
      // TODO: thow exception?
      // XXX Must do something, as returning NULL without setting error results
      // in an opaque error.
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

/* tell SWIG to treat char ** as a list of strings */
/* From https://stackoverflow.com/questions/3494598/passing-a-list-of-strings-to-from-python-ctypes-to-c-function-expecting-char */
/* XXX No need to freearg the vector, right? */
%typemap(in) std::vector<unsigned char *> {
    // check if is a list
    if(PyList_Check($input))
    {
        long expectedLength = (arg1)->getSLMWidth(arg2) * (arg1)->getSLMHeight(arg2);

        Py_ssize_t size = PyList_Size($input);
        std::vector<unsigned char*> inputVector;

        for(Py_ssize_t i = 0; i < size; i++)
        {
            //printf("Pushing %d\n",  i);
            PyObject * o = PyList_GetItem($input, i);
            if(PyString_Check(o))
            {
                if (PyString_Size(o) != expectedLength)
                {
                    PyErr_SetString(PyExc_TypeError, "One of the Image strings is the wrong length for this SLM.");
                    return NULL;
                }
   
                inputVector.push_back((unsigned char *)PyString_AsString(o));
            }
            else
            {
                PyErr_SetString(PyExc_TypeError, "list must contain strings");
                return NULL;
            }
        }
        $1 = inputVector;
    }
    else
    {
        PyErr_SetString(PyExc_TypeError, "not a list");
        return NULL;
    }
}

%rename(setSLMImage) setSLMImage_pywrap;
%apply (char *STRING, int LENGTH) { (char *pixels, int receivedLength) };
%extend CMMCore {
PyObject *setSLMImage_pywrap(const char* slmLabel, char *pixels, int receivedLength)
{
   long expectedLength = self->getSLMWidth(slmLabel) * self->getSLMHeight(slmLabel);
   //printf("expected: %d -- received: %d\n",expectedLength,receivedLength);

   if (receivedLength == expectedLength)
   {
      self->setSLMImage(slmLabel, (unsigned char *)pixels);
   }
   else if (receivedLength == 4*expectedLength)
   {
      self->setSLMImage(slmLabel, (imgRGB32)pixels);
   }
   else
   {
      PyErr_SetString(PyExc_TypeError, "Image dimensions are wrong for this SLM.");
      return (PyObject *) NULL;
   }
   return PyInt_FromLong(0);
}
}
%ignore setSLMImage;

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
  std::string __getitem__(int n) {
	return $self->getFullMsg();
  }
  
  std::string __str__() {
    return $self->getFullMsg();
  }
}

%extend MetadataKeyError {
  std::string __getitem__(int n) {
	return $self->getMsg();
  }
  
  std::string __str__() {
    return $self->getMsg();
  }
}


%extend MetadataIndexError {
  std::string __getitem__(int n) {
	return $self->getMsg();
  }
  
  std::string __str__() {
    return $self->getMsg();
  }
}


// instantiate STL mappings
namespace std {
    %template(CharVector)   vector<char>;
    %template(LongVector)   vector<long>;
    %template(DoubleVector) vector<double>;
    %template(StrVector)    vector<string>;
    %template(pair_ss)      pair<string, string>;
    %template(StrMap)       map<string, string>;
}


// output arguments
%apply double &OUTPUT { double &x_stage };
%apply double &OUTPUT { double &y_stage };
%apply int &OUTPUT { int &x };
%apply int &OUTPUT { int &y };
%apply int &OUTPUT { int &xSize };
%apply int &OUTPUT { int &ySize };


%include "../MMDevice/MMDeviceConstants.h"
%include "../MMCore/Error.h"
%include "../MMCore/Configuration.h"
%include "../MMCore/MMCore.h"
%include "../MMDevice/ImageMetadata.h"
%include "../MMCore/MMEventCallback.h"
