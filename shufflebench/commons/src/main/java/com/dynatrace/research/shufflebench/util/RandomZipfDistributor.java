package com.dynatrace.research.shufflebench.util;

import java.util.*;
import java.util.SplittableRandom;

public class RandomZipfDistributor {

  /**
   * Samples buffer sizes for an (unordered) list of rule IDs so that
   * on average they follow a Zipf(s) distribution over [minSize…maxSize].
   *
   * @param ruleIds  list of rule IDs (order doesn’t matter)
   * @param minSize  smallest buffer size
   * @param maxSize  largest buffer size
   * @param s        Zipf exponent
   * @param seed     PRNG seed for reproducibility
   */
  public static Map<String,Integer> fromRuleIds(
      List<String> ruleIds,
      int minSize,
      int maxSize,
      double s,
      long seed
  ) {
    int supportSize = maxSize - minSize + 1;
    double[] cdf = new double[supportSize];
    double sum = 0;
    for (int k = 1; k <= supportSize; k++) {
      sum += 1.0 / Math.pow(k, s);
      cdf[k - 1] = sum;
    }
    for (int i = 0; i < supportSize; i++) {
      cdf[i] /= sum;
    }

    SplittableRandom rng = new SplittableRandom(seed);
    Map<String,Integer> sizeMap = new HashMap<>(ruleIds.size());

    for (String ruleId : ruleIds) {
      double u = rng.nextDouble(); 
      int idx = Arrays.binarySearch(cdf, u);
      if (idx < 0) idx = -idx - 1;
      if (idx >= supportSize) idx = supportSize - 1;
      sizeMap.put(ruleId, minSize + idx);
    }

    return sizeMap;
  }
}