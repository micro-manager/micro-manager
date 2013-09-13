#include "devicelist.h"
#include <string.h>
#include <algorithm>
#include <cstdio>

devicelist::devicelist(void)
{
}
devicelist::~devicelist(void)
{
}
bool devicelist::isStage(std::string name){
	std::transform(name.begin(),name.end(),name.begin(),::toupper);
	name.assign(name.begin(), remove_if(name.begin(), name.end(), &ispunct));
	if(	
		//nanoX
		name == "NANOX" || name == "NANOSX" ||
		/*
		name == "NANOX200" || name == "NANOX200SG" || name == "NANOX200CAP" ||
		name == "NANOX400" || name == "NANOX400SG" || name == "NANOX400CAP" ||
		name == "NANOSX400" || name == "NANOSX400CAP" || name == "NANOSX400CAPDIG" ||
		name == "NANOSX800" || name == "NANOSX800CAP" || name == "NANOSX800CAPDIG" ||
		name == "NANOX240SG45" || name == "NANOX240SG45°"||
		*/

		//X positionierer
		name == "PX" || name == "PU" ||
		/*
		name == "PX38" || name == "PX38SG" ||
		name == "PX50" || name == "PX50CAP" ||
		name == "PX100" || name == "PX100SG" ||
		name == "PX200" || name == "PX200SG" || name == "PX200CAP" ||
		name == "PX300" || name == "PX300SG" || name == "PX300CAP" ||
		name == "PX400" || name == "PX400SG" || name == "PX400CAP" ||
		name == "PX500" || name == "PX1500" ||
		name == "PU40"  || name == "PU40SG"  ||
		name == "PU90" || name == "PU90SG" ||
		name == "PU100" || name == "PU100SG" ||
		name == "PU65HR" ||
		*/
		
		//Z Positionierer
		name == "PZ" ||
		/*
		name == "PZ10" ||
		name == "PZ16" || name == "PZ16CAP" ||
		name == "PZ38" || name == "PZ38SG" || name == "PZ38CAP" |
		name == "PZ100" || name == "PZ100SG" || name == "PZ100CAP" ||
		name == "PZ250" || name == "PZ250SG" ||
		name == "PZ300" || name == "PZ300AP" || name == "PZ100CAPAP" ||
		name == "PZ400" || name == "PZ400SG" || name == "PZ400OEM" || name == "PZ400SGOEM" ||
		name == "PZ200" || name == "PZ200OEM" || name == "PZ200SGOEM" ||
		name == "PZ8D12" || name == "PZ8D12SG" ||
		name == "PZ20D12" || name == "PZ20D12SG" ||
		*/
		//MIPOS
		name == "MIPOS" || name == "NANOMIPOS" ||
		/*
		name == "MIPOS20" || name == "MIPOS20SG"||
		name == "MIPOS100" || name == "MIPOS100UD" || name == "MIPOS100SG" || name == "MIPOS100SGUD" ||
		name == "MIPOS100PL" || name == "MIPOS100PLSG" || name == "MIPOS100PLCAP" ||
		name == "MIPOS250" || name == "MIPOS250SG" || name == "MIPOS250CAP" ||
		name == "MIPOS5500" || name == "MIPOS500UD" || name == "MIPOS500SG" || name == "MIPOS500SGUD" ||
		name == "NANOMIPOS400" || name == "NANOMIPOS400CAP" ||
		name == "MIPOSN10/2" || name == "MIPOSN10/2CAP" ||
		name == "MIPOS16-158" 
		*/
		//Stapelaktoren
		name == "N" ||
		name == "P" || name == "PA" || name == "PHL" || name == "PAHL" ||
		name == "R" || name == "RA" ||
		name == "PAT" || name == "PS" ||
		name == "HP" || name == "HPA"
		)
	{
		return true;
	}
	return false;
}
bool devicelist::isShutter(std::string name){
	std::transform(name.begin(),name.end(),name.begin(),::toupper);
	if( name =="PZS"
		/*
		name =="PZS1" ||
		name =="PZS2" ||
		name =="PZS3" ||
		name =="PZS4"
		*/
		)
	{
		return true;
	}
	return false;
}

