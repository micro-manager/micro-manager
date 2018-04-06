///////////////////////////////////////////////////////////////////////////////
// FILE:          XimeaCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   XIMEA camera module.
//
// AUTHOR:        Marian Zajko, <marian.zajko@ximea.com>
// COPYRIGHT:     Marian Zajko and XIMEA GmbH, Münster, 2011
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

#include "DeviceBase.h"
#include "ImgBuffer.h"
#include "DeviceThreads.h"
#include "ImgBuffer.h"
#include "xiApiPlus/xiapiplus.h"
#include "pugixml.hpp"
#include <string>
#include <sstream>

//////////////////////////////////////////////////////////////////////////////

#define DEV_SN_LEN   64		// length of device serial number string
#define DEF_EXP_TIME 10000  // 10ms default exposure time

//////////////////////////////////////////////////////////////////////////////

#define CAM_PARAM_ACQ_TIMEOUT "Acquisition timeout [ms]"
#define DEFAULT_ACQ_TOUT_MS    1000
#define DEFAULT_ACQ_TOUT_STR  "1000"
#define ACQ_TIMEOUT_MIN_MS     0
#define ACQ_TIMEOUT_MAX_MS     1E5

//////////////////////////////////////////////////////////////////////////////

class XiSequenceThread;
class XimeaParam;

//////////////////////////////////////////////////////////////////////////////

class XimeaCamera : public CCameraBase<XimeaCamera>
{
public:
	XimeaCamera(const char* name);
	~XimeaCamera();

	//////////////////////////////////////////////////////////////
	// MMDevice API
	void GetName(char* name) const;
	int  Initialize();
	int  Shutdown();
	//////////////////////////////////////////////////////////////
	int                  SnapImage();
	const unsigned char* GetImageBuffer();
	unsigned             GetNumberOfComponents()  const { return nComponents_;};
	//////////////////////////////////////////////////////////////
	unsigned GetImageWidth() const;
	unsigned GetImageHeight() const;
	//////////////////////////////////////////////////////////////
	unsigned GetImageBytesPerPixel() const;
	unsigned GetBitDepth() const;
	long     GetImageBufferSize() const;
	//////////////////////////////////////////////////////////////
	int      SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize);
	int      GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);
	int      ClearROI();
	//////////////////////////////////////////////////////////////
	double   GetExposure() const;
	void     SetExposure(double exp);
	//////////////////////////////////////////////////////////////
	int      GetBinning() const;
	int      SetBinning(int binSize);
	int      IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}
	//////////////////////////////////////////////////////////////
	bool     SupportsMultiROI();
	bool     IsMultiROISet();
	int      GetMultiROICount(unsigned& count);
	int      SetMultiROI(const unsigned* xs, const unsigned* ys, const unsigned* widths, const unsigned* heights, unsigned numROIs);
	int      GetMultiROI(unsigned* xs, unsigned* ys, unsigned* widths, unsigned* heights, unsigned* length);
	//////////////////////////////////////////////////////////////
	int      PrepareSequenceAcqusition() { return DEVICE_OK; }
	int      StartSequenceAcquisition(double interval);
	int      StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
	int      StopSequenceAcquisition();
	int      InsertImage();
	int      CaptureImage();
	int      RunSequenceOnThread(MM::MMTime startTime);
	bool     IsCapturing();
	void     OnThreadExiting() throw(); ;
	//////////////////////////////////////////////////////////////
	// action interface
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPropertyChange(MM::PropertyBase* pProp, MM::ActionType eAct);
	XimeaParam* GetXimeaParam(string param_name, bool use_xiapi_param_name = false);
	
private:

	void   UpdateRoiParams();
	void   CreateCameraProperties();
	void   ParseCameraManifest(char* manifest);
	string ReadNodeData(pugi::xml_node nd, string feature_name);
	int    ReadNodeDataInt(pugi::xml_node nd, string feature_name);
	int    CountNodes(pugi::xml_node nd, string node_name);
	int    ResizeImageBuffer();
	friend class XiSequenceThread;

	xiAPIplus_Camera* camera;

	MM::MMTime        readoutStartTime_;
	MM::MMTime        sequenceStartTime_;
	MMThreadLock      imgPixelsLock_;
	XiSequenceThread* seq_thd_;

	std::string     device_name;
	bool            initialized_;
	ImgBuffer*      img_;
	xiAPIplus_Image image;
	std::vector<XimeaParam> cam_params;

	int          nComponents_;
	int          bytesPerPixel_;
	long         imageCounter_;
	bool         stopOnOverflow_;
	bool         isAcqRunning;
	int          roiX_;
	int          roiY_;
};

//////////////////////////////////////////////////////////////////////////////

class XiSequenceThread : public MMDeviceThreadBase
{
	enum { default_numImages=1, default_intervalMS = 100 };

public:

