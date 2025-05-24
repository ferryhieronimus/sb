package com.dynatrace.research.shufflebench.matcher;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.dynatrace.research.shufflebench.record.TimestampedRecord;

/**
 * Launcher to test SimpleMatcherService.createFromZipf() by generating matchers
 * with different exponents and testing the selectivity distribution.
 */
public class Main {
    public static void main(String[] args) {
        final int numRules = 10_000_000;
        final double totalSelectivity = 1.0;
        final long seed = 0x2e3fac4f58fc98b4L;

        final double[] exponents = {1.0, 1.2, 1.5};

        for (double s : exponents) {
            String outputFile = String.format("selectivities_10m_s%.1f.csv", s);
            
            MatcherService<TimestampedRecord> matcher = 
                SimpleMatcherService.createFromZipf(numRules, totalSelectivity, s, seed);
            System.out.println("Matcher built for s=" + s);
            
            int[] matchCounts = new int[numRules];
            
            Random random = new Random(seed);
            int numTestRecords = 10_000_000; // Number of test records
            
            for (int i = 0; i < numTestRecords; i++) {
                TimestampedRecord record = createTestRecord(i, random);
                
                for (Map.Entry<String, TimestampedRecord> entry : matcher.match(record)) {
                    int ruleId = extractRuleId(entry.getKey());
                    if (ruleId >= 0 && ruleId < numRules) {
                        matchCounts[ruleId]++;
                    }
                }
            }
            
            double[] selectivities = new double[numRules];
            for (int i = 0; i < numRules; i++) {
                selectivities[i] = (double) matchCounts[i] / numTestRecords;
            }
            
            try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFile))) {
                out.write("ruleId,selectivity\n");
                
                // Create a list of rule ID and selectivity pairs
                List<Map.Entry<Integer, Double>> entries = new ArrayList<>(numRules);
                for (int i = 0; i < numRules; i++) {
                    entries.add(Map.entry(i, selectivities[i]));
                }
                
                // Sort by selectivity in descending order (highest first)
                entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
                
                // Write sorted data
                for (Map.Entry<Integer, Double> entry : entries) {
                    out.write(entry.getKey() + "," + entry.getValue() + "\n");
                }
                System.out.println("Wrote sorted selectivities to " + outputFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static TimestampedRecord createTestRecord(int id, Random random) {
        byte[] data = new byte[32];
        random.nextBytes(data);
        
        return new TimestampedRecord(System.currentTimeMillis(), data);
    }
    
    private static int extractRuleId(String key) {
        if (key != null && key.startsWith("consumer_")) {
            try {
                return Integer.parseInt(key.substring(9));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

}