///////////////////////////////////////////////////////////////////////////////
// FILE:          MMDevice.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   The interface to the Micro-Manager devices. Defines the 
//                plugin API for all devices.
//
// NOTE:          This file is also used in the main control module MMCore.
//                Do not change it undless as a part of the MMCore module
//                revision. Discrepancy between this file and the one used to
//                build MMCore will cause a malfunction and likely a crash too.
// 
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
//
// CVS:           $Id$
//

///////////////////////////////////////////////////////////////////////////////
// Header version
// If any of the class declarations changes, the interface version
// must be incremented
#define DEVICE_INTERFACE_VERSION 16
///////////////////////////////////////////////////////////////////////////////

#ifndef _MMDEVICE_H_
#define _MMDEVICE_H_

#include "MMDeviceConstants.h"
#include <string>
#include <vector>


#define HDEVMODULE void*

namespace MM {

   // forward declaration for the MMCore callback class
   class Core;
   
   /**
    * Generic device interface.
    */
   class Device {
   public:
      Device() {}
      virtual ~Device() {}
 
      virtual unsigned GetNumberOfProperties() const = 0;
      virtual int GetProperty(const char* name, char* value) const = 0;  
      virtual int SetProperty(const char* name, const char* value) = 0;
      virtual bool GetPropertyName(unsigned idx, char* name) const = 0;
      virtual int GetPropertyReadOnly(const char* name, bool& readOnly) const = 0;
      virtual int GetPropertyInitStatus(const char* name, bool& preInit) const = 0;

      virtual unsigned GetNumberOfPropertyValues(const char* propertyName) const = 0;
      virtual bool GetPropertyValueAt(const char* propertyName, unsigned index, char* value) const = 0;
      virtual bool GetErrorText(int errorCode, char* errMessage) const = 0;
      virtual bool Busy() = 0;
      virtual double GetDelayMs() const = 0;
      virtual void SetDelayMs(double delay) = 0;

      // library handle management (for use only in the client code)
      virtual HDEVMODULE GetModuleHandle() const = 0;
      virtual void SetModuleHandle(HDEVMODULE hLibraryHandle) = 0;
      virtual void SetLabel(const char* label) = 0;
      virtual void GetLabel(char* name) const = 0;
      virtual void SetModuleName(const char* label) = 0;
      virtual void GetModuleName(char* name) const = 0;

      virtual int Initialize() = 0;
      virtual int Shutdown() = 0;
   
      virtual DeviceType GetType() const = 0;
      virtual void GetName(char* name) const = 0;
      virtual void SetCallback(Core* callback) = 0;
   };

   /** 
    * Camera API
    */
   class Camera : public Device {
   public:
      Camera() {}
      ~Camera() {}

      DeviceType GetType() const {return Type;}
      static const DeviceType Type = CameraDevice;

