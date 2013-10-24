#ifndef AOISTRINGS_H_
#define AOISTRINGS_H_


class TAOIStrings
{
public:

   static const unsigned int NUMBER_R2_PREDEFINED_AOIS = 9;

   static const char * const AOI_R2LIST[NUMBER_R2_PREDEFINED_AOIS];

   static const std::string FULLIMAGE_AOI;

};

const std::string TAOIStrings::FULLIMAGE_AOI("Full Image");

const char * const TAOIStrings::AOI_R2LIST[NUMBER_R2_PREDEFINED_AOIS] =
{
   "Full Image",
   "2544x2160",
   "2064x2048",
   "1920x1080",
   "1776x1760",
   "1392x1040",
   " 528x512",
   " 240x256",
   " 144x128"
};



#endif //include only once

