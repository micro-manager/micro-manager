#ifndef datapackingH
#define datapackingH

#define EXTRACTLOWPACKED(SourcePtr) ( (SourcePtr[0] << 4) + (SourcePtr[1] & 0xF) )
#define EXTRACTHIGHPACKED(SourcePtr) ( (SourcePtr[2] << 4) + (SourcePtr[1] >> 4) )
#define ASSIGNLOWPACKED(SourcePtr, Value) { SourcePtr[0] = static_cast<unsigned char>(((Value) >> 4)); SourcePtr[1] = static_cast<unsigned char>((SourcePtr[1] & 0xF0) | ((Value) & 0xF)); }
#define ASSIGNHIGHPACKED(SourcePtr, Value) { SourcePtr[2] = static_cast<unsigned char>(((Value) >> 4)); SourcePtr[1] = static_cast<unsigned char>((SourcePtr[1] & 0x0F) | (((Value) & 0xF) << 4));}

#endif

