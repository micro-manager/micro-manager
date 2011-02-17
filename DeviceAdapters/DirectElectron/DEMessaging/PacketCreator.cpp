#include "PacketCreator.h"

PacketCreator::PacketCreator()
{
	this->_persistent = false;
}

PacketCreator::PacketCreator(bool persistent)
{
	this->_persistent = true;
}

PacketCreator::~PacketCreator()
{
}

void PacketCreator::add(AnyP value)
{
	this->add(value, string(""));
}

void PacketCreator::add(AnyP value, string label)
{
	_params.push_back(Param(value, label));
}

void PacketCreator::clear()
{
	_params.clear();
}

void PacketCreator::addSingleAcknowledge(DEPacket& message, type cmdVal)
{
	message.set_type(DEPacket::P_ACKNOWLEDGE);
	SingleAcknowledge * ack = message.add_acknowledge();
	ack->set_command_id(cmdVal);
	ack->set_error(false);

	vector<Param>::iterator  it;
	for (it = this->_params.begin(); it != this->_params.end(); it++)
	{
		AnyParameter* param = ack->add_parameter();
		this->setAnyParameter(param, *it);
	}

	if (!this->_persistent) this->clear();
}


void PacketCreator::addSingleCommand(DEPacket& message, type cmdVal)
{
	message.set_type(DEPacket::P_COMMAND);
	SingleCommand* cmd = message.add_command();
	cmd->set_command_id(cmdVal);

	vector<Param>::iterator  it;
	for (it = this->_params.begin(); it != this->_params.end(); it++)
	{
		AnyParameter* param = cmd->add_parameter();
		this->setAnyParameter(param, *it);
	}

	if (!this->_persistent) this->clear();
}

void PacketCreator::setAnyParameter(AnyParameter* param, Param& val)
{
	param->set_name(val.get<1>());
	param->set_type(boost::apply_visitor(AnyPVisitor(), 
		val.get<0>()));
	switch (param->type())
	{
		case AnyParameter::P_INT:
			param->set_p_int(boost::get<int>(val.get<0>()));
			break;
		case AnyParameter::P_BOOL:
			param->set_p_bool(boost::get<bool>(val.get<0>()));
			break;
		case AnyParameter::P_FLOAT:
			param->set_p_float(boost::get<double>(val.get<0>()));
			break;
		case AnyParameter::P_STRING:
			param->set_p_string(boost::get<string>(val.get<0>()));
			break;
	}
}

void PacketCreator::addError(DEPacket& message, type cmdVal, string text)
{
	message.set_type(DEPacket::P_ACKNOWLEDGE);
	SingleAcknowledge* ack = message.add_acknowledge();
	ack->set_command_id(cmdVal);
	ack->set_error(true);
	ack->set_error_message(text);

	if (!this->_persistent) this->clear();
}	
