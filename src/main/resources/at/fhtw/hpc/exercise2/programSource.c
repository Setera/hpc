__kernel void scan(__global float *g_odata,
                   __global float *g_idata,
                   __local float *temp,
                   __global float *blocksum,
                   int n) {
    int global_id = get_global_id(0);
	int local_id = get_local_id(0);
    int offset = 1;
    temp[2*local_id] = g_idata[2*global_id];
    temp[2*local_id+1] = g_idata[2*global_id+1];
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
        int index = global_id / get_local_size(0);
        blocksum[index] = temp[get_local_size(0) * 2 - 1];
        temp[n - 1] = 0;
    }
    for (int d = 1; d < n; d *= 2) {
        offset >>= 1;
        barrier(CLK_LOCAL_MEM_FENCE);
        if (local_id < d) {
            int ai = offset*(2*local_id+1)-1;
            int bi = offset*(2*local_id+2)-1;
            float t = temp[ai];
            temp[ai] = temp[bi];
            temp[bi] += t;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    g_odata[2*global_id] = temp[2*local_id];
    g_odata[2*global_id+1] = temp[2*local_id+1];
};