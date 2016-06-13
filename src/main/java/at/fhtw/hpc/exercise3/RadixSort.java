package at.fhtw.hpc.exercise3;

import at.fhtw.hpc.util.ExecutionStatisticHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jocl.*;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import static org.jocl.CL.*;

/**
 * Exercise 3
 */
public class RadixSort {

    static ExecutionStatisticHelper executionStatistic = new ExecutionStatisticHelper();

	private static cl_context context;
	private static cl_command_queue commandQueue;

	private static long maxWorkItemSizes[];
	private static long[] local_work_size = new long[1];

	public static void main(String args[]) {
		Random random = new Random();
		int size = (int) Math.pow(2, 24);
		System.out.println("Array size: " + size);

		Integer[] inputArray = new Integer[size];
		for (int i = 0; i < inputArray.length; i++) {
			inputArray[i] = random.nextInt(10);
			//inputArray[i] = i%10 + 1;
		}

		int[] inputArrayPrimitive = ArrayUtils.toPrimitive(inputArray);
		int[] resultArray = Arrays.copyOf(inputArrayPrimitive, inputArrayPrimitive.length);
		int sArray[] = new int[inputArray.length];

		initPlatform();

		BigInteger max = new BigInteger(Collections.max(Arrays.asList(inputArray)).toString());
		int bitLength = max.bitLength();
		for (int i = 0; i < bitLength; i++) {
			System.out.println("Round " + i);
			int n = resultArray.length;

			/** get bit array */
			setLocalWorkSize(resultArray.length);
			//local_work_size[0] = 64;
			int[] eArray = getE(resultArray, i);

			/** scan bit array*/
			Scanner scanner = new Scanner(eArray).invoke();
			int[] outputArray = scanner.getOutputArray();
			int[] blocksumArray = scanner.getBlocksumArray();

			int[] fArray = outputArray;
			if (blocksumArray.length > 1) {
				fArray = createCompleteScanFromBlocks(outputArray, blocksumArray);
			}

			/** reorder */
			setLocalWorkSize(resultArray.length);
			sArray = getD(resultArray, eArray, fArray);

			resultArray = Arrays.copyOf(sArray, sArray.length);
		}
		releasePlatform();

        executionStatistic.printSummary();

		for (int i = 1; i < resultArray.length; i++) {
			if (resultArray[i] < resultArray[i - 1]) {
				System.out.println("Sort was wrong at index: " + i + ". (Curr: " + resultArray[i] + " / Prev:" + resultArray[i - 1] + ")");
				return;
			}
		}

		if (resultArray[resultArray.length - 1] == 0) {
			System.out.println("Sort was wrong (Zero at last index)");
			return;
		}

		System.out.println("Sort was successful");
	}

	private static int[] getE(int[] inputarray, int bit) {
		File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise3/eSource.cl");
		String programSource = "";
		try {
			programSource = FileUtils.readFileToString(programSourceFile);
		} catch (IOException e) {
			e.printStackTrace();
		}

		int[] eArray = new int[inputarray.length];
		int n = inputarray.length;
		long global_work_size[] = new long[]{n};
		// Set the work-item dimensions
		Pointer inputPointer = Pointer.to(inputarray);
		Pointer ePointer = Pointer.to(eArray);

		// Allocate the memory objects for the input- and output data
		cl_mem memObjects[] = new cl_mem[2];
		memObjects[0] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * n, inputPointer, null);
		memObjects[1] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * n, ePointer, null);

		// Create the program from the source code
		cl_program program = clCreateProgramWithSource(context,
				1, new String[]{programSource}, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);

		// Create the kernel
		cl_kernel kernel = clCreateKernel(program, "getE", null);

		// Set the arguments for the kernel
		clSetKernelArg(kernel, 0,
				Sizeof.cl_mem, Pointer.to(memObjects[0]));
		clSetKernelArg(kernel, 1,
				Sizeof.cl_mem, Pointer.to(memObjects[1]));
		clSetKernelArg(kernel, 2, Sizeof.cl_int, Pointer.to(new int[]{bit}));

		// Execute the kernel
		cl_event kernelEvent = new cl_event();
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, kernelEvent);

		cl_event readEvent = new cl_event();
		clEnqueueReadBuffer(commandQueue, memObjects[1], CL_TRUE, 0,
				n * Sizeof.cl_int, ePointer, 0, null, readEvent);

		// Release kernel, program, and memory objects
		clReleaseMemObject(memObjects[0]);
		clReleaseKernel(kernel);
		clReleaseProgram(program);

		// Print statistic
		executionStatistic.addEntry("kernel", kernelEvent);
		executionStatistic.addEntry("read", readEvent);
		return eArray;
	}

	private static int[] getD(int[] inputarray, int[] eArray, int[] fArray) {
		File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise3/dSource.cl");
		String programSource = "";
		try {
			programSource = FileUtils.readFileToString(programSourceFile);
		} catch (IOException e) {
			e.printStackTrace();
		}

		int[] dArray = new int[inputarray.length];
		int n = inputarray.length;
		long global_work_size[] = new long[]{n};
		Pointer inputPointer = Pointer.to(inputarray);
		Pointer ePointer = Pointer.to(eArray);
		Pointer fPointer = Pointer.to(fArray);
		Pointer dPointer = Pointer.to(dArray);

		// Allocate the memory objects for the input- and output data
		cl_mem memObjects[] = new cl_mem[4];
		memObjects[0] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * n, inputPointer, null);
		memObjects[1] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * n, ePointer, null);
		memObjects[2] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * n, fPointer, null);
		memObjects[3] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * n, dPointer, null);

		// Create the program from the source code
		cl_program program = clCreateProgramWithSource(context,
				1, new String[]{programSource}, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);

		// Create the kernel
		cl_kernel kernel = clCreateKernel(program, "getD", null);

		// Set the arguments for the kernel
		clSetKernelArg(kernel, 0,
				Sizeof.cl_mem, Pointer.to(memObjects[0]));
		clSetKernelArg(kernel, 1,
				Sizeof.cl_mem, Pointer.to(memObjects[1]));
		clSetKernelArg(kernel, 2,
				Sizeof.cl_mem, Pointer.to(memObjects[2]));
		clSetKernelArg(kernel, 3,
				Sizeof.cl_mem, Pointer.to(memObjects[3]));
		clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{n}));

		// Execute the kernel
		cl_event kernelEvent = new cl_event();
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, kernelEvent);

		// Read the output data
		cl_event readEvent = new cl_event();
		clEnqueueReadBuffer(commandQueue, memObjects[3], CL_TRUE, 0,
				n * Sizeof.cl_int, dPointer, 0, null, readEvent);

		// Release kernel, program, and memory objects
		clReleaseMemObject(memObjects[0]);
		clReleaseMemObject(memObjects[1]);
		clReleaseKernel(kernel);
		clReleaseProgram(program);


		// Print statistic
		executionStatistic.addEntry("kernel", kernelEvent);
		executionStatistic.addEntry("read", readEvent);
		return dArray;
	}

	private static void setLocalWorkSize(int n) {
		int localWorkSize = n;
		while (localWorkSize > maxWorkItemSizes[0]) {
			localWorkSize = localWorkSize / 2;
		}
		local_work_size[0] = localWorkSize;
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
		if (currentWorkSize > blocksumArray.length) {
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
        executionStatistic.addEntry("blocksum kernel", kernelEvent);
        executionStatistic.addEntry("blocksum read", readEvent);

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
			int blocksumN = (int) Math.ceil((double) n / local_work_size[0]);
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
