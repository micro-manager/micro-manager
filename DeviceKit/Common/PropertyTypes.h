// PropertyTypes.h
#pragma once;

#include <string>
#include <sstream>
#include "../../MMDevice/MMDevice.h"

inline std::string getPropertyTypeVerbose(MM::PropertyType t)
{
   switch (t)
   {
      case (MM::Float):
         return std::string("Float");
      case (MM::Integer):
         return std::string("Integer");
      case (MM::String):
         return std::string("String");
   }

   // we don't know this property so we'll just use the id
   std::ostringstream os;
   os << "Property_type_" << t;
   return os.str();
}