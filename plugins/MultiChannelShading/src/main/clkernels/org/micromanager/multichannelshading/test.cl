__kernel void subtract( __global short* a,
                        __global short* b) 
{
   int i = get_global_id(0);
  
   a[i] = a[i] - b[i];
}
