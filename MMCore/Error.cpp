///////////////////////////////////////////////////////////////////////////////
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
// AUTHOR:        Nenad Amodaj (original header-only version)
//                Mark Tsuchida, 12/04/2013 (made chainable)
//
// NOTE:          API documentation is in the header file.

#include "Error.h"


CMMError::CMMError(const std::string& msg, Code code) :
   message_(msg),
   code_(code),
   underlying_(0)
{}


CMMError::CMMError(const char* msg, Code code) :
   message_(msg ? msg : "(null message)"),
   code_(code),
   underlying_(0)
{}


CMMError::CMMError(const std::string& msg, Code code, const CMMError& underlyingError) :
   message_(msg),
   code_(code),
   underlying_(new CMMError(underlyingError))
{}


CMMError::CMMError(const char* msg, Code code, const CMMError& underlyingError) :
   message_(msg ? msg : "(null message)"),
   code_(code),
   underlying_(new CMMError(underlyingError))
{}


CMMError::CMMError(const std::string& msg, const CMMError& underlyingError) :
   message_(msg),
   code_(MMERR_GENERIC),
   underlying_(new CMMError(underlyingError))
{}


CMMError::CMMError(const char* msg, const CMMError& underlyingError) :
   message_(msg ? msg : "(null message)"),
   code_(MMERR_GENERIC),
   underlying_(new CMMError(underlyingError))
{}


CMMError::CMMError(const CMMError& other) :
   message_(other.message_),
   code_(other.code_),
   underlying_(0)
{
   if (other.getUnderlyingError())
      underlying_.reset(new CMMError(*(other.getUnderlyingError())));
}


std::string
CMMError::getMsg() const
{
   if (message_.empty())
      return "Error (code " + boost::lexical_cast<std::string>(code_) + ")";
   return message_;
}


std::string
CMMError::getFullMsg() const
{
   if (getUnderlyingError())
      return getMsg() + " [ " + underlying_->getFullMsg() + " ]";
   return getMsg();
}


CMMError::Code
CMMError::getSpecificCode() const
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


const CMMError*
CMMError::getUnderlyingError() const
{
   return underlying_.get();
}
