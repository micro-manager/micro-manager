///////////////////////////////////////////////////////////////////////////////
// FILE:          MMEventCallback.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Callback class used to send notifications from MMCore to
//                higher levels (such as GUI)
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 12/10/2007
// COPYRIGHT:     University of California, San Francisco, 2007
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
// CVS:           $Id: Configuration.h 2 2007-02-27 23:33:17Z nenad $
//
#pragma once
#include <iostream>

class MMEventCallback
{
public:
   MMEventCallback() {std::cout << "MMEventCallback()" << std::endl; }
   virtual ~MMEventCallback() {std::cout << "~MMEventCallback()" << std::endl; }

   virtual void onPropertiesChanged()
   {
      std::cout << "onPropertiesChanged()" << std:: endl; 
   }

};
