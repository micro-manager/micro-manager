__kernel void run(__global const short* a, __global short* b) 
{
   int i = get_global_id(0);
  
   a[i] = a[i] - b[i];
}
