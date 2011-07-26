#ifndef ANDORVARTYPES_H
#define ANDORVARTYPES_H

#if !defined(huge)
#define huge
#endif

typedef char             andor8;
typedef unsigned char    andoru8;

typedef short             andor16;
typedef unsigned short    andoru16;

typedef wchar_t andorwc;

#ifdef linux
#ifdef _LP64
typedef int             andor32;
typedef unsigned int    andoru32;
#else
typedef long             andor32;
typedef unsigned long    andoru32;
#endif
typedef long long             andor64;
typedef long unsigned long    andoru64;
typedef unsigned long andorpi;
#else
typedef long             andor32;
typedef unsigned long    andoru32;
#if defined(__BORLANDC__) && (__BORLANDC__<=0x540)
typedef __int64             andor64;
typedef unsigned __int64    andoru64;
#else
typedef long long            andor64;
typedef long unsigned long    andoru64;
#endif
#ifdef _WIN64
typedef long unsigned long andorpi;
#else
typedef unsigned long andorpi;
#endif
#endif

#if defined(__BORLANDC__) && (__BORLANDC__<=0x540)
typedef long             ANDORFILEOFFSET;
typedef long             ANDORLARGESTINT;
typedef unsigned long    ANDORLARGESTUINT;
#else
typedef long long            ANDORFILEOFFSET;
typedef long long            ANDORLARGESTINT;
typedef long unsigned long    ANDORLARGESTUINT;
#endif
#endif

