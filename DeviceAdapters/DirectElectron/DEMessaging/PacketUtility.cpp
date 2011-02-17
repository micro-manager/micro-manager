#include "PacketUtility.h"

PacketUtility::PacketUtility()
{
	this->persistent = false;
}

PacketUtility::PacketUtility(bool persist)
{
	this->persistent = persist;
}

PacketUtility::~PacketUtility()
{
}

void PacketUtility::add(int* val)
{
	this->add(val, string(""));
}

void PacketUtility::add(float* val)
{
	this->add(val, string(""));
}

void PacketUtility::add(string* val)
{
	this->add(val, string(""));
}

void PacketUtility::add(int* val, string label)
{
	this->_params.push_back(paramType(AnyParameter::P_INT, val, label));
}

void PacketUtility::add(float* val, string label)
{
	this->_params.push_back(paramType(AnyParameter::P_FLOAT, val, label));
}

void PacketUtility::add(string* val, string label)
{
	this->_params.push_back(paramType(AnyParameter::P_STRING, val, string("")));
}

void PacketUtility::clear()
{
	this->_params.clear();
	//this->paramTypes.clear();
	//this->paramVals.clear();
}

bool PacketUtility::getValues(const SingleCommand& singleCommand)
{
	// Check return types.
	bool formatIsCorrect = true;
	if (singleCommand.parameter_size() != this->_params.size())
		formatIsCorrect = false;

	if (formatIsCorrect)
	{
		for (unsigned int i = 0; i < this->_params.size(); i++)
		{
			if (singleCommand.parameter(i).type() != this->_params[i].get<0>())
			{
				formatIsCorrect = false;
				break;
			}
		}
	}

	// Everything checks out, get values.
	if (formatIsCorrect)
	{
		for (int i = 0; i < singleCommand.parameter_size(); i++)
		{
			switch (singleCommand.parameter(i).type())
			{
				case AnyParameter::P_INT:
				 (*(int*)(this->_params[i].get<1>())) = singleCommand.parameter(i).p_int();
				 break;
				case AnyParameter::P_BOOL:
				 (*(bool*)(this->_params[i].get<1>())) = singleCommand.parameter(i).p_bool();
				 break;
				case AnyParameter::P_FLOAT:
				 (*(float*)(this->_params[i].get<1>())) = singleCommand.parameter(i).p_float();
				 break;
				default: // not implemented
				 formatIsCorrect =  false;
			}
		}
	}

	if (!this->persistent) this->clear();
	return formatIsCorrect;
}

bool PacketUtility::getStrings(const SingleAcknowledge& singleAck, vector<string>& values)
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

bool PacketUtility::getValues(const SingleAcknowledge& singleAck)
{
	bool formatIsCorrect = true;
	if (singleAck.parameter_size() != this->_params.size())
		formatIsCorrect =  false;

	// Check return types.
	if (formatIsCorrect)
	{
		for (unsigned int i = 0; i < this->_params.size(); i++)
		{
			if (singleAck.parameter(i).type() != this->_params[i].get<0>())
			{
				formatIsCorrect = false;
				break;
			}
		}
	}

	// Everything checks out, get values.
	if (formatIsCorrect)
	{	
		for (int i = 0; i < singleAck.parameter_size(); i++)
		{
			switch (singleAck.parameter(i).type())
			{
				case AnyParameter::P_INT:
				 (*reinterpret_cast<int*>(this->_params[i].get<1>())) = singleAck.parameter(i).p_int();
				 break;
				case AnyParameter::P_BOOL:
				 (*reinterpret_cast<bool*>(this->_params[i].get<1>())) = singleAck.parameter(i).p_bool();
				 break;
				case AnyParameter::P_FLOAT:
				 (*reinterpret_cast<float*>(this->_params[i].get<1>())) = singleAck.parameter(i).p_float();
				 break;
				case AnyParameter::P_STRING:
				  (*reinterpret_cast<string*>(this->_params[i].get<1>())) = singleAck.parameter(i).p_string();
				  break;
				default: // not implemented
				 return false;
			}
		}
	}
	if (!this->persistent) this->clear();
	return true;
}

