#include "Probe.h"

#include <malloc.h>
#include <string.h>

Probe::Probe(void)
{
}

void Probe::probe(void)
{
	char* p = (char*)malloc(512*512*4);
	memset(p,0,512*512*4);
	free(p);

}

Probe::~Probe(void)
{
}
