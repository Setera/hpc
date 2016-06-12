package at.fhtw.hpc.exercise2;

import at.fhtw.hpc.util.TimeLogger;
import org.apache.commons.io.FileUtils;
import org.jocl.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import static org.jocl.CL.*;

/**
 * Work Efficient Parallel Scan
 * 31.5.16
 */
public class WorkEfficientParallelScanWithLocal {

	private static cl_context context;
	private static cl_command_queue commandQueue;
	private static long maxWorkItemSizes[];
	private static long[] local_work_size = new long[1];

	//TODO: Bank conflicts
	//TODO: Timing info

	public static void main(String args[]) {
		ScanComparer.ExecutionStatisticHelper.clear();

		//int inputArray[] = {1, 1, 1, 1};  // 4
		//int inputArray[] = {1, 1, 1, 1, 1, 1, 1, 1};  // 8
		//int inputArray[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};  // 16
		//int inputArray[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};  // 32
		//int inputArray[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};   // 64

		int[] inputArray = new int[16777216];
		Random randomGenerator = new Random();
		for(int i = 0; i < inputArray.length; ++i){
			//input[i] = randomGenerator.nextInt(10);
			inputArray[i] = 1;
		}

		int[] output = performScan(inputArray);

		for(int i = 1; i < output.length; i++){
			if (output[i] != (output[i-1]+1)) {
				System.out.println("Scan was wrong at index: " + i + ". (" + output[i-1] + " vs. " + output[i] + ")");
				break;
			}
		}

		ScanComparer.ExecutionStatisticHelper.print();
	}

	public static int[] performScan(int inputArray[]) {
		TimeLogger logger = new TimeLogger();
		logger.start();

		initPlatform();

		//setLocalWorkSize(inputArray.length);
		local_work_size[0] = 256;

		Scanner scanner = new Scanner(inputArray).invoke();
		int[] outputArray = scanner.getOutputArray();
		int[] blocksumArray = scanner.getBlocksumArray();

		int[] scannedArray = outputArray;
		if (blocksumArray.length > 1) {
			scannedArray = createCompleteScanFromBlocks(outputArray, blocksumArray);
		}

		releasePlatform();

//		for (int i = 0; i < inputArray.length; i++) {
//			if (outputArray[i] != i) {
//				System.out.println(i + ": " + outputArray[i]);
//			}
//		}

		//System.out.println(Arrays.toString(outputArray));
		//System.out.println(Arrays.toString(blocksumArray));

		//System.out.println(Arrays.toString(scannedArray));

		logger.end("parallel scan");

		return scannedArray;
	}

	private static void setLocalWorkSize(int n) {
		int localWorkSize = n;
		while (localWorkSize > maxWorkItemSizes[0]) {
			localWorkSize = localWorkSize / 2;
		}
		local_work_size[0] = localWorkSize;
		System.out.println("Local work size: " + local_work_size[0]);
	}

	private static void releasePlatform() {
		clReleaseCommandQueue(commandQueue);
		clReleaseContext(context);
	}

	public static cl_device_id initPlatform() {
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

		maxWorkItemSizes = getSizes(device, CL_DEVICE_MAX_WORK_ITEM_SIZES, 3);

		return device;
	}

	public static int[] createCompleteScanFromBlocks(int[] scanArray, int[] blocksumArray) {
		//setLocalWorkSize(blocksumArray.length);
		long currentWorkSize = local_work_size[0];
		if(currentWorkSize > blocksumArray.length) {
			local_work_size[0] = blocksumArray.length;
		}

		Scanner scanner = new Scanner(blocksumArray).invoke();
		int[] outputArray = scanner.getOutputArray();
		int[] blocksumArray2 = scanner.getBlocksumArray();

		if (blocksumArray2.length > 1) {
			outputArray = createCompleteScanFromBlocks(outputArray, blocksumArray2);
		}

		local_work_size[0] = currentWorkSize;

		int[] scannedArray = addBlocksum(Arrays.copyOf(scanArray, scanArray.length), outputArray);
		return scannedArray;
	}

	public static int[] addBlocksum(int[] scanArray, int[] blocksumArray) {
		File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise2/addBlocksum.c");
		String programSource = "";
		try {
			programSource = FileUtils.readFileToString(programSourceFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		int n = scanArray.length;
		long global_work_size[] = new long[]{n};
		//setLocalWorkSize(n);

		// Set the work-item dimensions
		Pointer outputPointer = Pointer.to(scanArray);
		Pointer blocksumPointer = Pointer.to(blocksumArray);

		// Allocate the memory objects for the input- and output data
		cl_mem memObjects[] = new cl_mem[2];
		memObjects[0] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * n, outputPointer, null);
		memObjects[1] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * n, blocksumPointer, null);

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

		// Execute the kernel
		cl_event kernelEvent = new cl_event();
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, kernelEvent);

