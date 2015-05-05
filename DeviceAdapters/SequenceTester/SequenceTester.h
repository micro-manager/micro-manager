// Mock device adapter for testing of device sequencing
//
// Copyright (C) 2014 University of California, San Francisco.
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//
// Author: Mark Tsuchida

#pragma once

#include "SettingLogger.h"

#include "InterDevice.h"
#include "LoggedSetting.h"
#include "TriggerInput.h"

#include "DeviceBase.h"

#include <boost/shared_ptr.hpp>
#include <boost/signals2.hpp>
#include <boost/thread.hpp>
#include <boost/unordered_map.hpp>
#include <boost/weak_ptr.hpp>
#include <string>
#include <vector>


// Common base class for all devices. Goes in between TDeviceBase and
// UConcreteDevice in the inheritance graph.
template <template <class> class TDeviceBase, class UConcreteDevice>
class TesterBase : public TDeviceBase<UConcreteDevice>, public InterDevice
{
public:
   typedef TesterBase Self;
   typedef TDeviceBase<UConcreteDevice> Super;

   TesterBase(const std::string& name) : InterDevice(name) {}
   virtual ~TesterBase() {}

   virtual void GetName(char* name) const;
   virtual int Initialize();
   virtual int Shutdown();
   virtual bool Busy();

   // Must be called after setting hub if overriding Initialize()
   int CommonHubPeripheralInitialize();
   // Must be called before hub becomes unavailable if overriding Shutdown()
   void CommonHubPeripheralShutdown();

   virtual CountDownSetting::Ptr GetBusySetting() { return busySetting_; }

protected:
   void CreateOnOffProperty(const std::string& name,
         BoolSetting::Ptr setting);
   void CreateYesNoProperty(const std::string& name,
         BoolSetting::Ptr setting);
   void CreateOneZeroProperty(const std::string& name,
         BoolSetting::Ptr setting);
   void CreateIntegerProperty(const std::string& name,
         IntegerSetting::Ptr setting);
   void CreateFloatProperty(const std::string& name,
         FloatSetting::Ptr setting);
   void CreateStringProperty(const std::string& name,
         StringSetting::Ptr setting);

private:
   CountDownSetting::Ptr busySetting_;
};


// This device adapter is specifically designed to be used in parallel testing
// within the same process, using multiple MMCore instances. Since there will
// be only one copy of the device adapter module (library), we must not use any
// static data. All state (e.g. SettingLogger) shared between devices must be
// owned by the hub instance.
class TesterHub : public TesterBase<HubBase, TesterHub>
{
   // Synchronizes access to the hub and all devices attached to it. Must be
   // locked during every call from the Core (except for the ones that do not
   // access or modify state) _and_ when reading the current state from the
   // camera's sequence acquisition thread. (There is not much point in having
   // finer-grained locking because the Core already synchronizes access to the
   // device adapter. However, we do need this lock to be per-hub so that
   // access from different Core instances can run concurrently.)
   mutable boost::recursive_mutex hubGlobalMutex_;

   SettingLogger logger_;

   boost::unordered_map< std::string, boost::weak_ptr<InterDevice> > devices_;

public:
   typedef TesterHub Self;
   typedef TesterBase< ::HubBase, TesterHub > Super;

   TesterHub(const std::string& name);

   virtual int Initialize();
   virtual int Shutdown();

   virtual int DetectInstalledDevices();

   typedef boost::unique_lock<boost::recursive_mutex> Guard;
   Guard LockGlobalMutex() const { return Guard(hubGlobalMutex_); }

   boost::shared_ptr<TesterHub> GetSharedPtr()
   { return boost::static_pointer_cast<TesterHub>(shared_from_this()); }
   virtual SettingLogger* GetLogger() { return &logger_; }

   int RegisterDevice(const std::string& name, InterDevice::Ptr device);
   void UnregisterDevice(const std::string& name);
   InterDevice::Ptr FindPeerDevice(const std::string& name);
};


class TesterCamera : public TesterBase<CCameraBase, TesterCamera>
{
   typedef TesterCamera Self;
   typedef TesterBase< ::CCameraBase, TesterCamera > Super;

public:
   TesterCamera(const std::string& name);
   virtual ~TesterCamera();

   virtual int Initialize();

   virtual int SnapImage();
   virtual const unsigned char* GetImageBuffer();

