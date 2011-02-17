#include "PacketParser.h"

PacketParser::PacketParser()
{
	this->_persistent = false;
}

PacketParser::PacketParser(bool persist)
{
	this->_persistent = persist;
}

PacketParser::~PacketParser()
{
}

void PacketParser::add(int* val)
{
	this->add(val, string(""));
}

void PacketParser::add(float* val)
{
	this->add(val, string(""));
}

void PacketParser::add(string* val)
{
	this->add(val, string(""));
}

void PacketParser::add(int* val, string label)
{
	this->_params.push_back(ParamType(AnyParameter::P_INT, val, label));
}

void PacketParser::add(float* val, string label)
{
	this->_params.push_back(ParamType(AnyParameter::P_FLOAT, val, label));
}

void PacketParser::add(string* val, string label)
{
	this->_params.push_back(ParamType(AnyParameter::P_STRING, val, string("")));
}

void PacketParser::clear()
{
	this->_params.clear();
}


bool PacketParser::getStrings(const SingleAcknowledge& singleAck, vector<string>& values)
{
	bool success = false;
	values.clear();
	for (int i = 0; i < singleAck.parameter_size(); i++)
	{
		if (singleAck.parameter(i).type() == AnyParameter::P_STRING)
		{
			values.push_back(singleAck.parameter(i).p_string());
			success = true;
		}
	}
	return success;
}


void PacketParser::_writeValue(const AnyParameter& param, ParamType& writeTo)
{
	switch (param.type())
	{
		case AnyParameter::P_INT:
			(*reinterpret_cast<int*>(writeTo.get<1>())) = param.p_int();
			break;
		case AnyParameter::P_BOOL:
			(*reinterpret_cast<bool*>(writeTo.get<1>())) = param.p_bool();
			break;
		case AnyParameter::P_FLOAT:
			(*reinterpret_cast<float*>(writeTo.get<1>())) = param.p_float();
			break;
		case AnyParameter::P_STRING:
			(*reinterpret_cast<string*>(writeTo.get<1>())) = param.p_string();
			break;
	}
}
