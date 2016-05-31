package at.fhtw.hpc;

import org.jocl.*;

import java.util.Arrays;

import static org.jocl.CL.*;

/**
 * Naive Parallel Scan
 * 31.5.16
 */
public class WorkEfficientParallelScan {

	private static String programSource =
			"__kernel void scan(__global float *g_odata,\n" +
					"           __global float *g_idata,\n" +
					"           __local float *temp,\n" +
					"           int n) {\n" +
					"    int thid = get_global_id(0);\n" +
					"    int offset = 1;\n" +
					"    temp[2*thid] = g_idata[2*thid];\n" +
					"    temp[2*thid+1] = g_idata[2*thid+1];\n" +
					"    for (int d = n>>1; d > 0; d >>= 1) {   \n" +
					"       barrier(CLK_LOCAL_MEM_FENCE); \n" +
					"       if (thid < d) {  \n" +
					"           int ai = offset*(2*thid+1)-1;  \n" +
					"           int bi = offset*(2*thid+2)-1; \n" +
					"           temp[bi] += temp[ai];  \n" +
					"       }  \n" +
					"       offset *= 2;  \n" +
					"    }" +
					"    if (thid == 0) {\n" +
					"       temp[n - 1] = 0;\n" +
					"    }\n" +
					"    for (int d = 1; d < n; d *= 2) {  \n" +
					"       offset >>= 1;  \n" +
					"       barrier(CLK_LOCAL_MEM_FENCE);   \n" +
					"       if (thid < d) {\n" +
					"           int ai = offset*(2*thid+1)-1;  \n" +
					"           int bi = offset*(2*thid+2)-1;  \n" +
					"           float t = temp[ai];  \n" +
					"           temp[ai] = temp[bi];  \n" +
					"           temp[bi] += t;   \n" +
					"       }  \n" +
					"    }  \n" +
					"    barrier(CLK_LOCAL_MEM_FENCE);   \n" +
					"    g_odata[2*thid] = temp[2*thid]; // write results to device memory  \n" +
					"    g_odata[2*thid+1] = temp[2*thid+1];  \n" +
					"};";

	public static void main(String args[]) {
		float inputArray[] = {3, 1, 7, 0, 4, 1, 6, 3};
		int n = inputArray.length;
		long global_work_size[] = new long[]{n};
		long local_work_size[] = new long[]{n};
		float outputArray[] = new float[n];

		// Set the work-item dimensions

		Pointer inputPointer = Pointer.to(inputArray);
		Pointer outputPointer = Pointer.to(outputArray);

		// The platform, device type and device number
		// that will be used
		final int platformIndex = 0;
		final long deviceType = CL_DEVICE_TYPE_ALL;
		final int deviceIndex = 0;

		// Enable exceptions and subsequently omit error checks in this sample
		CL.setExceptionsEnabled(true);

		// Obtain the number of platforms
		int numPlatformsArray[] = new int[1];
		clGetPlatformIDs(0, null, numPlatformsArray);
		int numPlatforms = numPlatformsArray[0];

		// Obtain a platform ID
		cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
		clGetPlatformIDs(platforms.length, platforms, null);
		cl_platform_id platform = platforms[platformIndex];

		// Initialize the context properties
		cl_context_properties contextProperties = new cl_context_properties();
		contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

		// Obtain the number of devices for the platform
		int numDevicesArray[] = new int[1];
		clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
		int numDevices = numDevicesArray[0];

		// Obtain a device ID
		cl_device_id devices[] = new cl_device_id[numDevices];
		clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
		cl_device_id device = devices[deviceIndex];

		// Create a context for the selected device
		cl_context context = clCreateContext(
				contextProperties, 1, new cl_device_id[]{device},
				null, null, null);

		// Create a command-queue for the selected device
		cl_command_queue commandQueue =
				clCreateCommandQueue(context, device, 0, null);

		// Allocate the memory objects for the input- and output data
		cl_mem memObjects[] = new cl_mem[3];
		memObjects[0] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, outputPointer, null);
		memObjects[1] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, inputPointer, null);

		// Create the program from the source code
		cl_program program = clCreateProgramWithSource(context,
				1, new String[]{programSource}, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);

		// Create the kernel
		cl_kernel kernel = clCreateKernel(program, "scan", null);

		// Set the arguments for the kernel
		clSetKernelArg(kernel, 0,
				Sizeof.cl_mem, Pointer.to(memObjects[0]));
		clSetKernelArg(kernel, 1,
				Sizeof.cl_mem, Pointer.to(memObjects[1]));
		clSetKernelArg(kernel, 2,
				2 * local_work_size[0] * Sizeof.cl_float, null);
		clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{n}));

		// Execute the kernel
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, null);

		// Read the output data
		clEnqueueReadBuffer(commandQueue, memObjects[0], CL_TRUE, 0,
				n * Sizeof.cl_float, outputPointer, 0, null, null);

		// Release kernel, program, and memory objects
		clReleaseMemObject(memObjects[0]);
		clReleaseMemObject(memObjects[1]);
		clReleaseKernel(kernel);
		clReleaseProgram(program);
		clReleaseCommandQueue(commandQueue);
		clReleaseContext(context);

		System.out.println(Arrays.toString(outputArray));
	}
}
