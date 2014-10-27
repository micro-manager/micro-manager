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

#include "LoggedSetting.h"

#include "DeviceBase.h"

#include <boost/thread.hpp>
#include <string>


class TesterHub;


// Common base class for all devices. Goes in between TDeviceBase and
// UConcreteDevice in the inheritance graph.
template <template <class> class TDeviceBase, class UConcreteDevice>
class TesterBase : public TDeviceBase<UConcreteDevice>
{
public:
   typedef TesterBase Self;
   typedef TDeviceBase<UConcreteDevice> Super;

   TesterBase(const std::string& name);
   virtual ~TesterBase();

   virtual void GetName(char* name) const;
   virtual int Initialize();
   virtual int Shutdown();
   virtual bool Busy();

   virtual std::string GetName() const { return name_; }

protected:
   virtual TesterHub* GetHub();
   virtual SettingLogger* GetLogger() { return GetHub()->GetLogger(); }

   void CreateOnOffProperty(const std::string& name,
         typename BoolSetting<UConcreteDevice>::Ptr setting);
   void CreateYesNoProperty(const std::string& name,
         typename BoolSetting<UConcreteDevice>::Ptr setting);
   void CreateOneZeroProperty(const std::string& name,
         typename BoolSetting<UConcreteDevice>::Ptr setting);
   void CreateIntegerProperty(const std::string& name,
         typename IntegerSetting<UConcreteDevice>::Ptr setting);
   void CreateFloatProperty(const std::string& name,
         typename FloatSetting<UConcreteDevice>::Ptr setting);

   void MarkBusy() { GetLogger()->MarkBusy(name_); }

private:
   const std::string name_;
};


// This device adapter is specifically designed to be used in parallel testing
// within the same process, using multiple MMCore instances. Since there will
// be only one copy of the device adapter module (library), we must not use any
// static data. All state (e.g. SettingLogger) shared between devices must be
// owned by the hub instance.
class TesterHub : public TesterBase<HubBase, TesterHub>
{
   SettingLogger logger_;

public:
   typedef TesterHub Self;
   typedef TesterBase< ::HubBase, TesterHub > Super;

   TesterHub(const std::string& name);

   virtual int Initialize();
   virtual int Shutdown();

   virtual int DetectInstalledDevices();

   virtual TesterHub* GetHub() { return this; }
   virtual SettingLogger* GetLogger() { return &logger_; }
};


class TesterCamera : public TesterBase<CCameraBase, TesterCamera>
{
   typedef TesterCamera Self;
   typedef TesterBase< ::CCameraBase, TesterCamera > Super;

public:
   TesterCamera(const std::string& name);
   virtual ~TesterCamera();

   virtual int Initialize();
   virtual int Shutdown();

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
   // Returned pointer should be delete[]d by caller
   const unsigned char* GenerateLogImage(bool isSequenceImage,
         size_t cumulativeCount, size_t localCount = 0);

   int StartSequenceAcquisitionImpl(bool finite, long count,
         bool stopOnOverflow);

   void SendSequence(bool finite, long count, bool stopOnOverflow);

private:
   size_t snapCounter_;
   size_t cumulativeSequenceCounter_;

   const unsigned char* snapImage_;

   boost::mutex sequenceMutex_; // Guards stopSequence_ (only)
   bool stopSequence_; // Guarded by sequenceMutex_

   // Note: boost::future in more recent versions
   boost::unique_future<void> sequenceFuture_;
   boost::thread sequenceThread_;

   FloatSetting<Self>::Ptr exposureSetting_;
   IntegerSetting<Self>::Ptr binningSetting_;
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
   BoolSetting<Self>::Ptr shutterOpen_;
};


template <class TConcreteStage, long UMicronsPerStep = 1>
class TesterStageBase : public TesterBase<CStageBase, TConcreteStage>
{
   typedef TesterBase< ::CStageBase, TConcreteStage > Super;
   TConcreteStage* This() { return static_cast<TConcreteStage*>(this); }

   static const long umPerStep = UMicronsPerStep;

public:
   TesterStageBase(const std::string& name) : Super(name) {}

   virtual int Initialize();

   virtual int SetPositionUm(double pos);
   virtual int GetPositionUm(double& pos);
   virtual int SetPositionSteps(long steps);
   virtual int GetPositionSteps(long& steps);
   virtual int SetOrigin();
   virtual int GetLimits(double& lower, double& upper);
   virtual int IsStageSequenceable(bool& isSequenceable) const
   { isSequenceable = false; return DEVICE_OK; }
   virtual bool IsContinuousFocusDrive() const { return false; }

private:
   typename FloatSetting<TConcreteStage>::Ptr zPositionUm_;
   typename OneShotSetting<TConcreteStage>::Ptr originSet_;
};


class TesterZStage : public TesterStageBase<TesterZStage, 10>
{
   typedef TesterZStage Self;
   typedef TesterStageBase<Self, 10> Super;

public:
   TesterZStage(const std::string& name) : Super(name) {}

   virtual bool IsContinuousFocusDrive() const { return false; }
};


class TesterAFStage : public TesterStageBase<TesterAFStage>
{
   typedef TesterAFStage Self;
   typedef TesterStageBase<Self> Super;

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
   BoolSetting<Self>::Ptr continuousFocusEnabled_;
   FloatSetting<Self>::Ptr offset_;
   OneShotSetting<Self>::Ptr fullFocus_;
   OneShotSetting<Self>::Ptr incrementalFocus_;
};
