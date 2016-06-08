__kernel void getE(__global int *idata,
                    __global int *edata,
                    int bit) {
    int global_id = get_global_id(0);
    edata[global_id] = 1 - idata[global_id] >> bit & 1;
};