bool devicelist::isXYStage(std::string name){
	std::transform(name.begin(),name.end(),name.begin(),::toupper);
	if( 	
		//nanoXY
		name == "NANOSXY" ||
		/*
		name == "NANOSXY120" || name == "NANOSXY120CAP" ||
		name == "NANOSXY400" || name == "NANOSXY400CAP" ||
		*/
		//XY Positionierer
		name == "PXY"
		/*
		name == "PXY38" ||	name == "PXY38SG" ||	
		name == "PXY100" || name == "PXY100SG" ||name == "PXY100CAP" ||
		name == "PXY200" || name == "PXY200SG" ||
		name == "PXY201" ||	name == "PXY201CAP" || name == "PXY201CAPDIG" ||	 
		name == "PXY400" ||
		name == "PXY40D12" ||
		name == "PXY80D12" || name == "PXY80D12SG" || name == "PXY80D12CAP" ||
		name == "PXY200D12" || name == "PXY200D12SG" || name == "PXY200D12CAP" ||
		name == "PXY24AP" || name == "PXY500AP" ||
		name == "PXY16ENV" || name == "PXY16CAP" || name == "PXY16CAPEXTERN" || 
		name == "PXY16CAPDIG" || name == "PXY16CAPDIGITAL" 
		*/
		)
	{
		return true;
	}
	return false;
}
bool devicelist::isTritor(std::string name){
	std::transform(name.begin(),name.end(),name.begin(),::toupper);
	if( 
		name == "MICROTRITOR" ||		
		name == "MINITRITOR" ||
		name == "TRITOR"
		/*
		name == "MICROTRITOR" ||		
		name == "MINITRITOR" ||
		name == "TRITOR38" || name == "TRITOR38SG" ||
		name == "TRITOR50" || name == "TRITOR50CAP" ||	 	
		name == "TRITOR100" || name == "TRITOR100SG" || name == "TRITOR100CAP"||
		name == "TRITOR101" || name == "TRITOR101SG" || name == "TRITOR101CAP"||
		name == "TRITOR102" || name == "TRITOR102SG" || name == "TRITOR102CAP"||
		name == "TRITOR320" || name == "TRITOR320CAP" ||
		name == "TRITOR400" || name == "TRITOR400SG" || name == "TRITOR400CAP"
		*/
		)
	{
		return true;
	}
	return false;
}

bool devicelist::isMirror(std::string name){
	return (name=="PSH")?true:false;
}

//Mirror with 1 axis
bool devicelist::isMirror1(std::string name){
	if(	name == "PSH4/1" ||
		name == "PSH0.3/1" ||name == "PSH15/1" || name == "PSH30/1" ||
		name == "PSH35" || name == "PSH35SG"
		)
	{
		return true;
	}
	return false;
}
//Mirror with 2 axis
bool devicelist::isMirror2(std::string name){
	if( name == "PKS1" ||
		name == "PSH1" || name == "PSH2" || name == "PSH3" || name == "PSH4" ||
		name == "PSH1SG" || name == "PSH2SG" || name == "PSH3SG" || name == "PSH4SG" ||
		name == "PSH8" || name == "PSH8SG" ||
		name == "PSH5/2" || name == "PSH5/2SG" || 
		name == "PSH10/2" || name == "PSH10/2SG" 
		)
	{
		return true;
	}
	return false;
}
//Mirror with 3 axis
bool devicelist::isMirror3(std::string name){
	if( 
		name == "PSH1Z" || name == "PSH2Z" || name == "PSH3Z" || name == "PSH4Z" ||
		name == "PSH1ZSG" || name == "PSH2ZSG" || name == "PSH3ZSG" || name == "PSH4ZSG" 
		)
	{
		return true;
	}
	return false;
}
