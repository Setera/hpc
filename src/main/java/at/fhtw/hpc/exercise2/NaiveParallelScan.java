package at.fhtw.hpc.exercise2;

import at.fhtw.hpc.util.ExecutionStatisticHelper;
import org.jocl.*;

import java.util.Arrays;

import static org.jocl.CL.*;

/**
 * Naive Parallel Scan
 *         31.5.16
 */
public class NaiveParallelScan {
	private static String programSource =
			"__kernel void " +
			"scan(__global float *g_odata," +
			"     __global float *g_idata," +
			"     __global float *temp," +
			"     int n) {\n" +
			"    int thid = get_global_id(0);\n" +
			"    int pout = 0, pin = 1;\n" +
			"    temp[pout*n + thid] = (thid > 0) ? g_idata[thid-1] : 0;\n" +
			"    barrier(CLK_LOCAL_MEM_FENCE);\n" +
			"    for (int offset = 1; offset < n; offset *= 2) {\n" +
			"       pout = 1 - pout;\n" +
			"       pin = 1 - pin;\n" +
			"       if (thid >= offset) {\n" +
			"           temp[pout*n+thid] = temp[pin*n+thid] + temp[pin*n+thid - offset];\n" +
			"       } else {\n" +
			"           temp[pout*n+thid] = temp[pin*n+thid];\n" +
			"       }\n" +
			"       barrier(CLK_LOCAL_MEM_FENCE);\n" +
			"    }\n" +
			"    g_odata[thid] = temp[pout*n+thid];" +
			"}";

	public static void main(String args[]) {
		float inputArray[] = {3,1,7,0,4,1,6,3};
		int n = inputArray.length;
		float outputArray[] = new float[n];
		float tempArray[] = new float[2];

		Pointer inputPointer = Pointer.to(inputArray);
		Pointer outputPointer = Pointer.to(outputArray);
		Pointer tempPointer = Pointer.to(tempArray);

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
		long properties = 0;
		properties |= CL.CL_QUEUE_PROFILING_ENABLE;
		cl_command_queue commandQueue =
				clCreateCommandQueue(context, device, properties, null);

		// Allocate the memory objects for the input- and output data
		cl_mem memObjects[] = new cl_mem[3];
		memObjects[0] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, outputPointer, null);
		memObjects[1] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, inputPointer, null);
		memObjects[2] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, tempPointer, null);

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
				Sizeof.cl_mem, Pointer.to(memObjects[2]));
		clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{n}));

		// Set the work-item dimensions
		long global_work_size[] = new long[]{n};
		long local_work_size[] = new long[]{1};

		// Execute the kernel
		cl_event kernelEvent = new cl_event();
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, kernelEvent);

		// Read the output data
		cl_event readEvent = new cl_event();
		clEnqueueReadBuffer(commandQueue, memObjects[0], CL_TRUE, 0,
				n * Sizeof.cl_float, outputPointer, 0, null, readEvent);

		// Release kernel, program, and memory objects
		clReleaseMemObject(memObjects[0]);
		clReleaseMemObject(memObjects[1]);
		clReleaseKernel(kernel);
		clReleaseProgram(program);
		clReleaseCommandQueue(commandQueue);
		clReleaseContext(context);

		// Show output array
		System.out.println(Arrays.toString(outputArray));

		// Print statistic
		ExecutionStatisticHelper executionStatistic = new ExecutionStatisticHelper();
		executionStatistic.addEntry("kernel", kernelEvent);
		executionStatistic.addEntry("read", readEvent);
		executionStatistic.print();
	}
}
