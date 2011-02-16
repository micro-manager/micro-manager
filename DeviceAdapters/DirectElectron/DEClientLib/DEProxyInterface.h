#pragma once

class DEProxyInterface
{
	public:
		virtual bool connect(const char* ip, int rPort, int wPort) = 0;
		virtual bool close() = 0;
		virtual bool set_Binning(int x, int y) = 0;
		virtual bool get_Binning(int& x, int& y) = 0;
		virtual bool set_Offset(int x, int y) = 0;
		virtual bool get_Offset(int& x, int& y) = 0;
		virtual bool set_Dimension(int x, int y) = 0;
		virtual bool get_Dimension(int &x, int &y) = 0;
		virtual bool set_ExposureMode(int mode) = 0;
		virtual bool get_ExposureMode(int& mode) = 0;
		virtual bool set_ExposureTime(float time) = 0;
		virtual bool get_ExposureTime(float& time) = 0;
		virtual bool get_PixelSize(float& x, float& y) = 0;
		virtual bool get_Temperature(float& temp) = 0;
		virtual bool get_FaradayPlateVal(float& val) = 0;
		virtual bool set_IntegrationTime(float time) = 0;
		virtual bool get_IntegrationTime(float& time) = 0;
		virtual bool get_Image(void* image, unsigned int length) = 0;
};
