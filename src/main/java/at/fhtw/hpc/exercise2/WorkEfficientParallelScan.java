package at.fhtw.hpc.exercise2;

import at.fhtw.hpc.util.ExecutionStatisticHelper;
import at.fhtw.hpc.util.TimeLogger;
import org.apache.commons.io.FileUtils;
import org.jocl.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.jocl.CL.*;

/**
 * Work Efficient Parallel Scan
 * 31.5.16
 */
public class WorkEfficientParallelScan {

	private static cl_context context;
	private static cl_command_queue commandQueue;
	private static long[] local_work_size = new long[1];

	//TODO: Bank conflicts
	//TODO: Timing info

	public static void main(String args[]) {
		ScanComparer.ExecutionStatisticHelper.clear();

		//float inputArray[] = {1, 1, 1, 1};  // 4
		//float inputArray[] = {1, 1, 1, 1, 1, 1, 1, 1};  // 8
		//float inputArray[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};  // 16
		//float inputArray[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};  // 32
		float inputArray[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};   // 64

		performScan(inputArray);

		ScanComparer.ExecutionStatisticHelper.print();
	}

	public static float[] performScan(float inputArray[]) {
		TimeLogger logger = new TimeLogger();
		logger.start();

		initPlatform();

		setLocalWorkSize(inputArray.length);

		Scanner scanner = new Scanner(inputArray).invoke();
		float[] outputArray = scanner.getOutputArray();
		float[] blocksumArray = scanner.getBlocksumArray();

		float[] scannedArray = outputArray;
		if(blocksumArray.length > 1) {
			scannedArray = createCompleteScanFromBlocks(outputArray, blocksumArray);
		}

		releasePlatform();

		//System.out.println(Arrays.toString(outputArray));
		//System.out.println(Arrays.toString(blocksumArray));

		//System.out.println(Arrays.toString(scannedArray));

		logger.end("parallel scan");

		return scannedArray;
	}

	private static void setLocalWorkSize(int n) {
		int localWorkSize = n/4;
		while (localWorkSize * 2 > CL_DEVICE_MAX_WORK_ITEM_SIZES) {
			localWorkSize = localWorkSize/2;
		}
		local_work_size[0] = localWorkSize;
	}

	private static void releasePlatform() {
		clReleaseCommandQueue(commandQueue);
		clReleaseContext(context);
	}

	public static void initPlatform() {
		// The platform, device type and device number
		// that will be used
		int platformIndex = 0;
		long deviceType = CL_DEVICE_TYPE_ALL;
		int deviceIndex = 0;

		// Enable exceptions and subsequently omit error checks in this sample
		CL.setExceptionsEnabled(true);

		// Obtain the number of platforms
		int[] numPlatformsArray = new int[1];
		clGetPlatformIDs(0, null, numPlatformsArray);
		int numPlatforms = numPlatformsArray[0];

		// Obtain a platform ID
		cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
		clGetPlatformIDs(platforms.length, platforms, null);
		cl_platform_id platform = platforms[platformIndex];

		// Initialize the context properties
		cl_context_properties contextProperties = new cl_context_properties();
		contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

		// Obtain the number of devices for the platform
		int[] numDevicesArray = new int[1];
		clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
		int numDevices = numDevicesArray[0];

		// Obtain a device ID
		cl_device_id[] devices = new cl_device_id[numDevices];
		clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
		cl_device_id device = devices[deviceIndex];

		// Create a context for the selected device
		context = clCreateContext(
				contextProperties, 1, new cl_device_id[]{device},
				null, null, null);

		// Create a command-queue for the selected device
		long properties = 0;
		properties |= CL.CL_QUEUE_PROFILING_ENABLE;
		commandQueue = clCreateCommandQueue(context, device, properties, null);
	}

	public static float[] createCompleteScanFromBlocks(float[] scanArray, float[] blocksumArray) {
		setLocalWorkSize(blocksumArray.length);
		Scanner scanner = new Scanner(blocksumArray).invoke();
		float[] outputArray = scanner.getOutputArray();
		float[] blocksumArray2 = scanner.getBlocksumArray();

		if(blocksumArray2.length > 1) {
			outputArray = createCompleteScanFromBlocks(outputArray, blocksumArray2);
		}

		float[] scannedArray = addBlocksum(Arrays.copyOf(scanArray, scanArray.length), outputArray);
		return scannedArray;
	}

