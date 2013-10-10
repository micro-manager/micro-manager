#pragma once

#include <cstdint> // Must be included before JAI headers to prevent redefinition of integer types.

#include "Jai_Factory.h"

// Trivial conversion between char* and int8_t* for working with JAI's API.
inline int8_t* str2jai(char* s) { return reinterpret_cast<int8_t*>(s); }
inline int8_t* cstr2jai(const char* s) { return const_cast<int8_t*>(reinterpret_cast<const int8_t*>(s)); }
inline const int8_t* cstr2cjai(const char* s) { return reinterpret_cast<const int8_t*>(s); }