		// Read the output data
		cl_event readEvent = new cl_event();
		clEnqueueReadBuffer(commandQueue, memObjects[0], CL_TRUE, 0,
				n * Sizeof.cl_int, outputPointer, 0, null, readEvent);
		// Release kernel, program, and memory objects
		clReleaseMemObject(memObjects[0]);
		clReleaseMemObject(memObjects[1]);
		clReleaseKernel(kernel);
		clReleaseProgram(program);

		// Collect statistic
		ScanComparer.ExecutionStatisticHelper.addEntry("blocksum kernel", kernelEvent);
		ScanComparer.ExecutionStatisticHelper.addEntry("blocksum read", readEvent);

		return scanArray;
	}

	/**
	 * Returns the values of the device info parameter with the given name
	 *
	 * @param device    The device
	 * @param paramName The parameter name
	 * @param numValues The number of values
	 * @return The value
	 */
	private static long[] getSizes(cl_device_id device, int paramName, int numValues) {
		// The size of the returned data has to depend on
		// the size of a size_t, which is handled here
		ByteBuffer buffer = ByteBuffer.allocate(
				numValues * Sizeof.size_t).order(ByteOrder.nativeOrder());
		clGetDeviceInfo(device, paramName, Sizeof.size_t * numValues,
				Pointer.to(buffer), null);
		long values[] = new long[numValues];
		if (Sizeof.size_t == 4) {
			for (int i = 0; i < numValues; i++) {
				values[i] = buffer.getInt(i * Sizeof.size_t);
			}
		} else {
			for (int i = 0; i < numValues; i++) {
				values[i] = buffer.getLong(i * Sizeof.size_t);
			}
		}
		return values;
	}

	private static class Scanner {
		private int[] inputArray;
		private int[] outputArray;
		private int[] blocksumArray;

		Scanner(int... inputArray) {
			this.inputArray = inputArray;
		}

		int[] getOutputArray() {
			return outputArray;
		}

		int[] getBlocksumArray() {
			return blocksumArray;
		}

		Scanner invoke() {
			File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise2/scan.c");
			String programSource = "";
			try {
				programSource = FileUtils.readFileToString(programSourceFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
			int n = inputArray.length;
			int blocksumN = (int)Math.ceil((double)n / local_work_size[0]);
			blocksumN = blocksumN > 0 ? blocksumN : 1;
			long global_work_size[] = new long[]{n};
			outputArray = new int[n];
			blocksumArray = new int[blocksumN];

			// Set the work-item dimensions

			Pointer inputPointer = Pointer.to(inputArray);
			Pointer outputPointer = Pointer.to(outputArray);
			Pointer blocksumPointer = Pointer.to(blocksumArray);

			// Create the program from the source code
			cl_program program = clCreateProgramWithSource(context,
					1, new String[]{programSource}, null, null);

			// Build the program
			clBuildProgram(program, 0, null, null, null, null);

			// Allocate the memory objects for the input- and output data
			cl_mem memObjects[] = new cl_mem[3];
			memObjects[0] = clCreateBuffer(context,
					CL_MEM_READ_ONLY,
					Sizeof.cl_int * n, null, null);
			memObjects[1] = clCreateBuffer(context,
					CL_MEM_WRITE_ONLY,
					Sizeof.cl_int * n, null, null);
			memObjects[2] = clCreateBuffer(context,
					CL_MEM_READ_WRITE,
					Sizeof.cl_int * blocksumN, null, null);

			clEnqueueWriteBuffer(commandQueue,
					memObjects[0],
					CL_TRUE,
					0,
					Sizeof.cl_int * n,
					inputPointer,
					0,
					null,
					null);

			// Create the kernel
			cl_kernel kernel = clCreateKernel(program, "scan", null);

			// Set the arguments for the kernel
			int ai = 0;
			clSetKernelArg(kernel, ai++,
					Sizeof.cl_mem, Pointer.to(memObjects[0]));
			clSetKernelArg(kernel, ai++,
					Sizeof.cl_mem, Pointer.to(memObjects[1]));
			clSetKernelArg(kernel, ai++,
					Sizeof.cl_mem, Pointer.to(memObjects[2]));
			clSetKernelArg(kernel, ai++,
					Sizeof.cl_int * local_work_size[0] * 2, null);
			clSetKernelArg(kernel, ai++, Sizeof.cl_int, Pointer.to(new int[]{n}));

			// Execute the kernel
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
					global_work_size, local_work_size, 0, null, null);

			// Read the output data
			clEnqueueReadBuffer(commandQueue, memObjects[1], CL_TRUE, 0,
					n * Sizeof.cl_int, outputPointer, 0, null, null);
			clEnqueueReadBuffer(commandQueue, memObjects[2], CL_TRUE, 0,
					blocksumN * Sizeof.cl_int, blocksumPointer, 0, null, null);

			// Release kernel, program, and memory objects
			clReleaseMemObject(memObjects[0]);
			clReleaseMemObject(memObjects[1]);
			clReleaseMemObject(memObjects[2]);
			clReleaseKernel(kernel);
			clReleaseProgram(program);
			return this;
		}
	}
}
