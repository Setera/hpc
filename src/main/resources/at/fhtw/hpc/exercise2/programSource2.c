__kernel void addBlocksum(__global float *g_odata,
                   __global float *blocksum,
                   int blocksize) {
    int global_id = get_global_id(0);
	int index = global_id / blocksize;
	g_odata[global_id] += blocksum[index];
};