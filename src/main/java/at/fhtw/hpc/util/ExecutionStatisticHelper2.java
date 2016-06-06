package at.fhtw.hpc.util;

/**
 * A simple helper class for tracking cl_events and printing
 * timing information for the execution of the commands that
 * are associated with the events.
 */


import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ExecutionStatisticHelper2 {
    /**
     * The list of entries in this instance
     */
    private List<Entry> entries = new ArrayList<Entry>();

    private HashMap<String, Double[]> aggregatedEntries = new HashMap<String, Double[]>();

    /**
     * Adds the specified entry to this instance
     *
     * @param name  A name for the event
     * @param event The event
     */
    public void addEntry(String name, cl_event event) {
        entries.add(new Entry(name, event));
    }

    /**
     * Removes all entries
     */
    public void clear() {
        entries.clear();
    }

    /**
     * Normalize the entries, so that the times are relative
     * to the time when the first event was queued
     */
    private void normalize() {
        long minQueuedTime = Long.MAX_VALUE;
        for (Entry entry : entries) {
            minQueuedTime = Math.min(minQueuedTime, entry.getQueuedTime());
        }
        for (Entry entry : entries) {
            entry.normalize(minQueuedTime);
        }
    }

    private void aggregate() {
        aggregatedEntries = new HashMap<String, Double[]>();
        for (Entry entry : entries) {
            if(aggregatedEntries.containsKey(entry.getName())) {
                Double[] item = aggregatedEntries.get(entry.getName());
                item[0] += new Double(entry.getDuration());
                item[1] += 1d;
                aggregatedEntries.put(entry.getName(), item);
            } else {
                Double[] item = new Double[2];
                item[0] = new Double(entry.getDuration());
                item[1] = 1d;
                aggregatedEntries.put(entry.getName(), item);
            }
        }
    }

    /**
     * Print the statistics
     */
    public void print() {
        normalize();
        for (Entry entry : entries) {
            entry.print();
        }
    }

    public void printSummary() {
        normalize();
        aggregate();
        for (Map.Entry<String, Double[]> entry : aggregatedEntries.entrySet()) {
            System.out.println("Event: " + entry.getKey() + " (" + entry.getValue()[1].intValue() + " times)");
            System.out.println("Time : " +
                    String.format("%8.3f", entry.getValue()[0] / 1e6) + " ms");
        }
    }

    /**
     * A single entry of the ExecutionStatistics
     */
    private static class Entry {
        private String name;
        private long[] submitTime = new long[1];
        private long[] queuedTime = new long[1];
        private long[] startTime = new long[1];
        private long[] endTime = new long[1];
        private long duration = 0;

        Entry(String name) {
            this.setName(name);
        }

        Entry(String name, cl_event event) {
            this.setName(name);
            CL.clGetEventProfilingInfo(
                    event, CL.CL_PROFILING_COMMAND_QUEUED,
                    Sizeof.cl_ulong, Pointer.to(queuedTime), null);
            CL.clGetEventProfilingInfo(
                    event, CL.CL_PROFILING_COMMAND_SUBMIT,
                    Sizeof.cl_ulong, Pointer.to(submitTime), null);
            CL.clGetEventProfilingInfo(
                    event, CL.CL_PROFILING_COMMAND_START,
                    Sizeof.cl_ulong, Pointer.to(startTime), null);
            CL.clGetEventProfilingInfo(
                    event, CL.CL_PROFILING_COMMAND_END,
                    Sizeof.cl_ulong, Pointer.to(endTime), null);

            duration = endTime[0] - startTime[0];
        }

        void normalize(long baseTime) {
            submitTime[0] -= baseTime;
            queuedTime[0] -= baseTime;
            startTime[0] -= baseTime;
            endTime[0] -= baseTime;
        }

        void print() {
            System.out.println("Event " + getName() + ": ");
            System.out.println("Queued : " +
                    String.format("%8.3f", queuedTime[0] / 1e6) + " ms");
            System.out.println("Submit : " +
                    String.format("%8.3f", submitTime[0] / 1e6) + " ms");
            System.out.println("Start  : " +
                    String.format("%8.3f", startTime[0] / 1e6) + " ms");
            System.out.println("End    : " +
                    String.format("%8.3f", endTime[0] / 1e6) + " ms");
            System.out.println("Time   : " +
                    String.format("%8.3f", getDuration() / 1e6) + " ms");
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getSubmitTime() {
            return submitTime[0];
        }

        public void setSubmitTime(long submitTime) {
            this.submitTime[0] = submitTime;
        }

        public long getQueuedTime() {
            return queuedTime[0];
        }

        public void setQueuedTime(long queuedTime) {
            this.queuedTime[0] = queuedTime;
        }

        public long getStartTime() {
            return startTime[0];
        }

        public void setStartTime(long startTime) {
            this.startTime[0] = startTime;
        }

        public long getEndTime() {
            return endTime[0];
        }

        public void setEndTime(long endTime) {
            this.endTime[0] = endTime;
        }

        public long getDuration() {
            return duration;
        }
    }
}
