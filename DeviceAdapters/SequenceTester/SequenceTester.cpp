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

#include "SequenceTester.h"
#include "SequenceTesterImpl.h"

#include "ModuleInterface.h"

#include <boost/bind.hpp>
#include <boost/move/move.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/thread.hpp>
#include <boost/unordered_map.hpp>
#include <exception>
#include <string>
#include <utility>


// Container for Micro-Manager error code
class DeviceError : public std::exception
{
   const int code_;
public:
   DeviceError(int code) : code_(code) {}
   int GetCode() const { return code_; }
};


MODULE_API void
InitializeModuleData()
{
   RegisterDevice("THub", MM::HubDevice,
         "Fake devices for automated and interactive testing");
}


inline bool StartsWith(const std::string& prefix, const std::string& s)
{
   return s.substr(0, prefix.size()) == prefix;
}


namespace
{
   // The only way to manage our devices using shared_ptr is to hold a mapping
   // from MM::Device* to the shared_ptrs. This is because there is no way to
   // extract our shared_ptr from the MM::Device* passed to DeleteDevice()
   // without static knowledge of the concrete type of the device object.
   // We let this map also serve as a mechanism to retain the devices during
   // their lifetime.
   class DeviceRetainer
   {
      static boost::mutex mutex_;
      static boost::unordered_map<
         MM::Device*,
         boost::shared_ptr<InterDevice>
      > devices_;

   public:
      template <class TDevice>
      static MM::Device* CreateDevice(const std::string& name)
      {
         boost::lock_guard<boost::mutex> g(mutex_);

         boost::shared_ptr<InterDevice> interdev =
            boost::make_shared<TDevice>(name);

         MM::Device* pDevice = static_cast<TDevice*>(interdev.get());
         devices_.insert(std::make_pair(pDevice, interdev));

         return pDevice;
      }

      static void DeleteDevice(MM::Device* pDevice)
      {
         boost::lock_guard<boost::mutex> g(mutex_);
         devices_.erase(pDevice);
      }
   };

   boost::mutex DeviceRetainer::mutex_;
   boost::unordered_map<
      MM::Device*,
      boost::shared_ptr<InterDevice>
   > DeviceRetainer::devices_;
}


MODULE_API MM::Device*
CreateDevice(const char* deviceName)
{
   if (!deviceName)
      return 0;
   const std::string name(deviceName);
   if (name == "THub")
      return DeviceRetainer::CreateDevice<TesterHub>(name);

   // By using prefix matching, we can allow the creation of an arbitrary
   // number of each device for testing.
   if (StartsWith("TCamera", name))
      return DeviceRetainer::CreateDevice<TesterCamera>(name);
   if (StartsWith("TShutter", name))
      return DeviceRetainer::CreateDevice<TesterShutter>(name);
   if (StartsWith("TXYStage", name))
      return DeviceRetainer::CreateDevice<TesterXYStage>(name);
   if (StartsWith("TZStage", name))
      return DeviceRetainer::CreateDevice<TesterZStage>(name);
   if (StartsWith("TAFStage", name))
      return DeviceRetainer::CreateDevice<TesterAFStage>(name);
   if (StartsWith("TAutofocus", name))
      return DeviceRetainer::CreateDevice<TesterAutofocus>(name);
   if (StartsWith("TSwitcher", name))
      return DeviceRetainer::CreateDevice<TesterSwitcher>(name);
   return 0;
}


MODULE_API void
DeleteDevice(MM::Device* pDevice)
{
   DeviceRetainer::DeleteDevice(pDevice);
}


TesterHub::TesterHub(const std::string& name) :
   Super(name)
{
}


int
TesterHub::Initialize()
{
   // Guard against multiple calls
   if (GetHub())
      return DEVICE_OK;

   // For hub only, do _not_ call Super::Initialize(). Instead, set itself to
   // be the hub.
   InterDevice::SetHub(GetSharedPtr());

   return CommonHubPeripheralInitialize();
}


int
TesterHub::Shutdown()
{
   // Shutdown may be called more than once, in which case GetHub() may return
   // a null pointer.
   if (!GetHub())
      return DEVICE_OK;

   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   CommonHubPeripheralShutdown();

   // For hub only, do _not_ call Super::Shutdown(). Release the self-reference
   // created in Initialize().
   InterDevice::SetHub(boost::shared_ptr<TesterHub>());
   return DEVICE_OK;
}


