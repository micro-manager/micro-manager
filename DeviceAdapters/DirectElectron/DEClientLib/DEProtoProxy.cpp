#include <cassert>
#include "DEProtoProxy.h"
#include "DEExceptions.h"
#include "VariableBuffer.h"

/**
 * Using non standard C++ code for extracting function names.
 * __FUNCSIG__ can be replaced with __PRETTY_FUNCTION__ with GNU compiler
 * http://msdn.microsoft.com/library/b0084kay.aspx
 */
DEProtoProxy::DEProtoProxy()
{
	this->server = DENetwork::getInstance();
	this->_cameraName.clear();
	this->set_ParamTimeout(30);
	this->set_ImageTimeout(120);
}

DEProtoProxy::~DEProtoProxy()
{
}

bool DEProtoProxy::connect(const char* ip, int rPort, int wPort)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	return this->server->connect(ip, (port)rPort, (port)wPort);
}

bool DEProtoProxy::close()
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	return this->server->close();
}

void DEProtoProxy::set_ParamTimeout(size_t seconds) 
{ 
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	this->paramTimeout = seconds; 
}

void DEProtoProxy::set_ImageTimeout(size_t seconds) 
{ 
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	// Always wait a minimum paramTimeout (or 5 secs) when setting the 
	// image time out.
	this->imageTimeout = seconds + this->paramTimeout;
}

bool DEProtoProxy::get_CameraNames(vector<string>& names)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	PacketParser util;
	return this->getStrings(util, kEnumerateCameras, names);
}

bool DEProtoProxy::get_Properties(vector<string>& props)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	PacketParser util;
	return this->getStrings(util, kEnumerateProperties, props);
}

bool DEProtoProxy::get_PropertySettings(string prop, PropertyHelper& propSettings)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	DEPacket request, response;
	PacketCreator pktCreate;
	pktCreate.add(prop);
	pktCreate.addSingleCommand(request, kGetAllowableValues);
	bool success = this->sendCommand(request, response);
	
	success = success && propSettings.Parse(response);
	
	return success;
}

bool DEProtoProxy::set_CameraName(string name)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	vector<string> cameraNames;
	this->get_CameraNames(cameraNames);
	bool success = false;

	for (std::vector<string>::iterator it = cameraNames.begin(); it != cameraNames.end(); it++)
	{
		if (name.compare(*it) == 0)
		{
			success = true;
			this->_cameraName = name;
			break;
		}
	}

	return success;
}
bool DEProtoProxy::get_Image(void* image, unsigned int length)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());

	DEPacket pkt, dataHeader;
	pkt.set_type(DEPacket::P_COMMAND);
	SingleCommand* cmd = pkt.add_command();
	cmd->set_command_id(k_GetImage);

	this->sendCommand(pkt);
	this->receiveFromServer(dataHeader);
	if (dataHeader.type() != DEPacket::P_DATA_HEADER ||
		!dataHeader.has_data_header()) 
		BOOST_THROW_EXCEPTION(ProtocolException() << errorMessage("Did not receive expected data header for image."));
	
	this->imgBuffer.resizeIfNeeded(dataHeader.data_header().bytesize());

	if (!this->server->receive(this->imgBuffer.getBufferPtr(), dataHeader.data_header().bytesize(), this->imageTimeout))
	{
		BOOST_THROW_EXCEPTION(CommunicationException() << errorMessage("Unable to receive image from server."));
	}

	if (dataHeader.data_header().bytesize() != length)
	{
		BOOST_THROW_EXCEPTION(CommandException() << errorMessage ("Image received did not have the expected size"));
	}

	memcpy(image, this->imgBuffer.getBufferPtr(), length);

	return true;
}

// Possible exceptions that may occur here.
// 1. Unable to send command.
// 2. Networking problem: incorrect ack or nothing received.
// 3. Server error: unrecognized command.
// 4. Parsing error: values returned were not what was expected.
bool DEProtoProxy::getValues(PacketParser& util, type cmdVal)
{
	DEPacket pkt, response;
	PacketCreator utilSend;
	utilSend.addSingleCommand(pkt, cmdVal);

	if (!this->sendCommand(pkt, response))
		return false;  

	if (!util.getValues(response.acknowledge(0)))
		BOOST_THROW_EXCEPTION(ProtocolException() << errorMessage("Parameters returned do not match Get Expectations"));

	return true;
}

bool DEProtoProxy::getPropertyValue(PacketParser& util, string label)
{
	DEPacket pkt, response;
	PacketCreator utilSend;
	utilSend.add(label, string("label"));
	utilSend.addSingleCommand(pkt, kGetProperty);

	if (!this->sendCommand(pkt, response))
		return false;  

	if (!util.getValues(response.acknowledge(0)))
		BOOST_THROW_EXCEPTION(ProtocolException() << errorMessage("Parameters returned do not match Get Expectations"));

	return true;

}

