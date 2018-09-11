__kernel void subtract( __global ushort* a,
                        __global ushort* b) 
{
   int i = get_global_id(0);
  
   a[i] = a[i] - b[i];
}

__kernel void divide(__global ushort* a,
                     __global float* b)
{
    int i = get_global_id(0);

    a[i] = (ushort) ( (a[i] / b[i]) + 0.5f;
}

__kernel void subtractAndDivide(__global ushort* a,
                                __global ushort* background,
                                __global float* flatField)
{
    int i = get_global_id(0);

    a[i] = (ushort) ( ((a[i] - background[i]) / (flatField[i]) + 0.5f));
}