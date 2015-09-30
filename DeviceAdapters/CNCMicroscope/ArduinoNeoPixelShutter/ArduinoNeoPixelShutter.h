/*
Copyright 2015 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#ifndef _ArduinoNeoPixel_H_
#define _ArduinoNeoPixel_H_

#include "MMDevice.h"
#include "DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104
#define ERR_BOARD_NOT_FOUND 105
#define ERR_PORT_OPEN_FAILED 106
#define ERR_COMMUNICATION 107
#define ERR_NO_PORT_SET 108
#define ERR_VERSION_MISMATCH 109

class ArduinoInputMonitorThread;

class CArduinoNeoPixelHub : public HubBase<CArduinoNeoPixelHub>  
{
public:
   CArduinoNeoPixelHub();
   ~CArduinoNeoPixelHub();

   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   MM::DeviceDetectionStatus DetectDevice(void);
   int DetectInstalledDevices();

   // property handlers
   int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnVersion(MM::PropertyBase* pPropt, MM::ActionType eAct);

   // custom interface for child devices
   bool IsPortAvailable() {return portAvailable_;}

   int PurgeComPortH() {return PurgeComPort(port_.c_str());}
   int WriteToComPortH(const unsigned char* command, size_t len)
   {
      return WriteToComPort(port_.c_str(), command, static_cast<unsigned>(len));
   }
   int ReadFromComPortH(unsigned char* answer, unsigned maxLen, unsigned long& bytesRead)
   {
      return ReadFromComPort(port_.c_str(), answer, maxLen, bytesRead);
   }
   static MMThreadLock& GetLock() {return lock_;}
   void SetShutterState(unsigned state) {shutterState_ = state;}
   unsigned GetShutterState() {return shutterState_;}
   void SetIntensity(int intensity) {intensity_ = intensity;}
   int GetIntensity() {return intensity_;}
   void SetRedBrightness(int red) {red_ = red;}
   int GetRedBrightness() {return red_;}
   void SetGreenBrightness(int green) {green_ = green;}
   int GetGreenBrightness() {return green_;}
   void SetBlueBrightness(int blue) {blue_ = blue;}
   int GetBlueBrightness() {return blue_;}
   void SetMulti(int multi) {multi_ = multi;}
   int GetMulti() {return multi_;}

private:
   int GetControllerVersion(int&);
   std::string port_;
   bool initialized_;
   bool portAvailable_;
   int version_;
   static MMThreadLock lock_;
   unsigned shutterState_;
   int intensity_;
   int red_;
   int blue_;
   int green_;
   int multi_;
};

class CArduinoNeoPixelShutter : public CShutterBase<CArduinoNeoPixelShutter>  
{
public:
   CArduinoNeoPixelShutter();
   ~CArduinoNeoPixelShutter();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   
   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRedBrightness(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGreenBrightness(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBlueBrightness(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMulti(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int WriteToPort(long lnValue);
   MM::MMTime changedTime_;
   bool initialized_;
   std::string name_;
};

#endif //_ArduinoNeoPixel_H_