int
TesterHub::DetectInstalledDevices()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   ClearInstalledDevices();
   AddInstalledDevice(new TesterCamera("TCamera-0"));
   AddInstalledDevice(new TesterCamera("TCamera-1"));
   AddInstalledDevice(new TesterShutter("TShutter-0"));
   AddInstalledDevice(new TesterShutter("TShutter-1"));
   AddInstalledDevice(new TesterXYStage("TXYStage-0"));
   AddInstalledDevice(new TesterXYStage("TXYStage-1"));
   AddInstalledDevice(new TesterZStage("TZStage-0"));
   AddInstalledDevice(new TesterZStage("TZStage-1"));
   AddInstalledDevice(new TesterAFStage("TAFStage-0"));
   AddInstalledDevice(new TesterAFStage("TAFStage-1"));
   AddInstalledDevice(new TesterAutofocus("TAutofocus-0"));
   AddInstalledDevice(new TesterAutofocus("TAutofocus-1"));
   AddInstalledDevice(new TesterSwitcher("TSwitcher-0"));
   AddInstalledDevice(new TesterSwitcher("TSwitcher-1"));
   return DEVICE_OK;
}


int
TesterHub::RegisterDevice(const std::string& name, InterDevice::Ptr device)
{
   boost::weak_ptr<InterDevice>& ptr = devices_[name];
   if (!ptr.lock())
   {
      ptr = device;
      return DEVICE_OK;
   }
   // Forbid creation of multiple instances of devices with the same name under
   // the same hub.
   return DEVICE_ERR;
}


void
TesterHub::UnregisterDevice(const std::string& name)
{
   devices_.erase(name);
}


InterDevice::Ptr
TesterHub::FindPeerDevice(const std::string& name)
{
   boost::unordered_map< std::string, boost::weak_ptr<InterDevice> >::iterator
      found = devices_.find(name);
   if (found == devices_.end())
      return InterDevice::Ptr();
   return found->second.lock();
}


TesterCamera::TesterCamera(const std::string& name) :
   Super(name),
   produceHumanReadableImages_(true),
   imageWidth_(384),
   imageHeight_(384),
   nextSerialNr_(0),
   nextSnapImageNr_(0),
   nextSequenceImageNr_(0),
   snapImage_(0),
   stopSequence_(true)
{
   // For pre-init properties only, we use the traditional method to set up.
   CCameraBase<Self>::CreateStringProperty("ImageMode", "HumanReadable",
         false, 0, true);
   AddAllowedValue("ImageMode", "HumanReadable");
   AddAllowedValue("ImageMode", "MachineReadable");
   CCameraBase<Self>::CreateIntegerProperty("ImageWidth", imageWidth_,
         false, 0, true);
   SetPropertyLimits("ImageWidth", 32, 4096);
   CCameraBase<Self>::CreateIntegerProperty("ImageHeight", imageHeight_,
         false, 0, true);
   SetPropertyLimits("ImageHeight", 32, 4096);
}


TesterCamera::~TesterCamera()
{
   delete[] snapImage_;
}


int
TesterCamera::Initialize()
{
   // Guard against multiple calls
   if (GetHub())
      return DEVICE_OK;

   int err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   char imageMode[MM::MaxStrLength];
   GetProperty("ImageMode", imageMode);
   produceHumanReadableImages_ = (imageMode == std::string("HumanReadable"));
   GetProperty("ImageWidth", imageWidth_);
   GetProperty("ImageHeight", imageHeight_);

   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   exposureSetting_ = FloatSetting::New(GetLogger(), this, "Exposure",
         100.0, true, 0.1, 1000.0);
   exposureSetting_->SetBusySetting(GetBusySetting());
   binningSetting_ = IntegerSetting::New(GetLogger(), this, "Binning",
         1, true, 1, 1);
   binningSetting_->SetBusySetting(GetBusySetting());

   CreateFloatProperty("Exposure", exposureSetting_);
   CreateIntegerProperty("Binning", binningSetting_);

   RegisterEdgeTriggerSource("ExposureStartEdge", exposureStartEdgeTrigger_);
   RegisterEdgeTriggerSource("ExposureStopEdge", exposureStopEdgeTrigger_);

   return DEVICE_OK;
}


