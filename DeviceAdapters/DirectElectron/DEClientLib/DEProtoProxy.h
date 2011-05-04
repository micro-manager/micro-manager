#pragma once

#include <string>
#include <vector>

#include "DENetwork.h"
#include "DEServer.pb.h"
#include "DEConstants.h"
#include "PacketParser.h"
#include "PacketCreator.h"
#include "VariableBuffer.h"
#include "PropertyHelper.h"

using namespace DirectElectronPlugin;
using namespace DEMessaging;
using namespace std;

class DEProtoProxy
{
	public:
		DEProtoProxy();
		~DEProtoProxy();
		bool connect(const char* ip, int rPort, int wPort);
		bool close();

		// Config Functions
		void set_ParamTimeout(size_t seconds);
		void set_ImageTimeout(size_t seconds);

		// Set Functions
		bool set_Property(string prop, AnyP val);
		bool set_CameraName(string name);		

		// Get Functions
		bool get_Image(void* image, unsigned int length);
		bool get_CameraNames(vector<string>& names);
		bool get_Properties(vector<string>& props);
		bool get_PropertySettings(string prop, PropertyHelper& settings);
		bool get_Property(string label, string& val);
		bool get_Property(string label, float& val);
		bool get_Property(string label, double& val); // Does a double to float comparison
		bool get_Property(string label, int& val);

	private:
		DENetwork* server;
		string _cameraName;
		bool sendToServer(DEPacket& pkt);
		bool receiveFromServer(DEPacket& pkt);

		VariableBuffer sendBufferNew;
		VariableBuffer recvBufferNew;
		VariableBuffer imgBuffer;

		// Helper functions for sending the different classes of messages.
		bool getValues(PacketParser& util, type cmd);
		bool getStrings(PacketParser& util, type cmd, vector<string>& values);
		bool setValues(PacketCreator& util, type cmd);	
		bool getPropertyValue(PacketParser& util, string label);
		bool setPropertyValue(PacketCreator& util);

		// Responsible for sending message and making sure that the correct
		// acknowledgement is received.  
		bool sendCommand(DEPacket& message);
		bool sendCommand(DEPacket& message, DEPacket& reponse);

		size_t imageTimeout; 
		size_t paramTimeout;
};
