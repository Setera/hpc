__kernel void addBlocksum(__global int *g_odata,
                   __global int *blocksum,
                   int blocksize) {
    int global_id = get_global_id(0);
	int index = global_id / blocksize;
	g_odata[global_id] += blocksum[index];
};