int
TesterCamera::SnapImage()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   delete[] snapImage_;
   snapImage_ = GenerateLogImage(false, nextSnapImageNr_++);

   return DEVICE_OK;
}


const unsigned char*
TesterCamera::GetImageBuffer()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return snapImage_;
}


int
TesterCamera::GetBinning() const
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return static_cast<int>(binningSetting_->Get());
}


int
TesterCamera::SetBinning(int binSize)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   binningSetting_->MarkBusy();
   return binningSetting_->Set(binSize);
}


long
TesterCamera::GetImageBufferSize() const
{
   return GetImageWidth() * GetImageHeight() * GetImageBytesPerPixel();
}


unsigned
TesterCamera::GetImageWidth() const
{
   return imageWidth_;
}


unsigned
TesterCamera::GetImageHeight() const
{
   return imageHeight_;
}


void
TesterCamera::SetExposure(double exposureMs)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   exposureSetting_->MarkBusy();
   exposureSetting_->Set(exposureMs);
}


double
TesterCamera::GetExposure() const
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return exposureSetting_->Get();
}


int
TesterCamera::SetROI(unsigned, unsigned, unsigned, unsigned)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   GetBusySetting()->Set();
   return DEVICE_UNSUPPORTED_COMMAND;
}


int
TesterCamera::GetROI(unsigned& x, unsigned& y, unsigned& w, unsigned& h)
{
   // Always full-frame
   x = y = 0;
   w = GetImageWidth();
   h = GetImageHeight();
   return DEVICE_OK;
}


int
TesterCamera::StartSequenceAcquisition(long count, double, bool stopOnOverflow)
{
   return StartSequenceAcquisitionImpl(true, count, stopOnOverflow);
}


int
TesterCamera::StartSequenceAcquisition(double)
{
   return StartSequenceAcquisitionImpl(false, 0, false);
}


int
TesterCamera::StartSequenceAcquisitionImpl(bool finite, long count,
      bool stopOnOverflow)
{
   // There is no need to acquire the hub-global mutex here; no data protected
   // by it is accessed in this function.

   {
      boost::lock_guard<boost::mutex> lock(sequenceMutex_);
      if (!stopSequence_)
         return DEVICE_ERR;
      stopSequence_ = false;
   }

   GetCoreCallback()->PrepareForAcq(this);

   // Note: boost::packaged_task<void ()> in more recent versions of Boost.
   boost::packaged_task<void> captureTask(
         boost::bind(&TesterCamera::SendSequence, this,
            finite, count, stopOnOverflow));
   sequenceFuture_ = captureTask.get_future();

   boost::thread captureThread(boost::move(captureTask));
   captureThread.detach();

   return DEVICE_OK;
}


int
TesterCamera::StopSequenceAcquisition()
{
   // Do _not_ acquire the hub-global mutex here; it is not needed and will
   // deadlock with the wait on sequenceFuture_.

   {
      boost::lock_guard<boost::mutex> lock(sequenceMutex_);
      if (stopSequence_)
         return DEVICE_OK;
      stopSequence_ = true;
   }

   // In newer Boost versions: if (sequenceFuture_.valid())
   if (sequenceFuture_.get_state() != boost::future_state::uninitialized)
   {
      try
      {
         sequenceFuture_.get();
      }
      catch (const DeviceError& e)
      {
         sequenceFuture_ = boost::unique_future<void>();
         return e.GetCode();
      }
      sequenceFuture_ = boost::unique_future<void>();
   }

   return GetCoreCallback()->AcqFinished(this, 0);
}


bool
TesterCamera::IsCapturing()
{
   // Skip locking of hub global mutex, since we only access stopSequence_
   // which is protected by its own mutex.

   boost::lock_guard<boost::mutex> lock(sequenceMutex_);
   return !stopSequence_;
}


const unsigned char*
TesterCamera::GenerateLogImage(bool isSequenceImage, size_t cumulativeNr,
      size_t frameNr)
{
   exposureStartEdgeTrigger_();

   size_t bufSize = GetImageBufferSize();
   char* bytes = new char[bufSize];

   SettingLogger* logger = GetLogger();
   if (produceHumanReadableImages_)
   {
      logger->DrawTextToBuffer(bytes, GetImageWidth(), GetImageHeight(),
            GetDeviceName(), isSequenceImage, nextSerialNr_++,
            cumulativeNr, frameNr);
   }
   else
   {
      logger->DumpMsgPackToBuffer(bytes, bufSize, GetDeviceName(),
            isSequenceImage, nextSerialNr_++, cumulativeNr, frameNr);

   }
   logger->Reset();

   exposureStopEdgeTrigger_();

   return reinterpret_cast<unsigned char*>(bytes);
}