	public static float[] addBlocksum(float[] scanArray, float[] blocksumArray) {
		File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise2/programSource2.c");
		String programSource = "";
		try {
			programSource = FileUtils.readFileToString(programSourceFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		int n = scanArray.length;
		long global_work_size[] = new long[]{n};
		setLocalWorkSize(n);

		// Set the work-item dimensions
		Pointer outputPointer = Pointer.to(scanArray);
		Pointer blocksumPointer = Pointer.to(blocksumArray);

		// Allocate the memory objects for the input- and output data
		cl_mem memObjects[] = new cl_mem[2];
		memObjects[0] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, outputPointer, null);
		memObjects[1] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * n, blocksumPointer, null);

		// Create the program from the source code
		cl_program program = clCreateProgramWithSource(context,
				1, new String[]{programSource}, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);

		// Create the kernel
		cl_kernel kernel = clCreateKernel(program, "addBlocksum", null);

		// Set the arguments for the kernel
		clSetKernelArg(kernel, 0,
				Sizeof.cl_mem, Pointer.to(memObjects[0]));
		clSetKernelArg(kernel, 1,
				Sizeof.cl_mem, Pointer.to(memObjects[1]));
		clSetKernelArg(kernel, 2, Sizeof.cl_int, Pointer.to(new int[]{(int)local_work_size[0]*2}));

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

        // Collect statistic
        ScanComparer.ExecutionStatisticHelper.addEntry("blocksum kernel", kernelEvent);
		ScanComparer.ExecutionStatisticHelper.addEntry("blocksum read", readEvent);

		return  scanArray;
	}

	private static class Scanner {
		private float[] inputArray;
		private float[] outputArray;
		private float[] blocksumArray;

		Scanner(float... inputArray) {
			this.inputArray = inputArray;
		}

		float[] getOutputArray() {
			return outputArray;
		}

		float[] getBlocksumArray() {
			return blocksumArray;
		}

		Scanner invoke() {
			File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise2/programSource.c");
			String programSource = "";
			try {
				programSource = FileUtils.readFileToString(programSourceFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
			int n = inputArray.length;
			long global_work_size[] = new long[]{n};
			outputArray = new float[n];
			blocksumArray = new float[n / ((int) local_work_size[0] * 2)];

			// Set the work-item dimensions

			Pointer inputPointer = Pointer.to(inputArray);
			Pointer outputPointer = Pointer.to(outputArray);
			Pointer blocksumPointer = Pointer.to(blocksumArray);


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
					Sizeof.cl_float * n, blocksumPointer, null);

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
			clSetKernelArg(kernel, 3,
					Sizeof.cl_mem, Pointer.to(memObjects[2]));
			clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{n}));

			// Execute the kernel
			cl_event kernelEvent = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
					global_work_size, local_work_size, 0, null, kernelEvent);

			// Read the output data
			cl_event readEvent1 = new cl_event();
			clEnqueueReadBuffer(commandQueue, memObjects[0], CL_TRUE, 0,
					n * Sizeof.cl_float, outputPointer, 0, null, readEvent1);
			cl_event readEvent2 = new cl_event();
			clEnqueueReadBuffer(commandQueue, memObjects[2], CL_TRUE, 0,
					n * Sizeof.cl_float, blocksumPointer, 0, null, readEvent2);

			// Release kernel, program, and memory objects
			clReleaseMemObject(memObjects[0]);
			clReleaseMemObject(memObjects[1]);
			clReleaseMemObject(memObjects[2]);
			clReleaseKernel(kernel);
			clReleaseProgram(program);

			// Collect statistic
			ScanComparer.ExecutionStatisticHelper.addEntry("scan kernel", kernelEvent);
			ScanComparer.ExecutionStatisticHelper.addEntry("scan read", readEvent1);
			ScanComparer.ExecutionStatisticHelper.addEntry("scan read", readEvent2);

			return this;
		}
	}
}
