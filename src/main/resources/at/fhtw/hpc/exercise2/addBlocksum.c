__kernel void addBlocksum(__global int *g_odata,
                   __global int *blocksum) {
    int global_id = get_global_id(0);
    int group_id = get_group_id(0);
	g_odata[global_id] += blocksum[group_id];
};