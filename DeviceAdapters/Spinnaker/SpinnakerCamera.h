#ifndef _SPINNAKER_CAMERA_H_
#define _SPINNAKER_CAMERA_H_

#include "../../3rdparty/Spinnaker/include/Spinnaker.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"

#define SPKR Spinnaker
#define GENAPI Spinnaker::GenApi
#define GENICAM Spinnaker::GenICam
#define SPKR_ERROR 10002

class SpinnakerCamera : public CCameraBase<SpinnakerCamera>
{
public:
	SpinnakerCamera(GENICAM::gcstring serialNumber);
	~SpinnakerCamera();

	int Initialize();
	int Shutdown();
	void GetName(char* name) const;

	int SnapImage();
	const unsigned char* GetImageBuffer();
	unsigned GetImageWidth() const;
	unsigned GetImageHeight() const;
	unsigned GetImageBytesPerPixel() const;
	unsigned GetBitDepth() const;
	long GetImageBufferSize() const;
	double GetExposure() const;
	void SetExposure(double exp);
	int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize);
	int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);
	int ClearROI();
	int GetBinning() const;
	int SetBinning(int binSize);
	int IsExposureSequenceable(bool& isSequenceable) const { isSequenceable = false; return DEVICE_OK; };

	int PrepareSequenceAcqusition();
	int StartSequenceAcquisition(double interval);
	int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
	int StopSequenceAcquisition();
	bool IsCapturing();

	int MoveImageToCircularBuffer();

	// Acquisition Control
	int OnPixelFormat(MM::PropertyBase* pProp, MM::ActionType eAct); 
	int OnTestPattern(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFrameRateEnabled(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFrameRateAuto(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnExposureAuto(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBinningEnum(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBinningInt(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBinningModeEnum(MM::PropertyBase* pProp, MM::ActionType eAct);

	// Gain Control
	int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGainAuto(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGammaEnabled(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBlackLevel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBlackLevelAuto(MM::PropertyBase* pProp, MM::ActionType eAct);


	// Triggering
	int OnTriggerSelector(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerSource(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerActivation(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerOverlap(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnExposureMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnUserOutputSelector(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnUserOutputValue(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnLineSelector(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLineMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLineInverter(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLineSource(MM::PropertyBase* pProp, MM::ActionType eAct);

	/*int OnLineMode(MM::PropertyBase* pProp, MM::ActionType eAct, long lineNum);
	int OnLineInverter(MM::PropertyBase* pProp, MM::ActionType eAct, long lineNum);
	int OnLineSource(MM::PropertyBase* pProp, MM::ActionType eAct, long lineNum);*/

private:
	friend class SpinnakerAcquisitionThread;

#pragma pack(push, 1)
	struct Unpack12Struct {
		uint8_t _2;
		uint8_t _1;
		uint8_t _0;
	};
#pragma pack(pop)

	enum BinningControl
	{
		Independent,
		Vertical,
		Horizontal,
		None
	};

	template<typename enumType>
	void CreatePropertyFromEnum(
		const std::string& name,
		GENAPI::IEnumerationT<enumType> &camProp,
		int (SpinnakerCamera::*fpt)(MM::PropertyBase* pProp, MM::ActionType eAct));
	void CreatePropertyFromFloat(
		const std::string& name,
		GENAPI::IFloat &camProp,
		int (SpinnakerCamera::*fpt)(MM::PropertyBase* pProp, MM::ActionType eAct));
	void CreatePropertyFromBool(
		const std::string& name,
		GENAPI::IBoolean &camProp,
		int (SpinnakerCamera::*fpt)(MM::PropertyBase* pProp, MM::ActionType eAct));
	void CreatePropertyFromLineEnum(
		const std::string& nodeName,
		int lineNumber,
		int (SpinnakerCamera::*fpt)(MM::PropertyBase* pProp, MM::ActionType eAct, long data));
	void CreatePropertyFromLineBool(
		const std::string& nodeName,
		int lineNumber,
		int (SpinnakerCamera::*fpt)(MM::PropertyBase* pProp, MM::ActionType eAct, long data));

	template<typename enumType>
	int OnEnumPropertyChanged(
		GENAPI::IEnumerationT<enumType> &camProp,
		MM::PropertyBase* pProp,
		MM::ActionType eAct);
	int OnFloatPropertyChanged(
		GENAPI::IFloat &camProp,
		MM::PropertyBase* pProp,
		MM::ActionType eAct);
	int OnBoolPropertyChanged(
		GENAPI::IBoolean &camProp,
		MM::PropertyBase* pProp,
		MM::ActionType eAct);
	int OnLineEnumPropertyChanged(
		std::string name,
		MM::PropertyBase* pProp,
		MM::ActionType eAct,
		long lineNum);
	int OnLineBoolPropertyChanged(
		std::string name,
		MM::PropertyBase* pProp,
		MM::ActionType eAct,
		long lineNum);

	void Unpack12Bit(int packedSize, int width, int height, bool flip);

	std::string EAccessName(GENAPI::EAccessMode accessMode) {
		switch (accessMode) {
		case GENAPI::EAccessMode::NA:
			return "Not available";
		case GENAPI::EAccessMode::NI:
			return "Not Implemented";
		case GENAPI::EAccessMode::RO:
			return "Read Only";
		case GENAPI::EAccessMode::RW:
			return "Read Write";
		case GENAPI::EAccessMode::WO:
			return "Write Only";
		default:
			return "Unknown";
		}
	}
	

	GENICAM::gcstring m_SN;
	SPKR::SystemPtr m_system;
	SPKR::CameraPtr m_cam;
	SPKR::ImagePtr m_imagePtr;
	unsigned char *m_imageBuff;

	GENICAM::gcstring m_pixFormat;
	std::vector<std::string> m_BinningModes;

	BinningControl m_bc;

	SpinnakerAcquisitionThread *m_aqThread;
	MMThreadLock m_pixelLock;
	bool m_stopOnOverflow;


	SPKR::TriggerModeEnums m_aqTriggerMode;
	SPKR::TriggerSourceEnums m_aqTriggerSource;
	GENAPI::StringList_t m_gpioLines;
};


class SpinnakerAcquisitionThread : public MMDeviceThreadBase
{
public:
	SpinnakerAcquisitionThread(SpinnakerCamera *pCam);
	~SpinnakerAcquisitionThread();
	void Stop();
	void Start(long numImages, double intervalMs);
	bool IsStopped();
	void Suspend();
	bool IsSuspended();
	void Resume();
	void SetLength(long images) { m_numImages = images; }
	long GetLength() const { return m_numImages; }
	long GetImageCounter() { return m_imageCounter; }
	MM::MMTime GetStartTime() { return m_startTime; }
	MM::MMTime GetActualDuration() { return m_actualDuration; }
private:
	friend class SpinnakerCamera;
	int svc(void) throw();
	long m_numImages;
	double m_intervalMs;
	long m_imageCounter;
	bool m_stop;
	bool m_suspend;
	SpinnakerCamera* m_spkrCam;

	MM::MMTime m_startTime;
	MM::MMTime m_actualDuration;
	MM::MMTime m_lastFrameTime;
	MMThreadLock m_stopLock;
	MMThreadLock m_suspendLock;
};


template<typename enumType>
inline void SpinnakerCamera::CreatePropertyFromEnum(const std::string& name, GENAPI::IEnumerationT<enumType>& camProp, int(SpinnakerCamera::* fpt)(MM::PropertyBase *pProp, MM::ActionType eAct))
{
	auto accessMode = camProp.GetAccessMode();

	if (accessMode == GENAPI::EAccessMode::RO || accessMode == GENAPI::EAccessMode::RW || accessMode == GENAPI::EAccessMode::NA)
	{
		try
		{
			bool readOnly = accessMode == GENAPI::EAccessMode::RO || accessMode == GENAPI::EAccessMode::NA;
			auto pAct = new CPropertyAction(this, fpt);

			if (accessMode != GENAPI::EAccessMode::NA) 
			{
				GENAPI::StringList_t propertyValues;
				camProp.GetSymbolics(propertyValues);

				CreateProperty(name.c_str(), camProp.GetCurrentEntry()->GetSymbolic().c_str(), MM::String, readOnly, pAct);

				for (int i = 0; i < propertyValues.size(); i++)
					AddAllowedValue(name.c_str(), propertyValues[i].c_str());
			}
			else
			{
				CreateProperty(name.c_str(), "", MM::String, readOnly, pAct);
				AddAllowedValue(name.c_str(), "");
			}
		}
		catch (SPKR::Exception& ex)
		{
			LogMessage(ex.what());
		}
	}
	else
	{
		LogMessage(name + " property not created: Property not accessable\nAccess Mode: " + EAccessName(accessMode));
	}
}

template<typename enumType>
inline int SpinnakerCamera::OnEnumPropertyChanged(GENAPI::IEnumerationT<enumType>& camProp, MM::PropertyBase * pProp, MM::ActionType eAct)
{
	if (camProp.GetAccessMode() == GENAPI::EAccessMode::NA)
		return DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		try
		{
			auto mmProp = dynamic_cast<MM::Property*>(pProp);
			if (mmProp != nullptr) {
				mmProp->SetReadOnly(camProp.GetAccessMode() != GENAPI::EAccessMode::RW);

				mmProp->ClearAllowedValues();
				GENAPI::StringList_t propertyValues;
				camProp.GetSymbolics(propertyValues);
				for (int i = 0; i < propertyValues.size(); i++)
					mmProp->AddAllowedValue(propertyValues[i].c_str());
			}

			pProp->Set(camProp.GetCurrentEntry()->GetSymbolic());
		}
		catch (SPKR::Exception &ex)
		{
			//SetErrorText(SPKR_ERROR, ("Could not read " + pProp->GetName() + "! " + std::string(ex.what())).c_str());
			//return SPKR_ERROR;
			pProp->Set("");
		}
	}
	else if (eAct == MM::AfterSet)
	{
		try
		{
			std::string val;
			pProp->Get(val);

			camProp.FromString(GENICAM::gcstring(val.c_str()));
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ("Could not write " + pProp->GetName() + "! " + std::string(ex.what())).c_str());
			return SPKR_ERROR;
		}
	}
	return DEVICE_OK;
}



#endif // !_SPINNAKER_CAMERA_H_