   virtual long GetImageBufferSize() const;
   virtual unsigned GetImageWidth() const;
   virtual unsigned GetImageHeight() const;
   virtual unsigned GetImageBytesPerPixel() const { return 1; }
   virtual unsigned GetBitDepth() const { return 8; }

   virtual int GetBinning() const;
   virtual int SetBinning(int binSize);
   virtual void SetExposure(double exposureMs);
   virtual double GetExposure() const;
   virtual int SetROI(unsigned x, unsigned y, unsigned w, unsigned h);
   virtual int GetROI(unsigned& x, unsigned& y, unsigned& w, unsigned& h);
   virtual int ClearROI() { return DEVICE_OK; }

   virtual int StartSequenceAcquisition(long count, double intervalMs,
         bool stopOnOverflow);
   virtual int StartSequenceAcquisition(double intervalMs);
   virtual int StopSequenceAcquisition();
   virtual int PrepareSequenceAcquisition() { return DEVICE_OK; }
   virtual bool IsCapturing();
   virtual int IsExposureSequenceable(bool& f) const
   { f = false; return DEVICE_OK; }

private:
   // Must be called with hub global mutex held.
   // Returned pointer should be delete[]d by caller.
   const unsigned char* GenerateLogImage(bool isSequenceImage,
         size_t cumulativeNr, size_t frameNr = 0);

   int StartSequenceAcquisitionImpl(bool finite, long count,
         bool stopOnOverflow);

   void SendSequence(bool finite, long count, bool stopOnOverflow);

private:
   bool produceHumanReadableImages_;
   long imageWidth_;
   long imageHeight_;

   size_t nextSerialNr_;
   size_t nextSnapImageNr_;
   size_t nextSequenceImageNr_;

   const unsigned char* snapImage_;

   // Guards stopSequence_. Always acquire after acquiring the hub global mutex
   // (if acquiring both).
   boost::mutex sequenceMutex_;

   bool stopSequence_; // Guarded by sequenceMutex_

   // Note: boost::future in more recent versions
   boost::unique_future<void> sequenceFuture_;
   boost::thread sequenceThread_;

   FloatSetting::Ptr exposureSetting_;
   IntegerSetting::Ptr binningSetting_;

   EdgeTriggerSignal exposureStartEdgeTrigger_;
   EdgeTriggerSignal exposureStopEdgeTrigger_;
};


class TesterShutter : public TesterBase<CShutterBase, TesterShutter>
{
   typedef TesterShutter Self;
   typedef TesterBase< ::CShutterBase, TesterShutter > Super;

public:
   TesterShutter(const std::string& name) : Super(name) {}

   virtual int Initialize();

   virtual int SetOpen(bool open);
   virtual int GetOpen(bool& open);
   virtual int Fire(double) { return DEVICE_UNSUPPORTED_COMMAND; }

private:
   BoolSetting::Ptr shutterOpen_;
};


class TesterXYStage : public TesterBase<CXYStageBase, TesterXYStage>
{
   typedef TesterXYStage Self;
   typedef TesterBase< ::CXYStageBase, TesterXYStage > Super;

   static const long stepsPerUm = 10;

public:
   TesterXYStage(const std::string& name) : Super(name) {}

   virtual int Initialize();

   virtual int SetPositionSteps(long x, long y);
   virtual int GetPositionSteps(long& x, long& y);
   virtual int Home();
   virtual int Stop();
   virtual int SetOrigin();
   virtual int SetXOrigin();
   virtual int SetYOrigin();
   virtual int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   virtual double GetStepSizeXUm() { return 1.0 / stepsPerUm; }
   virtual double GetStepSizeYUm() { return 1.0 / stepsPerUm; }
   virtual int IsXYStageSequenceable(bool& isSequenceable) const
   { isSequenceable = false; return DEVICE_OK; }

private:
   IntegerSetting::Ptr xPositionSteps_;
   IntegerSetting::Ptr yPositionSteps_;
   OneShotSetting::Ptr home_;
   OneShotSetting::Ptr stop_;
   OneShotSetting::Ptr setOrigin_;
   OneShotSetting::Ptr setXOrigin_;
   OneShotSetting::Ptr setYOrigin_;
};