void
TesterCamera::SendSequence(bool finite, long count, bool stopOnOverflow)
{
   MM::Core* core = GetCoreCallback();

   char label[MM::MaxStrLength];
   GetLabel(label);
   Metadata md;
   md.put("Camera", label);
   std::string serializedMD(md.Serialize());

   const unsigned char* bytes = 0;

   // Currently assumed to be constant over device lifetime
   unsigned width = GetImageWidth();
   unsigned height = GetImageHeight();
   unsigned bytesPerPixel = GetImageBytesPerPixel();

   for (long frame = 0; !finite || frame < count; ++frame)
   {
      {
         boost::lock_guard<boost::mutex> lock(sequenceMutex_);
         if (stopSequence_)
            break;
      }

      delete[] bytes;

      {
         TesterHub::Guard g(GetHub()->LockGlobalMutex());
         bytes = GenerateLogImage(true, nextSequenceImageNr_++, frame);
      }

      try
      {
         int err;
         err = core->InsertImage(this, bytes, width, height,
               bytesPerPixel, serializedMD.c_str());

         if (!stopOnOverflow && err == DEVICE_BUFFER_OVERFLOW)
         {
            core->ClearImageBuffer(this);
            err = core->InsertImage(this, bytes, width, height,
                  bytesPerPixel, serializedMD.c_str(), false);
         }

         if (err != DEVICE_OK)
         {
            bool stopped;
            {
               boost::lock_guard<boost::mutex> lock(sequenceMutex_);
               stopped = stopSequence_;
            }
            // If we're stopped already, that could be the reason for the
            // error.
            if (!stopped)
               BOOST_THROW_EXCEPTION(DeviceError(err));
            else
               break;
         }
      }
      catch (...)
      {
         delete[] bytes;
         throw;
      }
   }

   delete[] bytes;
}


int
TesterShutter::Initialize()
{
   // Guard against multiple calls
   if (GetHub())
      return DEVICE_OK;

   int err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   shutterOpen_ = BoolSetting::New(GetLogger(), this, "ShutterState", false);
   shutterOpen_->SetBusySetting(GetBusySetting());
   CreateOneZeroProperty("State", shutterOpen_);

   return DEVICE_OK;
}


int
TesterShutter::SetOpen(bool open)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   shutterOpen_->MarkBusy();
   return shutterOpen_->Set(open);
}


int
TesterShutter::GetOpen(bool& open)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return shutterOpen_->Get(open);
}


int
TesterXYStage::Initialize()
{
   // Guard against multiple calls
   if (GetHub())
      return DEVICE_OK;

   int err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   xPositionSteps_ = IntegerSetting::New(GetLogger(), this,
         "XPositionSteps", 0, false);
   xPositionSteps_->SetBusySetting(GetBusySetting());
   yPositionSteps_ = IntegerSetting::New(GetLogger(), this,
         "YPositionSteps", 0, false);
   yPositionSteps_->SetBusySetting(GetBusySetting());
   home_ = OneShotSetting::New(GetLogger(), this, "Home");
   home_->SetBusySetting(GetBusySetting());
   stop_ = OneShotSetting::New(GetLogger(), this, "Stop");
   stop_->SetBusySetting(GetBusySetting());
   setOrigin_ = OneShotSetting::New(GetLogger(), this, "SetOrigin");
   setOrigin_->SetBusySetting(GetBusySetting());
   setXOrigin_ = OneShotSetting::New(GetLogger(), this, "SetXOrigin");
   setXOrigin_->SetBusySetting(GetBusySetting());
   setYOrigin_ = OneShotSetting::New(GetLogger(), this, "SetYOrigin");
   setYOrigin_->SetBusySetting(GetBusySetting());

   return DEVICE_OK;
}


int
TesterXYStage::SetPositionSteps(long x, long y)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   xPositionSteps_->MarkBusy();
   yPositionSteps_->MarkBusy();
   int err1 = xPositionSteps_->Set(x);
   int err2 = yPositionSteps_->Set(y);
   if (err1 != DEVICE_OK)
      return err1;
   if (err2 != DEVICE_OK)
      return err2;
   return DEVICE_OK;
}


