package com.example.pulsediagnosis;

/** Bounded, ordered raw capture frames. Independent of Android for transport checks. */
public final class RawCaptureAssembler {
    private StringBuilder samples;
    private int expected, received, sequence, rate;
    private long sum;
    public void reset() { samples = null; expected = received = sequence = 0; sum = 0; }
    public String accept(String frame) {
        if (frame.startsWith("RAW_BEGIN:")) {
            reset();
            String[] fields = frame.substring(10).split(",", -1);
            if (fields.length != 2) throw new IllegalArgumentException("Invalid capture header");
            rate = Integer.parseInt(fields[0]); expected = Integer.parseInt(fields[1]);
            if (rate < 16 || rate > 256 || expected < 320 || expected > 8192)
                throw new IllegalArgumentException("Invalid capture size/rate");
            samples = new StringBuilder(expected * 8);
            return null;
        }
        if (samples == null) throw new IllegalArgumentException("Capture header missing; reconnect and recapture");
        if (frame.startsWith("RAW_DATA:")) {
            String payload = frame.substring(9);
            int colon = payload.indexOf(':');
            if (colon < 1 || Integer.parseInt(payload.substring(0, colon)) != sequence)
                throw new IllegalArgumentException("Bluetooth sample chunk missing or out of order");
            String[] values = payload.substring(colon + 1).split(",", -1);
            if (values.length < 1 || values.length > 32 || received + values.length > expected)
                throw new IllegalArgumentException("Invalid sample chunk size");
            for (String value : values) {
                long sample = Long.parseLong(value);
                if (sample < 0 || sample > 262143) throw new IllegalArgumentException("Invalid IR sample");
                if (received++ > 0) samples.append(',');
                samples.append(sample); sum += sample;
            }
            sequence++;
            return null;
        }
        if (frame.startsWith("RAW_END:")) {
            String[] fields = frame.substring(8).split(",", -1);
            if (fields.length != 2 || Integer.parseInt(fields[0]) != sequence ||
                    Long.parseLong(fields[1]) != sum || received != expected)
                throw new IllegalArgumentException("Incomplete Bluetooth capture; recapture");
            String result = "{\"sample_rate\":" + rate + ",\"sensor\":\"MAX30102\",\"samples\":[" + samples + "]}";
            reset(); return result;
        }
        throw new IllegalArgumentException("Invalid raw capture frame");
    }
}
