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

#include <string>
#include <sstream>
#include "ErrorCodes.h"


///////////////////////////////////////////////////////////////////////////////
// CMMError
// --------
// Micro-Manager error class, used to create exception objects
// 
class CMMError
{
public:
   CMMError(const char* msg, int code) :
      errCode_(code),
      specificMsg_(msg)
      {}

   CMMError(const char* specificMsg, const char* coreMsg, int code) :
      errCode_(code),
      specificMsg_(specificMsg),
      coreMsg_(coreMsg)
      {}

   CMMError(int code) :
      errCode_(code) {}

   virtual ~CMMError() {}

   virtual std::string getMsg()
   {
      std::ostringstream msg;
      if (!specificMsg_.empty()) {
         msg << specificMsg_ << std::endl;
      } else {
         msg << "Error code: " << errCode_ << std::endl;
      }
      msg << coreMsg_;
      return msg.str();
   }
   int getCode() {return errCode_;}
   void setCoreMsg(const char* msg) {coreMsg_ = msg;}

   virtual std::string getCoreMsg()
   {
      return coreMsg_;
   }

private:
   long errCode_;
   std::string specificMsg_;
   std::string coreMsg_;
};

#endif //_ERROR_H_
