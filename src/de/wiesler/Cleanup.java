package de.wiesler;

public final class Cleanup {
    /*@ public model_behaviour
      @ requires 0 <= begin <= begin + bucket_begin <= begin + bucket_end <= end <= values.length;
      @
      @ accessible values[begin + bucket_begin..begin + bucket_end - 1], classifier.footprint;
      @
      @ static model boolean cleanedUpSlice(int[] values, int begin, int end, int bucket_begin, int bucket_end, Classifier classifier, int bucket) {
      @     return classifier.isClassOfSlice(values, begin + bucket_begin, begin + bucket_end, bucket) &&
      @         Sorter.smallBucketIsSorted(values, begin, end, bucket_begin, bucket_end);
      @ }
      @*/

    /*@ // For all buckets:
      @ // * Permutation of: (\old(written blocks) + buffer content) <-> (full block content)
      @ public normal_behaviour
      @ requires 0 <= begin <= end <= values.length;
      @ requires Functions.isValidBucketStarts(bucket_starts, classifier.num_buckets);
      @ requires bucket_starts[classifier.num_buckets] == end - begin;
      @ requires classifier.num_buckets == buffers.num_buckets && classifier.num_buckets == bucket_pointers.num_buckets;
      @ requires \invariant_for(buffers) && \invariant_for(bucket_pointers) && \invariant_for(classifier);
      @
      @ requires \disjoint(values[*], bucket_starts[*], overflow[*], bucket_pointers.buffer[*], buffers.indices[*], buffers.buffer[*], classifier.footprint);
      @ requires overflow.length == Buffers.BUFFER_SIZE;
      @
      @ requires (\forall int b; 0 <= b <= classifier.num_buckets;
      @     (int) bucket_pointers.aligned_bucket_starts[b] == Buffers.blockAligned(bucket_starts[b])
      @ );
      @ requires (\forall int b; 0 <= b < classifier.num_buckets;
      @     // All elements are read
      @     bucket_pointers.toReadCountOfBucket(b) == 0 &&
      @     // All written elements are classified as b
      @     bucket_pointers.writtenElementsOfBucketClassified(classifier, values, begin, end, overflow, b) &&
      @     // Remaining elements: bucket size == buffer length + written elements
      @     bucket_starts[b + 1] - bucket_starts[b] == buffers.bufferAt(b).length + bucket_pointers.writtenCountOfBucket(b) &&
      @     // Buffer length, just a remainder modulo BUFFER_SIZE that is never 0 for nonempty buckets
      @     buffers.bufferAt(b).length ==
      @         ((bucket_starts[b + 1] - bucket_starts[b] >= Buffers.BUFFER_SIZE && (bucket_starts[b + 1] - bucket_starts[b]) % Buffers.BUFFER_SIZE == 0) ?
      @             Buffers.BUFFER_SIZE : ((bucket_starts[b + 1] - bucket_starts[b]) % Buffers.BUFFER_SIZE))
      @ );
      @
      @ ensures (\forall int b; 0 <= b < classifier.num_buckets;
      @     classifier.isClassOfSlice(values, begin + bucket_starts[b], begin + bucket_starts[b + 1], b) &&
      @     Sorter.smallBucketIsSorted(values, begin, end, bucket_starts[b], bucket_starts[b + 1])
      @ );
      @
      @ // Permutation property
      @ ensures (\forall int b; 0 <= b < classifier.num_buckets;
      @     (\forall int element; true;
      @         \old(bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, b, element)) +
      @             \old(buffers.countElementInBucket(b, element)) ==
      @         Functions.countElement(values, begin + bucket_starts[b], begin + bucket_starts[b + 1], element))
      @ );
      @
      @ assignable values[begin..end - 1];
      @*/
    public static void cleanup(
            final int[] values,
            final int begin,
            final int end,
            final Buffers buffers,
            final int[] bucket_starts,
            final BucketPointers bucket_pointers,
            final Classifier classifier,
            final int[] overflow
    ) {
        final int num_buckets = classifier.num_buckets();
        final boolean is_last_level = end - begin <= Constants.SINGLE_LEVEL_THRESHOLD;

        /*@ loop_invariant 0 <= bucket <= num_buckets;
          @ loop_invariant (\forall int b; 0 <= b < bucket;
          @     cleanedUpSlice(values, begin, end, \old(bucket_starts[b]), \old(bucket_starts[b + 1]), classifier, b) &&
          @     (\forall int element; true;
          @         \old(bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, b, element)) +
          @             \old(buffers.countElementInBucket(b, element)) ==
          @         Functions.countElement(values, begin + \old(bucket_starts[b]), begin + \old(bucket_starts[b + 1]), element))
          @ );
          @
          @ loop_invariant (\forall int b; bucket <= b < classifier.num_buckets;
          @     bucket_pointers.writtenElementsOfBucketClassified(classifier, values, begin, end, overflow, b) &&
          @     (\forall int element; true;
          @         \old(bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, b, element)) ==
          @              bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, b, element) &&
          @         \old(buffers.countElementInBucket(b, element)) ==
          @              buffers.countElementInBucket(b, element))
          @ );
          @
          @ loop_invariant \invariant_for(classifier) && \invariant_for(bucket_pointers) && \invariant_for(buffers);
          @
          @ assignable values[begin..end - 1];
          @
          @ decreases num_buckets - bucket;
          @*/
        for (int bucket = 0; bucket < num_buckets; ++bucket) {
            final int relative_start = bucket_starts[bucket];
            final int relative_stop = bucket_starts[bucket + 1];
            final int relative_write = bucket_pointers.write(bucket);
            final int start = begin + relative_start;
            final int stop = begin + relative_stop;
            final int write = begin + relative_write;

            /*@ assert 0 <= bucket_starts[bucket] <= bucket_starts[bucket + 1] <= bucket_starts[num_buckets] &&
              @     Buffers.blockAligned(bucket_starts[num_buckets]) == bucket_pointers.bucketStart(num_buckets);
              @*/

            /*@ normal_behaviour
              @ ensures classifier.isClassOfSlice(values, start, stop, bucket);
              @ ensures (\forall int element; true;
              @     \old(bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, bucket, element)) +
              @         \old(buffers.countElementInBucket(bucket, element)) ==
              @     Functions.countElement(values, start, stop, element)
              @ );
              @
              @ assignable values[start..stop - 1];
              @*/
            {
                /*@ assert relative_write == \old(bucket_pointers.nextWriteOf(bucket)) &&
                  @     \old(Buffers.blockAligned(bucket_starts[bucket])) == \old(bucket_pointers.bucketStart(bucket)) &&
                  @     \old(Buffers.blockAligned(bucket_starts[bucket + 1])) == \old(bucket_pointers.bucketStart(bucket + 1));
                  @*/
                //@ assert \old(Buffers.blockAligned(bucket_starts[bucket])) <= relative_write <= \old(Buffers.blockAligned(bucket_starts[bucket + 1]));

                final int head_start = start;
                int head_stop;

                int tail_start;
                final int tail_stop = stop;

                /*@ normal_behaviour
                  @ requires begin <= start <= stop <= end;
                  @
                  @ ensures \invariant_for(classifier) && \invariant_for(bucket_pointers);
                  @
                  @ ensures start <= head_start <= head_stop <= tail_start <= tail_stop <= stop <= end <= values.length;
                  @ ensures tail_start - head_stop == \old(bucket_pointers.writtenCountOfBucket(bucket));
                  @ ensures classifier.isClassOfSlice(values, head_stop, tail_start, bucket);
                  @ ensures (\forall int element; true;
                  @     Functions.countElement(values, head_stop, tail_start, element) ==
                  @         \old(bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, bucket, element))
                  @ );
                  @
                  @ assignable values[start..stop - 1];
                  @*/
                {
                    // Overflow happened:
                    // - block was written at least once
                    // - write was out of bounds

                    if (relative_stop - relative_start <= Buffers.BUFFER_SIZE) {
                        // Nothing written
                        // Valid: use precondition and split on the buffer length
                        //@ assert \old(bucket_pointers.writtenCountOfBucket(bucket)) == 0;
                        //@ assert (\forall int element; true; \old(bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, bucket, element)) == 0);

                        // Bucket is at most one block large => head is the full block, no tail
                        head_stop = stop;
                        tail_start = stop;
                        // verified
                    } else {
                        final int aligned_relative_start = Buffers.align_to_next_block(relative_start);
                        //@ assert aligned_relative_start == \old(Buffers.blockAligned(relative_start));
                        //@ assert aligned_relative_start == (int) bucket_pointers.aligned_bucket_starts[bucket];
                        head_stop = begin + aligned_relative_start;

                        // Valid: use precondition and observer dependency
                        //@ assert head_start <= head_stop <= tail_stop;

                        if (stop < write) {
                            // Final block has been written
                            // Write pointer must be at aligned end of bucket

                            // Valid: use contract from nextWrite and blockAlign
                            //@ assert relative_write == \old(Buffers.blockAligned(relative_stop));

                            if (end < write) {
                                // Overflow: write is out of bounds, content is in overflow

                                tail_start = write - Buffers.BUFFER_SIZE;

                                // Valid: Use contract of blockAligned
                                //@ assert head_start <= head_stop <= tail_start <= tail_stop;

                                int tail_len = tail_stop - tail_start;

                                // There must be space for all elements
                                // Valid: Precondition and observer
                                //@ assert Buffers.BUFFER_SIZE + buffers.bufferAt(bucket).length == (tail_stop - tail_start) + (head_stop - head_start);

                                // Fill tail
                                Functions.copy_nonoverlapping(overflow, 0, values, tail_start, tail_len);

                                // Write remaining elements to end of head
                                int head_elements = Buffers.BUFFER_SIZE - tail_len;
                                head_stop -= head_elements;
                                Functions.copy_nonoverlapping(overflow, tail_len, values, head_stop, head_elements);

                                //@ assert \invariant_for(classifier) && \invariant_for(bucket_pointers);

                                // Follows from writtenElementsOfBucketClassified
                                //@ assert classifier.isClassOfSlice(overflow, 0, Buffers.BUFFER_SIZE, bucket);

                                //@ assert classifier.isClassOfSlice(values, head_stop + head_elements, tail_stop, bucket);
                                //@ assert classifier.isClassOfSliceSplit(overflow, 0, tail_len, Buffers.BUFFER_SIZE, bucket);
                                //@ assert classifier.isClassOfSliceCopy(overflow, 0, values, tail_stop, tail_len, bucket);
                                //@ assert classifier.isClassOfSliceCopy(overflow, tail_len, values, head_stop, head_elements, bucket);
                                //@ assert classifier.isClassOfSliceConcat(values, head_stop, head_stop + head_elements, tail_start, bucket);
                                //@ assert classifier.isClassOfSliceConcat(values, head_stop, tail_start, tail_stop, bucket);

                                /*@ assert (\forall int element; true;
                                  @     \old(bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, bucket, element)) ==
                                  @         Functions.countElement(values, head_stop + head_elements, tail_start, element) +
                                  @         Functions.countElement(overflow, 0, Buffers.BUFFER_SIZE, element)
                                  @ );
                                  @*/

                                // Split overflow buffer
                                //@ assert Functions.countElementSplit(overflow, 0, tail_len, Buffers.BUFFER_SIZE);
                                // Split off tail
                                //@ assert Functions.countElementSplit(values, head_stop, tail_start, tail_stop);
                                // Split off head
                                //@ assert Functions.countElementSplit(values, head_stop, head_stop + head_elements, tail_start);
                                // Rest comes from copy_nonoverlapping and two dependency contracts

                                tail_start = tail_stop;
                                // rest verified
                            } else {
                                // Normal overflow: write is in bounds and content is after the end of the bucket
                                int overflow_len = write - stop;

                                //@ assert head_start <= head_stop <= stop <= stop + overflow_len <= end;

                                // Must fit, no other empty space left
                                // Follows from precondition and lots of observers
                                //@ assert overflow_len + buffers.bufferAt(bucket).length == head_stop - head_start;
                                // Follows from previous and buffer length >= 0
                                //@ assert head_start <= head_stop - overflow_len;

                                // Write overflow of last block to [head_stop - overflow_len..head_stop)
                                head_stop -= overflow_len;

                                Functions.copy_nonoverlapping(values, stop, values, head_stop, overflow_len);

                                //@ assert \invariant_for(classifier) && \invariant_for(bucket_pointers);

                                //@ assert classifier.isClassOfSlice(values, head_stop + overflow_len, write, bucket);
                                //@ assert classifier.isClassOfSliceSplit(values, head_stop + overflow_len, stop, write, bucket);
                                //@ assert classifier.isClassOfSliceCopy(values, stop, values, head_stop, overflow_len, bucket);
                                //@ assert classifier.isClassOfSliceConcat(values, head_stop, head_stop + overflow_len, stop, bucket);

                                /*@ assert (\forall int element; true;
                                  @     \old(bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, bucket, element)) ==
                                  @         Functions.countElement(values, begin + \old(bucket_pointers.bucketStart(bucket)), begin + \old(bucket_pointers.nextWriteOf(bucket)), element)
                                  @ );
                                  @*/
                                //@ assert Functions.countElementSplit(values, head_stop + overflow_len, stop, write);
                                //@ assert Functions.countElementSplit(values, head_stop, head_stop + overflow_len, stop);

                                // Tail is empty
                                tail_start = tail_stop;
                                // rest verified (all the dependency contracts)
                            }
                        } else {
                            // No overflow, write <= stop
                            tail_start = write;

                            // verified: use equality
                        }
                    }

                    //@ assert \invariant_for(classifier) && \invariant_for(bucket_pointers);
                }

                // Write remaining elements from buffer to head and tail
                buffers.distribute(
                        bucket,
                        values,
                        head_start,
                        head_stop - head_start,
                        tail_start,
                        tail_stop - tail_start
                );
                //@ assert Functions.countElementSplit(values, head_start, head_stop, stop);
                //@ assert Functions.countElementSplit(values, head_stop, tail_start, tail_stop);
                //@ assert classifier.isClassOfSliceConcat(values, head_start, head_stop, tail_start, bucket);
                //@ assert classifier.isClassOfSliceConcat(values, head_stop, tail_start, tail_stop, bucket);
            }

            /*@ normal_behaviour
              @ ensures cleanedUpSlice(values, begin, end, \old(bucket_starts[bucket]), \old(bucket_starts[bucket + 1]), classifier, bucket);
              @ ensures (\forall int element; true;
              @     \old(bucket_pointers.writtenElementsOfBucketCountElement(values, begin, end, overflow, bucket, element)) +
              @         \old(buffers.countElementInBucket(bucket, element)) ==
              @     Functions.countElement(values, start, stop, element)
              @ );
              @
              @ assignable values[start..stop - 1];
              @*/
            {
                if (stop - start <= Constants.ACTUAL_BASE_CASE_SIZE || is_last_level) {
                    // seqPerm(seq, seq2)
                    // forall i in seq2; f(i) ==> forall i in seq; f(i)
                    Sorter.fallback_sort(values, start, stop);
                }
            }

            /*@ assert \invariant_for(classifier) && \invariant_for(bucket_pointers) && \invariant_for(buffers) &&
              @     (\forall int b; 0 <= b < num_buckets && b != bucket;
              @         (b < bucket ==> 0 <= \old(bucket_starts[b]) <= \old(bucket_starts[b + 1]) <= \old(bucket_starts[bucket])) &&
              @         (b > bucket ==> \old(bucket_starts[bucket + 1]) <= \old(bucket_starts[b]) <= \old(bucket_starts[b + 1]) <= \old(bucket_starts[num_buckets])));
              @*/
        }
    }
}
