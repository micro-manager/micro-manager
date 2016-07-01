///////////////////////////////////////////////////////////////////////////////
// FILE:          PeCon2000.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// see PeCon2000.cpp

#ifndef _PECON2000_H_
#define _PECON2000_H_

#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <sstream>
#include <map>
#include <vector>
#include <list>
#include <algorithm>

#include "pdl2000.h"


inline std::string formatVersion(DWORD version)
{
   std::string result;
   std::stringstream builder;
   builder << "00000000";
   builder << std::hex;
   builder << version;
   builder >> result;
   result = result.substr(result.size() - 8);
   return result.substr(0, 2) + std::string(".") + result.substr(2, 2) + std::string(".") + result.substr(4);
};


#define QUOTE(s) #s
#define ADD_MAP(mapper, identifier, message) mapper.add(identifier, QUOTE(identifier), message)

struct EnumMapper
{
   std::map<long, const char*> ToIdentifier;
   std::map<long, const char*> ToMessage;

   inline void add(long id, const char *identifier, const char *message)
   {
      ToIdentifier[id] = identifier;
      ToMessage[id] = message;
   }
};

#define MTYPE_NUMBER     0x0000000000000f00L
#define MTYPE_IDENTIFIER 0x000000000000f000L
#define MTYPE_MESSAGE    0x00000000000f0000L


inline std::string deviceInfoToString(PdlDeviceInfo *info)
{
   return std::string("Name: ") + std::string(info->name) + std::string(" Serial : ") + std::string(info->serial);
}

const double precision = 0.1;


class CPeCon2000HubDevice : public HubBase<CPeCon2000HubDevice>
{
public:
   CPeCon2000HubDevice();
   ~CPeCon2000HubDevice();
 
   int Initialize();
   int Shutdown();
   
   void GetName(char* pszName) const;
   bool Busy();
   
   int DetectInstalledDevices();
};


class CPeCon2000Device: public CGenericBase<CPeCon2000Device>
{
public:
   CPeCon2000Device(std::string name = "");
   ~CPeCon2000Device();

   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy();

   
   int OnSpecificSerial(MM::PropertyBase *pProp, MM::ActionType eAct);
   int OnReady(MM::PropertyBase *pProp, MM::ActionType eAct);

   int OnRetriesOnError(MM::PropertyBase *pProp, MM::ActionType eAct);

   int OnActualValue(MM::PropertyBase *pProp, MM::ActionType eAct, long data);
   int OnSetValue(MM::PropertyBase *pProp, MM::ActionType eAct, long data);
   int OnLoopControl(MM::PropertyBase *pProp, MM::ActionType eAct, long data);

   int OnControlMode(MM::PropertyBase *pProp, MM::ActionType eAct);

   int OnDeviceStatus(MM::PropertyBase *pProp, MM::ActionType eAct, long data);
   int OnChannelStatus(MM::PropertyBase *pProp, MM::ActionType eAct, long data);

private:
   bool checkDevice();

   bool checkError(PdlError error);

   int ready;

   long retries;
   bool errorLogged;
   bool retryErrorLogged;

   std::list<MM::MMTime> lastErrorOccurrences;

   std::string name;
   std::string specificSerial;

   std::string deviceIdentifierString;

   PdlHandle handle;

   EnumMapper DeviceStatusMapper;
   EnumMapper ChannelStatusMapper;
   EnumMapper ErrorMapper;
};


typedef MM::ActionEx<CPeCon2000Device> DeviceActionEx;
typedef MM::Action<CPeCon2000Device> DeviceAction;

#endif //_PECON2000_H_
