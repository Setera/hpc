package at.fhtw.hpc;

import org.jocl.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;

import static org.jocl.CL.*;

/**
 * Exercise 1
 */
public class ImageRotation {

	private static final double angle = 90;

	private static String programSource = "const sampler_t samplerIn = " +
			"    CLK_NORMALIZED_COORDS_FALSE | " +
			"    CLK_ADDRESS_CLAMP |" +
			"    CLK_FILTER_NEAREST;" +
			"const sampler_t samplerOut = " +
			"    CLK_NORMALIZED_COORDS_FALSE |" +
			"    CLK_ADDRESS_CLAMP |" +
			"    CLK_FILTER_NEAREST;" +
			"__kernel void image_rotate(" +
			"                           __read_only  image2d_t sourceImage, "+
			"                           __write_only image2d_t targetImage," +
			"                           int W, int H," +
			"                           float sinTheta," +
			"                           float cosTheta ) {" +
			"    const int ix = get_global_id(0);" +
			"    const int iy = get_global_id(1);" +
			"    float x0 = W/2;" +
			"    float y0 = H/2;" +
			"    float xpos = ((float)ix - x0) * cosTheta - ((float)iy - y0) * sinTheta + x0;" +
			"    float ypos = ((float)ix - x0) * sinTheta + ((float)iy - y0) * cosTheta + y0;" +
			"    int2 posIn = {xpos, ypos};"+
			"    int2 posOut = {ix, iy};"+
			"    if (( ((int)xpos >= 0) && ((int)xpos < W)) " +
			"    && (((int)ypos >= 0) && ((int)ypos < H))) {" +
			"        uint4 pixel = read_imageui(sourceImage, samplerIn, posIn);"+
			"    write_imageui(targetImage, posOut, pixel);"+
			"    }" +
			"}";

	public static void main(String[] args) {
		BufferedImage inputImage;
		int n = 10;
		int width;
		int height;
		try {
			inputImage = ImageIO.read(new File("src/main/resources/flower.png"));
			width = inputImage.getWidth();
			height = inputImage.getHeight();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);


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

		// Create the program from the source code
		cl_program program = clCreateProgramWithSource(context,
				1, new String[]{programSource}, null, null);

		// Build the program
		clBuildProgram(program, 0, null, null, null, null);

		// Create the kernel
		cl_kernel kernel = clCreateKernel(program, "sampleKernel", null);

		// Create the memory object for the input- and output image
		DataBufferInt dataBufferSrc =
				(DataBufferInt)inputImage.getRaster().getDataBuffer();
		int dataSrc[] = dataBufferSrc.getData();

		cl_image_format imageFormat = new cl_image_format();
		imageFormat.image_channel_order = CL_RGBA;
		imageFormat.image_channel_data_type = CL_UNSIGNED_INT8;

		cl_mem inputImageMem = clCreateImage2D(
				context, CL_MEM_READ_ONLY | CL_MEM_USE_HOST_PTR,
				new cl_image_format[]{imageFormat}, width, height,
				width * Sizeof.cl_uint, Pointer.to(dataSrc), null);

		cl_mem outputImageMem = clCreateImage2D(
				context, CL_MEM_WRITE_ONLY,
				new cl_image_format[]{imageFormat}, width, height,
				0, null, null);

		// Set the arguments for the kernel
		clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(inputImageMem));
		clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(outputImageMem));
		clSetKernelArg(kernel, 2, Sizeof.cl_int, Pointer.to(new int[]{width}));
		clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{height}));
		clSetKernelArg(kernel, 4, Sizeof.cl_float, Pointer.to(new float[]{(float) Math.sin(angle)}));
		clSetKernelArg(kernel, 5, Sizeof.cl_float, Pointer.to(new float[]{(float) Math.cos(angle)}));

		// Set the work-item dimensions
		long global_work_size[] = new long[]{n};
		long local_work_size[] = new long[]{1};

		// Execute the kernel
		clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
				global_work_size, local_work_size, 0, null, null);

		// Read the pixel data into the output image
		DataBufferInt dataBufferDst =
				(DataBufferInt)outputImage.getRaster().getDataBuffer();
		int dataDst[] = dataBufferDst.getData();
		clEnqueueReadImage(
				commandQueue, outputImageMem, true, new long[3],
				new long[]{width, height, 1},
				width * Sizeof.cl_uint, 0,
				Pointer.to(dataDst), 0, null, null);

		File outputfile = new File("output.png");
		try {
			ImageIO.write(outputImage, "png", outputfile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Release kernel, program
		clReleaseKernel(kernel);
		clReleaseProgram(program);
		clReleaseCommandQueue(commandQueue);
		clReleaseContext(context);


	}
}
