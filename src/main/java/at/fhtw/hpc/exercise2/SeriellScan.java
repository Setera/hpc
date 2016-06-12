package at.fhtw.hpc.exercise2;

import at.fhtw.hpc.util.TimeLogger;

/**
 * Exercise 2
 */
public class SeriellScan {

	private static final int SIZE = 100000;

	public static void main(String[] args) {
		// fill array with random numbers
		int[] in = new int[SIZE];
		for(int i = 0; i < in.length; i++)	{
			in[i] = (int)(Math.random() * 5);
		}

		performScan(in);
	}

	public static int[] performScan(int in[]) {
		TimeLogger logger = new TimeLogger();
		logger.start();

		int[] outSeq = new int[in.length];

		outSeq[0] = 0;
		for(int i = 1; i < outSeq.length; i++)	{
			outSeq[i] = outSeq[i-1] + in[i-1];
		}
		logger.end("sequential scan");

		return outSeq;
	}
}
