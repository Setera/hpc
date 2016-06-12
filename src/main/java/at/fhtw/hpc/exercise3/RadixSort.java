package at.fhtw.hpc.exercise3;

import at.fhtw.hpc.exercise2.ScanComparer;
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
 * @author Teresa Melchart
 *         2.6.16
 */
public class RadixSort {

	private static cl_context context;
	private static cl_command_queue commandQueue;

	private static long maxWorkItemSizes[];
	private static long[] local_work_size = new long[1];

	public static void main(String args[]) {
		Random random = new Random();
		int size = (int) Math.pow(2, 10);
		System.out.println("Array size: " + size);

		Integer[] inputArray = new Integer[size];
		for (int i = 0; i < inputArray.length; i++) {
			inputArray[i] = i%10 + 1;//random.nextInt(10);
		}

		//Integer[] inputArray = {2, 3, 4, 7, 1, 0, 5, 100, 33, 22, 6, 99, 50, 7, 10, 77};
		//Integer[] inputArray = {4,7,2,6,3,5,1,0};

		int[] inputArrayPrimitive = ArrayUtils.toPrimitive(inputArray);
		int[] resultArray = Arrays.copyOf(inputArrayPrimitive, inputArrayPrimitive.length);
		int sArray[] = new int[inputArray.length];

		initPlatform();
//		setLocalWorkSize(inputArray.length);
		local_work_size[0] = 256;

		BigInteger max = new BigInteger(Collections.max(Arrays.asList(inputArray)).toString());
		int bitLength = max.bitLength();
		for (int i = 0; i < bitLength; i++) {
			System.out.println("Round " + i);
			int n = resultArray.length;

			/** get bit array */
			cl_mem eBuffer = getE(resultArray, i);

			/** scan bit array*/
			Scanner scanner = new Scanner(eBuffer, n).invoke();
			cl_mem outputBuffer = scanner.getOutputBuffer();
			cl_mem blocksumBuffer = scanner.getBlocksumBuffer();
			int outputSize = scanner.getOutputSize();
			int blocksumSize = scanner.getBlocksumSize();

			cl_mem fBuffer = outputBuffer;
			if(blocksumSize > 1) {
				fBuffer = createCompleteScanFromBlocks(outputBuffer, blocksumBuffer, blocksumSize, outputSize);
			}

			/** reorder */
			sArray = getD(resultArray, eBuffer, fBuffer);

//			clReleaseMemObject(eBuffer);
//			clReleaseMemObject(outputBuffer);
//			clReleaseMemObject(blocksumBuffer);
//			clReleaseMemObject(fBuffer);

			resultArray = Arrays.copyOf(sArray, sArray.length);

//			System.out.println(Arrays.toString(sArray));
//			System.out.println();

		}
		releasePlatform();

		System.out.println(Arrays.toString(inputArray));
		System.out.println(Arrays.toString(resultArray));
		System.out.println(bitLength);

		for (int i = 1; i < resultArray.length; i++) {
			if (resultArray[i] < resultArray[i-1]) {
				System.out.println("Sort was wrong at index: " + i + ". (Curr: " + resultArray[i] + " / Prev:" + resultArray[i-1] + ")");
				return;
			}
		}
	}

//	private static void setLocalWorkSize(int n) {
//		int localWorkSize = n;
//		while (localWorkSize > maxWorkItemSizes[0]) {
//			localWorkSize = localWorkSize / 2;
//		}
//		local_work_size[0] = localWorkSize;
////		System.out.println("Local work size: " + local_work_size[0]);
//	}

