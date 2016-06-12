__kernel void scan(__global int *g_idata,
                   __global int *g_odata,
                   __global int *blocksum,
                   __local int *temp,
                   int a) {
    int n = get_local_size(0);

    int global_offset = get_group_id(0)*n;
	int local_id = get_local_id(0);
    int offset = 1;

    temp[2*local_id] = g_idata[2*local_id+global_offset];
    temp[2*local_id+1] = g_idata[2*local_id+1+global_offset];

    for (int d = n>>1; d > 0; d >>= 1) {
        barrier(CLK_LOCAL_MEM_FENCE);
        if (local_id < d) {
            int ai = offset*(2*local_id+1)-1;
            int bi = offset*(2*local_id+2)-1;
            temp[bi] += temp[ai];
        }
        offset *= 2;
    }
    if (local_id == 0) {
        int index = get_group_id(0);
        blocksum[index] = temp[n - 1];
        temp[n - 1] = 0;
    }
    for (int d = 1; d < n; d *= 2) {
        offset >>= 1;
        barrier(CLK_LOCAL_MEM_FENCE);
        if (local_id < d) {
            int ai = offset*(2*local_id+1)-1;
            int bi = offset*(2*local_id+2)-1;
            int t = temp[ai];
            temp[ai] = temp[bi];
            temp[bi] += t;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    for(int d=n>>1; d>0; d >>= 1){
        barrier(CLK_LOCAL_MEM_FENCE);
        if(local_id < d){
            g_odata[2*local_id+global_offset] = temp[2*local_id];
            g_odata[2*local_id+1+global_offset] = temp[2*local_id+1];
        }
    }
};