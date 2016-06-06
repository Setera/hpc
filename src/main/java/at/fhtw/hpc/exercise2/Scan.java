package at.fhtw.hpc.exercise2;

import at.fhtw.hpc.util.TimeLogger;

/**
 * Exercise 2
 */
public class Scan {

	private static final int SIZE = 100000;

	public static void main(String[] args) {
		// fill array with random numbers
		float[] in = new float[SIZE];
		for(int i = 0; i < in.length; i++)	{
			in[i] = (float)(Math.random() * 5);
		}

		performScan(in);
	}

	public static float[] performScan(float in[]) {
		TimeLogger logger = new TimeLogger();
		logger.start();

		float[] outSeq = new float[in.length];

		outSeq[0] = 0;
		for(int i = 1; i < outSeq.length; i++)	{
			outSeq[i] = outSeq[i-1] + in[i-1];
		}
		logger.end("sequential scan");

		return outSeq;
	}
}
