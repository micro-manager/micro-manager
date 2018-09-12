/**
  Simple kernels for manipulation of images
  Treat images as buffers
*/


__kernel void subtractUS( __global ushort* a,
                        __global ushort* b) 
{
   int i = get_global_id(0);
  
   a[i] = a[i] - b[i];
}

__kernel void divideUSF(__global ushort* a,
                     __global float* b)
{
    int i = get_global_id(0);

    a[i] = (ushort) ( (a[i] / b[i]) + 0.5f);
}

__kernel void subtractAndDivideUSF(__global ushort* a,
                                __global ushort* background,
                                __global float* flatField)
{
    int i = get_global_id(0);

    a[i] = (ushort) ( ((a[i] - background[i]) * (flatField[i]) + 0.5f));
}