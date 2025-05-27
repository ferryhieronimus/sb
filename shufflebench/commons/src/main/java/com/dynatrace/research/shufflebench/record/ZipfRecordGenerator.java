package com.dynatrace.research.shufflebench.record;

import java.util.SplittableRandom;
import java.util.function.Supplier;
import java.util.Arrays;

/**
 * Generates Records whose lengths follow a Zipf distribution
 * over the integer range [minSize ... maxSize].
 */
public class ZipfRecordGenerator implements Supplier<Record> {

  private static final int DEFAULT_MIN_SIZE = 128;
  private static final int DEFAULT_MAX_SIZE = 1024;

  private final SplittableRandom rng;
  private final double[] cdf;
  private final int minSize;
  private final int supportSize;

  /**
   * @param seed      PRNG seed (so each source is reproducible)
   * @param exponent  the Zipf exponent s
   */
  public ZipfRecordGenerator(long seed, double exponent) {
    this(seed, DEFAULT_MIN_SIZE, DEFAULT_MAX_SIZE, exponent);
  }

  /**
   * @param seed      PRNG seed (so each source is reproducible)
   * @param minSize   smallest record size
   * @param maxSize   largest record size
   * @param exponent  the Zipf exponent s
   */
  public ZipfRecordGenerator(long seed, int minSize, int maxSize, double exponent) {
    if (minSize < 1 || minSize > maxSize) {
      throw new IllegalArgumentException("minSize must be >=1 and <= maxSize");
    }
    this.rng = new SplittableRandom(seed);
    this.minSize = minSize;
    this.supportSize = maxSize - minSize + 1;

    // build CDF over ranks 1…supportSize
    this.cdf = new double[supportSize];
    double sum = 0.0;
    for (int k = 1; k <= supportSize; k++) {
      sum += 1.0 / Math.pow(k, exponent);
      cdf[k - 1] = sum;
    }
    // normalize to 1.0
    for (int i = 0; i < supportSize; i++) {
      cdf[i] /= sum;
    }
  }

  @Override
  public Record get() {
    // sample uniform in [0,1)
    double u = rng.nextDouble();
    int idx = Arrays.binarySearch(cdf, u);
    if (idx < 0) {
      idx = -idx - 1;
    }
    // map rank back to actual size
    int size = minSize + idx;
    byte[] data = new byte[size];
    rng.nextBytes(data);
    return new Record(data);
  }
}
