package com.aare.analyzer.service;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Data
public class MetricWindow {
    private final long windowMillis;
    private final ConcurrentLinkedQueue<EventData> events = new ConcurrentLinkedQueue<>();
    private long totalRequests = 0;
    private long errorRequests = 0;
    private long totalLatency = 0;

    public MetricWindow(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public void addEvent(long latency, boolean isError) {
        cleanOldEvents();
        events.offer(new EventData(System.currentTimeMillis(), latency, isError));
        totalRequests++;
        totalLatency += latency;
        if (isError) {
            errorRequests++;
        }
    }

    private void cleanOldEvents() {
        long cutoff = System.currentTimeMillis() - windowMillis;
        EventData head;
        while ((head = events.peek()) != null && head.timestamp < cutoff) {
            events.poll();
            totalRequests--;
            totalLatency -= head.latency;
            if (head.isError) {
                errorRequests--;
            }
        }
    }

    public long getRequestCount() {
        cleanOldEvents();
        return totalRequests;
    }

    public BigDecimal getErrorRatePct() {
        cleanOldEvents();
        if (totalRequests == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(errorRequests)
                .divide(BigDecimal.valueOf(totalRequests), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public int getP95Latency() {
        cleanOldEvents();
        if (events.isEmpty()) {
            return 0;
        }
        List<Long> latencies = new ArrayList<>();
        events.forEach(event -> latencies.add(event.latency));
        Collections.sort(latencies);
        int index = (int) Math.ceil(0.95 * latencies.size()) - 1;
        return latencies.get(Math.max(0, index)).intValue();
    }

    public int getP50Latency() {
        cleanOldEvents();
        if (events.isEmpty()) {
            return 0;
        }
        List<Long> latencies = new ArrayList<>();
        events.forEach(event -> latencies.add(event.latency));
        Collections.sort(latencies);
        int index = (int) Math.ceil(0.50 * latencies.size()) - 1;
        return latencies.get(Math.max(0, index)).intValue();
    }

    public int getP99Latency() {
        cleanOldEvents();
        if (events.isEmpty()) {
            return 0;
        }
        List<Long> latencies = new ArrayList<>();
        events.forEach(event -> latencies.add(event.latency));
        Collections.sort(latencies);
        int index = (int) Math.ceil(0.99 * latencies.size()) - 1;
        return latencies.get(Math.max(0, index)).intValue();
    }

    private static class EventData {
        long timestamp;
        long latency;
        boolean isError;

        public EventData(long timestamp, long latency, boolean isError) {
            this.timestamp = timestamp;
            this.latency = latency;
            this.isError = isError;
        }
    }
}