int
TesterXYStage::GetPositionSteps(long& x, long& y)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   int err1 = xPositionSteps_->Get(x);
   int err2 = yPositionSteps_->Get(y);
   if (err1 != DEVICE_OK)
      return err1;
   if (err2 != DEVICE_OK)
      return err2;
   return DEVICE_OK;
}


int
TesterXYStage::Home()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   home_->MarkBusy();
   return home_->Set();
}


int
TesterXYStage::Stop()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   stop_->MarkBusy();
   return stop_->Set();
}


int
TesterXYStage::SetOrigin()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   setOrigin_->MarkBusy();
   return setOrigin_->Set();
}


int
TesterXYStage::SetXOrigin()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   setXOrigin_->MarkBusy();
   return setXOrigin_->Set();
}


int
TesterXYStage::SetYOrigin()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   setYOrigin_->MarkBusy();
   return setYOrigin_->Set();
}


int
TesterXYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   // Not (yet) designed for testing
   xMin = yMin = -10000000;
   xMax = yMax = +10000000;
   return DEVICE_OK;
}


int
TesterXYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
   long x, X, y, Y;
   int err = GetStepLimits(x, X, y, Y);
   xMin = static_cast<double>(x) / stepsPerUm;
   xMax = static_cast<double>(X) / stepsPerUm;
   yMin = static_cast<double>(y) / stepsPerUm;
   yMax = static_cast<double>(Y) / stepsPerUm;
   return err;
}


int
TesterZStage::Initialize()
{
   // Guard against multiple calls
   if (GetHub())
      return DEVICE_OK;

   int err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   triggerInput_.Initialize(shared_from_this(), GetZPositionUmSetting());

   CreateStringProperty("TriggerSourceDevice",
         triggerInput_.GetSourceDeviceSetting());
   CreateStringProperty("TriggerSourcePort",
         triggerInput_.GetSourcePortSetting());
   CreateIntegerProperty("TriggerSequenceMaxLength",
         triggerInput_.GetSequenceMaxLengthSetting());

   return DEVICE_OK;
}


int
TesterZStage::IsStageSequenceable(bool& isSequenceable) const
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   long len;
   int err = Super::GetZPositionUmSetting()->GetSequenceMaxLength(len);
   isSequenceable = (len > 0);
   return err;
}


int
TesterZStage::GetStageSequenceMaxLength(long& nrEvents) const
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return Super::GetZPositionUmSetting()->GetSequenceMaxLength(nrEvents);
}


int
TesterZStage::ClearStageSequence()
{
   // No locking needed for access to deviceInterfaceSequenceBuffer_
   deviceInterfaceSequenceBuffer_.clear();
   return DEVICE_OK;
}


int
TesterZStage::AddToStageSequence(double positionUm)
{
   // No locking needed for access to deviceInterfaceSequenceBuffer_
   deviceInterfaceSequenceBuffer_.push_back(positionUm);
   return DEVICE_OK;
}


int
TesterZStage::SendStageSequence()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return GetZPositionUmSetting()->
      SetTriggerSequence(deviceInterfaceSequenceBuffer_);
}


int
TesterZStage::StartStageSequence()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return GetZPositionUmSetting()->StartTriggerSequence();
}


int
TesterZStage::StopStageSequence()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return GetZPositionUmSetting()->StopTriggerSequence();
}


int
TesterAutofocus::Initialize()
{
   // Guard against multiple calls
   if (GetHub())
      return DEVICE_OK;

   int err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   continuousFocusEnabled_ = BoolSetting::New(GetLogger(), this,
         "ContinuousFocusEnabled", false);
   continuousFocusEnabled_->SetBusySetting(GetBusySetting());
   CreateOnOffProperty("ContinuousFocus", continuousFocusEnabled_);

   offset_ = FloatSetting::New(GetLogger(), this, "Offset", 0.0, false);
   offset_->SetBusySetting(GetBusySetting());

   fullFocus_ = OneShotSetting::New(GetLogger(), this, "FullFocus");
   fullFocus_->SetBusySetting(GetBusySetting());
   incrementalFocus_ = OneShotSetting::New(GetLogger(), this,
         "IncrementalFocus");
   incrementalFocus_->SetBusySetting(GetBusySetting());

   linkedZStage_ = StringSetting::New(GetLogger(), this, "LinkedZStage");
   linkedZStage_->GetPostSetSignal().connect(
         boost::bind(&Self::UpdateZStageLink, this));
   CreateStringProperty("LinkedZStage", linkedZStage_);

   setZDisablesContinuousFocus_ = BoolSetting::New(GetLogger(), this,
         "SetZDisablesContinuousFocus", false);
   CreateYesNoProperty("SetZDisablesContinuousFocus",
         setZDisablesContinuousFocus_);

   return DEVICE_OK;
}