template <class TConcreteStage, long UStepsPerMicrometer = 1>
class Tester1DStageBase : public TesterBase<CStageBase, TConcreteStage>
{
   typedef TesterBase< ::CStageBase, TConcreteStage > Super;
   TConcreteStage* This() { return static_cast<TConcreteStage*>(this); }

public:
   Tester1DStageBase(const std::string& name) : Super(name) {}

   virtual int Initialize();

   virtual int SetPositionUm(double pos);
   virtual int GetPositionUm(double& pos);
   virtual int SetPositionSteps(long steps);
   virtual int GetPositionSteps(long& steps);
   virtual int Home();
   virtual int Stop();
   virtual int SetOrigin();
   virtual int GetLimits(double& lower, double& upper);
   virtual int IsStageSequenceable(bool& isSequenceable) const
   { isSequenceable = false; return DEVICE_OK; }
   virtual bool IsContinuousFocusDrive() const { return false; }

protected:
   FloatSetting::Ptr GetZPositionUmSetting() { return zPositionUm_; }
   FloatSetting::ConstPtr GetZPositionUmSetting() const
   { return zPositionUm_; }

private:
   FloatSetting::Ptr zPositionUm_;
   OneShotSetting::Ptr home_;
   OneShotSetting::Ptr stop_;
   OneShotSetting::Ptr originSet_;
};


class TesterZStage : public Tester1DStageBase<TesterZStage, 10>
{
   typedef TesterZStage Self;
   typedef Tester1DStageBase<Self, 10> Super;

public:
   TesterZStage(const std::string& name) : Super(name) {}

   virtual int Initialize();

   virtual int IsStageSequenceable(bool& isSequenceable) const;
   virtual int GetStageSequenceMaxLength(long& nrEvents) const;
   virtual int ClearStageSequence();
   virtual int AddToStageSequence(double positionUm);
   virtual int SendStageSequence();
   virtual int StartStageSequence();
   virtual int StopStageSequence();

   virtual bool IsContinuousFocusDrive() const { return false; }

   FloatSetting::Ptr GetZPositionUmSetting()
   { return Super::GetZPositionUmSetting(); }

private:
   TriggerInput triggerInput_;
   std::vector<double> deviceInterfaceSequenceBuffer_;
};


class TesterAFStage : public Tester1DStageBase<TesterAFStage>
{
   typedef TesterAFStage Self;
   typedef Tester1DStageBase<Self> Super;

public:
   TesterAFStage(const std::string& name) : Super(name) {}

   virtual bool IsContinuousFocusDrive() const { return true; }
};


class TesterAutofocus : public TesterBase<CAutoFocusBase, TesterAutofocus>
{
   typedef TesterAutofocus Self;
   typedef TesterBase< ::CAutoFocusBase, TesterAutofocus > Super;

public:
   TesterAutofocus(const std::string& name) : Super(name) {}

   virtual int Initialize();

   virtual int SetContinuousFocusing(bool state);
   virtual int GetContinuousFocusing(bool& state);
   virtual bool IsContinuousFocusLocked();
   virtual int FullFocus();
   virtual int IncrementalFocus();
   virtual int GetLastFocusScore(double& score);
   virtual int GetCurrentFocusScore(double& score);
   virtual int GetOffset(double& offset);
   virtual int SetOffset(double offset);

private:
   BoolSetting::Ptr continuousFocusEnabled_;
   FloatSetting::Ptr offset_;
   OneShotSetting::Ptr fullFocus_;
   OneShotSetting::Ptr incrementalFocus_;

   StringSetting::Ptr linkedZStage_;
   BoolSetting::Ptr setZDisablesContinuousFocus_;
   boost::signals2::connection zStageConnection_;
   void UpdateZStageLink();
   void HandleLinkedZStageSetPosition();
};


class TesterSwitcher : public TesterBase<CStateDeviceBase, TesterSwitcher>
{
   typedef TesterSwitcher Self;
   typedef TesterBase< ::CStateDeviceBase, TesterSwitcher > Super;

   // More than enough positions for most testing use
   static const unsigned nrPositions_ = 16;

public:
   TesterSwitcher(const std::string& name) : Super(name) {}

   virtual int Initialize();

   virtual unsigned long GetNumberOfPositions() const;
   virtual int SetGateOpen(bool open);
   virtual int GetGateOpen(bool& open);

private:
   TriggerInput triggerInput_;
   IntegerSetting::Ptr position_;
   BoolSetting::Ptr gateOpen_;
};
