// DeviceTypes.h
#pragma once;

#include <string>
#include <sstream>
#include "../../MMDevice/MMDevice.h"

inline std::string getDeviceTypeVerbose(MM::DeviceType t)
{
   switch (t)
   {
      case (MM::CameraDevice):
         return std::string("Camera");
      case (MM::CoreDevice):
         return std::string("Core");
      case (MM::AutoFocusDevice):
         return std::string("Autofocus");
      case (MM::CommandDispatchDevice):
         return std::string("CommandDispatch");
      case (MM::HubDevice):
         return std::string("Hub");
      case (MM::GenericDevice):
         return std::string("Generic");
      case (MM::ImageProcessorDevice):
         return std::string("ImageProcessor");
      case (MM::ImageStreamerDevice):
         return std::string("ImageStreamer");
      case (MM::ProgrammableIODevice):
         return std::string("ProgrammableIO");
      case (MM::SerialDevice):
         return std::string("SerialPort");
      case (MM::ShutterDevice):
         return std::string("Shutter");
      case (MM::SignalIODevice):
         return std::string("SignalIO");
      case (MM::SLMDevice):
         return std::string("SLM");
      case (MM::StageDevice):
         return std::string("Stage");
      case (MM::StateDevice):
         return std::string("State");
      case (MM::XYStageDevice):
         return std::string("XYStage");
   }

   // we don't know this device so we'll just use the id
   std::ostringstream os;
   os << "Device_type_" << t;
   return os.str();
}