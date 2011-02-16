#pragma once
#include "DENetwork.h"
#include "DEProxyInterface.h"

namespace DirectElectronPlugin
{
	// Packet types
	struct IntPair
	{
		int x;
		int y;
	};

	struct FloatPair
	{
		float x;
		float y;
	};

	class DECamProxy : public DEProxyInterface
	{
	public:
		DECamProxy(void);
		~DECamProxy(void);
		virtual bool connect(const char* ip, int rPort, int wPort);
		virtual bool close();
		virtual bool set_Binning(int x, int y);
		virtual bool get_Binning(int& x, int& y);
		virtual bool set_Offset(int x, int y);
		virtual bool get_Offset(int& x, int& y);
		virtual bool set_Dimension(int x, int y);
		virtual bool get_Dimension(int &x, int &y);
		virtual bool set_ExposureMode(int mode);
		virtual bool get_ExposureMode(int& mode);
		virtual bool set_ExposureTime(float time);
		virtual bool get_ExposureTime(float& time);
		virtual bool get_PixelSize(float& x, float& y);
		virtual bool get_Temperature(float& temp);
		virtual bool get_FaradayPlateVal(float& val);
		virtual bool set_IntegrationTime(float time);
		virtual bool get_IntegrationTime(float& time);
		virtual bool get_Image(void* image, unsigned int length);

	private:
	  PropertyPacket<IntPair>* binning;
	  PropertyPacket<float>* exposureTime;
	  // Used for determining the ROI.
	  PropertyPacket<IntPair>* dimension;
	  PropertyPacket<IntPair>* offset;
	  PropertyPacket<int>* exposureMode;
	  PropertyPacket<FloatPair>* pixelSize;
	  PropertyPacket<float>* temperature;
	  PropertyPacket<float>* faradayPlateVal;
	  PropertyPacket<float>* integrationTime;
	  BufferPacket* remoteImage;

		////////////////////////////////////////////////////////////////////////////////////////////////////
		/// <summary>	Saves a set of information to the server.</summary>
		///
		/// <remarks>	Sunny, 5/28/2010. </remarks>
		///
		/// <param name="pkt">	[in] The packet of information to be sent. </param>
		///
		/// <returns>	true if it succeeds, false if it fails. </returns>
		////////////////////////////////////////////////////////////////////////////////////////////////////

		bool saveToServer(BasicPacket* pkt);

		////////////////////////////////////////////////////////////////////////////////////////////////////
		/// <summary>	Loads a set of information from the server. </summary>
		///
		/// <remarks>	Sunny, 5/28/2010. </remarks>
		///
		/// <param name="pkt">	[out] The packet of information to be received. </param>
		///
		/// <returns>	true if it succeeds, false if it fails. </returns>
		////////////////////////////////////////////////////////////////////////////////////////////////////

		bool loadFromServer(BasicPacket* pkt);

		DENetwork* server;
	};
}
