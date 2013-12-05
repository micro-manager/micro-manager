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
// 
// CVS:           $Id$
//

#ifndef _ERROR_H_
#define _ERROR_H_

#include "ErrorCodes.h"

#include <boost/lexical_cast.hpp>

#include <exception>
#include <memory>
#include <string>


/**
 * Core error class. Exceptions thrown by the Core public API are of this type.
 *
 * Exceptions can be "chained" to express underlying causes of errors.
 *
 * There are no methods to modify error objects. This is intentional, to keep
 * it easy to determine the source of information. Use chaining to augment
 * error messages with higher-level information.
 *
 * Error codes are used to distinguish between well known errors by calling
 * code.
 *
 * Although, strictly speaking, exception class constructors are not supposed
 * to throw, we relax this rule and ignore the possibility of a std::bad_alloc.
 * If memory is low enough that error message strings cannot be copied, we're
 * not going to get very far anyway.
 */
class CMMError : public std::exception
{
public:
   // Constructors: we consider msg to be a required argument.
   //
   // Both std::string versions and const char* versions are provided, so that
   // .c_str() is not necessary when you have a std::string and so that we
   // don't have to worry about segfaulting if a null pointer is passed (even
   // though that would be a bug).

   explicit CMMError(const std::string& msg, int code = MMERR_GENERIC) :
      message_(msg),
      code_(code),
      underlying_(0)
   {}

   explicit CMMError(const char* msg, int code = MMERR_GENERIC) :
      message_(msg ? msg : "(null message)"),
      code_(code),
      underlying_(0)
   {}

   CMMError(const std::string& msg, int code, const CMMError& underlyingError) :
      message_(msg),
      code_(code),
      underlying_(new CMMError(underlyingError))
   {}

   CMMError(const char* msg, int code, const CMMError& underlyingError) :
      message_(msg ? msg : "(null message)"),
      code_(code),
      underlying_(new CMMError(underlyingError))
   {}

   // Copy constructor (do a deep copy)
   CMMError(const CMMError& other) :
      message_(other.message_),
      code_(other.code_),
      underlying_(0)
   {
      if (other.getUnderlyingError())
         underlying_.reset(new CMMError(*(other.getUnderlyingError())));
   }

   CMMError& operator=(const CMMError& rhs)
   {
      // No attempt made at exception safety (if bad_alloc, so be it).
      message_ = rhs.message_;
      code_ = rhs.code_;
      if (rhs.getUnderlyingError())
         underlying_.reset(new CMMError(*(rhs.getUnderlyingError())));
      else
         underlying_.reset();
      return *this;
   }

   virtual ~CMMError() {}

   virtual const char* what() const { return message_.c_str(); }

   virtual std::string getMsg() const
   {
      if (message_.empty())
         return "Error (code " + boost::lexical_cast<std::string>(code_) + ")";
      return message_;
   }

   virtual std::string getFullMsg() const
   {
      if (getUnderlyingError())
         return getMsg() + " [ " + underlying_->getFullMsg() + " ]";
      return getMsg();
   }

   virtual int getCode() const { return code_; }

   // Search underlying error chain for the first specific error code.
   virtual int getSpecificCode() const
   {
      if (code_ == MMERR_OK || code_ == MMERR_GENERIC)
      {
         if (getUnderlyingError())
            return getUnderlyingError()->getSpecificCode();
         else
            return MMERR_GENERIC;
      }
      return code_;
   }

   // Access the underlying error, if any. Returned pointer is valid until the
   // instance from which it was obtained is destroyed.
   virtual const CMMError* getUnderlyingError() const
   { return underlying_.get(); }

private:
   std::string message_;
   int code_;
   std::auto_ptr<CMMError> underlying_;
};

#endif //_ERROR_H_
