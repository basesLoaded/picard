/*
 * The MIT License
 *
 * Copyright (c) 2018 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package picard.sam.markduplicates;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.Histogram;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import picard.cmdline.CommandLineProgram;
import picard.sam.DuplicationMetrics;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

/**
 * This class is an extension of AbstractMarkDuplicatesCommandLineProgramTester used to test MarkDuplicatesWithMateCigar with SAM files generated on the fly.
 * This performs the underlying tests defined by classes such as see AbstractMarkDuplicatesCommandLineProgramTest and MarkDuplicatesWithMateCigarTest.
 */
public class MarkDuplicatesSetSizeHistogramTester extends AbstractMarkDuplicatesCommandLineProgramTester {

    final public Map<List<String>, Double> expectedSetSizeMap = new HashMap<>(); // key=(Histogram Label, histogram bin), value=histogram entry

    public MarkDuplicatesSetSizeHistogramTester() {}

    @Override
    protected CommandLineProgram getProgram() { return new MarkDuplicates(); }

    @Test
    public void test() {
        updateExpectedDuplicationMetrics();

        // Read the output and check the duplicate flag
        int outputRecords = 0;
        final SamReader reader = SamReaderFactory.makeDefault().open(getOutput());
        System.out.println(getOutput().getAbsolutePath());
        for (final SAMRecord record : reader) {
            outputRecords++;
            final String key = samRecordToDuplicatesFlagsKey(record);
            if (!this.duplicateFlags.containsKey(key)) {
                System.err.println("DOES NOT CONTAIN KEY: " + key);
            }
            Assert.assertTrue(this.duplicateFlags.containsKey(key));
            final boolean value = this.duplicateFlags.get(key);
            this.duplicateFlags.remove(key);
            if (value != record.getDuplicateReadFlag()) {
                System.err.println("Mismatching read:");
                System.err.print(record.getSAMString());
            }
            Assert.assertEquals(record.getDuplicateReadFlag(), value);
        }
        CloserUtil.close(reader);

        // Ensure the program output the same number of records as were read in
        Assert.assertEquals(outputRecords, this.getNumberOfRecords(), ("saw " + outputRecords + " output records, vs. " + this.getNumberOfRecords() + " input records"));

        // Check the values written to metrics.txt against our input expectations
        final MetricsFile<DuplicationMetrics, Double> metricsOutput = new MetricsFile<DuplicationMetrics, Double>();
        try{
            metricsOutput.read(new FileReader(metricsFile));
        }
        catch (final FileNotFoundException ex) {
            System.err.println("Metrics file not found: " + ex);
        }
        Assert.assertEquals(metricsOutput.getMetrics().size(), 1);
        final DuplicationMetrics observedMetrics = metricsOutput.getMetrics().get(0);
        Assert.assertEquals(observedMetrics.UNPAIRED_READS_EXAMINED, expectedMetrics.UNPAIRED_READS_EXAMINED, "UNPAIRED_READS_EXAMINED does not match expected");
        Assert.assertEquals(observedMetrics.READ_PAIRS_EXAMINED, expectedMetrics.READ_PAIRS_EXAMINED, "READ_PAIRS_EXAMINED does not match expected");
        Assert.assertEquals(observedMetrics.UNMAPPED_READS, expectedMetrics.UNMAPPED_READS, "UNMAPPED_READS does not match expected");
        Assert.assertEquals(observedMetrics.UNPAIRED_READ_DUPLICATES, expectedMetrics.UNPAIRED_READ_DUPLICATES, "UNPAIRED_READ_DUPLICATES does not match expected");
        Assert.assertEquals(observedMetrics.READ_PAIR_DUPLICATES, expectedMetrics.READ_PAIR_DUPLICATES, "READ_PAIR_DUPLICATES does not match expected");
        Assert.assertEquals(observedMetrics.READ_PAIR_OPTICAL_DUPLICATES, expectedMetrics.READ_PAIR_OPTICAL_DUPLICATES, "READ_PAIR_OPTICAL_DUPLICATES does not match expected");
        Assert.assertEquals(observedMetrics.PERCENT_DUPLICATION, expectedMetrics.PERCENT_DUPLICATION, "PERCENT_DUPLICATION does not match expected");
        Assert.assertEquals(observedMetrics.ESTIMATED_LIBRARY_SIZE, expectedMetrics.ESTIMATED_LIBRARY_SIZE, "ESTIMATED_LIBRARY_SIZE does not match expected");
        Assert.assertEquals(observedMetrics.SECONDARY_OR_SUPPLEMENTARY_RDS, expectedMetrics.SECONDARY_OR_SUPPLEMENTARY_RDS, "SECONDARY_OR_SUPPLEMENTARY_RDS does not match expected");


        // Check contents of set size bin against expected values //
        for (final Histogram<Double> histo : metricsOutput.getAllHistograms()) {
            final String label = histo.getValueLabel();
            for (Double bin : histo.keySet()) {
                final List<String> binList =  Arrays.asList(label, String.valueOf(bin));
                if (expectedSetSizeMap.containsKey(binList)) {
                    Histogram.Bin<Double> binValue = histo.get(bin);
                    final double actual = binValue.getValue();
                    final double expected = expectedSetSizeMap.get(binList);
                    Assert.assertEquals(actual, expected);
                }
            }
        }
    }

    @AfterClass
    private void rmTemp() {
        TestUtil.recursiveDelete(getOutputDir());
    }

}
