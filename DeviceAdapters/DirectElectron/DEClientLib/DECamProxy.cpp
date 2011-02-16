#include "DECamProxy.h"


DECamProxy::DECamProxy(void)
{
   this->server = DENetwork::getInstance();

   // set up properties
   this->binning = new PropertyPacket<IntPair>(0, false);
   this->dimension = new PropertyPacket<IntPair>(4, false);
   this->offset = new PropertyPacket<IntPair>(2, false);
   this->exposureTime = new PropertyPacket<float>(6, false);
   this->pixelSize = new PropertyPacket<FloatPair>(13, true);
   this->exposureMode = new PropertyPacket<int>(15, false);
   this->faradayPlateVal = new PropertyPacket<float>(17, true);
   this->remoteImage = new BufferPacket(12, NULL, 0);
   this->temperature = new PropertyPacket<float>(18, true);
   this->integrationTime = new PropertyPacket<float>(22, false);
}


DECamProxy::~DECamProxy(void)
{
	delete this->binning;
	delete this->exposureTime;
	delete this->dimension;
	delete this->offset;
	delete this->pixelSize;
	delete this->exposureMode;
	delete this->faradayPlateVal;
	delete this->temperature;
	delete this->integrationTime;
	delete this->remoteImage;
}


bool DECamProxy::set_Offset(int x, int y)
{	
  this->offset->getData()->x = x;
  this->offset->getData()->y = y;

	return this->saveToServer(this->offset);
}

bool DECamProxy::get_Offset(int& x, int& y)
{	
	if (this->loadFromServer(this->offset))
	{
		x = this->offset->getData()->x;
		y = this->offset->getData()->y;
		return true;
	}
	return false;
}

bool DECamProxy::set_Dimension(int x, int y)
{
  this->dimension->getData()->x = x;
  this->dimension->getData()->y = y;
	return this->saveToServer(this->dimension);;
}

bool DECamProxy::get_Dimension(int &x, int &y)
{
	if (this->loadFromServer(this->dimension))
	{
		x = this->dimension->getData()->x;
		y = this->dimension->getData()->y;
		return true;
	}

	return false;
}


bool DECamProxy::set_Binning(int x, int y)
{
  this->binning->getData()->x = x;
  this->binning->getData()->y = y;
	return this->saveToServer(this->binning);;
}

bool DECamProxy::get_Binning(int& x, int& y)
{
	if (this->loadFromServer(this->binning))
	{
		x = this->binning->getData()->x;
		y = this->binning->getData()->y;
		return true;
	}

	return false;
}

bool DECamProxy::set_ExposureMode(int mode)
{
	*this->exposureMode->getData() = mode;
	return this->saveToServer(this->exposureMode);
}

bool DECamProxy::get_ExposureMode(int& mode)
{
	if (this->loadFromServer(this->exposureMode))
	{
		mode = *this->exposureMode->getData();
		return true;
	}
	return false;
}

bool DECamProxy::set_ExposureTime(float time)
{
	*this->exposureTime->getData() = time;
	return this->saveToServer(this->exposureTime);
}

bool DECamProxy::get_ExposureTime(float& time)
{
	if (this->loadFromServer(this->exposureTime))
	{
		time = *this->exposureTime->getData();
		return true;
	}
	return false;
}
bool DECamProxy::get_PixelSize(float& x, float& y)
{
	if (this->loadFromServer(this->pixelSize))
	{
		x = this->pixelSize->getData()->x;
		y = this->pixelSize->getData()->y;
		return true;
	}

	return false;
}
bool DECamProxy::get_Temperature(float& temp)
{
	if (this->loadFromServer(this->temperature))
	{
		temp = *this->temperature->getData();
		return true;
	}
	return false;
}
bool DECamProxy::get_FaradayPlateVal(float& val)
{
	if (this->loadFromServer(this->faradayPlateVal))
	{
		val = *this->faradayPlateVal->getData();
		return true;
	}
	return false;
}

bool DECamProxy::set_IntegrationTime(float time)
{
	*this->integrationTime->getData() = time;
	return this->saveToServer(this->integrationTime);
}

bool DECamProxy::get_IntegrationTime(float& time)
{
	if (this->loadFromServer(this->integrationTime))
	{
		time = *this->integrationTime->getData();
		return true;
	}
	return false;
}

bool DECamProxy::get_Image(void* image, unsigned int length)
{
	this->remoteImage->setNewBuffer(image, length);
	return this->loadFromServer(this->remoteImage);
}

bool DECamProxy::connect(const char* ip, int rPort, int wPort)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());
	return this->server->connect(ip, rPort, wPort);
}

bool DECamProxy::close()
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());

	return this->server->close();
}

bool DECamProxy::loadFromServer(BasicPacket* pkt)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());

	const int dataSize = 3*sizeof(int);
	
	// Create request
	byte data[dataSize];
	memset(data, 0, dataSize);
	((int*)data)[0] = pkt->getCommand();
	
	bool success = true;
	success = (success && this->server->send((void*)data, dataSize));
	success = (success && this->server->receive(pkt->getDataAsVoidPtr(), pkt->getDataSize()));

	return success;
}

bool DECamProxy::saveToServer(BasicPacket* pkt)
{
	boost::lock_guard<boost::mutex>(*this->server->getTransactionMutex());

	if (pkt->isReadOnly()) return false;

	// Making assumptions about the size of an int and the size of a command packet.
	// Assert if these assumptions are invalid.
	const int dataSize = 3*sizeof(int);
	assert(pkt->getDataSize() + sizeof(int) <= dataSize);

	byte data[dataSize];

	((int*)data)[0] = pkt->getCommand() + 1;
	memset(data + sizeof(int), 0, 8);
	memcpy(data + sizeof(int), pkt->getDataAsVoidPtr(), pkt->getDataSize());

	return this->server->send((void*)data, dataSize);  
}
