///////////////////////////////////////////////////////////////////////////////
// FILE:          Error.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Exception class for core errors
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 09/19/2005
//                Mark Tsuchida, 12/04/2013 (made chainable)

#ifndef _ERROR_H_
#define _ERROR_H_

#include "ErrorCodes.h"

#include <boost/lexical_cast.hpp>

#include <exception>
#include <memory>
#include <string>


/// Core error class. Exceptions thrown by the Core public API are of this type.
/**
 * Exceptions can be "chained" to express underlying causes of errors.
 *
 * There are no methods to modify error objects after construction. This is
 * intentional, to keep it easy to determine the source of information. Use
 * chaining if you want to augment error messages with higher-level
 * information.
 *
 * The main information contained in an instance is the error message and the
 * error code. The message is required and should be concise but should try to
 * provide complete information (including, e.g., parameter values) about the
 * error.
 *
 * Error codes are optional and are used to distinguish between well known
 * errors by calling code. They are only useful if it is important that the
 * calling code can determine the type of error and take appropriate action.
 *
 * Note: Although, strictly speaking, exception class constructors are not
 * supposed to throw, we relax this rule and ignore the possibility of a
 * std::bad_alloc. If memory is low enough that error message strings cannot be
 * copied, we're not going to get very far anyway.
 */
class CMMError : public std::exception
{
public:
   /// Error code type.
   typedef int Code;

   // Constructors: we consider msg to be a required argument.
   //
   // Both std::string versions and const char* versions are provided, so that
   // .c_str() is not necessary when you have a std::string and so that we
   // don't have to worry about segfaulting if a null pointer is passed (even
   // though that would be a bug).

   /// Construct with error message and optionally an error code.
   /**
    * code should not be MMERR_OK (0).
    */
   explicit CMMError(const std::string& msg, Code code = MMERR_GENERIC);

   /// Construct with error message and optionally an error code.
   /**
    * msg should not be null. code should not be MMERR_OK (0).
    */
   explicit CMMError(const char* msg, Code code = MMERR_GENERIC);

   /// Construct with an error code and underlying (chained/wrapped) error.
   /**
    * Use this form of the constructor when adding information and rethrowing
    * the exception.
    *
    * code should not be MMERR_OK (0).
    */
   CMMError(const std::string& msg, Code code, const CMMError& underlyingError);

   /// Construct with an error code and underlying (chained/wrapped) error.
   /**
    * Use this form of the constructor when adding information and rethrowing
    * the exception.
    *
    * msg should not be null. code should not be MMERR_OK (0).
    */
   CMMError(const char* msg, Code code, const CMMError& underlyingError);

   /// Construct with an underlying (chained/wrapped) error.
   /**
    * Use this form of the constructor when adding information and rethrowing
    * the exception.
    */
   CMMError(const std::string& msg, const CMMError& underlyingError);

   /// Construct with an underlying (chained/wrapped) error.
   /**
    * Use this form of the constructor when adding information and rethrowing
    * the exception.
    *
    * msg should not be null.
    */
   CMMError(const char* msg, const CMMError& underlyingError);

   /// Copy constructor (perform a deep copy).
   CMMError(const CMMError& other);

   virtual ~CMMError() throw() {}

   /// Implements std::exception interface.
   virtual const char* what() const throw() { return message_.c_str(); }

   /// Get the error message for this error.
   virtual std::string getMsg() const;

   /// Get a message containing the messages from all chained errors.
   virtual std::string getFullMsg() const;

   /// Get the error code for this error.
   virtual Code getCode() const { return code_; }

   /// Search the chain of underlying errors for the first specific error code.
   /**
    * The chained errors are searched in order and the first code that is not
    * MMERR_GENERIC (1) is returned. If none of the chained errors have a
    * specific code, MMERR_GENERIC is returned.
    */
   virtual Code getSpecificCode() const;

   /// Access the underlying error.
   /**
    * This is intended for code that wants to perform custom formatting or
    * analysis of the chained errors.
    *
    * The returned pointer is valid until the instance on which this method was
    * called is destroyed. If there is no underlying error (i.e. if this
    * instance is at the end of the chain), a null pointer is returned.
    */
   virtual const CMMError* getUnderlyingError() const;

private:
   // Prohibit assignment.
   CMMError& operator=(const CMMError&);

   std::string message_;
   Code code_;
   std::auto_ptr<CMMError> underlying_;
};

#endif //_ERROR_H_