      // Camera API
      virtual int SnapImage() = 0;
      virtual const unsigned char* GetImageBuffer() = 0;
      virtual long GetImageBufferSize()const = 0;
      virtual unsigned GetImageWidth() const = 0;
      virtual unsigned GetImageHeight() const = 0;
      virtual unsigned GetImageBytesPerPixel() const = 0;
      virtual unsigned GetBitDepth() const = 0;
      virtual void SetExposure(double exp_ms) = 0;
      virtual double GetExposure() const = 0;
      virtual int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize) = 0; 
      virtual int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize) = 0;
      virtual int ClearROI() = 0;
   };

   /** 
    * Shutter API
    */
   class Shutter : public Device
   {
   public:
      Shutter() {}
      ~Shutter() {}
   
      // Device API
      virtual DeviceType GetType() const {return Type;}
      static const DeviceType Type = ShutterDevice;
   
      // Shutter API
      virtual int SetOpen(bool open = true) = 0;
      virtual int GetOpen(bool& open) = 0;
      virtual int Fire(double deltaT) = 0;
   };

   /** 
    * Single axis stage API
    */
   class Stage : public Device
   {
   public:
      Stage() {}
      ~Stage() {}
   
      // Device API
      virtual DeviceType GetType() const {return Type;}
      static const DeviceType Type = StageDevice;
   
      // Stage API
      virtual int SetPositionUm(double pos) = 0;
      virtual int GetPositionUm(double& pos) = 0;
      virtual int SetPositionSteps(long steps) = 0;
      virtual int GetPositionSteps(long& steps) = 0;
      virtual int SetOrigin() = 0;
      virtual int GetLimits(double& lower, double& upper) = 0;
   };

   /** 
    * Dual axis stage API
    */
   class XYStage : public Device
   {
   public:
      XYStage() {}
      ~XYStage() {}

      // Device API
      virtual DeviceType GetType() const {return Type;}
      static const DeviceType Type = XYStageDevice;

      // XYStage API
      virtual int SetPositionUm(double x, double y) = 0;
      virtual int GetPositionUm(double& x, double& y) = 0;
      virtual int SetPositionSteps(long x, long y) = 0;
      virtual int GetPositionSteps(long& x, long& y) = 0;
      virtual int Home() = 0;
      virtual int Stop() = 0;
	  virtual int SetOrigin() = 0;//jizhen, 4/12/2007
      virtual int GetLimits(double& xMin, double& xMax, double& yMin, double& yMax) = 0;
   };

   /**
    * State device API, e.g. filter wheel, objective turret, etc.
    */
   class State : public Device
   {
   public:
      State() {}
      ~State() {}
      
      // MMDevice API
      virtual DeviceType GetType() const {return Type;}
      static const DeviceType Type = StateDevice;
      
      // MMStateDevice API
      virtual int SetPosition(long pos) = 0;
      virtual int SetPosition(const char* label) = 0;
      virtual int GetPosition(long& pos) const = 0;
      virtual int GetPosition(char* label) const = 0;
      virtual int GetPositionLabel(long pos, char* label) const = 0;
      virtual int GetLabelPosition(const char* label, long& pos) const = 0;
      virtual int SetPositionLabel(long pos, const char* label) = 0;
      virtual unsigned long GetNumberOfPositions() const = 0;
   };

   /**
    * Serial port API.
    */
   class Serial : public Device
   {
   public:
      Serial() {}
      ~Serial() {}
      
      // MMDevice API
      virtual DeviceType GetType() const {return Type;}
      static const DeviceType Type = SerialDevice;
      
      // Serial API
      virtual int SetCommand(const char* command, const char* term) = 0;
      virtual int GetAnswer(char* txt, unsigned maxChars, const char* term) = 0;
      virtual int Write(const char* buf, unsigned long bufLen) = 0;
      virtual int Read(char* buf, unsigned long bufLen, unsigned long& charsRead) = 0;
      virtual int Purge() = 0; 
   };

   /**
    * Auto-focus device API.
    */
   class AutoFocus : public Device
   {
   public:
      AutoFocus() {}
      ~AutoFocus() {}
      
      // MMDevice API
      virtual DeviceType GetType() const {return AutoFocusDevice;}
      static const DeviceType Type = AutoFocusDevice;

      // AutoFocus API
      virtual int SetContinuousFocusing(bool state) = 0;
      virtual int GetContinuousFocusing(bool& state) = 0;
      virtual int Focus() = 0;
      virtual int GetFocusScore(double& score) = 0;
   };

   /**
    * Callback API to the core control module.
    * Devices use this abstract interface to use services of the control client.
    */
   class Core
   {
   public:
      Core() {}
      virtual ~Core() {}

      virtual int LogMessage(const Device* caller, const char* msg, bool debugOnly) = 0;
      virtual Device* GetDevice(const Device* caller, const char* label) = 0;
      virtual int SetSerialCommand(const Device* caller, const char* portName, const char* command, const char* term) = 0;
      virtual int GetSerialAnswer(const Device* caller, const char* portName, unsigned long ansLength, char* answer, const char* term) = 0;
      virtual int WriteToSerial(const Device* caller, const char* port, const char* buf, unsigned long length) = 0;
      virtual int ReadFromSerial(const Device* caller, const char* port, char* buf, unsigned long length, unsigned long& read) = 0;
      virtual int PurgeSerial(const Device* caller, const char* portName) = 0;
      virtual int OnStatusChanged(const Device* caller) = 0;
      virtual int OnFinished(const Device* caller) = 0;
      virtual long GetClockTicksUs(const Device* caller) = 0;
   };

} // namespace MM

#endif //_MMDEVICE_H_