int
TesterAutofocus::SetContinuousFocusing(bool state)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   continuousFocusEnabled_->MarkBusy();
   return continuousFocusEnabled_->Set(state);
}


int
TesterAutofocus::GetContinuousFocusing(bool& state)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return continuousFocusEnabled_->Get(state);
}


bool
TesterAutofocus::IsContinuousFocusLocked()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   bool enabled = (continuousFocusEnabled_->Get() != 0);
   if (!enabled)
      return false;

   // TODO Use a busy-like mechanism to return true on the second call after
   // enabling continuous focus. For now, always pretend we're locked.
   return true;
}


int
TesterAutofocus::FullFocus()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return fullFocus_->Set();
}


int
TesterAutofocus::IncrementalFocus()
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return incrementalFocus_->Set();
}


int
TesterAutofocus::GetLastFocusScore(double& score)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   // I don't see any non-dead use of this function except for one mysterious
   // case in acqEngine that might be a bug. For now, return a constant.
   // Returning an error is not an option due to poor assumptions made by
   // existing code.
   score = 0.0;
   return DEVICE_OK;
}


int
TesterAutofocus::GetCurrentFocusScore(double& score)
{
   // This does not appear to be used by any of our non-dead code.
   return DEVICE_UNSUPPORTED_COMMAND;
}


int
TesterAutofocus::GetOffset(double& offset)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   return offset_->Get(offset);
}


int
TesterAutofocus::SetOffset(double offset)
{
   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   offset_->MarkBusy();
   return offset_->Set(offset);
}


void
TesterAutofocus::UpdateZStageLink()
{
   zStageConnection_.disconnect();

   const std::string zStageName = linkedZStage_->Get();
   if (zStageName.empty())
      return;

   InterDevice::Ptr device = GetHub()->FindPeerDevice(zStageName);
   boost::shared_ptr<TesterZStage> zStage =
      boost::dynamic_pointer_cast<TesterZStage>(device);
   if (!zStage)
      return;

   FloatSetting::Ptr zPosUm = zStage->GetZPositionUmSetting();
   if (!zPosUm)
      return;

   zStageConnection_ = zPosUm->GetPostSetSignal().connect(
         boost::bind(&Self::HandleLinkedZStageSetPosition, this));
}


void
TesterAutofocus::HandleLinkedZStageSetPosition()
{
   if (setZDisablesContinuousFocus_->Get())
      continuousFocusEnabled_->Set(false);
}


int
TesterSwitcher::Initialize()
{
   // Guard against multiple calls
   if (GetHub())
      return DEVICE_OK;

   int err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   TesterHub::Guard g(GetHub()->LockGlobalMutex());

   position_ = IntegerSetting::New(GetLogger(), this, "Position",
         0, true, 0, nrPositions_);
   position_->SetBusySetting(GetBusySetting());
   CreateIntegerProperty("State", position_);

   gateOpen_ = BoolSetting::New(GetLogger(), this, "GateOpen", true);

   triggerInput_.Initialize(shared_from_this(), position_);

   CreateStringProperty("TriggerSourceDevice",
         triggerInput_.GetSourceDeviceSetting());
   CreateStringProperty("TriggerSourcePort",
         triggerInput_.GetSourcePortSetting());
   CreateIntegerProperty("TriggerSequenceMaxLength",
         triggerInput_.GetSequenceMaxLengthSetting());

   return DEVICE_OK;
}


unsigned long
TesterSwitcher::GetNumberOfPositions() const
{
   return nrPositions_;
}


int
TesterSwitcher::SetGateOpen(bool open)
{
   gateOpen_->MarkBusy();
   return gateOpen_->Set(open);
}


int
TesterSwitcher::GetGateOpen(bool& open)
{
   return gateOpen_->Get(open);
}
