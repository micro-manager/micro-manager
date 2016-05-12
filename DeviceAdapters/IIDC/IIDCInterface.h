// Micro-Manager IIDC Device Adapter
//
// AUTHOR:        Mark A. Tsuchida
//
// COPYRIGHT:     2014-2015, Regents of the University of California
//                2016, Open Imaging, Inc.
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#pragma once

#include <dc1394/dc1394.h>

#ifdef _MSC_VER
#undef restrict
#endif

#include <boost/enable_shared_from_this.hpp>
#include <boost/function.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/utility.hpp>
#include <string>
#include <vector>


namespace IIDC {

class Camera;


class Interface : boost::noncopyable, public boost::enable_shared_from_this<Interface>
{
   dc1394_t* libdc1394context_;
   boost::function<void (const std::string&, bool)> logger_;

public:
   Interface();
   Interface(boost::function<void (const std::string&, bool)> logger);
   ~Interface();

   dc1394_t* GetLibDC1394Context() { return libdc1394context_; }

   std::vector<std::string> GetCameraIDs();
   boost::shared_ptr<Camera> NewCamera(const std::string& idString);

   // Needed to cope with MMCore logging, which is tied to device lifetime.
   void RemoveLogger();

private:
   void Construct();
   static void LogLibDC1394Message(dc1394log_t logType, const char* message,
         void* user);
};

} // namespace IIDC
