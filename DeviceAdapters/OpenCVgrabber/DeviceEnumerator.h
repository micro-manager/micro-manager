#pragma once

#include <Windows.h>
#include <dshow.h>

#pragma comment(lib, "strmiids")

#include <map>
#include <string>

struct OpenCVDevice {
	int id; // This can be used to open the device in OpenCV
	std::string devicePath;
	std::string deviceName; // This can be used to show the devices to the user
};

class DeviceEnumerator {

public:

	std::map<int, OpenCVDevice> getDevicesMap(const GUID deviceClass);
	std::map<int, OpenCVDevice> getVideoDevicesMap();
	std::map<int, OpenCVDevice> getAudioDevicesMap();

private:

	std::string ConvertBSTRToMBS(BSTR bstr);
	std::string ConvertWCSToMBS(const wchar_t* pstr, long wslen);

};
