__kernel void scan(__global int *g_odata,
                   __global int *g_idata,
                   __global int *temp,
                   __global int *blocksum,
                   int n) {
    int global_id = get_global_id(0);
	int local_id = get_local_id(0);
	int group_id = get_group_id(0);
	int group_offset = group_id * n;
//	printf("group: %d, group offset: %d\n", group_id, group_offset);
//	printf("global: %d, local: %d, group: %d\n", global_id, local_id, group_id);
    int offset = 1;
    temp[2*local_id+group_offset] = g_idata[2*global_id];
    temp[2*local_id+1+group_offset] = g_idata[2*global_id+1];
//    printf("d = n>>1 (%d), n=%d\n", n>>1, n);
    for (int d = n>>1; d > 0; d >>= 1) {
//        printf("d=%d, ", d);
        barrier(CLK_LOCAL_MEM_FENCE);
        if (local_id < d) {
//            printf("offset=%d\n", offset);
            int ai = offset*(2*local_id+1)-1;
            int bi = offset*(2*local_id+2)-1;
            temp[bi+group_offset] += temp[ai+group_offset];
//            if (temp[ai] != 0 || temp[bi] != 0) {
//                printf("d: %d - temp[%d] += temp[%d] = %f + %f\n", d, ai, bi, temp[bi], temp[ai]);
//            }
        }
        offset *= 2;
    }
    if (local_id == 0) {
        int index = global_id / get_local_size(0);
        blocksum[index] = temp[get_local_size(0) * 2 - 1+group_offset];
        temp[n - 1+group_offset] = 0;
//        printf("temp[n - 1] = %f\n", temp[n - 1]);
    }
    for (int d = 1; d < n; d *= 2) {
        offset >>= 1;
        barrier(CLK_LOCAL_MEM_FENCE);
        if (local_id < d) {
            int ai = offset*(2*local_id+1)-1;
            int bi = offset*(2*local_id+2)-1;
            int t = temp[ai+group_offset];
            temp[ai+group_offset] = temp[bi+group_offset];
            temp[bi+group_offset] += t;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);
    g_odata[2*global_id] = temp[2*local_id+group_offset];
    g_odata[2*global_id+1] = temp[2*local_id+1+group_offset];
};