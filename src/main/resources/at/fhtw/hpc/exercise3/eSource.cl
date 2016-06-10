__kernel void getE(__global unsigned int *idata,
                    __global unsigned int *edata,
                    unsigned int bit) {
    int global_id = get_global_id(0);
    edata[global_id] = (idata[global_id]&(1u<<bit)) ? 0 : 1;
};