	XiSequenceThread(XimeaCamera* pCam);
	~XiSequenceThread();
	void Stop();
	void Start(long numImages, double intervalMs);
	bool IsStopped();
	void Suspend();
	bool IsSuspended();
	void Resume();
	double GetIntervalMs(){return intervalMs_;}
	void SetLength(long images) {numImages_ = images;}
	long GetLength() const {return numImages_;}
	long GetImageCounter(){return imageCounter_;}
	MM::MMTime GetStartTime(){return startTime_;}
	MM::MMTime GetActualDuration(){return actualDuration_;}

private:

	int svc(void) throw();
	XimeaCamera* camera_;
	bool stop_;
	bool suspend_;
	long numImages_;
	long imageCounter_;
	double intervalMs_;
	MM::MMTime startTime_;
	MM::MMTime actualDuration_;
	MM::MMTime lastFrameTime_;
	MMThreadLock stopLock_;
	MMThreadLock suspendLock_;
};

//////////////////////////////////////////////////////////////////////////////
// XML tag definitions

#define TAG_GROUP_NAME        "Group"
#define TAG_INTEGER_FEAT      "Integer"
#define TAG_BOOLEAN_FEAT      "Boolean"
#define TAG_ENUMERATION_FEAT  "Enumeration"
#define TAG_COMMAND_FEAT      "Command"
#define TAG_FLOAT_FEAT        "Float"
#define TAG_STRING_FEAT       "String"

#define TAG_DISP_NAME         "DisplayName"
#define TAG_ACCES_MODE        "ImposedAccessMode"
#define TAG_XIAPI_PARAM       "Xiapi_Par"
#define TAG_ENUM_ENTRY        "EnumEntry"
#define TAG_ENUM_VALUE        "Value"
#define TAG_IS_PATH           "IsPath"
#define TAG_APP_DEFAULT       "AppDefault"

#define FEAT_ACCESS_RO        "RO"
#define FEAT_ACCESS_WO        "WO"
#define FEAT_ACCESS_RW        "RW"

#define BOOL_VALUE_ON         "On"
#define BOOL_VALUE_OFF        "Off"

///////////////////////////////////////////////////////////////////////////////

enum type_t
{
	type_undef = 0,
	type_int,
	type_float,
	type_enum,
	type_bool,
	type_string,
	type_command
};

enum access_t
{
	access_undef = 0,
	access_readwrite,
	acces_read,
	acces_write
};

class XimeaParam
{
public:

	XimeaParam()
	{
		param_type = type_undef;
		access_type = acces_read;
		is_path = false;
	}

	void SetName(string name){ display_name = name; }
	void SetXiParamName(string name){xiapi_param_name = name;}
	void SetAppDefault(string val){ app_default = val; };
	void SetParamType(type_t type){ param_type = type; };
	void SetAccessType(string access)
	{
		if(access == FEAT_ACCESS_RW)      access_type = access_readwrite;
		else if(access == FEAT_ACCESS_RO) access_type = acces_read;
		else if(access == FEAT_ACCESS_WO) access_type = acces_write;
		else                              access_type = access_undef;
	};

	string GetName () { return display_name; }
	string GetXiParamName(){ return xiapi_param_name; };
	string GetAppDefault(){ return app_default; };
	type_t GetParamType(){ return param_type; };
	access_t GetAccessType(){ return access_type; };

	void SetParamTypePath(){ is_path = true; };
	bool IsParamTypePath(){ return is_path; };

	void AddEnumValue(string name, int value)
	{
		enum_values.insert(std::pair<string, int>(name, value) );
	}
	int CountEnumEntries(){ return (int) enum_values.size(); };
	vector<string> GetEnumValues()
	{
		vector<string> vals;
		for (std::map<string,int>::iterator it=enum_values.begin(); it!=enum_values.end(); ++it)
		{
			vals.push_back(it->first);
		}
		return vals;
	}
	int GetEnumValue(string val)
	{
		int value = 0;
		if ( enum_values.find(val) == enum_values.end() )
		{
			// not found
			throw(std::exception("Value was not found among enumerators"));
		}
		else
		{
			value = enum_values.at(val);
		}
		return value;
	}
	string GetEnumName(int value)
	{
		string str_val = "";
		for (map<string,int>::iterator it= enum_values.begin(); it!= enum_values.end(); ++it)
		{
			if(value == it->second)
			{
				str_val = it->first;
				break;
			}
		}
		return str_val;
	}
	void RemoveEnumItem(string val)
	{
		std::map<string,int>::iterator it;
		it = enum_values.find(val);
		if(it != enum_values.end())
		{
			enum_values.erase(val);
		}
	}

private:
	string   display_name;
	string   xiapi_param_name;
	string   app_default;
	type_t   param_type;
	access_t access_type;
	bool     is_path;
	std::map<string, int> enum_values;
};

//////////////////////////////////////////////////////////////////////////////
