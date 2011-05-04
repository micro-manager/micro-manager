#pragma once

class DEProxyInterface
{
	public:
		virtual bool connect(const char* ip, int rPort, int wPort) = 0;
		virtual bool close() = 0;				
		virtual bool get_Image(void* image, unsigned int length) = 0;
};
