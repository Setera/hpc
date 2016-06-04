package at.fhtw.hpc.exercise3;

import at.fhtw.hpc.exercise2.WorkEfficientParallelScan;
import org.apache.commons.io.FileUtils;
import org.jocl.*;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.jocl.CL.*;

/**
 * @author Teresa Melchart
 *         2.6.16
 */
public class RadixSort {

	private static cl_context context;
	private static cl_command_queue commandQueue;
	private static long[] local_work_size;

	public static void main(String args[]) {
		Integer[] inputArray = {2, 3, 4, 7, 1, 0, 5, 100, 33, 22, 6, 99, 50, 7, 10, 77};
		//Integer[] inputArray = {4,7,2,6,3,5,1,0};
		Integer[] resultArray = Arrays.copyOf(inputArray, inputArray.length);
		int bArray[] = new int[inputArray.length];
		int eArray[] = new int[inputArray.length];
		Integer sArray[] = new Integer[inputArray.length];
		local_work_size = new long[]{2};

		initPlatform();

		BigInteger max = new BigInteger(Collections.max(Arrays.asList(inputArray)).toString());
		int bitLength = max.bitLength();
		for (int i = 0; i < bitLength; i++) {
			for (int j = 0; j < resultArray.length; j++) {
				int b = getBit(resultArray[j], i);
				bArray[j] = b;
				eArray[j] = b == 1 ? 0 : 1;
			}
			Scanner scanner = new Scanner(eArray).invoke();
			int[] outputArray = scanner.getOutputArray();
			int[] blocksumArray = scanner.getBlocksumArray();

			int[] fArray = outputArray;
			if(blocksumArray.length > 1) {
				fArray = createCompleteScanFromBlocks(outputArray, blocksumArray);
			}

			int totalFalse = eArray[eArray.length - 1] + fArray[fArray.length - 1];

			for (int j = 0; j < fArray.length; j++) {
				int t = j - fArray[j] + totalFalse;
				int d = bArray[j] == 1 ? t : fArray[j];
				sArray[d] = resultArray[j];
			}

			resultArray = Arrays.copyOf(sArray, sArray.length);

			System.out.println(Arrays.toString(bArray));
			System.out.println(Arrays.toString(eArray));

			System.out.println(Arrays.toString(outputArray));
			System.out.println(Arrays.toString(blocksumArray));

			System.out.println(Arrays.toString(fArray));
			System.out.println(Arrays.toString(sArray));
		}
		System.out.println(Arrays.toString(resultArray));
		System.out.println(bitLength);
		releasePlatform();
	}

	private static int getBit(int number, int bit) {
		return (number >> bit) & 1;
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
		commandQueue = clCreateCommandQueue(context, device, 0, null);
	}

	public static int[] createCompleteScanFromBlocks(int[] scanArray, int[] blocksumArray) {
		Scanner scanner = new Scanner(blocksumArray).invoke();
		int[] outputArray = scanner.getOutputArray();
		int[] blocksumArray2 = scanner.getBlocksumArray();

		if(blocksumArray2.length > 1) {
			outputArray = createCompleteScanFromBlocks(outputArray, blocksumArray2);
		}

		int[] scannedArray = addBlocksum(Arrays.copyOf(scanArray, scanArray.length), outputArray);
		return scannedArray;
	}

	public static int[] addBlocksum(int[] scanArray, int[] blocksumArray) {
		File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise3/programSource2.c");
		String programSource = "";
		try {
			programSource = FileUtils.readFileToString(programSourceFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		int n = scanArray.length;
		long global_work_size[] = new long[]{n};
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
		clSetKernelArg(kernel, 2, Sizeof.cl_int, Pointer.to(new int[]{(int)local_work_size[0]*2}));

		// Execute the kernel
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, null);

		// Read the output data
		clEnqueueReadBuffer(commandQueue, memObjects[0], CL_TRUE, 0,
				n * Sizeof.cl_int, outputPointer, 0, null, null);
		// Release kernel, program, and memory objects
		clReleaseMemObject(memObjects[0]);
		clReleaseMemObject(memObjects[1]);
		clReleaseKernel(kernel);
		clReleaseProgram(program);
		return scanArray;
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
			File programSourceFile = new File("src/main/resources/at/fhtw/hpc/exercise3/programSource.c");
			String programSource = "";
			try {
				programSource = FileUtils.readFileToString(programSourceFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
			int n = inputArray.length;
			long global_work_size[] = new long[]{n};
			outputArray = new int[n];
			blocksumArray = new int[n / ((int) local_work_size[0] * 2)];

			// Set the work-item dimensions

			Pointer inputPointer = Pointer.to(inputArray);
			Pointer outputPointer = Pointer.to(outputArray);
			Pointer blocksumPointer = Pointer.to(blocksumArray);


			// Allocate the memory objects for the input- and output data
			cl_mem memObjects[] = new cl_mem[3];
			memObjects[0] = clCreateBuffer(context,
					CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
					Sizeof.cl_int * n, outputPointer, null);
			memObjects[1] = clCreateBuffer(context,
					CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
					Sizeof.cl_int * n, inputPointer, null);
			memObjects[2] = clCreateBuffer(context,
					CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
					Sizeof.cl_int * n, blocksumPointer, null);

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
					local_work_size[0] * Sizeof.cl_int, null);
			clSetKernelArg(kernel, 3,
					Sizeof.cl_mem, Pointer.to(memObjects[2]));
			clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[]{n}));

			// Execute the kernel
			clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
					global_work_size, local_work_size, 0, null, null);

			// Read the output data
			clEnqueueReadBuffer(commandQueue, memObjects[0], CL_TRUE, 0,
					n * Sizeof.cl_int, outputPointer, 0, null, null);
			clEnqueueReadBuffer(commandQueue, memObjects[2], CL_TRUE, 0,
					n * Sizeof.cl_int, blocksumPointer, 0, null, null);

			// Release kernel, program, and memory objects
			clReleaseMemObject(memObjects[0]);
			clReleaseMemObject(memObjects[1]);
			clReleaseKernel(kernel);
			clReleaseProgram(program);
			return this;
		}
	}
}
