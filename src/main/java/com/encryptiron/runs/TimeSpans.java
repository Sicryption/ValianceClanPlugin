package com.encryptiron.runs;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;

class TimeSpans
{
    private static final long LONGEST_GAP_WITHIN_ONE_SPAN_MS = 2000;

    private final List<long[]> spans = new ArrayList<>();

    void add(long timeMs)
    {
        if (!spans.isEmpty())
        {
            long[] lastSpan = spans.get(spans.size() - 1);
            if (timeMs - lastSpan[1] <= LONGEST_GAP_WITHIN_ONE_SPAN_MS)
            {
                lastSpan[1] = Math.max(lastSpan[1], timeMs);
                return;
            }
        }

        spans.add(new long[] {timeMs, timeMs});
    }

    void dropEndedBefore(long timeMs)
    {
        spans.removeIf(span -> span[1] < timeMs);
    }

    boolean isEmpty()
    {
        return spans.isEmpty();
    }

    JsonArray toOffsetsWithin(long fromMs, long toMs)
    {
        JsonArray offsets = new JsonArray();
        for (long[] span : spans)
        {
            if (span[1] < fromMs || span[0] > toMs)
            {
                continue;
            }

            JsonArray spanOffsets = new JsonArray();
            spanOffsets.add(Math.max(span[0], fromMs) - toMs);
            spanOffsets.add(Math.min(span[1], toMs) - toMs);
            offsets.add(spanOffsets);
        }

        return offsets;
    }
}
