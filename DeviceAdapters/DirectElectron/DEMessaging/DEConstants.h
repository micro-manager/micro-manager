#pragma once

#ifndef DECONSTANTS_H
#define DECONSTANTS_H

#include <string>
#include <boost/variant.hpp>
#include <boost/tuple/tuple.hpp>
#include "DEServer.pb.h"

namespace DEMessaging {

	typedef boost::variant<int, double, bool, std::string> AnyP;

	class AnyPVisitor : public boost::static_visitor<AnyParameter::Type>
	{
	public:
		AnyParameter::Type operator()(int v) const { return AnyParameter::P_INT; }
		AnyParameter::Type operator()(double v) const  { return AnyParameter::P_FLOAT; }
		AnyParameter::Type operator()(bool v) const  { return AnyParameter::P_BOOL; }
		AnyParameter::Type operator()(std::string v) const { return AnyParameter::P_STRING; }
	};

	enum type {
		kEnumerateCameras = 0,
		kEnumerateProperties,
		kGetAllowableValues,
		kGetProperty,
		kSetProperty,
		k_GetImage
	};
}
#endif