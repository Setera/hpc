__kernel void getD(__global unsigned int *idata,
                    __global unsigned int *edata,
                    __global unsigned int *fdata,
                    __global unsigned int *ddata,
                    int n) {
    int global_id = get_global_id(0);
    int totalFalse = edata[n-1] + fdata[n-1];
    int t = global_id - fdata[global_id] + totalFalse;
    int d = edata[global_id] == 0 ? t : fdata[global_id];
    ddata[d] = idata[global_id];
};