package at.fhtw.hpc.exercise2;

import at.fhtw.hpc.util.ExecutionStatisticHelper;

import java.util.Random;

/**
 * Created by JS on 06/06/2016.
 */
public class ScanComparer {


	public static at.fhtw.hpc.util.ExecutionStatisticHelper ExecutionStatisticHelper = new ExecutionStatisticHelper();

	public static void main(String args[]) {
		// fill array with random numbers
		Random random = new Random();

		int size = (int) Math.pow(2, 18);
		System.out.println("Perform scan with array size: " + size);

		float[] input = new float[size];
		for (int i = 0; i < input.length; i++) {
			//input[i] = (float) random.nextInt(9999);
			input[i] = 1;
		}

		float serialOutput[] = Scan.performScan(input);

		ScanComparer.ExecutionStatisticHelper.clear();
		float parallelOutput[] = WorkEfficientParallelScan.performScan(input);
		ScanComparer.ExecutionStatisticHelper.printSummary();

		// compare results
		for (int i = 0; i < serialOutput.length; i++) {
			if (parallelOutput[i] != serialOutput[i]) {
				System.out.println("Scan was wrong at index: " + i + ". (" + serialOutput[i] + " vs. " + parallelOutput[i] + ")");
				return;
			}
		}
	}
}
