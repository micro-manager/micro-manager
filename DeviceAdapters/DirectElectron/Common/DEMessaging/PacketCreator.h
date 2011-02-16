#pragma once

#include <vector>
#include <string>
#include "DEConstants.h"
#include "DEServer.pb.h"

using namespace std;
using namespace DEMessaging;

namespace DEMessaging
{
	typedef boost::tuple<AnyP, std::string> Param;

	class PacketCreator
	{
		public:
			PacketCreator();
			PacketCreator(bool persistent);
			~PacketCreator();
			void add(AnyP value, string label);
			void add(AnyP value);
			void clear();

			// Creation commands
			void addSingleAcknowledge(DEPacket& message, type cmd);
			void addSingleCommand(DEPacket& message, type cmd);
			void addError(DEPacket& message, type cmd, string text);
		private:
			vector<Param> _params;
			bool _persistent;

			void setAnyParameter(AnyParameter* param, Param& val);
	};
}
