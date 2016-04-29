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

#include "IIDCInterface.h"

#include "IIDCCamera.h"
#include "IIDCError.h"
#include "IIDCUtils.h"

#include <boost/make_shared.hpp>

#include <string>
#include <vector>


namespace IIDC {

Interface::Interface()
{
   Construct();
}


Interface::Interface(boost::function<void (const std::string&, bool)> logger) :
   logger_(logger)
{
   for (int t = DC1394_LOG_MIN; t <= DC1394_LOG_MAX; ++t)
   {
      dc1394error_t err;
      err = dc1394_log_register_handler(static_cast<dc1394log_t>(t),
            &Interface::LogLibDC1394Message, this);
   }
   if (logger)
      logger("Registered libdc1394 logger", true);

   Construct();
}


void
Interface::Construct()
{
   libdc1394context_ = dc1394_new();
   if (!libdc1394context_)
      throw Error("Cannot create libdc1394 context");
}


Interface::~Interface()
{
   dc1394_free(libdc1394context_);
}


std::vector<std::string>
Interface::GetCameraIDs()
{
   dc1394error_t err;
   dc1394camera_list_t* cameraList;
   err = dc1394_camera_enumerate(libdc1394context_, &cameraList);
   if (err != DC1394_SUCCESS)
      throw Error(err, "Cannot get list of available cameras");
   std::vector<std::string> idList;
   idList.reserve(cameraList->num);
   for (uint32_t i = 0; i < cameraList->num; ++i)
   {
      dc1394camera_id_t* id = &cameraList->ids[i];
      idList.push_back(detail::CameraIdToString(id));
   }
   dc1394_camera_free_list(cameraList);
   return idList;
}


boost::shared_ptr<Camera>
Interface::NewCamera(const std::string& idString)
{
   dc1394camera_id_t id;
   detail::StringToCameraId(idString, &id);
   return boost::make_shared<Camera>(shared_from_this(), id.guid, id.unit);
}


void
Interface::RemoveLogger()
{
   if (!logger_)
      return;
   logger_("Disabling further libdc1394 logging", true);
   logger_ = boost::function<void (const std::string&, bool)>();
}


void
Interface::LogLibDC1394Message(dc1394log_t logType, const char* message,
      void* user)
{
   if (!message || !user)
      return;

   Interface* self = reinterpret_cast<Interface*>(user);
   if (!self->logger_)
      return;

   bool isDebug = false;
   const char* prefix = "[libdc1394    ] ";
   switch (logType)
   {
      case DC1394_LOG_ERROR:
         prefix = "[libdc1394 ERR] ";
         break;
      case DC1394_LOG_WARNING:
         prefix = "[libdc1394 WRN] ";
         break;
      case DC1394_LOG_DEBUG:
         prefix = "[libdc1394 DBG] ";
         isDebug = true;
         break;
   }

   self->logger_(prefix + std::string(message), isDebug);
}

} // namespace IIDC
