package at.fhtw.hpc;

import at.fhtw.hpc.util.TimeLogger;

import java.util.Arrays;

/**
 * Exercise 2
 */
public class Scan {

	private static final int SIZE = 100000;

	public static void main(String[] args) {
		TimeLogger logger = new TimeLogger();

		// fill array with random numbers
		int[] in = new int[SIZE];
		for(int i = 0; i < in.length; i++)	{
			in[i] = (int)(Math.random() * 5);
		}
		int[] outSeq = new int[SIZE];
		int[] outPar = new int[SIZE];

		// sequential version
		logger.start();
		outSeq[0] = 0;
		for(int i = 1; i < outSeq.length; i++)	{
			outSeq[i] = outSeq[i-1] + in[i-1];
		}
		logger.end("sequential scan");

		logger.start();
		// parallel scan
		// TODO: 25.05.2016
		logger.end("parallel scan");

		// compare
		for(int i = 0; i<outSeq.length;i++)	{
			if(outPar[i] != outSeq[i])	{
				System.out.println("Scan was wrong at index: " + i + ". (" + outSeq[i] + " vs. " + outPar[i] + ")");
				return;
			}
		}
	}
}
