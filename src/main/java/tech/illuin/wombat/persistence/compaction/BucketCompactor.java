package tech.illuin.wombat.persistence.compaction;

import java.util.List;

public interface BucketCompactor
{
    int compactBucket(long bucketStartMs, long stepMs);

    List<Long> uncompactedBuckets(long stepMs, long beforeMs);
}