bool DEProtoProxy::setPropertyValue(PacketCreator&util)
{
	DEPacket message;
	util.addSingleCommand(message, kSetProperty);

	return this->sendCommand(message);
}

bool DEProtoProxy::getStrings(PacketParser& util, type cmdVal, vector<string>& values)
{
	DEPacket pkt, response;
	PacketCreator utilSend;
	utilSend.addSingleCommand(pkt, cmdVal);

	if (!this->sendCommand(pkt, response))
		return false;
	if (!util.getStrings(response.acknowledge(0), values))
		BOOST_THROW_EXCEPTION(ProtocolException() << errorMessage("At least one value expected."));

	return true;
}

// Possible exceptions that may occur here.
// 1. Unable to send command.
// 2. Networking problem : for example incorrect ack or nothing received.
// 3. Problem w/ command : perhaps the values were out of bounds from the server (client code can handle)
bool DEProtoProxy::setValues(PacketCreator& util, type cmdVal)
{
	DEPacket message;
	util.addSingleCommand(message, cmdVal);

	return this->sendCommand(message);
}

bool DEProtoProxy::sendCommand(DEPacket& message)
{
	DEPacket response;

	return sendCommand(message, response); 
}

bool DEProtoProxy::sendCommand(DEPacket& message, DEPacket& response)
{
	assert(message.type() == DEPacket::P_COMMAND);

	this->sendToServer(message); 
	this->receiveFromServer(response);

	// Message format error. 
	if (response.type() != DEPacket::P_ACKNOWLEDGE
		|| response.acknowledge_size() != 1
		|| response.acknowledge(0).command_id() != message.command(0).command_id() 
		)
	{		 
		BOOST_THROW_EXCEPTION(ProtocolException() << errorMessage("Did not receive a proper acknowledgement.")
			<< errorPacket(message)); //error occurred
	}

	if (response.acknowledge(0).error())
	{
		BOOST_THROW_EXCEPTION(CommandException() << errorMessage(response.acknowledge(0).error_message().c_str())
			<< errorPacket(message)); //error occurred
	}
	return true;
}

bool DEProtoProxy::sendToServer(DEPacket& pkt)
{
	int sz = sizeof(unsigned long);
	pkt.set_camera_name(this->_cameraName);

	int pktSize = pkt.ByteSize();

	this->sendBufferNew.resetOffset();
	this->sendBufferNew.add(pktSize);
	this->sendBufferNew.add(pkt);

	if (!this->server->send(this->sendBufferNew.getBufferPtr(), pktSize + sz, 5))
	{
		BOOST_THROW_EXCEPTION(CommunicationException() << errorMessage("Unable to send message to server")
			<< boost::errinfo_file_name(__FILE__)
			<< boost::errinfo_at_line(__LINE__)
			<<errorPacket(pkt));
	}

	return true;
}

bool DEProtoProxy::receiveFromServer(DEPacket& pkt)
{
	long pktSize = 0;

	// Get Packet size.
	if (!this->server->receive((void*)&pktSize, sizeof(unsigned long), this->paramTimeout))
	{
		BOOST_THROW_EXCEPTION(CommunicationException() << errorMessage("Unable to receive message size from server.\n"));
	}
	else { 
		this->recvBufferNew.resizeIfNeeded(pktSize);
	}

	if (!this->server->receive((void*)this->recvBufferNew.getBufferPtr(), pktSize, this->paramTimeout * 2))
	{
		BOOST_THROW_EXCEPTION(CommunicationException() << errorMessage("Unable to receive message from server.\n"));
	}

	if (!pkt.ParseFromArray(this->recvBufferNew.getBufferPtr(), pktSize))
	{
		BOOST_THROW_EXCEPTION(CommunicationException() << errorMessage("Unable to parse message on receipt from server.\n"));
	}

	return true;
}

bool DEProtoProxy::get_Property(string label, string& val)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	PacketParser parser;
	parser.add(&val);
	return this->getPropertyValue(parser, label);
}

bool DEProtoProxy::get_Property(string label, int& val)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	PacketParser parser;
	parser.add(&val);
	return this->getPropertyValue(parser, label);
}


bool DEProtoProxy::get_Property(string label, float& val)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	PacketParser parser;
	parser.add(&val);	
	return this->getPropertyValue(parser, label);
}

bool DEProtoProxy::get_Property(string label, double& val)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	float temp;  
	PacketParser parser;
	parser.add(&temp);	
	bool retVal = this->getPropertyValue(parser, label);
	val = temp;
	return retVal;
}

bool DEProtoProxy::set_Property(string prop,  AnyP val)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	PacketCreator creator;
	creator.add(prop, string("label"));
	creator.add(val, string("val"));

	return this->setPropertyValue(creator);
}
