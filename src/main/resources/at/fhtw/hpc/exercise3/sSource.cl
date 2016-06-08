__kernel void getS(__global int *idata,
                    __global int *fdata,
                    __global int *edata,
                    __global int *sdata,
                    int n) {
    int global_id = get_global_id(0);
    int totalFalses = edata[n -1] + fdata[n - 1];
    //printf("edata[n -1]: %d,  fdata[n - 1]: %d\n", edata[n -1],  fdata[n - 1]);
    int t = global_id - fdata[global_id] + totalFalses;
    int d = edata[global_id] == 0 ? t : fdata[global_id];
    //printf("t: %d, d: %d\n", t, d);
    sdata[d] = idata[global_id];
};