	private static cl_mem getE(int[] inputarray, int bit) {
		File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise3/eSource.cl");
		String programSource = "";
		try {
			programSource = FileUtils.readFileToString(programSourceFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
//		setLocalWorkSize(inputarray.length);
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

		// Read the output data
		cl_event readEvent = new cl_event();

		// Release kernel, program, and memory objects
		clReleaseMemObject(memObjects[0]);
		clReleaseKernel(kernel);
		clReleaseProgram(program);


		// Print statistic
		/*ExecutionStatisticHelper executionStatistic = new ExecutionStatisticHelper();
		executionStatistic.addEntry("kernel", kernelEvent);
		executionStatistic.addEntry("read", readEvent);
		executionStatistic.print();*/
		return memObjects[1];
	}

	private static int[] getD(int[] inputarray, cl_mem eBuffer, cl_mem fBuffer) {
		File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise3/dSource.cl");
		String programSource = "";
		try {
			programSource = FileUtils.readFileToString(programSourceFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
//		setLocalWorkSize(inputarray.length);
		int[] dArray = new int[inputarray.length];
		int n = inputarray.length;
		long global_work_size[] = new long[]{n};
		Pointer inputPointer = Pointer.to(inputarray);
		Pointer dPointer = Pointer.to(dArray);

		// Allocate the memory objects for the input- and output data
		cl_mem memObjects[] = new cl_mem[2];
		memObjects[0] = clCreateBuffer(context,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * n, inputPointer, null);
		memObjects[1] = clCreateBuffer(context,
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
				Sizeof.cl_mem, Pointer.to(eBuffer));
		clSetKernelArg(kernel, 2,
				Sizeof.cl_mem, Pointer.to(fBuffer));
		clSetKernelArg(kernel, 3,
				Sizeof.cl_mem, Pointer.to(memObjects[1]));
		clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{n}));

		// Execute the kernel
		cl_event kernelEvent = new cl_event();
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, kernelEvent);

		// Read the output data
		cl_event readEvent = new cl_event();
		clEnqueueReadBuffer(commandQueue, memObjects[1], CL_TRUE, 0,
				n * Sizeof.cl_int, dPointer, 0, null, readEvent);

		// Release kernel, program, and memory objects
		clReleaseMemObject(memObjects[0]);
		clReleaseMemObject(memObjects[1]);
//		clReleaseMemObject(eBuffer);
//		clReleaseMemObject(fBuffer);
		clReleaseKernel(kernel);
		clReleaseProgram(program);


		// Print statistic
		/*ExecutionStatisticHelper executionStatistic = new ExecutionStatisticHelper();
		executionStatistic.addEntry("kernel", kernelEvent);
		executionStatistic.addEntry("read", readEvent);
		executionStatistic.print();*/
		return dArray;
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

	public static cl_mem createCompleteScanFromBlocks(cl_mem scanBuffer, cl_mem blocksumBuffer, int blocksumSize, int scanSize) {
//		setLocalWorkSize(blocksumSize);
		long currentWorkSize = local_work_size[0];
		if(currentWorkSize > blocksumSize) {
			local_work_size[0] = blocksumSize;
		}
		Scanner scanner = new Scanner(blocksumBuffer, blocksumSize).invoke();
		cl_mem outputBuffer = scanner.getOutputBuffer();
		cl_mem blocksumBuffer2 = scanner.getBlocksumBuffer();
		int outputSize2 = scanner.getOutputSize();
		int blocksumsize2 = scanner.getBlocksumSize();

		if (blocksumsize2 > 1) {
			outputBuffer = createCompleteScanFromBlocks(outputBuffer, blocksumBuffer2, blocksumsize2, outputSize2);
		}

		local_work_size[0] = currentWorkSize;

		cl_mem scannedArray = addBlocksum(scanBuffer, outputBuffer, scanSize);
		clReleaseMemObject(outputBuffer);
		clReleaseMemObject(blocksumBuffer2);
		return scannedArray;
	}
	public static cl_mem addBlocksum(cl_mem scanArray, cl_mem blocksumArray, int scanSize) {
		File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise2/addBlocksum.c");
		String programSource = "";
		try {
			programSource = FileUtils.readFileToString(programSourceFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		int n = scanSize;
		long global_work_size[] = new long[]{n};
//		setLocalWorkSize(n);

		// Create the program from the source code
		cl_program program = clCreateProgramWithSource(context,
				1, new String[]{programSource}, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);

		// Create the kernel
		cl_kernel kernel = clCreateKernel(program, "addBlocksum", null);

		// Set the arguments for the kernel
		clSetKernelArg(kernel, 0,
				Sizeof.cl_mem, Pointer.to(scanArray));
		clSetKernelArg(kernel, 1,
				Sizeof.cl_mem, Pointer.to(blocksumArray));
//		clSetKernelArg(kernel, 2, Sizeof.cl_int, Pointer.to(new int[]{(int) local_work_size[0] * 2}));

		// Execute the kernel
		cl_event kernelEvent = new cl_event();
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, kernelEvent);

		// Read the output data
		cl_event readEvent = new cl_event();
		// Release kernel, program, and memory objects
		clReleaseKernel(kernel);
		clReleaseProgram(program);

		// Collect statistic
//		ScanComparer.ExecutionStatisticHelper.addEntry("blocksum kernel", kernelEvent);
//		ScanComparer.ExecutionStatisticHelper.addEntry("blocksum read", readEvent);

		return scanArray;
	}

	private static class Scanner {
		private cl_mem inputBuffer;
		private cl_mem outputBuffer;
		private cl_mem blocksumBuffer;
		private int n;
		private int outputSize;
		private int blocksumSize;

		Scanner(cl_mem inputBuffer, int n) {
			this.inputBuffer = inputBuffer;
			this.n = n;
		}

		cl_mem getOutputBuffer() {
			return outputBuffer;
		}

		cl_mem getBlocksumBuffer() {
			return blocksumBuffer;
		}

		int getOutputSize() {
			return outputSize;
		}

		int getBlocksumSize() {
			return blocksumSize;
		}

		Scanner invoke() {
			File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise2/kernel.c");
			String programSource = "";
			try {
				programSource = FileUtils.readFileToString(programSourceFile);
			} catch (IOException e) {
				e.printStackTrace();
			}

//			int n = inputArray.length;
			int blocksumN = n / ((int) local_work_size[0] * 2);
			blocksumN = blocksumN > 0 ? blocksumN : 1;
			long global_work_size[] = new long[]{n};
			int[] outputArray = new int[n];
			int[] blocksumArray = new int[blocksumN];
//			int tempN = local_work_size[0] < n ? n*(n/((int)local_work_size[0]*2)) : n;
//			int[] tempArray = new int[tempN];

			// Set the work-item dimensions

			Pointer outputPointer = Pointer.to(outputArray);
			Pointer blocksumPointer = Pointer.to(blocksumArray);
//			Pointer tempPointer = Pointer.to(tempArray);

			// Allocate the memory objects for the input- and output data
//			cl_mem memObjects[] = new cl_mem[1];
			outputBuffer = clCreateBuffer(context,
					CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
					Sizeof.cl_int * n, outputPointer, null);
			blocksumBuffer = clCreateBuffer(context,
					CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
					Sizeof.cl_int * blocksumN, blocksumPointer, null);
//			memObjects[0] = clCreateBuffer(context,
//					CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
//					Sizeof.cl_int * tempN, tempPointer, null);

			// Create the program from the source code
			cl_program program = clCreateProgramWithSource(context,
					1, new String[]{programSource}, null, null);

			// Build the program
			clBuildProgram(program, 0, null, null, null, null);

			// Create the kernel
			cl_kernel kernel = clCreateKernel(program, "scan", null);

			// Set the arguments for the kernel
			clSetKernelArg(kernel, 0,
					Sizeof.cl_mem, Pointer.to(outputBuffer));
			clSetKernelArg(kernel, 1,
					Sizeof.cl_mem, Pointer.to(inputBuffer));
			clSetKernelArg(kernel, 2,
					Sizeof.cl_mem, Pointer.to(blocksumBuffer));
//			clSetKernelArg(kernel, 3,
//					Sizeof.cl_mem, Pointer.to(memObjects[0]));
			clSetKernelArg(kernel, 3, Sizeof.cl_int * local_work_size[0] * 2, null);
			clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{n}));

			// Execute the kernel
			cl_event kernelEvent = new cl_event();
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
					global_work_size, local_work_size, 0, null, kernelEvent);

			// Read the output data
			cl_event readEvent1 = new cl_event();
			clEnqueueReadBuffer(commandQueue, outputBuffer, CL_TRUE, 0,
					n * Sizeof.cl_int, outputPointer, 0, null, readEvent1);
			cl_event readEvent2 = new cl_event();
			clEnqueueReadBuffer(commandQueue, blocksumBuffer, CL_TRUE, 0,
					blocksumN * Sizeof.cl_int, blocksumPointer, 0, null, readEvent2);

			outputSize = outputArray.length;
			blocksumSize = blocksumArray.length;

			// Release kernel, program, and memory objects
//			clReleaseMemObject(memObjects[0]);
			clReleaseKernel(kernel);
			clReleaseProgram(program);

			// Collect statistic
//			ScanComparer.ExecutionStatisticHelper.addEntry("scan kernel", kernelEvent);
//			ScanComparer.ExecutionStatisticHelper.addEntry("scan read", readEvent1);
//			ScanComparer.ExecutionStatisticHelper.addEntry("scan read", readEvent2);

			return this;
		}
	}
}
