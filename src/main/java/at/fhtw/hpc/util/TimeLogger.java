package at.fhtw.hpc.util;


import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class TimeLogger {

	private long start;
	
	public void start()	{
		start = System.nanoTime();
	}

	public void end(String step) {
		long diff = System.nanoTime() - start;
		long seconds = NANOSECONDS.toSeconds(diff);
		long millis = NANOSECONDS.toMillis(diff) % 1000;
		long micros = NANOSECONDS.toMicros(diff) % 1000;
		long nanos = NANOSECONDS.toNanos(diff) % 1000;
		System.out.printf("%-20s %2d s, %3d ms, %3d µs, %3d ns\n", step, seconds, millis, micros, nanos);
	}
}