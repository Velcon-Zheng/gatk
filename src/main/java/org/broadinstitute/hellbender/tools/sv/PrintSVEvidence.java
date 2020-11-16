package org.broadinstitute.hellbender.tools.sv;

import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureCodec;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import org.broadinstitute.barclay.argparser.ExperimentalFeature;
import org.broadinstitute.barclay.help.DocumentedFeature;
import org.broadinstitute.hellbender.cmdline.StandardArgumentDefinitions;
import org.broadinstitute.hellbender.cmdline.programgroups.StructuralVariantDiscoveryProgramGroup;
import org.broadinstitute.hellbender.engine.*;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.utils.io.FeatureOutputStream;
import org.broadinstitute.hellbender.utils.io.TabixIndexedFeatureOutputStream;
import org.broadinstitute.hellbender.utils.io.UncompressedFeatureOutputStream;

/**
 * Prints SV evidence records. Can be used with -L to retrieve records on a set of intervals.
 *
 * <h3>Inputs</h3>
 *
 * <ul>
 *     <li>
 *         Evidence file URI
 *     </li>
 * </ul>
 *
 * <h3>Output</h3>
 *
 * <ul>
 *     <li>
 *         Evidence file (local)
 *     </li>
 * </ul>
 *
 * <h3>Usage example</h3>
 *
 * <pre>
 *     gatk PrintSVEvidence \
 *       --evidence-file gs://my-bucket/batch_name.SR.txt.gz \
 *       -L intervals.bed \
 *       -O local.SR.txt.gz
 * </pre>
 *
 * @author Mark Walker &lt;markw@broadinstitute.org&gt;
 */

@CommandLineProgramProperties(
        summary = "Prints SV evidence records",
        oneLineSummary = "Prints SV evidence records",
        programGroup = StructuralVariantDiscoveryProgramGroup.class
)
@ExperimentalFeature
@DocumentedFeature
public final class PrintSVEvidence extends FeatureWalker<Feature> {

    public static final String EVIDENCE_FILE_NAME = "evidence-file";
    public static final String COMPRESSION_LEVEL_NAME = "compression-level";

    @Argument(
            doc = "Input file URI with extension '.SR.txt', '.PE.txt', '.BAF.txt', or '.RD.txt' (may be gzipped).",
            fullName = EVIDENCE_FILE_NAME
    )
    private GATKPath inputFilePath;

    @Argument(
            doc = "Output file. Filenames ending in '.gz' will be block compressed.",
            fullName = StandardArgumentDefinitions.OUTPUT_LONG_NAME,
            shortName = StandardArgumentDefinitions.OUTPUT_SHORT_NAME
    )
    private GATKPath outputFile;

    @Argument(
            doc = "Output compression level",
            fullName = COMPRESSION_LEVEL_NAME
    )
    private int compressionLevel = 4;

    private FeatureOutputStream outputStream;

    @Override
    protected boolean isAcceptableFeatureType(final Class<? extends Feature> featureType) {
        return featureType.equals(BafEvidence.class) || featureType.equals(DepthEvidence.class)
                || featureType.equals(DiscordantPairEvidence.class) || featureType.equals(SplitReadEvidence.class);
    }

    @Override
    public GATKPath getDrivingFeaturesPath() {
        return inputFilePath;
    }

    @Override
    public void onTraversalStart() {
        initializeOutput();
        writeHeader();
    }

    private void initializeOutput() {
        if (IOUtil.hasBlockCompressedExtension(outputFile.toPath())) {
            final FeatureCodec codec = FeatureManager.getCodecForFile(outputFile.toPath());
            outputStream = new TabixIndexedFeatureOutputStream(outputFile, codec, getBestAvailableSequenceDictionary(), compressionLevel);
        } else {
            outputStream = new UncompressedFeatureOutputStream(outputFile, getBestAvailableSequenceDictionary());
        }
    }

    private void writeHeader() {
        final Object header = getDrivingFeaturesHeader();
        if (header != null) {
            if (header instanceof String) {
                outputStream.writeHeader((String) header);
            } else {
                throw new GATKException.ShouldNeverReachHereException("Expected header object of type " + String.class.getSimpleName());
            }
        }
    }

    @Override
    public void apply(final Feature feature,
                      final ReadsContext readsContext,
                      final ReferenceContext referenceContext,
                      final FeatureContext featureContext) {
        // All evidence data types implement an encoding with toString()
        outputStream.add(feature, f -> f.toString());
    }

    @Override
    public Object onTraversalSuccess() {
        outputStream.close();
        return null;
    }
}
