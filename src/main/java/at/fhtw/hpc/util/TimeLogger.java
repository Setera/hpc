package at.fhtw.hpc.util;

public class TimeLogger {

	private long start;
	
	public void start()	{
		start = System.currentTimeMillis();
	}

	public void end(String step) {
		long diff = System.currentTimeMillis() - start;
		
		int minutes = (int) (diff / 60000.0);
		int seconds = (int) (diff / 1000.0) - (minutes * 60);
		int millisecs = (int) (((diff / 1000.0) - (seconds + minutes * 60)) * 1000);

		System.out.printf("%-20s %1d:%02d.%03d\n", step, minutes, seconds, millisecs);
	}
}
