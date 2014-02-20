package eu.isas.peptideshaker;

import com.compomics.util.Util;
import com.compomics.util.db.ObjectsCache;
import com.compomics.util.experiment.MsExperiment;
import com.compomics.util.experiment.ProteomicAnalysis;
import com.compomics.util.experiment.biology.*;
import com.compomics.util.experiment.identification.*;
import com.compomics.util.experiment.identification.identifications.Ms2Identification;
import com.compomics.util.experiment.identification.matches.ModificationMatch;
import com.compomics.util.experiment.identification.matches.PeptideMatch;
import com.compomics.util.experiment.identification.matches.ProteinMatch;
import com.compomics.util.experiment.identification.matches.SpectrumMatch;
import com.compomics.util.experiment.identification.ptm.PtmScore;
import com.compomics.util.experiment.identification.ptm.PtmSiteMapping;
import com.compomics.util.experiment.identification.ptm.ptmscores.AScore;
import com.compomics.util.experiment.identification.ptm.ptmscores.PhosphoRS;
import com.compomics.util.experiment.identification.spectrum_annotators.PeptideSpectrumAnnotator;
import com.compomics.util.experiment.massspectrometry.MSnSpectrum;
import com.compomics.util.experiment.massspectrometry.Precursor;
import com.compomics.util.experiment.massspectrometry.Spectrum;
import com.compomics.util.experiment.massspectrometry.SpectrumFactory;
import com.compomics.util.math.statistics.distributions.NonSymmetricalNormalDistribution;
import eu.isas.peptideshaker.fileimport.FileImporter;
import com.compomics.util.preferences.IdFilter;
import com.compomics.util.waiting.WaitingHandler;
import com.compomics.util.messages.FeedBack;
import com.compomics.util.preferences.AnnotationPreferences;
import com.compomics.util.preferences.ModificationProfile;
import eu.isas.peptideshaker.myparameters.PSMaps;
import eu.isas.peptideshaker.myparameters.PSParameter;
import eu.isas.peptideshaker.myparameters.PSPtmScores;
import com.compomics.util.preferences.PTMScoringPreferences;
import com.compomics.util.preferences.ProcessingPreferences;
import eu.isas.peptideshaker.filtering.PeptideFilter;
import eu.isas.peptideshaker.filtering.ProteinFilter;
import eu.isas.peptideshaker.filtering.PsmFilter;
import eu.isas.peptideshaker.preferences.ProjectDetails;
import eu.isas.peptideshaker.preferences.SpectrumCountingPreferences;
import eu.isas.peptideshaker.scoring.InputMap;
import eu.isas.peptideshaker.scoring.*;
import eu.isas.peptideshaker.scoring.targetdecoy.TargetDecoyMap;
import eu.isas.peptideshaker.scoring.targetdecoy.TargetDecoyResults;
import eu.isas.peptideshaker.utils.IdentificationFeaturesGenerator;
import eu.isas.peptideshaker.utils.Metrics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import javax.swing.RowFilter;
import uk.ac.ebi.jmzml.xml.io.MzMLUnmarshallerException;

/**
 * This class will be responsible for the identification import and the
 * associated calculations.
 *
 * @author Marc Vaudel
 * @author Harald Barsnes
 */
public class PeptideShaker {

    /**
     * The experiment conducted.
     */
    private MsExperiment experiment;
    /**
     * The sample analyzed.
     */
    private Sample sample;
    /**
     * The replicate number.
     */
    private int replicateNumber;
    /**
     * The psm target decoy map.
     */
    private PsmSpecificMap psmMap;
    /**
     * The peptide target decoy map
     */
    private PeptideSpecificMap peptideMap;
    /**
     * The protein target decoy map.
     */
    private ProteinMap proteinMap;
    /**
     * The PSM PTM localization conflict map.
     */
    private PsmPTMMap psmPTMMap;
    /**
     * The id importer will import and process the identifications.
     */
    private FileImporter fileImporter = null;
    /**
     * The sequence factory.
     */
    private SequenceFactory sequenceFactory = SequenceFactory.getInstance();
    /**
     * The spectrum factory.
     */
    private SpectrumFactory spectrumFactory = SpectrumFactory.getInstance();
    /**
     * The xml file containing the enzymes.
     */
    public static final String ENZYME_FILE = "resources/conf/peptideshaker_enzymes.xml";
    /**
     * Modification file.
     */
    public static final String MODIFICATIONS_FILE = "resources/conf/peptideshaker_mods.xml";
    /**
     * User modification file.
     */
    public static final String USER_MODIFICATIONS_FILE = "resources/conf/peptideshaker_usermods.xml";
    /**
     * User preferences file.
     */
    public static final String USER_PREFERENCES_FILE = System.getProperty("user.home") + "/.peptideshaker/userpreferences.cpf";
    /**
     * The location of the folder used for serialization of matches.
     */
    public final static String SERIALIZATION_DIRECTORY = "resources/matches";
    /**
     * The name of the serialized experiment
     */
    public final static String experimentObjectName = "experiment";
    /**
     * The compomics PTM factory.
     */
    private PTMFactory ptmFactory = PTMFactory.getInstance();
    /**
     * Metrics to be picked when loading the identification.
     */
    private Metrics metrics = new Metrics();
    /**
     * An identification features generator which will compute figures on the
     * identification matches and keep some of them in memory.
     */
    private IdentificationFeaturesGenerator identificationFeaturesGenerator;
    /**
     * Map indicating how often a protein was found in a search engine first hit
     * whenever this protein was found more than one time.
     */
    private HashMap<String, Integer> proteinCount = new HashMap<String, Integer>();
    /**
     * A cache where the objects will be saved.
     */
    private ObjectsCache objectsCache;
    /**
     * List of warnings collected while working on the data.
     */
    private HashMap<String, FeedBack> warnings = new HashMap<String, FeedBack>();
    /**
     * Number of groups deleted because of protein evidence issues.
     */
    private int evidenceIssue = 0;
    /**
     * Number of groups deleted because of enzymatic issues.
     */
    private int enzymaticIssue = 0;
    /**
     * Number of groups deleted because of protein characterization issues.
     */
    private int uncharacterizedIssue = 0;
    /**
     * The type of matching used for peptide to protein matching.
     */
    public final static AminoAcidPattern.MatchingType MATCHING_TYPE = AminoAcidPattern.MatchingType.indistiguishibleAminoAcids;

    /**
     * Constructor without mass specification. Calculation will be done on new
     * maps which will be retrieved as compomics utilities parameters.
     *
     * @param experiment The experiment conducted
     * @param sample The sample analyzed
     * @param replicateNumber The replicate number
     */
    public PeptideShaker(MsExperiment experiment, Sample sample, int replicateNumber) {
        this.experiment = experiment;
        this.sample = sample;
        this.replicateNumber = replicateNumber;
        psmMap = new PsmSpecificMap();
        peptideMap = new PeptideSpecificMap();
        proteinMap = new ProteinMap();
        psmPTMMap = new PsmPTMMap();
    }

    /**
     * Constructor with map specifications.
     *
     * @param experiment The experiment conducted
     * @param sample The sample analyzed
     * @param replicateNumber The replicate number
     * @param psMaps the peptide shaker maps
     */
    public PeptideShaker(MsExperiment experiment, Sample sample, int replicateNumber, PSMaps psMaps) {
        this.experiment = experiment;
        this.sample = sample;
        this.replicateNumber = replicateNumber;
        this.psmMap = psMaps.getPsmSpecificMap();
        this.peptideMap = psMaps.getPeptideSpecificMap();
        this.proteinMap = psMaps.getProteinMap();
    }

    /**
     * Method used to import identification from identification result files.
     *
     * @param waitingHandler the handler displaying feedback to the user
     * @param idFilter The identification filter to use
     * @param idFiles The files to import
     * @param spectrumFiles The corresponding spectra (can be empty: spectra
     * will not be loaded)
     * @param searchParameters the identification parameters used for the search
     * @param annotationPreferences The annotation preferences to use for PTM
     * scoring
     * @param projectDetails The project details
     * @param processingPreferences the initial processing preferences
     * @param ptmScoringPreferences the PTM scoring preferences
     * @param spectrumCountingPreferences the spectrum counting preferences
     * @param backgroundThread boolean indicating whether the import should be
     * done in a background thread (GUI mode) or in the current thread (command
     * line mode).
     */
    public void importFiles(WaitingHandler waitingHandler, IdFilter idFilter, ArrayList<File> idFiles, ArrayList<File> spectrumFiles,
            SearchParameters searchParameters, AnnotationPreferences annotationPreferences, ProjectDetails projectDetails,
            ProcessingPreferences processingPreferences, PTMScoringPreferences ptmScoringPreferences, SpectrumCountingPreferences spectrumCountingPreferences, boolean backgroundThread) {

        waitingHandler.appendReport("Import process for " + experiment.getReference() + " (Sample: " + sample.getReference() + ", Replicate: " + replicateNumber + ")", true, true);
        waitingHandler.appendReportEndLine();

        objectsCache = new ObjectsCache();
        objectsCache.setAutomatedMemoryManagement(true);

        ProteomicAnalysis analysis = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber);
        analysis.addIdentificationResults(IdentificationMethod.MS2_IDENTIFICATION, new Ms2Identification(getIdentificationReference()));
        Identification identification = analysis.getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        identification.setIsDB(true);

        fileImporter = new FileImporter(this, waitingHandler, analysis, idFilter, metrics);
        fileImporter.importFiles(idFiles, spectrumFiles, searchParameters, annotationPreferences, processingPreferences, ptmScoringPreferences, spectrumCountingPreferences, projectDetails, backgroundThread);
    }

    /**
     * Returns the object cache.
     *
     * @return the object cache
     */
    public ObjectsCache getCache() {
        return objectsCache;
    }

    /**
     * Returns the reference identifying the identification under process.
     *
     * @return a String identifying the identification under process
     */
    public String getIdentificationReference() {
        return Identification.getDefaultReference(experiment.getReference(), sample.getReference(), replicateNumber);
    }

    /**
     * This method processes the identifications and fills the PeptideShaker
     * maps.
     *
     * @param inputMap The input map
     * @param waitingHandler the handler displaying feedback to the user
     * @param searchParameters
     * @param annotationPreferences
     * @param idFilter
     * @param processingPreferences
     * @param ptmScoringPreferences
     * @param spectrumCountingPreferences
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws Exception
     */
    public void processIdentifications(InputMap inputMap, WaitingHandler waitingHandler, SearchParameters searchParameters, AnnotationPreferences annotationPreferences,
            IdFilter idFilter, ProcessingPreferences processingPreferences, PTMScoringPreferences ptmScoringPreferences, SpectrumCountingPreferences spectrumCountingPreferences)
            throws IllegalArgumentException, IOException, Exception {

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        identificationFeaturesGenerator = new IdentificationFeaturesGenerator(identification, searchParameters, idFilter, metrics, spectrumCountingPreferences);

        if (!objectsCache.memoryCheck()) {
            waitingHandler.appendReport("PeptideShaker is encountering memory issues! See <a href=\"http://peptide-shaker.googlecode.com\">http://peptide-shaker.googlecode.com</a> for help.", true, true);
        }
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Computing assumptions probabilities.", true, true);
        if (sequenceFactory.concatenatedTargetDecoy()) {
            inputMap.estimateProbabilities(waitingHandler);
        }
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Saving assumptions probabilities.", true, true);
        attachAssumptionsProbabilities(inputMap, waitingHandler);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Selecting best peptide per spectrum.", true, true);
        fillPsmMap(inputMap, waitingHandler, searchParameters, annotationPreferences, idFilter);
        psmMap.cure();
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Computing PSM probabilities.", true, true);
        psmMap.estimateProbabilities(waitingHandler);
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        String report = "Scoring PTMs in PSMs (D-score";
        if (ptmScoringPreferences.isProbabilitsticScoreCalculation()) {
            report += " and " + ptmScoringPreferences.getSelectedProbabilisticScore().getName();
        }
        report += ")";
        waitingHandler.appendReport(report, true, true); // @TODO: this is very slow if memory is full!!
        scorePsmPtms(waitingHandler, searchParameters, annotationPreferences, ptmScoringPreferences);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        if (ptmScoringPreferences.isEstimateFlr()) {
            waitingHandler.appendReport("Thresholding PTM localizations.", true, true);
            psmPTMMap.cure();
            psmPTMMap.estimateProbabilities(waitingHandler);
            computeFLR(waitingHandler, ptmScoringPreferences.getFlrThreshold());
        }
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Resolving peptide inference issues.", true, true);
        ptmInference(waitingHandler, ptmScoringPreferences, searchParameters);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Saving probabilities, building peptides and proteins.", true, true);
        attachSpectrumProbabilitiesAndBuildPeptidesAndProteins(waitingHandler, searchParameters); // @TODO: this is very slow if memory is full!!
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Simplifying protein groups.", true, true);
        removeIrrelevantGroups(searchParameters, waitingHandler);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Generating peptide map.", true, true); // slow?
        fillPeptideMaps(waitingHandler, searchParameters);
        peptideMap.cure();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Computing peptide probabilities.", true, true); // should be fast
        peptideMap.estimateProbabilities(waitingHandler);
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Saving peptide probabilities.", true, true); // could be slow
        attachPeptideProbabilities(waitingHandler);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Generating protein map.", true, true); // could be slow
        fillProteinMap(waitingHandler);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Resolving protein inference issues, inferring peptide and protein PI status.", true, true); // could be slow
        retainBestScoringGroups(searchParameters, waitingHandler);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Correcting protein probabilities.", true, true);
        proteinMap.estimateProbabilities(waitingHandler);
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Saving protein probabilities.", true, true);
        attachProteinProbabilities(waitingHandler, processingPreferences);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        if (processingPreferences.getPsmFDR() == 1
                && processingPreferences.getPeptideFDR() == 1
                && processingPreferences.getProteinFDR() == 1) {
            waitingHandler.appendReport("Validating identifications at 1% FDR.", true, true);
        } else {
            waitingHandler.appendReport("Validating identifications.", true, true);
        }
        fdrValidation(waitingHandler, processingPreferences.getPsmFDR(), processingPreferences.getPeptideFDR(), processingPreferences.getProteinFDR(), searchParameters, annotationPreferences, identificationFeaturesGenerator);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Scoring PTMs in peptides.", true, true);
        scorePeptidePtms(waitingHandler, searchParameters, annotationPreferences, ptmScoringPreferences);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }
        waitingHandler.appendReport("Scoring PTMs in proteins.", true, true);
        scoreProteinPtms(waitingHandler, searchParameters, annotationPreferences, ptmScoringPreferences, idFilter, spectrumCountingPreferences);
        waitingHandler.increasePrimaryProgressCounter();
        if (waitingHandler.isRunCanceled()) {
            return;
        }

        report = "Identification processing completed.";

        // get the detailed report
        ArrayList<Integer> suspiciousInput = inputMap.suspiciousInput();
        ArrayList<String> suspiciousPsms = psmMap.suspiciousInput();
        ArrayList<String> suspiciousPeptides = peptideMap.suspiciousInput();
        boolean suspiciousProteins = proteinMap.suspicousInput();

        if (suspiciousInput.size() > 0
                || suspiciousPsms.size() > 0
                || suspiciousPeptides.size() > 0
                || suspiciousProteins) {

            String detailedReport = "";

            boolean firstLine = true;

            for (int searchEngine : suspiciousInput) {
                if (firstLine) {
                    firstLine = false;
                } else {
                    detailedReport += ", ";
                }
                detailedReport += Advocate.getAdvocate(searchEngine).getName();
            }

            if (suspiciousInput.size() > 0) {
                detailedReport += " identifications.<br>";
            }

            firstLine = true;

            if (psmMap.getKeys().size() == 1) {
                detailedReport += "PSMs.<br>";
            } else {
                for (String fraction : suspiciousPsms) {
                    if (firstLine) {
                        firstLine = false;
                    } else {
                        detailedReport += ", ";
                    }
                    detailedReport += fraction;
                }
                if (suspiciousPsms.size() > 0) {
                    detailedReport += " charged PSMs.<br>";
                }
            }

            if (peptideMap.getKeys().size() == 1) {
                detailedReport += "Peptides.<br>";
            } else {
                firstLine = true;
                for (String fraction : suspiciousPeptides) {
                    if (firstLine) {
                        firstLine = false;
                    } else {
                        detailedReport += "<br>";
                    }
                    detailedReport += PeptideSpecificMap.getKeyName(searchParameters.getModificationProfile(), fraction);
                    if (suspiciousPeptides.size() > 0) {
                        detailedReport += " peptides.<br>";
                    }
                }
            }

            if (suspiciousProteins) {
                detailedReport += "Proteins.<br>";
            }

            if (detailedReport.length() > 0) {
                detailedReport = "The following identification classes resulted in non robust statistical estimators, the confidence estimation and validation will be inaccurate for these matches:<br><br>"
                        + detailedReport
                        + "<br>You can inspect this in the <i>Validation</i> tab.";
                //addWarning(new FeedBack(FeedBack.FeedBackType.WARNING, "Non robust statistical estimations", new ArrayList<String>(), detailedReport)); // @TODO: re-add later
            }
        }

        waitingHandler.appendReport(report, true, true);
        waitingHandler.appendReportEndLine();
        waitingHandler.appendReportEndLine();
        identification.addUrParam(new PSMaps(proteinMap, psmMap, peptideMap));
        waitingHandler.setRunFinished();
    }

    /**
     * Computes the FLR calculation.
     *
     * @param waitingHandler waiting handler displaying progress to the user
     * @param psmFLR the PSM FLR
     */
    public void computeFLR(WaitingHandler waitingHandler, double psmFLR) {

        waitingHandler.setWaitingText("Estimating FLR. Please Wait...");

        for (Double ptmMass : psmPTMMap.getModificationsScored()) {

            for (int mapKey : psmPTMMap.getKeys(ptmMass).keySet()) {
                if (waitingHandler.isRunCanceled()) {
                    return;
                }
                waitingHandler.increaseSecondaryProgressCounter();
                TargetDecoyMap currentMap = psmPTMMap.getTargetDecoyMap(ptmMass, mapKey);
                TargetDecoyResults currentResults = currentMap.getTargetDecoyResults();
                currentResults.setInputType(1);
                currentResults.setUserInput(psmFLR);
                currentResults.setClassicalEstimators(true);
                currentResults.setClassicalValidation(true);
                currentResults.setFdrLimit(psmFLR);
                currentMap.getTargetDecoySeries().getFDRResults(currentResults);
            }
        }
    }

    /**
     * Makes a preliminary validation of hits.
     *
     * @param waitingHandler the handler displaying feedback to the user
     * @param aPSMFDR Accepted FDR at Peptide-Spectrum-Match level (e.g. '1.0'
     * for 1% FDR)
     * @param aPeptideFDR Accepted FDR at Peptide level (e.g. '1.0' for 1% FDR)
     * @param aProteinFDR Accepted FDR at Protein level (e.g. '1.0' for 1% FDR)
     * @param searchParameters the identification parameters used for this
     * project
     * @param annotationPreferences the spectrum annotation preferences
     * @param identificationFeaturesGenerator the identification features
     * generator providing information about the matches
     */
    public void fdrValidation(WaitingHandler waitingHandler, double aPSMFDR, double aPeptideFDR, double aProteinFDR, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, IdentificationFeaturesGenerator identificationFeaturesGenerator) {

        waitingHandler.setWaitingText("Validating Identifications. Please Wait...");

        TargetDecoyMap currentMap = proteinMap.getTargetDecoyMap();
        TargetDecoyResults currentResults = currentMap.getTargetDecoyResults();
        currentResults.setInputType(1);
        currentResults.setUserInput(aProteinFDR);
        currentResults.setClassicalEstimators(true);
        currentResults.setClassicalValidation(true);
        currentResults.setFdrLimit(aProteinFDR);
        currentMap.getTargetDecoySeries().getFDRResults(currentResults);

        int max = peptideMap.getKeys().size() + psmMap.getKeys().keySet().size();
        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(max);

        for (String mapKey : peptideMap.getKeys()) {
            if (waitingHandler.isRunCanceled()) {
                return;
            }
            waitingHandler.increaseSecondaryProgressCounter();
            currentMap = peptideMap.getTargetDecoyMap(mapKey);
            currentResults = currentMap.getTargetDecoyResults();
            currentResults.setInputType(1);
            currentResults.setUserInput(aPeptideFDR);
            currentResults.setClassicalEstimators(true);
            currentResults.setClassicalValidation(true);
            currentResults.setFdrLimit(aPeptideFDR);
            currentMap.getTargetDecoySeries().getFDRResults(currentResults);
        }

        for (int mapKey : psmMap.getKeys().keySet()) {
            if (waitingHandler.isRunCanceled()) {
                return;
            }
            waitingHandler.increaseSecondaryProgressCounter();
            currentMap = psmMap.getTargetDecoyMap(mapKey);
            currentResults = currentMap.getTargetDecoyResults();
            currentResults.setInputType(1);
            currentResults.setUserInput(aPSMFDR);
            currentResults.setClassicalEstimators(true);
            currentResults.setClassicalValidation(true);
            currentResults.setFdrLimit(aPSMFDR);
            currentMap.getTargetDecoySeries().getFDRResults(currentResults);
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);

        try {
            validateIdentifications(waitingHandler, identificationFeaturesGenerator, searchParameters, annotationPreferences);
        } catch (Exception e) {
            waitingHandler.appendReport("An error occurred while validating the results.", true, true);
            waitingHandler.setRunCanceled();
            e.printStackTrace();
        }
        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Processes the identifications if a change occured in the psm map.
     *
     * @param waitingHandler the waiting handler
     * @param processingPreferences the processing preferences
     * @param searchParameters the parameters used for the search
     *
     * @throws Exception Exception thrown whenever it is attempted to attach
     * more than one identification per search engine per spectrum
     */
    public void spectrumMapChanged(WaitingHandler waitingHandler, ProcessingPreferences processingPreferences, SearchParameters searchParameters) throws Exception {
        peptideMap = new PeptideSpecificMap();
        proteinMap = new ProteinMap();
        attachSpectrumProbabilitiesAndBuildPeptidesAndProteins(waitingHandler, searchParameters);
        fillPeptideMaps(waitingHandler, searchParameters);
        peptideMap.cure();
        peptideMap.estimateProbabilities(waitingHandler);
        attachPeptideProbabilities(waitingHandler);
        fillProteinMap(waitingHandler);
        proteinMap.estimateProbabilities(waitingHandler);
        attachProteinProbabilities(waitingHandler, processingPreferences);
        retainBestScoringGroups(searchParameters, waitingHandler);
    }

    /**
     * Processes the identifications if a change occurred in the peptide map.
     *
     * @param waitingHandler the waiting handler
     * @param processingPreferences the processing preferences
     * @param searchParameters the parameters used for the search
     *
     * @throws Exception Exception thrown whenever it is attempted to attach
     * more than one identification per search engine per spectrum
     */
    public void peptideMapChanged(WaitingHandler waitingHandler, ProcessingPreferences processingPreferences, SearchParameters searchParameters) throws Exception {
        proteinMap = new ProteinMap();
        attachPeptideProbabilities(waitingHandler);
        fillProteinMap(waitingHandler);
        proteinMap.estimateProbabilities(waitingHandler);
        attachProteinProbabilities(waitingHandler, processingPreferences);
        retainBestScoringGroups(searchParameters, waitingHandler);
    }

    /**
     * Processes the identifications if a change occured in the protein map.
     *
     * @param waitingHandler the waiting handler
     * @param processingPreferences
     * @throws SQLException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     */
    public void proteinMapChanged(WaitingHandler waitingHandler, ProcessingPreferences processingPreferences) throws SQLException, IOException, ClassNotFoundException, InterruptedException {
        attachProteinProbabilities(waitingHandler, processingPreferences);
    }

    /**
     * This method will flag validated identifications.
     *
     * @param waitingHandler the progress bar
     * @param identificationFeaturesGenerator an identification features
     * generator computing information about the identification matches
     * @param searchParameters the search parameters used for the search
     * @param annotationPreferences the spectrum annotation preferences
     *
     * @throws SQLException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws MzMLUnmarshallerException
     * @throws InterruptedException
     */
    public void validateIdentifications(WaitingHandler waitingHandler, IdentificationFeaturesGenerator identificationFeaturesGenerator, SearchParameters searchParameters, AnnotationPreferences annotationPreferences) throws SQLException, IOException, ClassNotFoundException, MzMLUnmarshallerException, InterruptedException {

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        PSParameter psParameter = new PSParameter();
        PSParameter psParameter2 = new PSParameter();

        if (waitingHandler != null) {
            waitingHandler.setSecondaryProgressCounterIndeterminate(false);
            waitingHandler.setMaxSecondaryProgressCounter(identification.getProteinIdentification().size()
                    + identification.getPeptideIdentification().size()
                    + 2 * identification.getSpectrumIdentificationSize());
        }

        // validate the spectra
        for (String spectrumFileName : identification.getSpectrumFiles()) {
            identification.loadSpectrumMatches(spectrumFileName, null);
            identification.loadSpectrumMatchParameters(spectrumFileName, new PSParameter(), null);
            ArrayList<Double> precursorMzDeviations = new ArrayList<Double>();
            for (String spectrumKey : identification.getSpectrumIdentification(spectrumFileName)) {

                updateSpectrumMatchValidationLevel(identification, identificationFeaturesGenerator, searchParameters, annotationPreferences, psmMap, spectrumKey);

                psParameter = (PSParameter) identification.getSpectrumMatchParameter(spectrumKey, psParameter);
                if (psParameter.getMatchValidationLevel().isValidated()) {
                    SpectrumMatch spectrumMatch = identification.getSpectrumMatch(spectrumKey);
                    Precursor precursor = spectrumFactory.getPrecursor(spectrumKey);
                    double precursorMzError = spectrumMatch.getBestPeptideAssumption().getDeltaMass(precursor.getMz(), searchParameters.isPrecursorAccuracyTypePpm());
                    precursorMzDeviations.add(precursorMzError);
                }

                if (waitingHandler != null) {
                    waitingHandler.increaseSecondaryProgressCounter();
                    if (waitingHandler.isRunCanceled()) {
                        return;
                    }
                }
            }
            // Check if we should narrow the mass accuracy window, if yes, do a second pass validation
            NonSymmetricalNormalDistribution precDeviationDistribution = NonSymmetricalNormalDistribution.getRobustNonSymmetricalNormalDistribution(precursorMzDeviations);
            Double minDeviation = precDeviationDistribution.getMinValueForProbability(0.0001);
            Double maxDeviation = precDeviationDistribution.getMaxValueForProbability(0.0001);
            boolean needSecondPass = false;
            if (minDeviation < maxDeviation) {
                String unit = "ppm";
                if (!searchParameters.isPrecursorAccuracyTypePpm()) {
                    unit = "Da";
                }
                if (minDeviation != Double.NaN && minDeviation > -searchParameters.getPrecursorAccuracy()) {
                    needSecondPass = true;
                    PsmFilter psmFilter = new PsmFilter("Precursor m/z deviation > " + Util.roundDouble(minDeviation, 2) + " " + unit);
                    psmFilter.setDescription("Precursor m/z deviation < " + Util.roundDouble(minDeviation, 2) + " " + unit);
                    psmFilter.setMinPrecursorMzError(minDeviation);
                    psmFilter.setPrecursorMinMzErrorComparison(RowFilter.ComparisonType.AFTER);
                    psmMap.addDoubtfulMatchesFilter(psmFilter);
                }
                if (minDeviation != Double.NaN && maxDeviation < searchParameters.getPrecursorAccuracy()) {
                    needSecondPass = true;
                    PsmFilter psmFilter = new PsmFilter("Precursor m/z deviation < " + Util.roundDouble(maxDeviation, 2) + " " + unit);
                    psmFilter.setDescription("Precursor m/z deviation > " + Util.roundDouble(maxDeviation, 2) + " " + unit);
                    psmFilter.setMaxPrecursorMzError(maxDeviation);
                    psmFilter.setPrecursorMaxMzErrorComparison(RowFilter.ComparisonType.BEFORE);
                    psmMap.addDoubtfulMatchesFilter(psmFilter);
                }
            }
            // @TODO: debug
//            if (needSecondPass) {
//                for (String spectrumKey : identification.getSpectrumIdentification(spectrumFileName)) {
//
//                    updateSpectrumMatchValidationLevel(identification, identificationFeaturesGenerator, searchParameters, annotationPreferences, psmMap, spectrumKey);
//
//                    if (waitingHandler != null) {
//                        waitingHandler.increaseSecondaryProgressCounter();
//                        if (waitingHandler.isRunCanceled()) {
//                            return;
//                        }
//                    }
//                }
//            } else if (waitingHandler != null) {
//                waitingHandler.increaseSecondaryProgressCounter(identification.getSpectrumIdentification(spectrumFileName).size());
//            }
        }

        HashMap<String, Integer> validatedTotalPeptidesPerFraction = new HashMap<String, Integer>();

        identification.loadPeptideMatches(null);
        identification.loadPeptideMatchParameters(new PSParameter(), null);
        // validate the peptides
        for (String peptideKey : identification.getPeptideIdentification()) {

            updatePeptideMatchValidationLevel(identification, identificationFeaturesGenerator, searchParameters, peptideMap, peptideKey);

            // set the fraction details
            psParameter = (PSParameter) identification.getPeptideMatchParameter(peptideKey, psParameter);
            // @TODO: could be a better more elegant way of doing this?
            HashMap<String, Integer> validatedPsmsPerFraction = new HashMap<String, Integer>();
            HashMap<String, ArrayList<Double>> precursorIntensitesPerFractionPeptideLevel = new HashMap<String, ArrayList<Double>>();

            for (String fraction : psParameter.getFractions()) {

                ArrayList<Double> precursorIntensities = new ArrayList<Double>();

                if (metrics.getFractionPsmMatches().get(fraction + "_" + peptideKey) != null) {
                    ArrayList<String> spectrumKeys = metrics.getFractionPsmMatches().get(fraction + "_" + peptideKey);

                    for (int k = 0; k < spectrumKeys.size(); k++) {

                        psParameter2 = (PSParameter) identification.getSpectrumMatchParameter(spectrumKeys.get(k), psParameter2);

                        if (psParameter2.getMatchValidationLevel().isValidated()) {
                            if (validatedPsmsPerFraction.containsKey(fraction)) {
                                Integer value = validatedPsmsPerFraction.get(fraction);
                                validatedPsmsPerFraction.put(fraction, value + 1);
                            } else {
                                validatedPsmsPerFraction.put(fraction, 1);
                            }

                            if (SpectrumFactory.getInstance().getPrecursor(spectrumKeys.get(k)).getIntensity() > 0) {
                                precursorIntensities.add(SpectrumFactory.getInstance().getPrecursor(spectrumKeys.get(k)).getIntensity());
                            }
                        }

                        if (waitingHandler != null) {
                            if (waitingHandler.isRunCanceled()) {
                                return;
                            }
                        }
                    }
                }

                precursorIntensitesPerFractionPeptideLevel.put(fraction, precursorIntensities);

                // save the total number of peptides per fraction
                if (psParameter.getMatchValidationLevel().isValidated()) {
                    if (validatedTotalPeptidesPerFraction.containsKey(fraction)) {
                        Integer value = validatedTotalPeptidesPerFraction.get(fraction);
                        validatedTotalPeptidesPerFraction.put(fraction, value + 1);
                    } else {
                        validatedTotalPeptidesPerFraction.put(fraction, 1);
                    }
                }
            }

            // set the number of validated spectra per fraction for each peptide
            psParameter.setFractionValidatedSpectra(validatedPsmsPerFraction);
            psParameter.setPrecursorIntensityPerFraction(precursorIntensitesPerFractionPeptideLevel);

            identification.updatePeptideMatchParameter(peptideKey, psParameter);
            if (waitingHandler != null) {
                waitingHandler.increaseSecondaryProgressCounter();
                if (waitingHandler.isRunCanceled()) {
                    return;
                }
            }
        }

        // validate the proteins
        TargetDecoyMap targetDecoyMap = proteinMap.getTargetDecoyMap();
        TargetDecoyResults targetDecoyResults = targetDecoyMap.getTargetDecoyResults();
        double proteinThreshold = targetDecoyResults.getScoreLimit();
        double proteinConfidentThreshold = targetDecoyResults.getConfidenceLimit() + targetDecoyMap.getResolution();
        if (proteinConfidentThreshold > 100) {
            proteinConfidentThreshold = 100;
        }
        boolean noValidated = proteinMap.getTargetDecoyMap().getTargetDecoyResults().noValidated();

        int maxValidatedSpectraFractionLevel = 0;
        int maxValidatedPeptidesFractionLevel = 0;
        double maxProteinAveragePrecursorIntensity = 0;
        double maxProteinSummedPrecursorIntensity = 0;

        identification.loadProteinMatches(null);
        identification.loadProteinMatchParameters(new PSParameter(), null);
        for (String proteinKey : identification.getProteinIdentification()) {

            updateProteinMatchValidationLevel(identification, identificationFeaturesGenerator, searchParameters, annotationPreferences, targetDecoyMap, proteinThreshold, proteinConfidentThreshold, noValidated, proteinMap.getDoubtfulMatchesFilters(), proteinKey);

            // set the fraction details
            psParameter = (PSParameter) identification.getProteinMatchParameter(proteinKey, psParameter);
            // @TODO: could be a better more elegant way of doing this?
            HashMap<String, Integer> validatedPsmsPerFraction = new HashMap<String, Integer>();
            HashMap<String, Integer> validatedPeptidesPerFraction = new HashMap<String, Integer>();
            HashMap<String, ArrayList<Double>> precursorIntensitesPerFractionProteinLevel = new HashMap<String, ArrayList<Double>>();

            ArrayList<String> peptideKeys = identification.getProteinMatch(proteinKey).getPeptideMatches();

            identification.loadPeptideMatchParameters(peptideKeys, psParameter, null);
            for (String currentPeptideKey : peptideKeys) {

                psParameter2 = (PSParameter) identification.getPeptideMatchParameter(currentPeptideKey, psParameter2);

                for (String fraction : psParameter2.getFractions()) {

                    if (psParameter2.getFractionValidatedSpectra(fraction) != null) {
                        if (validatedPsmsPerFraction.containsKey(fraction)) {
                            Integer value = validatedPsmsPerFraction.get(fraction);
                            validatedPsmsPerFraction.put(fraction, value + psParameter2.getFractionValidatedSpectra(fraction));
                        } else {
                            validatedPsmsPerFraction.put(fraction, psParameter2.getFractionValidatedSpectra(fraction));
                        }

                        if (validatedPsmsPerFraction.get(fraction) > maxValidatedSpectraFractionLevel) {
                            maxValidatedSpectraFractionLevel = validatedPsmsPerFraction.get(fraction);
                        }
                    }

                    if (psParameter2.getPrecursorIntensityPerFraction(fraction) != null) {
                        if (precursorIntensitesPerFractionProteinLevel.containsKey(fraction)) {
                            for (int i = 0; i < psParameter2.getPrecursorIntensityPerFraction(fraction).size(); i++) {
                                precursorIntensitesPerFractionProteinLevel.get(fraction).add(psParameter2.getPrecursorIntensityPerFraction(fraction).get(i));
                            }
                        } else {
                            precursorIntensitesPerFractionProteinLevel.put(fraction, psParameter2.getPrecursorIntensityPerFraction(fraction));
                        }
                    }

                    if (psParameter2.getMatchValidationLevel().isValidated()) {
                        if (validatedPeptidesPerFraction.containsKey(fraction)) {
                            Integer value = validatedPeptidesPerFraction.get(fraction);
                            validatedPeptidesPerFraction.put(fraction, value + 1);
                        } else {
                            validatedPeptidesPerFraction.put(fraction, 1);
                        }

                        if (validatedPeptidesPerFraction.get(fraction) > maxValidatedPeptidesFractionLevel) {
                            maxValidatedPeptidesFractionLevel = validatedPeptidesPerFraction.get(fraction);
                        }
                    }
                }

                if (waitingHandler != null) {
                    if (waitingHandler.isRunCanceled()) {
                        return;
                    }
                }
            }

            // set the number of validated spectra per fraction for each peptide
            psParameter.setFractionValidatedSpectra(validatedPsmsPerFraction);
            psParameter.setFractionValidatedPeptides(validatedPeptidesPerFraction);
            psParameter.setPrecursorIntensityPerFraction(precursorIntensitesPerFractionProteinLevel);

            for (String fraction : psParameter.getFractions()) {
                if (psParameter.getPrecursorIntensityAveragePerFraction(fraction) != null) {
                    if (psParameter.getPrecursorIntensityAveragePerFraction(fraction) > maxProteinAveragePrecursorIntensity) {
                        maxProteinAveragePrecursorIntensity = psParameter.getPrecursorIntensityAveragePerFraction(fraction);
                    }
                    if (psParameter.getPrecursorIntensityAveragePerFraction(fraction) > maxProteinSummedPrecursorIntensity) {
                        maxProteinAveragePrecursorIntensity = psParameter.getPrecursorIntensitySummedPerFraction(fraction);
                    }
                }
            }

            identification.updateProteinMatchParameter(proteinKey, psParameter);

            if (waitingHandler != null) {
                waitingHandler.increaseSecondaryProgressCounter();
            }
        }

        // set the max values in the metrics
        metrics.setMaxValidatedPeptidesPerFraction(maxValidatedPeptidesFractionLevel);
        metrics.setMaxValidatedSpectraPerFraction(maxValidatedSpectraFractionLevel);
        metrics.setMaxProteinAveragePrecursorIntensity(maxProteinAveragePrecursorIntensity);
        metrics.setMaxProteinSummedPrecursorIntensity(maxProteinSummedPrecursorIntensity);
        metrics.setTotalPeptidesPerFraction(validatedTotalPeptidesPerFraction);
    }

    /**
     * Updates the validation status of a protein match. If the match was
     * manually validated nothing will be changed.
     *
     * @param identification the identification object
     * @param proteinMap the protein level target/decoy scoring map
     * @param identificationFeaturesGenerator the identification features
     * generator
     * @param searchParameters the settings used for the identification
     * @param proteinKey the key of the protein match of interest
     * @param annotationPreferences the spectrum annotation preferences
     *
     * @throws SQLException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     * @throws MzMLUnmarshallerException
     */
    public static void updateProteinMatchValidationLevel(Identification identification, IdentificationFeaturesGenerator identificationFeaturesGenerator, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, ProteinMap proteinMap,
            String proteinKey) throws SQLException, IOException, ClassNotFoundException, InterruptedException, MzMLUnmarshallerException {
        TargetDecoyMap targetDecoyMap = proteinMap.getTargetDecoyMap();
        TargetDecoyResults targetDecoyResults = targetDecoyMap.getTargetDecoyResults();
        double proteinThreshold = targetDecoyResults.getScoreLimit();
        double proteinConfidentThreshold = targetDecoyResults.getConfidenceLimit() + targetDecoyMap.getResolution();
        if (proteinConfidentThreshold > 100) {
            proteinConfidentThreshold = 100;
        }
        boolean noValidated = proteinMap.getTargetDecoyMap().getTargetDecoyResults().noValidated();
        updateProteinMatchValidationLevel(identification, identificationFeaturesGenerator, searchParameters, annotationPreferences, targetDecoyMap, proteinThreshold, proteinConfidentThreshold, noValidated, proteinMap.getDoubtfulMatchesFilters(), proteinKey);
    }

    /**
     * Updates the validation status of a protein match. If the match was
     * manually validated nothing will be changed.
     *
     * @param identification the identification object
     * @param targetDecoyMap the protein level target/decoy map
     * @param scoreThreshold the validation score threshold
     * @param confidenceThreshold the confidence threshold after which a match
     * should be considered as confident
     * @param noValidated boolean indicating whether no validation was actually
     * conducted
     * @param doubtfulMatchFilters the filters to use for quality filtering
     * @param identificationFeaturesGenerator the identification features
     * generator
     * @param searchParameters the settings used for the identification
     * @param proteinKey the key of the protein match of interest
     * @param annotationPreferences the spectrum annotation preferences
     *
     * @throws SQLException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     * @throws MzMLUnmarshallerException
     */
    public static void updateProteinMatchValidationLevel(Identification identification, IdentificationFeaturesGenerator identificationFeaturesGenerator, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, TargetDecoyMap targetDecoyMap, double scoreThreshold, double confidenceThreshold, boolean noValidated, ArrayList<ProteinFilter> doubtfulMatchFilters,
            String proteinKey) throws SQLException, IOException, ClassNotFoundException, InterruptedException, MzMLUnmarshallerException {

        SequenceFactory sequenceFactory = SequenceFactory.getInstance();
        PSParameter psParameter = new PSParameter();
        psParameter = (PSParameter) identification.getProteinMatchParameter(proteinKey, psParameter);

        if (!psParameter.isManualValidation()) {

            if (sequenceFactory.concatenatedTargetDecoy()) {

                if (!noValidated && psParameter.getProteinProbabilityScore() <= scoreThreshold) {
                    String reasonDoubtful = null;
                    boolean filterPassed = true;
                    for (ProteinFilter filter : doubtfulMatchFilters) {
                        if (!filter.isValidated(proteinKey, identification, identificationFeaturesGenerator, searchParameters, annotationPreferences)) {
                            filterPassed = false;
                            if (reasonDoubtful == null) {
                                reasonDoubtful = "";
                            } else {
                                reasonDoubtful += ", ";
                            }
                            reasonDoubtful += filter.getDescription();
                        }
                    }
                    boolean confidenceThresholdPassed = psParameter.getProteinConfidence() >= confidenceThreshold; //@TODO: not sure whether we should include all 100% confidence hits by default?
                    if (!confidenceThresholdPassed) {
                        if (reasonDoubtful == null) {
                            reasonDoubtful = "";
                        } else {
                            reasonDoubtful += ", ";
                        }
                        reasonDoubtful += "Low confidence";
                    }
                    boolean enoughHits = targetDecoyMap.getnTargetOnly() > 100 && targetDecoyMap.getnTargetOnly() > targetDecoyMap.getnMax();
                    if (!enoughHits) {
                        if (reasonDoubtful == null) {
                            reasonDoubtful = "";
                        } else {
                            reasonDoubtful += ", ";
                        }
                        reasonDoubtful += "Low number of hits";
                    }
                    if (!sequenceFactory.hasEnoughSequences()) {
                        if (reasonDoubtful == null) {
                            reasonDoubtful = "";
                        } else {
                            reasonDoubtful += ", ";
                        }
                        reasonDoubtful += "Database too small";
                    }
                    if (filterPassed && confidenceThresholdPassed && enoughHits && sequenceFactory.hasEnoughSequences()) {
                        psParameter.setMatchValidationLevel(MatchValidationLevel.confident);
                    } else {
                        psParameter.setMatchValidationLevel(MatchValidationLevel.doubtful);
                        if (reasonDoubtful != null) {
                            psParameter.setReasonDoubtful(reasonDoubtful);
                        }
                    }
                } else {
                    psParameter.setMatchValidationLevel(MatchValidationLevel.not_validated);
                }
            } else {
                psParameter.setMatchValidationLevel(MatchValidationLevel.none);
            }

            identification.updateProteinMatchParameter(proteinKey, psParameter);
        }
    }

    /**
     * Updates the validation status of a peptide match. If the match was
     * manually validated nothing will be changed.
     *
     * @param identification the identification object
     * @param peptideMap the peptide level target/decoy scoring map
     * @param identificationFeaturesGenerator the identification features
     * generator
     * @param searchParameters the settings used for the identification
     * @param peptideKey the key of the peptide match of interest
     *
     * @throws SQLException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     * @throws MzMLUnmarshallerException
     */
    public static void updatePeptideMatchValidationLevel(Identification identification, IdentificationFeaturesGenerator identificationFeaturesGenerator, SearchParameters searchParameters, PeptideSpecificMap peptideMap,
            String peptideKey) throws SQLException, IOException, ClassNotFoundException, InterruptedException, MzMLUnmarshallerException {

        SequenceFactory sequenceFactory = SequenceFactory.getInstance();
        PSParameter psParameter = new PSParameter();
        psParameter = (PSParameter) identification.getPeptideMatchParameter(peptideKey, psParameter);
        if (sequenceFactory.concatenatedTargetDecoy()) {
            TargetDecoyMap targetDecoyMap = peptideMap.getTargetDecoyMap(peptideMap.getCorrectedKey(psParameter.getSpecificMapKey()));
            TargetDecoyResults targetDecoyResults = targetDecoyMap.getTargetDecoyResults();
            double peptideThreshold = targetDecoyResults.getScoreLimit();
            double confidenceThreshold = targetDecoyResults.getConfidenceLimit() + targetDecoyMap.getResolution();
            if (confidenceThreshold > 100) {
                confidenceThreshold = 100;
            }
            boolean noValidated = peptideMap.getTargetDecoyMap(peptideMap.getCorrectedKey(psParameter.getSpecificMapKey())).getTargetDecoyResults().noValidated();
            if (!noValidated && psParameter.getPeptideProbabilityScore() <= peptideThreshold) {
                String reasonDoubtful = null;
                boolean filterPassed = true;
                for (PeptideFilter filter : peptideMap.getDoubtfulMatchesFilters()) {
                    if (!filter.isValidated(peptideKey, identification, identificationFeaturesGenerator)) {
                        filterPassed = false;
                        if (reasonDoubtful == null) {
                            reasonDoubtful = "";
                        } else {
                            reasonDoubtful += ", ";
                        }
                        reasonDoubtful += filter.getDescription();
                    }
                }
                boolean confidenceThresholdPassed = psParameter.getPeptideConfidence() >= confidenceThreshold; //@TODO: not sure whether we should include all 100% confidence hits by default?
                if (!confidenceThresholdPassed) {
                    if (reasonDoubtful == null) {
                        reasonDoubtful = "";
                    } else {
                        reasonDoubtful += ", ";
                    }
                    reasonDoubtful += "Low confidence";
                }
                boolean enoughHits = targetDecoyMap.getnTargetOnly() > 100 && targetDecoyMap.getnTargetOnly() > targetDecoyMap.getnMax();
                if (!enoughHits) {
                    if (reasonDoubtful == null) {
                        reasonDoubtful = "";
                    } else {
                        reasonDoubtful += ", ";
                    }
                    reasonDoubtful += "Low number of hits";
                }
                if (!sequenceFactory.hasEnoughSequences()) {
                    if (reasonDoubtful == null) {
                        reasonDoubtful = "";
                    } else {
                        reasonDoubtful += ", ";
                    }
                    reasonDoubtful += "Database too small";
                }
                if (filterPassed && confidenceThresholdPassed && enoughHits && sequenceFactory.hasEnoughSequences()) {
                    psParameter.setMatchValidationLevel(MatchValidationLevel.confident);
                } else {
                    psParameter.setMatchValidationLevel(MatchValidationLevel.doubtful);
                    if (reasonDoubtful != null) {
                        psParameter.setReasonDoubtful(reasonDoubtful);
                    }
                }

            } else {
                psParameter.setMatchValidationLevel(MatchValidationLevel.not_validated);
            }
        } else {
            psParameter.setMatchValidationLevel(MatchValidationLevel.none);
        }
        identification.updatePeptideMatchParameter(peptideKey, psParameter);
    }

    /**
     * Updates the validation status of a spectrum match. If the match was
     * manually validated nothing will be changed.
     *
     * @param identification the identification object
     * @param psmMap the PSM level target/decoy scoring map
     * @param identificationFeaturesGenerator the identification features
     * generator
     * @param searchParameters the settings used for the identification
     * @param spectrumKey the key of the spectrum match of interest
     * @param annotationPreferences the spectrum annotation preferences
     *
     * @throws SQLException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     * @throws MzMLUnmarshallerException
     */
    public static void updateSpectrumMatchValidationLevel(Identification identification, IdentificationFeaturesGenerator identificationFeaturesGenerator, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, PsmSpecificMap psmMap,
            String spectrumKey) throws SQLException, IOException, ClassNotFoundException, InterruptedException, MzMLUnmarshallerException {

        SequenceFactory sequenceFactory = SequenceFactory.getInstance();
        PSParameter psParameter = new PSParameter();
        psParameter = (PSParameter) identification.getSpectrumMatchParameter(spectrumKey, psParameter);
        if (sequenceFactory.concatenatedTargetDecoy()) {
            TargetDecoyMap targetDecoyMap = psmMap.getTargetDecoyMap(psmMap.getCorrectedKey(psParameter.getSpecificMapKey()));
            TargetDecoyResults targetDecoyResults = targetDecoyMap.getTargetDecoyResults();
            double psmThreshold = targetDecoyResults.getScoreLimit();
            double confidenceThreshold = targetDecoyResults.getConfidenceLimit() + targetDecoyMap.getResolution();
            if (confidenceThreshold > 100) {
                confidenceThreshold = 100;
            }
            boolean noValidated = targetDecoyResults.noValidated();
            if (!noValidated && psParameter.getPsmProbabilityScore() <= psmThreshold) {
                String reasonDoubtful = null;
                boolean filterPassed = true;
                for (PsmFilter filter : psmMap.getDoubtfulMatchesFilters()) {
                    if (!filter.isValidated(spectrumKey, identification, searchParameters, annotationPreferences)) {
                        filterPassed = false;
                        if (reasonDoubtful == null) {
                            reasonDoubtful = "";
                        } else {
                            reasonDoubtful += ", ";
                        }
                        reasonDoubtful += filter.getDescription();
                    }
                }
                boolean confidenceThresholdPassed = psParameter.getPsmConfidence() >= confidenceThreshold; //@TODO: not sure whether we should include all 100% confidence hits by default?
                if (!confidenceThresholdPassed) {
                    if (reasonDoubtful == null) {
                        reasonDoubtful = "";
                    } else {
                        reasonDoubtful += ", ";
                    }
                    reasonDoubtful += "Low confidence";
                }
                boolean enoughHits = targetDecoyMap.getnTargetOnly() > 100 && targetDecoyMap.getnTargetOnly() > targetDecoyMap.getnMax();
                if (!enoughHits) {
                    if (reasonDoubtful == null) {
                        reasonDoubtful = "";
                    } else {
                        reasonDoubtful += ", ";
                    }
                    reasonDoubtful += "Low number of hits";
                }
                if (!sequenceFactory.hasEnoughSequences()) {
                    if (reasonDoubtful == null) {
                        reasonDoubtful = "";
                    } else {
                        reasonDoubtful += ", ";
                    }
                    reasonDoubtful += "Database too small";
                }
                if (filterPassed && confidenceThresholdPassed && enoughHits && sequenceFactory.hasEnoughSequences()) {
                    psParameter.setMatchValidationLevel(MatchValidationLevel.confident);
                } else {
                    psParameter.setMatchValidationLevel(MatchValidationLevel.doubtful);
                    if (reasonDoubtful != null) {
                        psParameter.setReasonDoubtful(reasonDoubtful);
                    }
                }
            } else {
                psParameter.setMatchValidationLevel(MatchValidationLevel.not_validated);
            }
        } else {
            psParameter.setMatchValidationLevel(MatchValidationLevel.none);
        }
        identification.updateSpectrumMatchParameter(spectrumKey, psParameter);
    }

    /**
     * Fills the PSM specific map.
     *
     * @param inputMap The input map
     * @param waitingHandler the handler displaying feedback to the user
     * @param searchParameters the search parameters
     * @param annotationPreferences the annotation preferences
     * @throws Exception
     */
    private void fillPsmMap(InputMap inputMap, WaitingHandler waitingHandler, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, IdFilter idFilter) throws Exception {

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(identification.getSpectrumIdentificationSize());

        PeptideSpectrumAnnotator spectrumAnnotator = new PeptideSpectrumAnnotator();

        // map of the first hits for this spectrum: score -> max protein count -> max search engine votes -> sequence coverage annotated -> min mass deviation (unless you have a better idea?)
        HashMap<Double, HashMap<Integer, HashMap<Integer, HashMap<Double, HashMap<Double, ArrayList<PeptideAssumption>>>>>> peptideAssumptions;
        PSParameter psParameter, psParameter2;
        boolean multiSE = inputMap.isMultipleSearchEngines();

        for (String spectrumFileName : identification.getSpectrumFiles()) {
            identification.loadSpectrumMatches(spectrumFileName, null);
            for (String spectrumKey : identification.getSpectrumIdentification(spectrumFileName)) {
                psParameter = new PSParameter();
                ArrayList<String> identifications = new ArrayList<String>();
                peptideAssumptions = new HashMap<Double, HashMap<Integer, HashMap<Integer, HashMap<Double, HashMap<Double, ArrayList<PeptideAssumption>>>>>>();
                SpectrumMatch spectrumMatch = identification.getSpectrumMatch(spectrumKey);

                for (int searchEngine1 : spectrumMatch.getAdvocates()) {
                    double bestEvalue = Collections.min(spectrumMatch.getAllAssumptions(searchEngine1).keySet());
                    for (SpectrumIdentificationAssumption assumption1 : spectrumMatch.getAllAssumptions(searchEngine1).get(bestEvalue)) {
                        PeptideAssumption peptideAssumption1 = (PeptideAssumption) assumption1;
                        String id = peptideAssumption1.getPeptide().getKey();
                        double p;

                        if (!identifications.contains(id)) {
                            psParameter = (PSParameter) peptideAssumption1.getUrParam(psParameter);
                            if (multiSE && sequenceFactory.concatenatedTargetDecoy()) {
                                p = psParameter.getSearchEngineProbability();
                            } else {
                                p = peptideAssumption1.getScore();
                            }
                            int nSE = 1;
                            int proteinMax = 1;
                            for (String protein : peptideAssumption1.getPeptide().getParentProteins(MATCHING_TYPE, searchParameters.getFragmentIonAccuracy())) {
                                Integer tempCount = proteinCount.get(protein);
                                if (tempCount != null && tempCount > proteinMax) {
                                    proteinMax = tempCount;
                                }
                            }
                            for (int searchEngine2 : spectrumMatch.getAdvocates()) {
                                if (searchEngine1 != searchEngine2) {
                                    boolean found = false;
                                    ArrayList<Double> eValues2 = new ArrayList<Double>(spectrumMatch.getAllAssumptions(searchEngine2).keySet());
                                    Collections.sort(eValues2);
                                    for (double eValue2 : eValues2) {
                                        for (SpectrumIdentificationAssumption assumption2 : spectrumMatch.getAllAssumptions(searchEngine2).get(eValue2)) {
                                            PeptideAssumption peptideAssumption2 = (PeptideAssumption) assumption2;
                                            if (peptideAssumption1.getPeptide().isSameSequenceAndModificationStatus(peptideAssumption2.getPeptide(), MATCHING_TYPE, searchParameters.getFragmentIonAccuracy())) {
                                                psParameter2 = (PSParameter) peptideAssumption2.getUrParam(psParameter);
                                                p = p * psParameter2.getSearchEngineProbability();
                                                nSE++;
                                                found = true;
                                                break;
                                            }
                                        }
                                        if (found) {
                                            break;
                                        }
                                    }
                                }
                            }
                            identifications.add(id);
                            if (!peptideAssumptions.containsKey(p)) {
                                peptideAssumptions.put(p, new HashMap<Integer, HashMap<Integer, HashMap<Double, HashMap<Double, ArrayList<PeptideAssumption>>>>>());
                            }
                            if (!peptideAssumptions.get(p).containsKey(proteinMax)) {
                                peptideAssumptions.get(p).put(proteinMax, new HashMap<Integer, HashMap<Double, HashMap<Double, ArrayList<PeptideAssumption>>>>());
                            }
                            if (!peptideAssumptions.get(p).get(proteinMax).containsKey(nSE)) {
                                ArrayList<PeptideAssumption> assumptions = new ArrayList<PeptideAssumption>();
                                assumptions.add(peptideAssumption1);
                                peptideAssumptions.get(p).get(proteinMax).put(nSE, new HashMap<Double, HashMap<Double, ArrayList<PeptideAssumption>>>());
                                peptideAssumptions.get(p).get(proteinMax).get(nSE).put(-1.0, new HashMap<Double, ArrayList<PeptideAssumption>>());
                                peptideAssumptions.get(p).get(proteinMax).get(nSE).get(-1.0).put(-1.0, assumptions);
                            } else {
                                HashMap<Ion.IonType, ArrayList<Integer>> iontypes = annotationPreferences.getIonTypes();
                                NeutralLossesMap neutralLosses = annotationPreferences.getNeutralLosses();
                                ArrayList<Integer> charges = annotationPreferences.getValidatedCharges();
                                MSnSpectrum spectrum = (MSnSpectrum) spectrumFactory.getSpectrum(spectrumKey);
                                double mzTolerance = searchParameters.getFragmentIonAccuracy();
                                boolean isPpm = false; //@TODO change this as soon as search engine support fragment ion tolerance in ppm
                                if (peptideAssumptions.get(p).get(proteinMax).get(nSE).containsKey(-1.0)) {
                                    ArrayList<PeptideAssumption> assumptions = peptideAssumptions.get(p).get(proteinMax).get(nSE).get(-1.0).get(-1.0);
                                    PeptideAssumption tempAssumption = assumptions.get(0);
                                    Peptide peptide = tempAssumption.getPeptide();
                                    int precursorCharge = tempAssumption.getIdentificationCharge().value;
                                    double nIons = spectrumAnnotator.getCoveredAminoAcids(iontypes, neutralLosses, charges, precursorCharge, spectrum, peptide, 0, mzTolerance, isPpm).keySet().size();
                                    double coverage = nIons / peptide.getSequence().length();
                                    peptideAssumptions.get(p).get(proteinMax).get(nSE).put(coverage, new HashMap<Double, ArrayList<PeptideAssumption>>());
                                    peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).put(-1.0, assumptions);
                                    peptideAssumptions.get(p).get(proteinMax).get(nSE).remove(-1.0);
                                }
                                Peptide peptide = peptideAssumption1.getPeptide();
                                int precursorCharge = peptideAssumption1.getIdentificationCharge().value;
                                double nIons = spectrumAnnotator.getCoveredAminoAcids(iontypes, neutralLosses, charges, precursorCharge, spectrum, peptide, 0, mzTolerance, isPpm).keySet().size();
                                double coverage = nIons / peptide.getSequence().length();
                                if (!peptideAssumptions.get(p).get(proteinMax).get(nSE).containsKey(coverage)) {
                                    ArrayList<PeptideAssumption> assumptions = new ArrayList<PeptideAssumption>();
                                    assumptions.add(peptideAssumption1);
                                    peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).put(-1.0, assumptions);
                                } else {
                                    if (peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).containsKey(-1.0)) {
                                        ArrayList<PeptideAssumption> assumptions = peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).get(-1.0);
                                        PeptideAssumption tempAssumption = assumptions.get(0);
                                        double massError = tempAssumption.getDeltaMass(spectrum.getPrecursor().getMz(), searchParameters.isPrecursorAccuracyTypePpm());
                                        peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).put(massError, assumptions);
                                        peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).remove(-1.0);
                                    }
                                    double massError = peptideAssumption1.getDeltaMass(spectrum.getPrecursor().getMz(), searchParameters.isPrecursorAccuracyTypePpm());
                                    ArrayList<PeptideAssumption> assumptions = peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).get(massError);
                                    if (assumptions == null) {
                                        assumptions = new ArrayList<PeptideAssumption>();
                                        peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).put(massError, assumptions);
                                    }
                                    assumptions.add(peptideAssumption1);
                                }
                            }
                        }
                    }
                }

                PeptideAssumption bestAssumption = null;
                ArrayList<Double> ps = new ArrayList<Double>(peptideAssumptions.keySet());
                Collections.sort(ps);
                double retainedP = 0;
                for (double p : ps) {
                    retainedP = p;
                    ArrayList<Integer> proteinMaxs = new ArrayList<Integer>(peptideAssumptions.get(p).keySet());
                    Collections.sort(proteinMaxs, Collections.reverseOrder());
                    for (int proteinMax : proteinMaxs) {
                        ArrayList<Integer> nSEs = new ArrayList<Integer>(peptideAssumptions.get(p).get(proteinMax).keySet());
                        Collections.sort(nSEs, Collections.reverseOrder());
                        for (int nSE : nSEs) {
                            ArrayList<Double> coverages = new ArrayList<Double>(peptideAssumptions.get(p).get(proteinMax).get(nSE).keySet());
                            Collections.sort(coverages);
                            for (double coverage : coverages) {
                                ArrayList<Double> minErrors = new ArrayList<Double>(peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).keySet());
                                Collections.sort(minErrors);
                                for (double minError : minErrors) {
                                    for (PeptideAssumption peptideAssumption : peptideAssumptions.get(p).get(proteinMax).get(nSE).get(coverage).get(minError)) {
                                        if (idFilter.validateProteins(peptideAssumption.getPeptide(), MATCHING_TYPE, searchParameters.getFragmentIonAccuracy())) {
                                            bestAssumption = peptideAssumption;
                                            break;
                                        }
                                    }
                                    if (bestAssumption != null) {
                                        break;
                                    }
                                }
                                if (bestAssumption != null) {
                                    break;
                                }
                            }
                            if (bestAssumption != null) {
                                break;
                            }
                        }
                        if (bestAssumption != null) {
                            break;
                        }
                    }
                    if (bestAssumption != null) {
                        break;
                    }
                }
                if (bestAssumption == null) {
                    throw new IllegalArgumentException("No best assumption found for spectrum " + spectrumKey + ".");
                }

                if (multiSE) {

                    // try to find the most likely modification localization
                    HashMap<PeptideAssumption, ArrayList<Double>> assumptions = new HashMap<PeptideAssumption, ArrayList<Double>>();
                    String bestAssumptionKey = bestAssumption.getPeptide().getKey();

                    for (int searchEngine1 : spectrumMatch.getAdvocates()) {

                        boolean found = false;
                        ArrayList<Double> eValues2 = new ArrayList<Double>(spectrumMatch.getAllAssumptions(searchEngine1).keySet());
                        Collections.sort(eValues2);

                        for (double eValue : eValues2) {
                            for (SpectrumIdentificationAssumption assumption : spectrumMatch.getAllAssumptions(searchEngine1).get(eValue)) {
                                PeptideAssumption peptideAssumption = (PeptideAssumption) assumption;
                                if (peptideAssumption.getPeptide().getKey().equals(bestAssumptionKey)) {
                                    found = true;
                                    boolean found2 = false;
                                    for (PeptideAssumption assumption1 : assumptions.keySet()) {
                                        if (assumption1.getPeptide().sameModificationsAs(peptideAssumption.getPeptide())) {
                                            found2 = true;
                                            psParameter = (PSParameter) assumption.getUrParam(psParameter);
                                            assumptions.get(assumption1).add(psParameter.getSearchEngineProbability());
                                            break;
                                        }
                                    }
                                    if (!found2) {
                                        assumptions.put(peptideAssumption, new ArrayList<Double>());
                                        psParameter = (PSParameter) assumption.getUrParam(psParameter);
                                        assumptions.get(peptideAssumption).add(psParameter.getSearchEngineProbability());
                                    }
                                }
                            }
                            if (found) {
                                break;
                            }
                        }
                    }

                    Double bestSeP = null;
                    int nSe = -1;

                    for (PeptideAssumption peptideAssumption : assumptions.keySet()) {
                        Double sep = Collections.min(assumptions.get(peptideAssumption));
                        if (bestSeP == null || bestSeP > sep) {
                            bestSeP = sep;
                            nSe = assumptions.get(peptideAssumption).size();
                            bestAssumption = peptideAssumption;
                        } else if (sep == bestSeP && assumptions.get(peptideAssumption).size() > nSe) {
                            nSe = assumptions.get(peptideAssumption).size();
                            bestAssumption = peptideAssumption;
                        }
                    }
                }

                // create a PeptideShaker match based on the best search engine match
                Peptide sePeptide = bestAssumption.getPeptide();
                ArrayList<String> psProteins = new ArrayList<String>(sePeptide.getParentProteins(MATCHING_TYPE, searchParameters.getFragmentIonAccuracy()));
                ArrayList<ModificationMatch> psModificationMatches = new ArrayList<ModificationMatch>();
                for (ModificationMatch seModMatch : sePeptide.getModificationMatches()) {
                    psModificationMatches.add(new ModificationMatch(seModMatch.getTheoreticPtm(), seModMatch.isVariable(), seModMatch.getModificationSite()));
                }
                Peptide psPeptide = new Peptide(sePeptide.getSequence(), psModificationMatches);
                psPeptide.setParentProteins(psProteins);
                PeptideAssumption psAssumption = new PeptideAssumption(psPeptide, 1, Advocate.PeptideShaker.getIndex(), bestAssumption.getIdentificationCharge(), retainedP);
                spectrumMatch.setBestPeptideAssumption(psAssumption);
                psParameter = new PSParameter();
                psParameter.setSpectrumProbabilityScore(retainedP);
                psmMap.addPoint(retainedP, spectrumMatch, searchParameters.getFragmentIonAccuracy());
                psParameter.setSpecificMapKey(psmMap.getKey(spectrumMatch) + "");
                identification.addSpectrumMatchParameter(spectrumKey, psParameter);
                identification.updateSpectrumMatch(spectrumMatch);
                waitingHandler.increaseSecondaryProgressCounter();

                if (waitingHandler.isRunCanceled()) {
                    return;
                }
            }
        }

        // the protein count map is no longer needed
        proteinCount.clear();

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Attaches the spectrum posterior error probabilities to the peptide
     * assumptions.
     *
     * @param inputMap
     * @param waitingHandler the handler displaying feedback to the user
     */
    private void attachAssumptionsProbabilities(InputMap inputMap, WaitingHandler waitingHandler) throws Exception {
        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(identification.getSpectrumIdentificationSize());

        for (String spectrumFileName : identification.getSpectrumFiles()) {
            identification.loadSpectrumMatches(spectrumFileName, null);
            for (String spectrumKey : identification.getSpectrumIdentification(spectrumFileName)) {

                waitingHandler.increaseSecondaryProgressCounter();

                SpectrumMatch spectrumMatch = identification.getSpectrumMatch(spectrumKey);

                for (int searchEngine : spectrumMatch.getAdvocates()) {

                    ArrayList<Double> eValues = new ArrayList<Double>(spectrumMatch.getAllAssumptions(searchEngine).keySet());
                    Collections.sort(eValues);
                    double previousP = 0;

                    for (double eValue : eValues) {

                        for (SpectrumIdentificationAssumption assumption : spectrumMatch.getAllAssumptions(searchEngine).get(eValue)) {

                            PeptideAssumption peptideAssumption = (PeptideAssumption) assumption;
                            PSParameter psParameter = new PSParameter();

                            if (sequenceFactory.concatenatedTargetDecoy()) {

                                double newP = inputMap.getProbability(searchEngine, eValue);

                                if (newP > previousP) {
                                    psParameter.setSearchEngineProbability(newP);
                                    previousP = newP;
                                } else {
                                    psParameter.setSearchEngineProbability(previousP);
                                }

                            } else {
                                psParameter.setSearchEngineProbability(1.0);
                            }

                            peptideAssumption.addUrParam(psParameter);

                        }

                    }
                }

                identification.updateSpectrumMatch(spectrumMatch);
                if (waitingHandler.isRunCanceled()) {
                    return;
                }
            }
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Attaches the spectrum posterior error probabilities to the spectrum
     * matches.
     *
     * @param waitingHandler the handler displaying feedback to the user
     */
    private void attachSpectrumProbabilitiesAndBuildPeptidesAndProteins(WaitingHandler waitingHandler, SearchParameters searchParameters) throws SQLException, IOException, ClassNotFoundException, IllegalArgumentException, Exception {

        waitingHandler.setWaitingText("Attaching Spectrum Probabilities - Building Peptides and Proteins. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(identification.getSpectrumIdentificationSize());

        PSParameter psParameter = new PSParameter();

        for (String spectrumFileName : identification.getSpectrumFiles()) {
            identification.loadSpectrumMatches(spectrumFileName, null);
            identification.loadSpectrumMatchParameters(spectrumFileName, psParameter, null);
            for (String spectrumKey : identification.getSpectrumIdentification(spectrumFileName)) {

                psParameter = (PSParameter) identification.getSpectrumMatchParameter(spectrumKey, psParameter);
                if (sequenceFactory.concatenatedTargetDecoy()) {
                    psParameter.setPsmProbability(psmMap.getProbability(psParameter.getSpecificMapKey(), psParameter.getPsmProbabilityScore()));
                } else {
                    psParameter.setPsmProbability(1.0);
                }
                identification.updateSpectrumMatchParameter(spectrumKey, psParameter);

                identification.buildPeptidesAndProteins(spectrumKey, MATCHING_TYPE, searchParameters.getFragmentIonAccuracy());

                waitingHandler.increaseSecondaryProgressCounter();
                if (waitingHandler.isRunCanceled()) {
                    return;
                }
            }
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Attaches scores to possible PTM locations to spectrum matches.
     *
     * @param inspectedSpectra
     * @param waitingHandler the handler displaying feedback to the user
     * @param searchParameters the search preferences containing the m/z
     * tolerances
     * @param annotationPreferences the spectrum annotation preferences
     * @param ptmScoringPreferences the prm scoring preferences
     * @throws Exception
     */
    public void scorePSMPTMs(ArrayList<String> inspectedSpectra, WaitingHandler waitingHandler, SearchParameters searchParameters,
            AnnotationPreferences annotationPreferences, PTMScoringPreferences ptmScoringPreferences) throws Exception {

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        int max = inspectedSpectra.size();
        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(max);

        if (ptmScoringPreferences.isProbabilitsticScoreCalculation()) {
            psmPTMMap = new PsmPTMMap();
        }

        PSParameter psParameter = new PSParameter();
        for (String spectrumKey : inspectedSpectra) {
            waitingHandler.increaseSecondaryProgressCounter();
            SpectrumMatch spectrumMatch = identification.getSpectrumMatch(spectrumKey);
            psParameter = (PSParameter) identification.getSpectrumMatchParameter(spectrumKey, psParameter);
            double confidenceThreshold = psmMap.getTargetDecoyMap(psmMap.getCorrectedKey(psParameter.getSpecificMapKey())).getTargetDecoyResults().getConfidenceLimit();
            scorePTMs(spectrumMatch, searchParameters, annotationPreferences, ptmScoringPreferences, confidenceThreshold);
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Infers the PTM localization and its confidence for the best match of
     * every spectrum
     *
     * @param waitingHandler waiting handler displaying progress to the user
     * @param ptmScoringPreferences the PTM scoring preferences
     * @param searchParameters the search parameters used
     *
     * @throws SQLException exception thrown whenever a problem occurred while
     * interacting with the database
     * @throws IOException exception thrown whenever a problem occurred while
     * writing/reading the database or the FASTA file
     * @throws ClassNotFoundException exception thrown whenever a problem
     * occurred while deserializing an object from the database
     * @throws IllegalArgumentException exception thrown whenever an error
     * occurred while reading a protein sequence
     * @throws InterruptedException exception thrown whenever an error occurred
     * while reading a protein sequence
     */
    public void ptmInference(WaitingHandler waitingHandler, PTMScoringPreferences ptmScoringPreferences, SearchParameters searchParameters) throws SQLException, IOException, ClassNotFoundException, IllegalArgumentException, InterruptedException {

        waitingHandler.setWaitingText("Solving Peptide Inference. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(identification.getSpectrumIdentificationSize());

        HashMap<String, HashMap<String, ArrayList<String>>> confidentPeptideInference = new HashMap<String, HashMap<String, ArrayList<String>>>();
        HashMap<String, HashMap<String, ArrayList<String>>> notConfidentPeptideInference = new HashMap<String, HashMap<String, ArrayList<String>>>();

        for (String spectrumFileName : identification.getSpectrumFiles()) {
            identification.loadSpectrumMatches(spectrumFileName, null);
            for (String spectrumKey : identification.getSpectrumIdentification(spectrumFileName)) {
                SpectrumMatch spectrumMatch = identification.getSpectrumMatch(spectrumKey);
                boolean variableAA = false;
                for (ModificationMatch modificationMatch : spectrumMatch.getBestPeptideAssumption().getPeptide().getModificationMatches()) {
                    if (modificationMatch.isVariable()) {
                        String modName = modificationMatch.getTheoreticPtm();
                        PTM ptm = ptmFactory.getPTM(modName);
                        if (ptm.getType() == PTM.MODAA) {
                            variableAA = true;
                            break;
                        }
                    }
                }
                if (variableAA) {
                    ptmInference(spectrumMatch, ptmScoringPreferences, searchParameters);
                    boolean confident = true;
                    for (ModificationMatch modMatch : spectrumMatch.getBestPeptideAssumption().getPeptide().getModificationMatches()) {
                        if (modMatch.isVariable()) {
                            String modName = modMatch.getTheoreticPtm();
                            PTM ptm = ptmFactory.getPTM(modName);
                            if (ptm.getType() == PTM.MODAA) {
                                if (!modMatch.isConfident()) {
                                    if (!notConfidentPeptideInference.containsKey(spectrumFileName)) {
                                        notConfidentPeptideInference.put(spectrumFileName, new HashMap<String, ArrayList<String>>());
                                    }
                                    if (!notConfidentPeptideInference.get(spectrumFileName).containsKey(modMatch.getTheoreticPtm())) {
                                        notConfidentPeptideInference.get(spectrumFileName).put(modMatch.getTheoreticPtm(), new ArrayList<String>());
                                    }
                                    notConfidentPeptideInference.get(spectrumFileName).get(modMatch.getTheoreticPtm()).add(spectrumKey);
                                    confident = false;
                                } else {
                                    if (!confidentPeptideInference.containsKey(modName)) {
                                        confidentPeptideInference.put(modName, new HashMap<String, ArrayList<String>>());
                                    }
                                    String sequence = spectrumMatch.getBestPeptideAssumption().getPeptide().getSequence();
                                    if (!confidentPeptideInference.get(modName).containsKey(sequence)) {
                                        confidentPeptideInference.get(modName).put(sequence, new ArrayList<String>());
                                    }
                                    confidentPeptideInference.get(modName).get(sequence).add(spectrumKey);
                                }
                            }
                        }
                    }
                    identification.updateSpectrumMatch(spectrumMatch);
                    if (confident) {
                        waitingHandler.increaseSecondaryProgressCounter();
                    }
                    if (waitingHandler.isRunCanceled()) {
                        return;
                    }
                } else {
                    waitingHandler.increaseSecondaryProgressCounter();
                    if (waitingHandler.isRunCanceled()) {
                        return;
                    }
                }
            }
        }
        // try to infer the modification site based on any related peptide
        for (String spectrumFile : notConfidentPeptideInference.keySet()) {

            ArrayList<String> progress = new ArrayList<String>();

            for (String modification : notConfidentPeptideInference.get(spectrumFile).keySet()) {

                identification.loadSpectrumMatches(notConfidentPeptideInference.get(spectrumFile).get(modification), null);

                for (String spectrumKey : notConfidentPeptideInference.get(spectrumFile).get(modification)) {

                    SpectrumMatch spectrumMatch = identification.getSpectrumMatch(spectrumKey);
                    Peptide peptide = spectrumMatch.getBestPeptideAssumption().getPeptide();
                    String sequence = peptide.getSequence();
                    String notConfidentKey = peptide.getKey();
                    int nMod = Peptide.getModificationCount(notConfidentKey, modification);
                    ArrayList<Integer> tempLocalizations, oldLocalizations = Peptide.getNModificationLocalized(notConfidentKey, modification);
                    ArrayList<Integer> newLocalizationCandidates = new ArrayList<Integer>();

                    if (confidentPeptideInference.containsKey(modification)) {

                        // See if we can explain this peptide by another already identified peptides with the same number of modifications (the two peptides will be merged)
                        ArrayList<String> keys = confidentPeptideInference.get(modification).get(sequence);

                        if (keys != null) {
                            for (String tempKey : keys) {
                                SpectrumMatch secondaryMatch = identification.getSpectrumMatch(tempKey);
                                String secondaryKey = secondaryMatch.getBestPeptideAssumption().getPeptide().getKey();
                                if (Peptide.getModificationCount(secondaryKey, modification) == nMod) {
                                    tempLocalizations = Peptide.getNModificationLocalized(secondaryKey, modification);
                                    for (int localization : tempLocalizations) {
                                        if (!oldLocalizations.contains(localization) && !newLocalizationCandidates.contains(localization)) {
                                            newLocalizationCandidates.add(localization);
                                        }
                                    }
                                }
                            }
                            if (oldLocalizations.size() + newLocalizationCandidates.size() < nMod) {
                                // we cannot merge this peptide, see whether we can explain the remaining modifications using peptides with the same sequence but other modification profile
                                for (String tempKey : keys) {
                                    SpectrumMatch secondaryMatch = identification.getSpectrumMatch(tempKey);
                                    String secondaryKey = secondaryMatch.getBestPeptideAssumption().getPeptide().getKey();
                                    tempLocalizations = Peptide.getNModificationLocalized(secondaryKey, modification);
                                    for (int localization : tempLocalizations) {
                                        if (!oldLocalizations.contains(localization) && !newLocalizationCandidates.contains(localization)) {
                                            newLocalizationCandidates.add(localization);
                                        }
                                    }
                                }
                            }
                        }
                        if (oldLocalizations.size() + newLocalizationCandidates.size() < nMod) {
                            // There are still unexplained sites, let's see if we find a related peptide which can help. That is uggly but the results should be cool.
                            for (String otherSequence : confidentPeptideInference.get(modification).keySet()) {

                                // @TODO: are semi-specific, top-down, whole protein and no enzyme handled correctly??
                                Enzyme enzyme = searchParameters.getEnzyme();
                                if (enzyme.isSemiSpecific() || enzyme.getNmissedCleavages(sequence) == enzyme.getNmissedCleavages(otherSequence)) {
                                    if (!sequence.equals(otherSequence) && sequence.contains(otherSequence)) {
                                        for (String tempKey : confidentPeptideInference.get(modification).get(otherSequence)) {
                                            SpectrumMatch secondaryMatch = identification.getSpectrumMatch(tempKey);
                                            String secondaryKey = secondaryMatch.getBestPeptideAssumption().getPeptide().getKey();
                                            tempLocalizations = Peptide.getNModificationLocalized(secondaryKey, modification);
                                            int tempIndex, ref = 0;
                                            String tempSequence = sequence;
                                            while ((tempIndex = tempSequence.indexOf(otherSequence)) >= 0) {
                                                ref += tempIndex;
                                                for (int localization : tempLocalizations) {
                                                    int shiftedLocalization = ref + localization;
                                                    if (!oldLocalizations.contains(shiftedLocalization) && !newLocalizationCandidates.contains(shiftedLocalization)) {
                                                        newLocalizationCandidates.add(shiftedLocalization);
                                                        PTM ptm = ptmFactory.getPTM(modification);
                                                        if (!peptide.getPotentialModificationSites(ptm, MATCHING_TYPE, searchParameters.getFragmentIonAccuracy()).contains(shiftedLocalization)) {
                                                            throw new IllegalArgumentException("Wrong PTM site inference: " + modification + " at position " + shiftedLocalization + " on " + sequence + " in spectrum " + spectrumKey + " when using related sequence " + otherSequence + " modified at " + localization + ".");
                                                        }
                                                    }
                                                }
                                                tempSequence = tempSequence.substring(tempIndex + 1);
                                                ref++;
                                            }
                                        }
                                    } else if (!sequence.equals(otherSequence) && otherSequence.contains(sequence)) {
                                        for (String tempKey : confidentPeptideInference.get(modification).get(otherSequence)) {
                                            SpectrumMatch secondaryMatch = identification.getSpectrumMatch(tempKey);
                                            String secondaryKey = secondaryMatch.getBestPeptideAssumption().getPeptide().getKey();
                                            tempLocalizations = Peptide.getNModificationLocalized(secondaryKey, modification);
                                            int tempIndex, ref = 0;
                                            String tempSequence = otherSequence;
                                            while ((tempIndex = tempSequence.indexOf(sequence)) >= 0) {
                                                ref += tempIndex;
                                                for (int localization : tempLocalizations) {
                                                    int shiftedLocalization = localization - ref;
                                                    if (shiftedLocalization > 0 && shiftedLocalization <= sequence.length()
                                                            && !oldLocalizations.contains(shiftedLocalization) && !newLocalizationCandidates.contains(shiftedLocalization)) {
                                                        newLocalizationCandidates.add(shiftedLocalization);
                                                        PTM ptm = ptmFactory.getPTM(modification);
                                                        if (!peptide.getPotentialModificationSites(ptm, MATCHING_TYPE, searchParameters.getFragmentIonAccuracy()).contains(shiftedLocalization)) {
                                                            throw new IllegalArgumentException("Wrong PTM site inference: " + modification + " at position " + shiftedLocalization + " on " + sequence + " in spectrum " + spectrumKey + " when using related sequence " + otherSequence + " modified at " + localization + ".");
                                                        }
                                                    }
                                                }
                                                tempSequence = tempSequence.substring(tempIndex + 1);
                                                ref++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Map the most likely inferred sites
                        if (!newLocalizationCandidates.isEmpty()) {
                            HashMap<Integer, ModificationMatch> nonConfidentMatches = new HashMap<Integer, ModificationMatch>();
                            for (ModificationMatch modificationMatch : peptide.getModificationMatches()) {
                                if (modificationMatch.getTheoreticPtm().equals(modification) && !modificationMatch.isConfident()) {
                                    nonConfidentMatches.put(modificationMatch.getModificationSite(), modificationMatch);
                                }
                            }
                            HashMap<Integer, Integer> mapping = PtmSiteMapping.align(nonConfidentMatches.keySet(), newLocalizationCandidates);
                            for (Integer oldLocalization : mapping.keySet()) {
                                ModificationMatch modificationMatch = nonConfidentMatches.get(oldLocalization);
                                Integer newLocalization = mapping.get(oldLocalization);
                                if (modificationMatch != null && newLocalization != null) {
                                    PTM ptm = ptmFactory.getPTM(modificationMatch.getTheoreticPtm());
                                    if (!peptide.getPotentialModificationSites(ptm, MATCHING_TYPE, searchParameters.getFragmentIonAccuracy()).contains(newLocalization)) {
                                        throw new IllegalArgumentException("Wrong PTM site inference: " + modificationMatch.getTheoreticPtm()
                                                + " at position " + newLocalization + " on " + sequence + " in spectrum " + spectrumKey + ".");
                                    }
                                    modificationMatch.setInferred(true);
                                    modificationMatch.setModificationSite(newLocalization);
                                }
                            }
                        }
                        identification.updateSpectrumMatch(spectrumMatch);
                    }
                    if (waitingHandler.isRunCanceled()) {
                        return;
                    }
                    if (!progress.contains(spectrumKey)) {
                        progress.add(spectrumKey);
                        waitingHandler.increaseSecondaryProgressCounter();
                    }
                }
            }
        }
    }

    /**
     * Infers the modification site of every PSM based on the PTM scores and the
     * FLR settings. The FLR must have been calculated before.
     *
     * @param spectrumMatch the spectrum match inspected
     * @param ptmScoringPreferences the PTM scoring preferences as set by the
     * user
     * @param searchParameters the identification parameters
     *
     * @throws IOException exception thrown whenever an error occurred while
     * reading a protein sequence
     * @throws IllegalArgumentException exception thrown whenever an error
     * occurred while reading a protein sequence
     * @throws InterruptedException exception thrown whenever an error occurred
     * while reading a protein sequence
     */
    private void ptmInference(SpectrumMatch spectrumMatch, PTMScoringPreferences ptmScoringPreferences, SearchParameters searchParameters) throws IOException, IllegalArgumentException, InterruptedException, FileNotFoundException, ClassNotFoundException, SQLException {

        Peptide psPeptide = spectrumMatch.getBestPeptideAssumption().getPeptide();

        HashMap<String, ArrayList<ModificationMatch>> modMatches = new HashMap<String, ArrayList<ModificationMatch>>();
        for (ModificationMatch modMatch : psPeptide.getModificationMatches()) {
            if (modMatch.isVariable()) {
                String modName = modMatch.getTheoreticPtm();
                PTM ptm = ptmFactory.getPTM(modName);
                if (ptm.getType() == PTM.MODAA) {
                    if (!modMatches.containsKey(modName)) {
                        modMatches.put(modName, new ArrayList<ModificationMatch>());
                    }
                    modMatches.get(modName).add(modMatch);
                }
            }
        }
        if (!modMatches.isEmpty()) {
            PSPtmScores ptmScores = (PSPtmScores) spectrumMatch.getUrParam(new PSPtmScores());
            if (ptmScores != null) {
                for (String modName : modMatches.keySet()) {
                    PtmScoring ptmScoring = ptmScores.getPtmScoring(modName);
                    if (ptmScoring != null) {
                        PTM ptm = ptmFactory.getPTM(modName);
                        ArrayList<Integer> possiblePositions = new ArrayList<Integer>();
                        for (String ptmName : searchParameters.getModificationProfile().getAllNotFixedModifications()) {
                            PTM tempPtm = ptmFactory.getPTM(ptmName);
                            if (tempPtm.getMass() == ptm.getMass()) {
                                for (int pos : psPeptide.getPotentialModificationSites(tempPtm, MATCHING_TYPE, searchParameters.getFragmentIonAccuracy())) {
                                    if (!possiblePositions.contains(pos)) {
                                        possiblePositions.add(pos);
                                    }
                                }
                            }
                        }
                        if (possiblePositions.size() < modMatches.get(modName).size()) {
                            throw new IllegalArgumentException("The occurence of " + modName + " (" + modMatches.get(modName).size()
                                    + ") is higher than the number of possible sites on sequence " + psPeptide.getSequence()
                                    + " in spectrum " + spectrumMatch.getKey() + ".");
                        } else if (ptm.getType() != PTM.MODAA || possiblePositions.size() == modMatches.get(modName).size()) {
                            for (ModificationMatch modMatch : modMatches.get(modName)) {
                                modMatch.setConfident(true);
                                ptmScoring.setSiteConfidence(modMatch.getModificationSite(), PtmScoring.VERY_CONFIDENT);
                            }
                        } else {
                            if (ptmScoringPreferences.isProbabilitsticScoreCalculation() && !ptmScoring.getProbabilisticSites().isEmpty()) {
                                Double ptmMass = ptm.getMass();
                                int key = spectrumMatch.getBestPeptideAssumption().getIdentificationCharge().value;
                                TargetDecoyMap currentMap = psmPTMMap.getTargetDecoyMap(ptmMass, key);
                                if (currentMap == null) {
                                    currentMap = psmPTMMap.getTargetDecoyMap(ptmMass, key);
                                    throw new IllegalArgumentException("No FLR map found for " + modName + " in PSMs of charge " + key + ".");
                                }
                                double threshold;
                                if (ptmScoringPreferences.isEstimateFlr()) {
                                    threshold = -currentMap.getTargetDecoyResults().getScoreLimit();
                                } else {
                                    threshold = ptmScoringPreferences.getProbabilisticScoreThreshold();
                                }
                                for (ModificationMatch modMatch : modMatches.get(modName)) {
                                    int site = modMatch.getModificationSite();
                                    double score = ptmScoring.getProbabilisticScore(site);
                                    if (score == 0) {
                                        ptmScoring.setSiteConfidence(site, PtmScoring.RANDOM);
                                        modMatch.setConfident(false);
                                    } else if (score >= threshold) {
                                        ptmScoring.setSiteConfidence(site, PtmScoring.VERY_CONFIDENT);
                                        modMatch.setConfident(true);
                                    } else {
                                        ptmScoring.setSiteConfidence(site, PtmScoring.DOUBTFUL);
                                        modMatch.setConfident(true);
                                    }
                                }
                            } else {
                                for (ModificationMatch modMatch : modMatches.get(modName)) {
                                    int site = modMatch.getModificationSite();
                                    double score = ptmScoring.getDeltaScore(site);
                                    if (score == 0) {
                                        ptmScoring.setSiteConfidence(site, PtmScoring.RANDOM);
                                        modMatch.setConfident(false);
                                    } else if (score >= 95) {
                                        ptmScoring.setSiteConfidence(site, PtmScoring.CONFIDENT);
                                        modMatch.setConfident(true);
                                    } else {
                                        ptmScoring.setSiteConfidence(site, PtmScoring.DOUBTFUL);
                                        modMatch.setConfident(true);
                                    }
                                }
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("PTM scoring null for modification " + modName + "in spectrum " + spectrumMatch.getKey() + ".");
                    }
                }
            } else {
                throw new IllegalArgumentException("PTM scores null for spectrum " + spectrumMatch.getKey() + ".");
            }
        }
    }

    /**
     * Scores the PTMs of all PSMs.
     *
     * @param waitingHandler the handler displaying feedback to the user
     * @param searchParameters the search preferences containing the m/z
     * tolerances
     * @param annotationPreferences the spectrum annotation preferences
     * @param ptmScoringPreferences the PTM scoring preferences
     * @throws Exception exception thrown whenever a problem occurred while
     * deserializing a match
     */
    public void scorePsmPtms(WaitingHandler waitingHandler, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, PTMScoringPreferences ptmScoringPreferences) throws Exception {

        waitingHandler.setWaitingText("Scoring Peptide PTMs. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(identification.getSpectrumIdentificationSize());

        for (String spectrumFileName : identification.getSpectrumFiles()) {
            identification.loadSpectrumMatches(spectrumFileName, null);
            for (String spectrumKey : identification.getSpectrumIdentification(spectrumFileName)) {
                SpectrumMatch spectrumMatch = identification.getSpectrumMatch(spectrumKey);
                scorePTMs(spectrumMatch, searchParameters, annotationPreferences, ptmScoringPreferences, replicateNumber);
                waitingHandler.increaseSecondaryProgressCounter();
                if (waitingHandler.isRunCanceled()) {
                    return;
                }
            }
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Scores the PTMs of all peptides.
     *
     * @param waitingHandler the handler displaying feedback to the user
     * @param searchParameters the search preferences containing the m/z
     * tolerances
     * @param annotationPreferences the spectrum annotation preferences
     * @param ptmScoringPreferences the PTM scoring preferences
     * @throws Exception exception thrown whenever a problem occurred while
     * deserializing a match
     */
    public void scorePeptidePtms(WaitingHandler waitingHandler, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, PTMScoringPreferences ptmScoringPreferences) throws Exception {

        waitingHandler.setWaitingText("Scoring Peptide PTMs. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);

        int max = identification.getPeptideIdentification().size();
        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(max);

        identification.loadPeptideMatches(null);

        for (String peptideKey : identification.getPeptideIdentification()) {
            PeptideMatch peptideMatch = identification.getPeptideMatch(peptideKey);
            scorePTMs(peptideMatch, searchParameters, annotationPreferences, ptmScoringPreferences);
            waitingHandler.increaseSecondaryProgressCounter();
            if (waitingHandler.isRunCanceled()) {
                return;
            }
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Scores the PTMs of all validated proteins.
     *
     * @param waitingHandler the handler displaying feedback to the user
     * @param searchParameters the search preferences containing the m/z
     * tolerances
     * @param annotationPreferences the spectrum annotation preferences
     * @param ptmScoringPreferences the PTM scoring preferences
     * @throws Exception exception thrown whenever a problem occurred while
     * deserializing a match
     */
    public void scoreProteinPtms(WaitingHandler waitingHandler, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, PTMScoringPreferences ptmScoringPreferences) throws Exception {
        scoreProteinPtms(waitingHandler, searchParameters, annotationPreferences, ptmScoringPreferences, new IdFilter(), null);
    }

    /**
     * Scores the PTMs of all validated proteins.
     *
     * @param waitingHandler the handler displaying feedback to the user
     * @param searchParameters the search preferences containing the m/z
     * tolerances
     * @param annotationPreferences the spectrum annotation preferences
     * @param idFilter the identification filter, needed only to get max values
     * @param estimateAscore a boolean indicating whether the A-score should be
     * estimated for the metrics, can be null when rescoring PTMs
     * @param spectrumCountingPreferences the spectrum counting preferences. If
     * not null, the maximum spectrum counting value will be stored in the
     * Metrics.
     * @throws Exception exception thrown whenever a problem occurred while
     * deserializing a match
     */
    private void scoreProteinPtms(WaitingHandler waitingHandler, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, PTMScoringPreferences ptmScoringPreferences, IdFilter idFilter, SpectrumCountingPreferences spectrumCountingPreferences) throws Exception {

        waitingHandler.setWaitingText("Scoring Protein PTMs. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);

        int max = identification.getProteinIdentification().size();
        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(max);
        identification.loadProteinMatches(null);
        identification.loadProteinMatchParameters(new PSParameter(), null);

        // If needed, while we are iterating proteins, we will take the maximal spectrum counting value and number of validated proteins as well.
        int nValidatedProteins = 0;
        int nConfidentProteins = 0;
        PSParameter psParameter = new PSParameter();
        double tempSpectrumCounting, maxSpectrumCounting = 0;
        Enzyme enzyme = searchParameters.getEnzyme();
        int maxPepLength = idFilter.getMaxPepLength();

        for (String proteinKey : identification.getProteinIdentification()) {
            ProteinMatch proteinMatch = identification.getProteinMatch(proteinKey);
            scorePTMs(proteinMatch, searchParameters, annotationPreferences, false, ptmScoringPreferences);

            if (metrics != null) {
                psParameter = (PSParameter) identification.getProteinMatchParameter(proteinKey, psParameter);
                if (psParameter.getMatchValidationLevel().isValidated()) {
                    nValidatedProteins++;
                    if (psParameter.getMatchValidationLevel() == MatchValidationLevel.confident) {
                        nConfidentProteins++;
                    }
                }
                if (spectrumCountingPreferences != null) {
                    tempSpectrumCounting = IdentificationFeaturesGenerator.estimateSpectrumCounting(identification, sequenceFactory, proteinKey, spectrumCountingPreferences, enzyme, maxPepLength, searchParameters.getFragmentIonAccuracy());
                    if (tempSpectrumCounting > maxSpectrumCounting) {
                        maxSpectrumCounting = tempSpectrumCounting;
                    }
                }
            }
            waitingHandler.increaseSecondaryProgressCounter();
            if (waitingHandler.isRunCanceled()) {
                return;
            }
        }
        if (metrics != null) {
            metrics.setMaxSpectrumCounting(maxSpectrumCounting);
            metrics.setnValidatedProteins(nValidatedProteins);
            metrics.setnConfidentProteins(nConfidentProteins);
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Scores ptms in a protein match.
     *
     * @param proteinMatch the protein match
     * @param searchParameters the search preferences containing the m/z
     * tolerances
     * @param annotationPreferences the spectrum annotation preferences
     * @param scorePeptides if true peptide scores will be recalculated
     * @param ptmScoringPreferences the prm scoring preferences
     * @throws Exception exception thrown whenever an error occurred while
     * deserilalizing a match
     */
    public void scorePTMs(ProteinMatch proteinMatch, SearchParameters searchParameters, AnnotationPreferences annotationPreferences,
            boolean scorePeptides, PTMScoringPreferences ptmScoringPreferences) throws Exception {

        PSPtmScores proteinScores = new PSPtmScores();
        PSParameter psParameter = new PSParameter();
        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        String proteinSequence = null;
        identification.loadPeptideMatches(proteinMatch.getPeptideMatches(), null);
        identification.loadPeptideMatchParameters(proteinMatch.getPeptideMatches(), psParameter, null);

        for (String peptideKey : proteinMatch.getPeptideMatches()) {
            psParameter = (PSParameter) identification.getPeptideMatchParameter(peptideKey, psParameter);
            if (psParameter.getMatchValidationLevel().isValidated() && Peptide.isModified(peptideKey)) {
                PeptideMatch peptideMath = identification.getPeptideMatch(peptideKey);
                String peptideSequence = Peptide.getSequence(peptideKey);
                if (peptideMath.getUrParam(new PSPtmScores()) == null || scorePeptides) {
                    scorePTMs(peptideMath, searchParameters, annotationPreferences, ptmScoringPreferences);
                }
                PSPtmScores peptideScores = (PSPtmScores) peptideMath.getUrParam(new PSPtmScores());
                if (peptideScores != null) {
                    for (String modification : peptideScores.getScoredPTMs()) {
                        if (proteinSequence == null) {
                            proteinSequence = sequenceFactory.getProtein(proteinMatch.getMainMatch()).getSequence();
                        }
                        PtmScoring ptmScoring = peptideScores.getPtmScoring(modification);
                        for (int pos : getProteinModificationIndexes(proteinSequence, peptideSequence, ptmScoring.getConfidentPtmLocations(), searchParameters.getFragmentIonAccuracy())) {
                            proteinScores.addMainModificationSite(modification, pos);
                        }
                        for (int pos : getProteinModificationIndexes(proteinSequence, peptideSequence, ptmScoring.getSecondaryPtmLocations(), searchParameters.getFragmentIonAccuracy())) {
                            proteinScores.addSecondaryModificationSite(modification, pos);
                        }
                    }
                }
            }
        }

        proteinMatch.addUrParam(proteinScores);
        identification.updateProteinMatch(proteinMatch);
    }

    /**
     * Returns the protein indexes of a modification found in a peptide.
     *
     * @param proteinSequence The protein sequence
     * @param peptideSequence The peptide sequence
     * @param positionInPeptide The position(s) of the modification in the
     * peptide sequence
     * @param mzTolerance the ms2 m/z tolerance
     *
     * @return the possible modification sites in a protein
     */
    public static ArrayList<Integer> getProteinModificationIndexes(String proteinSequence, String peptideSequence, ArrayList<Integer> positionInPeptide, double mzTolerance) {
        ArrayList<Integer> result = new ArrayList<Integer>();
        AminoAcidPattern aminoAcidPattern = new AminoAcidPattern(peptideSequence);
        for (int peptideTempStart : aminoAcidPattern.getIndexes(proteinSequence, MATCHING_TYPE, mzTolerance)) {
            for (int pos : positionInPeptide) {
                result.add(peptideTempStart + pos - 2);
            }
        }
        return result;
    }

    /**
     * Scores the PTMs for a peptide match.
     *
     * @param peptideMatch the peptide match of interest
     * @param searchParameters the search preferences containing the m/z
     * tolerances
     * @param annotationPreferences the spectrum annotation preferences
     * @param scoringPreferences the PTM scoring preferences
     * @throws Exception exception thrown whenever an error occurred while
     * deserializing a match
     */
    public void scorePTMs(PeptideMatch peptideMatch, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, PTMScoringPreferences scoringPreferences) throws Exception {

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        PSPtmScores peptideScores = new PSPtmScores();
        PSParameter psParameter = new PSParameter();
        ArrayList<String> variableModifications = new ArrayList<String>();

        for (ModificationMatch modificationMatch : peptideMatch.getTheoreticPeptide().getModificationMatches()) {
            if (modificationMatch.isVariable()) {
                PTM ptm = ptmFactory.getPTM(modificationMatch.getTheoreticPtm());
                if (ptm.getType() == PTM.MODAA && !variableModifications.contains(modificationMatch.getTheoreticPtm())) {
                    variableModifications.add(modificationMatch.getTheoreticPtm());
                }
            }
        }

        if (variableModifications.size() > 0) {

            int validationLevel = MatchValidationLevel.not_validated.getIndex();
            double bestConfidence = 0;
            ArrayList<String> bestKeys = new ArrayList<String>();

            identification.loadSpectrumMatches(peptideMatch.getSpectrumMatches(), null);
            identification.loadSpectrumMatchParameters(peptideMatch.getSpectrumMatches(), psParameter, null);

            for (String spectrumKey : peptideMatch.getSpectrumMatches()) {
                psParameter = (PSParameter) identification.getSpectrumMatchParameter(spectrumKey, psParameter);
                int tempValidationLevel = psParameter.getMatchValidationLevel().getIndex();
                if (tempValidationLevel >= validationLevel) {
                    if (tempValidationLevel != validationLevel) {
                        bestKeys.clear();
                        validationLevel = tempValidationLevel;
                    }
                    bestKeys.add(spectrumKey);
                } else if (tempValidationLevel == MatchValidationLevel.not_validated.getIndex()) {
                    if (psParameter.getPsmConfidence() > bestConfidence) {
                        bestConfidence = psParameter.getPsmConfidence();
                        bestKeys.clear();
                        bestKeys.add(spectrumKey);
                    } else if (psParameter.getPsmConfidence() == bestConfidence) {
                        bestKeys.add(spectrumKey);
                    }
                }
            }

            identification.loadSpectrumMatches(bestKeys, null);
            identification.loadSpectrumMatchParameters(bestKeys, psParameter, null);

            for (String spectrumKey : bestKeys) {

                psParameter = (PSParameter) identification.getSpectrumMatchParameter(spectrumKey, psParameter);
                SpectrumMatch spectrumMatch = identification.getSpectrumMatch(spectrumKey);

                for (String modification : variableModifications) {

                    if (!peptideScores.containsPtm(modification)) {
                        peptideScores.addPtmScoring(modification, new PtmScoring(modification));
                    }

                    PSPtmScores psmScores = (PSPtmScores) spectrumMatch.getUrParam(new PSPtmScores());

                    if (psmScores != null) {
                        PtmScoring spectrumScoring = psmScores.getPtmScoring(modification);
                        if (spectrumScoring != null) {
                            peptideScores.getPtmScoring(modification).addAll(spectrumScoring);
                        }
                    }
                }
            }

            for (String modification : variableModifications) {

                PtmScoring scoring = peptideScores.getPtmScoring(modification);

                if (scoring != null) {
                    for (int mainLocation : scoring.getConfidentPtmLocations()) {
                        peptideScores.addMainModificationSite(modification, mainLocation);
                    }
                    for (int secondaryLocation : scoring.getSecondaryPtmLocations()) {
                        peptideScores.addSecondaryModificationSite(modification, secondaryLocation);
                    }
                }
            }

            peptideMatch.addUrParam(peptideScores);
            identification.updatePeptideMatch(peptideMatch);
        }
    }

    /**
     * Scores PTM locations for a desired spectrumMatch.
     *
     * @param spectrumMatch The spectrum match of interest
     * @param searchParameters the search preferences containing the m/z
     * tolerances
     * @param annotationPreferences the spectrum annotation preferences
     * @param scoringPreferences the PTM scoring preferences
     * @param confidenceThreshold the confidence validation threshold for this
     * PSM
     * @throws Exception exception thrown whenever an error occurred while
     * reading/writing the an identification match
     */
    public void scorePTMs(SpectrumMatch spectrumMatch, SearchParameters searchParameters, AnnotationPreferences annotationPreferences,
            PTMScoringPreferences scoringPreferences, double confidenceThreshold) throws Exception {

        attachDeltaScore(spectrumMatch, searchParameters);

        if (scoringPreferences.isProbabilitsticScoreCalculation()) {
            attachProbabilisticScore(spectrumMatch, searchParameters, annotationPreferences, scoringPreferences);
        }

        PSPtmScores ptmScores = (PSPtmScores) spectrumMatch.getUrParam(new PSPtmScores());

        if (ptmScores != null) {

            Peptide peptide = spectrumMatch.getBestPeptideAssumption().getPeptide();

            ArrayList<Double> modificationMasses = new ArrayList<Double>();
            for (ModificationMatch modificationMatch : peptide.getModificationMatches()) {
                PTM ptm = ptmFactory.getPTM(modificationMatch.getTheoreticPtm());
                if (!modificationMasses.contains(ptm.getMass())) {
                    modificationMasses.add(ptm.getMass());
                }
            }

            for (double ptmMass : modificationMasses) {
                int nPtm = peptide.getNVariableModifications(ptmMass);
                HashMap<Double, ArrayList<Integer>> dSitesMap = new HashMap<Double, ArrayList<Integer>>();
                HashMap<Double, ArrayList<Integer>> pSitesMap = new HashMap<Double, ArrayList<Integer>>();
                HashMap<Integer, Double> pScores = new HashMap<Integer, Double>();
                for (String modification : ptmScores.getScoredPTMs()) {
                    PTM ptm = ptmFactory.getPTM(modification);
                    if (ptm.getMass() == ptmMass) {
                        PtmScoring ptmScoring = ptmScores.getPtmScoring(modification);
                        for (int site : ptmScoring.getDSites()) {
                            double score = ptmScoring.getDeltaScore(site);
                            ArrayList<Integer> sites = dSitesMap.get(score);
                            if (sites == null) {
                                sites = new ArrayList<Integer>();
                                dSitesMap.put(score, sites);
                            }
                            sites.add(site);
                        }
                        for (int site : ptmScoring.getProbabilisticSites()) {
                            double score = ptmScoring.getProbabilisticScore(site);
                            ArrayList<Integer> sites = pSitesMap.get(score);
                            if (sites == null) {
                                sites = new ArrayList<Integer>();
                                pSitesMap.put(score, sites);
                            }
                            sites.add(site);
                            if (!pScores.containsKey(site)) {
                                pScores.put(site, score);
                            } else {
                                throw new IllegalArgumentException("Duplicate PTM score found at site " + site + " for peptide " + peptide.getSequence() + " in spectrum " + spectrumMatch.getKey() + ".");
                            }
                        }
                    }
                }

                ArrayList<Integer> dSites = new ArrayList<Integer>(nPtm);
                ArrayList<Double> scores = new ArrayList<Double>(dSitesMap.keySet());
                Collections.sort(scores, Collections.reverseOrder());
                int cpt = 0;
                for (double score : scores) {
                    ArrayList<Integer> sites = dSitesMap.get(score);
                    if (sites.size() >= nPtm - cpt) {
                        dSites.addAll(sites);
                        cpt += sites.size();
                    } else {
                        Collections.shuffle(sites);
                        for (Integer site : sites) {
                            if (cpt == nPtm) {
                                break;
                            }
                            dSites.add(site);
                        }
                    }
                    if (cpt == nPtm) {
                        break;
                    }
                }

                ArrayList<Integer> pSites = new ArrayList<Integer>(nPtm);
                scores = new ArrayList<Double>(pSitesMap.keySet());
                Collections.sort(scores, Collections.reverseOrder());
                cpt = 0;
                for (double score : scores) {
                    ArrayList<Integer> sites = pSitesMap.get(score);
                    if (sites.size() >= nPtm - cpt) {
                        pSites.addAll(sites);
                        cpt += sites.size();
                    } else {
                        Collections.shuffle(sites);
                        for (Integer site : sites) {
                            if (cpt == nPtm) {
                                break;
                            }
                            pSites.add(site);
                        }
                    }
                    if (cpt == nPtm) {
                        break;
                    }
                }
                if (dSites.size() < nPtm) {
                    throw new IllegalArgumentException("found less D-scores than PTMs for modification of mass " + ptmMass + " in peptide " + peptide.getSequence() + " in spectrum " + spectrumMatch.getKey() + ".");
                }
                for (Integer site : pSites) {
                    boolean conflict = !dSites.contains(site);
                    psmPTMMap.addPoint(ptmMass, -pScores.get(site), spectrumMatch, conflict);
                }
            }
        }
    }

    /**
     * Scores the PTM locations using the delta score.
     *
     * @param spectrumMatch the spectrum match of interest
     * @param searchParameters the search parameters
     *
     * @throws Exception exception thrown whenever an error occurred while
     * reading/writing the an identification match
     */
    private void attachDeltaScore(SpectrumMatch spectrumMatch, SearchParameters searchParameters) throws Exception {
        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);

        HashMap<String, ArrayList<Integer>> modificationProfiles = new HashMap<String, ArrayList<Integer>>();
        PSPtmScores ptmScores = new PSPtmScores();

        if (spectrumMatch.getUrParam(new PSPtmScores()) != null) {
            ptmScores = (PSPtmScores) spectrumMatch.getUrParam(new PSPtmScores());
        }

        PSParameter psParameter = new PSParameter();
        double p1 = 1;
        Peptide psPeptide = spectrumMatch.getBestPeptideAssumption().getPeptide();
        for (SpectrumIdentificationAssumption assumption : spectrumMatch.getAllAssumptions()) {
            PeptideAssumption peptideAssumption = (PeptideAssumption) assumption;
            Peptide sePeptide = peptideAssumption.getPeptide();
            if (psPeptide.isSameSequence(sePeptide, MATCHING_TYPE, searchParameters.getFragmentIonAccuracy()) && psPeptide.sameModificationsAs(sePeptide)) {
                psParameter = (PSParameter) peptideAssumption.getUrParam(psParameter);
                double ptemp = psParameter.getSearchEngineProbability();
                if (ptemp < p1) {
                    p1 = ptemp;
                }
            }
        }

        String mainSequence = psPeptide.getSequence();
        ArrayList<String> modifications = new ArrayList<String>();

        int nace = 0;
        for (ModificationMatch modificationMatch : psPeptide.getModificationMatches()) {
            if (modificationMatch.isVariable()) {
                String modificationName = modificationMatch.getTheoreticPtm();
                if (modificationName.contains("ace")) {
                    nace++;
                }
                if (!modifications.contains(modificationName)) {
                    modifications.add(modificationName);
                    modificationProfiles.put(modificationName, new ArrayList<Integer>());
                }
                modificationProfiles.get(modificationName).add(modificationMatch.getModificationSite());
            }
        }

        if (!modifications.isEmpty()) {
            for (String modName : modifications) {
                PTM ptm1 = ptmFactory.getPTM(modName);
                for (int modSite : modificationProfiles.get(modName)) {
                    double refP = 1, secondaryP = 1;
                    for (SpectrumIdentificationAssumption assumption : spectrumMatch.getAllAssumptions()) {
                        PeptideAssumption peptideAssumption = (PeptideAssumption) assumption;
                        if (peptideAssumption.getPeptide().getSequence().equals(mainSequence)) {
                            boolean modificationAtSite = false, modificationFound = false;
                            for (ModificationMatch modMatch : peptideAssumption.getPeptide().getModificationMatches()) {
                                PTM ptm2 = ptmFactory.getPTM(modMatch.getTheoreticPtm());
                                if (ptm1.getMass() == ptm2.getMass()) {
                                    modificationFound = true;
                                    psParameter = (PSParameter) peptideAssumption.getUrParam(psParameter);
                                    double p = psParameter.getSearchEngineProbability();
                                    if (modMatch.getModificationSite() == modSite) {
                                        modificationAtSite = true;
                                        if (p < refP) {
                                            refP = p;
                                        }
                                    }
                                }
                            }
                            if (!modificationAtSite && modificationFound) {
                                psParameter = (PSParameter) peptideAssumption.getUrParam(psParameter);
                                double p = psParameter.getSearchEngineProbability();
                                if (p < secondaryP) {
                                    secondaryP = p;
                                }
                            }
                        }
                    }
                    PtmScoring ptmScoring = ptmScores.getPtmScoring(modName);
                    if (ptmScoring == null) {
                        ptmScoring = new PtmScoring(modName);
                        ptmScores.addPtmScoring(modName, ptmScoring);
                    }
                    if (secondaryP < refP) {
                        secondaryP = refP;
                    }
                    double deltaScore = (secondaryP - refP) * 100;
                    ptmScoring.setDeltaScore(modSite, deltaScore);
                }

                spectrumMatch.addUrParam(ptmScores);
                identification.updateSpectrumMatch(spectrumMatch);
            }
        }
    }

    /**
     * Attaches the selected probabilistic PTM score.
     *
     * @param spectrumMatch the spectrum match studied, the A-score will be
     * calculated for the best assumption
     * @param searchParameters the identification parameters
     * @param annotationPreferences the annotation preferences
     * @throws Exception exception thrown whenever an error occurred while
     * computing the score
     */
    private void attachProbabilisticScore(SpectrumMatch spectrumMatch, SearchParameters searchParameters, AnnotationPreferences annotationPreferences, PTMScoringPreferences scoringPreferences) throws Exception {

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        ModificationProfile ptmProfile = searchParameters.getModificationProfile();

        PSPtmScores ptmScores = new PSPtmScores();
        if (spectrumMatch.getUrParam(new PSPtmScores()) != null) {
            ptmScores = (PSPtmScores) spectrumMatch.getUrParam(new PSPtmScores());
        }

        HashMap<Double, ArrayList<PTM>> modifications = new HashMap<Double, ArrayList<PTM>>();
        HashMap<Double, Integer> nMod = new HashMap<Double, Integer>();
        HashMap<Double, ModificationMatch> modificationMatches = new HashMap<Double, ModificationMatch>();
        Peptide peptide = spectrumMatch.getBestPeptideAssumption().getPeptide();

        for (ModificationMatch modificationMatch : peptide.getModificationMatches()) {
            if (modificationMatch.isVariable()) {
                PTM refPTM = ptmFactory.getPTM(modificationMatch.getTheoreticPtm());
                double ptmMass = refPTM.getMass();
                if (!modifications.containsKey(ptmMass)) {
                    ArrayList<PTM> ptms = new ArrayList<PTM>();
                    for (String ptm : ptmProfile.getSimilarNotFixedModifications(ptmMass)) {
                        ptms.add(ptmFactory.getPTM(ptm));
                    }
                    modifications.put(ptmMass, ptms);
                    nMod.put(ptmMass, 1);
                } else {
                    nMod.put(ptmMass, nMod.get(ptmMass) + 1);
                }
                modificationMatches.put(ptmMass, modificationMatch);
            }
        }

        if (!modifications.isEmpty()) {

            MSnSpectrum spectrum = (MSnSpectrum) spectrumFactory.getSpectrum(spectrumMatch.getKey());
            annotationPreferences.setCurrentSettings(spectrumMatch.getBestPeptideAssumption(), true, MATCHING_TYPE, searchParameters.getFragmentIonAccuracy());

            for (Double ptmMass : modifications.keySet()) {
                HashMap<Integer, Double> scores = null;
                if (scoringPreferences.getSelectedProbabilisticScore() == PtmScore.AScore && nMod.get(ptmMass) == 1) {
                    scores = AScore.getAScore(peptide, modifications.get(ptmMass), spectrum, annotationPreferences.getIonTypes(),
                            annotationPreferences.getNeutralLosses(), annotationPreferences.getValidatedCharges(),
                            spectrumMatch.getBestPeptideAssumption().getIdentificationCharge().value,
                            searchParameters.getFragmentIonAccuracy(), scoringPreferences.isProbabilisticScoreNeutralLosses(), MATCHING_TYPE);
                } else if (scoringPreferences.getSelectedProbabilisticScore() == PtmScore.PhosphoRS) {
                    scores = PhosphoRS.getSequenceProbabilities(peptide, modifications.get(ptmMass), spectrum, annotationPreferences.getIonTypes(),
                            annotationPreferences.getNeutralLosses(), annotationPreferences.getValidatedCharges(),
                            spectrumMatch.getBestPeptideAssumption().getIdentificationCharge().value,
                            searchParameters.getFragmentIonAccuracy(), scoringPreferences.isProbabilisticScoreNeutralLosses(), MATCHING_TYPE);
                }
                if (scores != null) {
                    // remap to searched PTMs
                    PTM mappedModification = null;
                    for (int site : scores.keySet()) {
                        for (PTM ptm : modifications.get(ptmMass)) {
                            if (peptide.getPotentialModificationSites(ptm, MATCHING_TYPE, searchParameters.getFragmentIonAccuracy()).contains(site)) {
                                mappedModification = ptm;
                                break;
                            }
                        }
                        if (mappedModification == null) {
                            throw new IllegalArgumentException("Could not map the PTM of mass " + ptmMass + " at site " + site + " in peptide " + peptide.getSequence() + ".");
                        }

                        String ptmName = mappedModification.getName();

                        PtmScoring ptmScoring = ptmScores.getPtmScoring(ptmName);

                        if (ptmScoring == null) {
                            ptmScoring = new PtmScoring(ptmName);
                        }

                        ptmScoring.setProbabilisticScore(site, scores.get(site));

                        ptmScores.addPtmScoring(ptmName, ptmScoring);
                    }
                }
            }

            spectrumMatch.addUrParam(ptmScores);
            identification.updateSpectrumMatch(spectrumMatch);
        }
    }

    /**
     * Fills the peptide specific map.
     *
     * @param waitingHandler the handler displaying feedback to the user
     * @param searchParameters the search parameters
     * 
     * @throws Exception
     */
    private void fillPeptideMaps(WaitingHandler waitingHandler, SearchParameters searchParameters) throws Exception {

        waitingHandler.setWaitingText("Filling Peptide Maps. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        PSParameter psParameter = new PSParameter();

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(identification.getPeptideIdentification().size() * 2);

        ArrayList<String> foundModifications = new ArrayList<String>();
        HashMap<String, ArrayList<String>> fractionPsmMatches = new HashMap<String, ArrayList<String>>();

        // load the peptides into memory
        identification.loadPeptideMatches(identification.getPeptideIdentification(), waitingHandler); // @TODO: should speed up the process below?

        for (String peptideKey : identification.getPeptideIdentification()) {
            for (String modification : Peptide.getModificationFamily(peptideKey)) {
                if (!foundModifications.contains(modification)) {
                    foundModifications.add(modification);
                }
            }

            double probaScore = 1;
            HashMap<String, Double> fractionScores = new HashMap<String, Double>();
            PeptideMatch peptideMatch = identification.getPeptideMatch(peptideKey);

            // get the fraction scores
            identification.loadSpectrumMatchParameters(peptideMatch.getSpectrumMatches(), psParameter, null);
            for (String spectrumKey : peptideMatch.getSpectrumMatches()) {
                psParameter = (PSParameter) identification.getSpectrumMatchParameter(spectrumKey, psParameter);
                probaScore = probaScore * psParameter.getPsmProbability();
                String fraction = Spectrum.getSpectrumFile(spectrumKey);

                if (!fractionScores.containsKey(fraction)) {
                    fractionScores.put(fraction, 1.0);
                }

                fractionScores.put(fraction, fractionScores.get(fraction) * psParameter.getPsmProbability());

                if (!fractionPsmMatches.containsKey(fraction + "_" + peptideKey)) {
                    ArrayList<String> spectrumMatches = new ArrayList<String>(1);
                    spectrumMatches.add(spectrumKey);
                    fractionPsmMatches.put(fraction + "_" + peptideKey, spectrumMatches);
                } else {
                    fractionPsmMatches.get(fraction + "_" + peptideKey).add(spectrumKey);
                }
            }

            psParameter = new PSParameter();
            psParameter.setPeptideProbabilityScore(probaScore);
            psParameter.setSpecificMapKey(peptideMap.getKey(peptideMatch));

            // set the fraction scores
            for (String fractionName : fractionScores.keySet()) {
                psParameter.setFractionScore(fractionName, fractionScores.get(fractionName));
            }

            identification.addPeptideMatchParameter(peptideKey, psParameter); // @TODO: batch insertion?
            peptideMap.addPoint(probaScore, peptideMatch, searchParameters.getFragmentIonAccuracy());

            waitingHandler.increaseSecondaryProgressCounter();

            if (waitingHandler.isRunCanceled()) {
                return;
            }
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);

        // set the fraction psm matches
        metrics.setFractionPsmMatches(fractionPsmMatches);

        // set the ptms
        metrics.setFoundModifications(foundModifications);
    }

    /**
     * Attaches the peptide posterior error probabilities to the peptide
     * matches.
     *
     * @param waitingHandler
     */
    private void attachPeptideProbabilities(WaitingHandler waitingHandler) throws SQLException, IOException, ClassNotFoundException, InterruptedException {

        waitingHandler.setWaitingText("Attaching Peptide Probabilities. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        PSParameter psParameter = new PSParameter();

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(identification.getPeptideIdentification().size());

        identification.loadPeptideMatchParameters(psParameter, null);
        for (String peptideKey : identification.getPeptideIdentification()) {
            psParameter = (PSParameter) identification.getPeptideMatchParameter(peptideKey, psParameter);
            if (sequenceFactory.concatenatedTargetDecoy()) {
                psParameter.setPeptideProbability(peptideMap.getProbability(psParameter.getSpecificMapKey(), psParameter.getPeptideProbabilityScore()));
            } else {
                psParameter.setPeptideProbability(1.0);
            }
            for (String fraction : psParameter.getFractions()) {
                if (sequenceFactory.concatenatedTargetDecoy()) {
                    psParameter.setFractionPEP(fraction, peptideMap.getProbability(psParameter.getSpecificMapKey(), psParameter.getFractionScore(fraction)));
                } else {
                    psParameter.setFractionPEP(fraction, 1.0);
                }
            }
            identification.updatePeptideMatchParameter(peptideKey, psParameter); // @TODO: batch insert?
            waitingHandler.increaseSecondaryProgressCounter();
            if (waitingHandler.isRunCanceled()) {
                return;
            }
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Fills the protein map.
     *
     * @param waitingHandler the handler displaying feedback to the user
     */
    private void fillProteinMap(WaitingHandler waitingHandler) throws Exception {

        waitingHandler.setWaitingText("Filling Protein Map. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        PSParameter psParameter = new PSParameter();

        int max = identification.getProteinIdentification().size();

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(max);

        identification.loadPeptideMatchParameters(psParameter, null);
        identification.loadProteinMatches(null);

        for (String proteinKey : identification.getProteinIdentification()) {

            waitingHandler.increaseSecondaryProgressCounter();
            if (waitingHandler.isRunCanceled()) {
                return;
            }

            HashMap<String, Double> fractionScores = new HashMap<String, Double>();
            double probaScore = 1;
            ProteinMatch proteinMatch = identification.getProteinMatch(proteinKey);

            if (proteinMatch == null) {
                throw new IllegalArgumentException("Protein match " + proteinKey + " not found.");
            }

            // get the fraction scores
            identification.loadPeptideMatchParameters(proteinMatch.getPeptideMatches(), psParameter, null); // @TODO: already covered by the loadPeptideMatchParameters call above?
            for (String peptideKey : proteinMatch.getPeptideMatches()) {

                psParameter = (PSParameter) identification.getPeptideMatchParameter(peptideKey, psParameter);
                probaScore = probaScore * psParameter.getPeptideProbability();

                for (String fraction : psParameter.getFractions()) {
                    if (!fractionScores.containsKey(fraction)) {
                        fractionScores.put(fraction, 1.0);
                    }

                    fractionScores.put(fraction, fractionScores.get(fraction) * psParameter.getFractionPEP(fraction));
                }
            }

            psParameter = new PSParameter();
            psParameter.setProteinProbabilityScore(probaScore);

            // set the fraction scores
            for (String fractionName : fractionScores.keySet()) {
                psParameter.setFractionScore(fractionName, fractionScores.get(fractionName));
            }

            identification.addProteinMatchParameter(proteinKey, psParameter); // @TODO: batch insertion?
            proteinMap.addPoint(probaScore, proteinMatch.isDecoy());
        }

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Attaches the protein posterior error probability to the protein matches.
     *
     * @param waitingHandler the handler displaying feedback to the user
     * @param processingPreferences
     */
    private void attachProteinProbabilities(WaitingHandler waitingHandler, ProcessingPreferences processingPreferences) throws SQLException, IOException, ClassNotFoundException, InterruptedException {

        waitingHandler.setWaitingText("Attaching Protein Probabilities. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(identification.getProteinIdentification().size());

        PSParameter psParameter = new PSParameter();
        HashMap<String, ArrayList<Double>> fractionMW = new HashMap<String, ArrayList<Double>>();

        for (String proteinKey : identification.getProteinIdentification()) {

            //@TODO: this molecular weigth stuff shall not be done here!
            ProteinMatch proteinMatch = identification.getProteinMatch(proteinKey);
            Double proteinMW = sequenceFactory.computeMolecularWeight(proteinMatch.getMainMatch());

            psParameter = (PSParameter) identification.getProteinMatchParameter(proteinKey, psParameter);
            if (sequenceFactory.concatenatedTargetDecoy()) {
                double proteinProbability = proteinMap.getProbability(psParameter.getProteinProbabilityScore());
                psParameter.setProteinProbability(proteinProbability);
            } else {
                psParameter.setProteinProbability(1.0);
            }

            for (String fraction : psParameter.getFractions()) {
                if (sequenceFactory.concatenatedTargetDecoy()) {
                    psParameter.setFractionPEP(fraction, proteinMap.getProbability(psParameter.getFractionScore(fraction)));
                } else {
                    psParameter.setFractionPEP(fraction, 1.0);
                }

                // set the fraction molecular weights
                if (!proteinMatch.isDecoy() && psParameter.getFractionConfidence(fraction) > processingPreferences.getProteinConfidenceMwPlots()) {
                    if (fractionMW.containsKey(fraction)) {
                        fractionMW.get(fraction).add(proteinMW);
                    } else {
                        ArrayList<Double> mw = new ArrayList<Double>();
                        mw.add(proteinMW);
                        fractionMW.put(fraction, mw);
                    }
                }
            }

            identification.updateProteinMatchParameter(proteinKey, psParameter);
            waitingHandler.increaseSecondaryProgressCounter();

            if (waitingHandler.isRunCanceled()) {
                return;
            }
        }

        // set the observed fractional molecular weights per fraction
        metrics.setObservedFractionalMassesAll(fractionMW);

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
    }

    /**
     * Reduce artifact groups due to non-enzymatic mapping or proteins with
     * little evidence.
     *
     * @param searchParameters the search parameters used for the search
     * @param waitingHandler the handler displaying feedback to the user
     *
     * @throws Exception exception thrown whenever it is attempted to attach two
     * different spectrum matches to the same spectrum from the same search
     * engine.
     * @throws IOException
     * @throws IllegalArgumentException
     * @throws SQLException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     */
    private void removeIrrelevantGroups(SearchParameters searchParameters, WaitingHandler waitingHandler) throws IOException, IllegalArgumentException, SQLException, ClassNotFoundException, InterruptedException {

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        ArrayList<String> toRemove = new ArrayList<String>();
        int max = identification.getProteinIdentification().size();

        identification.loadProteinMatches(waitingHandler);

        if (waitingHandler != null) {
            waitingHandler.setWaitingText("Symplifying Protein Groups. Please Wait...");
            waitingHandler.setSecondaryProgressCounterIndeterminate(false);
            waitingHandler.setMaxSecondaryProgressCounter(max);
        }

        ArrayList<String> toDelete = new ArrayList<String>();
        HashMap<String, String> processedKeys = new HashMap<String, String>();

        for (String proteinSharedKey : identification.getProteinIdentification()) {
            if (ProteinMatch.getNProteins(proteinSharedKey) > 1) {
                if (!processedKeys.containsKey(proteinSharedKey)) {
                    String uniqueKey = getSubgroup(identification, proteinSharedKey, processedKeys, toDelete, searchParameters);
                    if (uniqueKey != null) {
                        mergeProteinGroups(identification, proteinSharedKey, uniqueKey, toDelete);
                        processedKeys.put(proteinSharedKey, uniqueKey);
                    } else {
                        processedKeys.put(proteinSharedKey, proteinSharedKey);
                    }
                }
                if (waitingHandler != null) {
                    if (waitingHandler.isRunCanceled()) {
                        return;
                    }
                    waitingHandler.increaseSecondaryProgressCounter();
                }
            }
        }

        if (enzymaticIssue + evidenceIssue + uncharacterizedIssue > 0) { // special case to not divide by zero

            if (waitingHandler != null) {
                waitingHandler.setWaitingText("Deleting Mapping Artifacts. Please Wait...");
                int enzymaticPercent = 100 * enzymaticIssue / (enzymaticIssue + evidenceIssue + uncharacterizedIssue);
                int evidencePercent = 100 * evidenceIssue / (enzymaticIssue + evidenceIssue + uncharacterizedIssue);
                int uncharacterizedPercent = 100 - enzymaticPercent - evidencePercent;
                waitingHandler.appendReport(toDelete.size() + " unlikely mappings found. (" + enzymaticPercent + "% non-enzymatic accessions, "
                        + evidencePercent + "% lower evidence accessions, " + uncharacterizedPercent + "% not characterized accessions)", true, true);
                waitingHandler.setSecondaryProgressCounterIndeterminate(false);
                waitingHandler.setMaxSecondaryProgressCounter(toRemove.size());
            }

            for (String proteinKey : toRemove) { // @TODO: nothing is ever added to this map..?
                identification.removeProteinMatch(proteinKey);
                if (waitingHandler != null) {
                    if (waitingHandler.isRunCanceled()) {
                        return;
                    }
                    waitingHandler.increaseSecondaryProgressCounter();
                }
            }
        }
    }

    /**
     * Returns the best subgroup of a protein key, null if none found. If
     * intermediate groups are found they will be processed. Processed keys are
     * stored in processedKeys. Keys to delete are stored in keysToDelete.
     * Returns null if no simpler group is found.
     *
     * @param identification the identification where to get the matches from.
     * @param sharedKey the key of the group to inspect
     * @param processedKeys map of already processed keys and their best smaller
     * key
     * @param keysToDelete list of keys to delete
     * @param searchParameters the search parameters
     *
     * @return the best smaller group, null if none found.
     *
     * @throws IllegalArgumentException
     * @throws SQLException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private String getSubgroup(Identification identification, String sharedKey, HashMap<String, String> processedKeys, ArrayList<String> keysToDelete, SearchParameters searchParameters)
            throws IllegalArgumentException, SQLException, IOException, ClassNotFoundException, InterruptedException {

        String[] sharedAccessions = ProteinMatch.getAccessions(sharedKey);
        ArrayList<String> candidateUnique = new ArrayList<String>();

        for (String accession : sharedAccessions) {
            for (String uniqueGroupCandidate : identification.getProteinMap().get(accession)) {
                if (ProteinMatch.contains(sharedKey, uniqueGroupCandidate) && !keysToDelete.contains(uniqueGroupCandidate)) {
                    String subGroup = uniqueGroupCandidate;
                    if (ProteinMatch.getNProteins(uniqueGroupCandidate) > 1) {
                        String reducedGroup = processedKeys.get(uniqueGroupCandidate);
                        if (reducedGroup == null) {
                            reducedGroup = getSubgroup(identification, uniqueGroupCandidate, processedKeys, keysToDelete, searchParameters);
                            if (reducedGroup != null) {
                                mergeProteinGroups(identification, uniqueGroupCandidate, reducedGroup, keysToDelete);
                                processedKeys.put(uniqueGroupCandidate, reducedGroup);
                                subGroup = reducedGroup;
                            } else {
                                processedKeys.put(uniqueGroupCandidate, uniqueGroupCandidate);
                            }
                        }
                    }
                    if (!candidateUnique.contains(subGroup)) {
                        candidateUnique.add(subGroup);
                    }
                }
            }
        }
        ArrayList<String> keys = new ArrayList<String>();
        for (String accession : candidateUnique) {
            if (!keysToDelete.contains(accession)) {
                keys.add(accession);
            }
        }
        String minimalKey = null;
        if (keys.size() > 1) {
            ProteinMatch match = identification.getProteinMatch(sharedKey);
            HashMap<String, Integer> preferenceReason = new HashMap<String, Integer>();
            for (String key1 : keys) {
                for (String accession1 : ProteinMatch.getAccessions(key1)) {
                    if (minimalKey == null) {
                        preferenceReason = new HashMap<String, Integer>();
                        boolean best = true;
                        for (String key2 : keys) {
                            if (!best) {
                                break;
                            } else if (!key1.equals(key2)) {
                                if (!ProteinMatch.contains(key1, key2)) {
                                    if (!ProteinMatch.getCommonProteins(key1, key2).isEmpty()) {
                                        best = false;
                                        break;
                                    }
                                    for (String accession2 : ProteinMatch.getAccessions(key2)) {
                                        int tempPrefernce = compareMainProtein(match, accession2, match, accession1, searchParameters);
                                        if (tempPrefernce != 1) {
                                            best = false;
                                            break;
                                        } else {
                                            if (preferenceReason.containsKey(accession2)) {
                                                tempPrefernce = Math.min(preferenceReason.get(accession2), tempPrefernce);
                                            }
                                            preferenceReason.put(accession2, tempPrefernce);
                                        }
                                    }
                                }
                            }
                        }
                        if (best) {
                            ArrayList<String> accessions = ProteinMatch.getOtherProteins(sharedKey, key1);
                            for (String accession2 : accessions) {
                                int tempPrefernce = compareMainProtein(match, accession2, match, accession1, searchParameters);
                                if (tempPrefernce == 0) {
                                    best = false;
                                    break;
                                } else {
                                    if (preferenceReason.containsKey(accession2)) {
                                        tempPrefernce = Math.min(preferenceReason.get(accession2), tempPrefernce);
                                    }
                                    preferenceReason.put(accession2, tempPrefernce);
                                }
                            }
                            if (best) {
                                minimalKey = key1;
                            }
                        }
                    } else {
                        break;
                    }
                }
                if (minimalKey != null) {
                    for (String key2 : keys) {
                        if (!key2.equals(minimalKey)) {
                            keysToDelete.add(key2);
                            for (int reason : preferenceReason.values()) {
                                if (reason == 1) {
                                    enzymaticIssue++;
                                }
                                if (reason == 2) {
                                    evidenceIssue++;
                                }
                                if (reason == 3) {
                                    uncharacterizedIssue++;
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }

        return minimalKey;
    }

    /**
     * Puts the peptide of the shared group in the unique group and adds the
     * shared group to the list of proteins to delete.
     *
     * @param identification the identification whether to get the matches
     * @param sharedGroup the key of the shared group
     * @param uniqueGroup the key of the unique group
     * @param keysToDelete list of keys to be deleted where sharedGroup will be
     * added
     *
     * @throws IllegalArgumentException
     * @throws SQLException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void mergeProteinGroups(Identification identification, String sharedGroup, String uniqueGroup, ArrayList<String> keysToDelete)
            throws IllegalArgumentException, SQLException, IOException, ClassNotFoundException, InterruptedException {

        ProteinMatch sharedMatch = identification.getProteinMatch(sharedGroup);
        ProteinMatch uniqueMatch = identification.getProteinMatch(uniqueGroup);

        for (String peptideKey : sharedMatch.getPeptideMatches()) {
            uniqueMatch.addPeptideMatch(peptideKey);
        }

        if (!keysToDelete.contains(sharedGroup)) {
            keysToDelete.add(sharedGroup);
        }
    }

    /**
     * Retains the best scoring of intricate groups.
     *
     * @param waitingHandler the handler displaying feedback to the user
     * @throws Exception exception thrown whenever it is attempted to attach two
     * different spectrum matches to the same spectrum from the same search
     * engine.
     * @throws IOException
     * @throws IllegalArgumentException
     * @throws SQLException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     */
    private void retainBestScoringGroups(SearchParameters searchParameters, WaitingHandler waitingHandler) throws IOException, IllegalArgumentException, SQLException, ClassNotFoundException, InterruptedException {

        waitingHandler.setWaitingText("Cleaning Protein Groups. Please Wait...");

        Identification identification = experiment.getAnalysisSet(sample).getProteomicAnalysis(replicateNumber).getIdentification(IdentificationMethod.MS2_IDENTIFICATION);
        PSParameter psParameter = new PSParameter();
        ArrayList<String> toRemove = new ArrayList<String>();
        int maxProteinKeyLength = 0;

        int max = 3 * identification.getProteinIdentification().size();

        identification.loadProteinMatchParameters(psParameter, null);
        identification.loadProteinMatches(waitingHandler);

        waitingHandler.setSecondaryProgressCounterIndeterminate(false);
        waitingHandler.setMaxSecondaryProgressCounter(max);

        for (String proteinSharedKey : identification.getProteinIdentification()) {
            if (ProteinMatch.getNProteins(proteinSharedKey) > 1) {
                psParameter = (PSParameter) identification.getProteinMatchParameter(proteinSharedKey, psParameter);
                double sharedProteinProbabilityScore = psParameter.getProteinProbabilityScore();
                boolean better = false;
                for (String accession : ProteinMatch.getAccessions(proteinSharedKey)) {
                    for (String proteinUniqueKey : identification.getProteinMap().get(accession)) {
                        if (ProteinMatch.contains(proteinSharedKey, proteinUniqueKey)) {
                            psParameter = (PSParameter) identification.getProteinMatchParameter(proteinUniqueKey, psParameter);
                            double uniqueProteinProbabilityScore = psParameter.getProteinProbabilityScore();
                            ProteinMatch proteinUnique = identification.getProteinMatch(proteinUniqueKey);
                            ProteinMatch proteinShared = identification.getProteinMatch(proteinSharedKey);
                            for (String sharedPeptideKey : proteinShared.getPeptideMatches()) {
                                proteinUnique.addPeptideMatch(sharedPeptideKey);
                            }
                            identification.updateProteinMatch(proteinUnique);
                            if (uniqueProteinProbabilityScore <= sharedProteinProbabilityScore) {
                                better = true;
                            }
                        }
                    }
                }
                if (better) {
                    toRemove.add(proteinSharedKey);
                } else {
                    waitingHandler.increaseSecondaryProgressCounter();
                    if (waitingHandler.isRunCanceled()) {
                        return;
                    }
                }
            }
        }

        for (String proteinKey : toRemove) {
            psParameter = (PSParameter) identification.getProteinMatchParameter(proteinKey, psParameter);
            proteinMap.removePoint(psParameter.getProteinProbabilityScore(), ProteinMatch.isDecoy(proteinKey));
            identification.removeProteinMatch(proteinKey);
            waitingHandler.increaseSecondaryProgressCounter();
        }

        int nSolved = toRemove.size();
        int nGroups = 0;
        int nLeft = 0;

        // As we go through all protein ids, keep the sorted list of proteins and maxima in the instance of the Metrics class to pass them to the GUI afterwards
        // proteins are sorted according to the protein score, then number of peptides (inverted), then number of spectra (inverted).
        HashMap<Double, HashMap<Integer, HashMap<Integer, ArrayList<String>>>> orderMap
                = new HashMap<Double, HashMap<Integer, HashMap<Integer, ArrayList<String>>>>();
        ArrayList<Double> scores = new ArrayList<Double>();
        PSParameter probabilities = new PSParameter();
        double maxMW = 0;

        identification.loadProteinMatches(null);
        for (String proteinKey : identification.getProteinIdentification()) {
            ProteinMatch proteinMatch = identification.getProteinMatch(proteinKey);

            if (!ProteinMatch.isDecoy(proteinKey)) {
                probabilities = (PSParameter) identification.getProteinMatchParameter(proteinKey, probabilities);
                double score = probabilities.getProteinProbabilityScore();
                int nPeptides = -proteinMatch.getPeptideMatches().size();
                int nSpectra = 0;

                Protein currentProtein = sequenceFactory.getProtein(proteinMatch.getMainMatch());

                if (currentProtein != null) {
                    double mw = sequenceFactory.computeMolecularWeight(proteinMatch.getMainMatch());
                    if (mw > maxMW) {
                        maxMW = mw;
                    }
                }

                identification.loadPeptideMatches(proteinMatch.getPeptideMatches(), null);
                for (String peptideKey : proteinMatch.getPeptideMatches()) {
                    PeptideMatch peptideMatch = identification.getPeptideMatch(peptideKey);
                    nSpectra -= peptideMatch.getSpectrumCount();
                }
                if (!orderMap.containsKey(score)) {
                    orderMap.put(score, new HashMap<Integer, HashMap<Integer, ArrayList<String>>>());
                    scores.add(score);
                }

                if (!orderMap.get(score).containsKey(nPeptides)) {
                    orderMap.get(score).put(nPeptides, new HashMap<Integer, ArrayList<String>>());
                }

                if (!orderMap.get(score).get(nPeptides).containsKey(nSpectra)) {
                    orderMap.get(score).get(nPeptides).put(nSpectra, new ArrayList<String>());
                }
                orderMap.get(score).get(nPeptides).get(nSpectra).add(proteinKey);

                // save the lenght of the longest protein accession number
                if (proteinMatch.getMainMatch().length() > maxProteinKeyLength) {
                    maxProteinKeyLength = proteinMatch.getMainMatch().length();
                }
            }

            ArrayList<String> accessions = new ArrayList<String>(Arrays.asList(ProteinMatch.getAccessions(proteinKey)));
            Collections.sort(accessions);
            String mainKey = accessions.get(0);

            if (accessions.size() > 1) {
                boolean similarityFound = false;
                boolean allSimilar = false;
                psParameter = (PSParameter) identification.getProteinMatchParameter(proteinKey, psParameter);
                for (String accession : accessions) {
                    if (compareMainProtein(proteinMatch, mainKey, proteinMatch, accession, searchParameters) > 0) {
                        mainKey = accession;
                    }
                }
                for (int i = 0; i < accessions.size() - 1; i++) {
                    for (int j = i + 1; j < accessions.size(); j++) {
                        if (getSimilarity(accessions.get(i), accessions.get(j))) {
                            similarityFound = true;
                            if (compareMainProtein(proteinMatch, mainKey, proteinMatch, accessions.get(j), searchParameters) > 0) {
                                mainKey = accessions.get(i);
                            }
                            break;
                        }
                    }
                    if (similarityFound) {
                        break;
                    }
                }
                if (similarityFound) {
                    allSimilar = true;
                    for (String key : accessions) {
                        if (!mainKey.equals(key)) {
                            if (!getSimilarity(mainKey, key)) {
                                allSimilar = false;
                                break;
                            }
                        }
                    }
                }
                if (!similarityFound) {
                    psParameter.setProteinInferenceClass(PSParameter.UNRELATED);
                    nGroups++;
                    nLeft++;
                    identification.updateProteinMatchParameter(proteinKey, psParameter);

                    identification.loadPeptideMatchParameters(proteinMatch.getPeptideMatches(), psParameter, null);
                    for (String peptideKey : proteinMatch.getPeptideMatches()) {
                        psParameter = (PSParameter) identification.getPeptideMatchParameter(peptideKey, psParameter);
                        psParameter.setProteinInferenceClass(PSParameter.UNRELATED);
                        identification.updatePeptideMatchParameter(peptideKey, psParameter);
                    }

                } else if (!allSimilar) {
                    psParameter.setProteinInferenceClass(PSParameter.RELATED_AND_UNRELATED);
                    nGroups++;
                    nSolved++;
                    identification.updateProteinMatchParameter(proteinKey, psParameter);

                    identification.loadPeptideMatchParameters(proteinMatch.getPeptideMatches(), psParameter, null);
                    for (String peptideKey : proteinMatch.getPeptideMatches()) {
                        psParameter = (PSParameter) identification.getPeptideMatchParameter(peptideKey, psParameter);
                        psParameter.setProteinInferenceClass(PSParameter.RELATED_AND_UNRELATED);
                        identification.updatePeptideMatchParameter(peptideKey, psParameter);
                    }

                } else {
                    psParameter.setProteinInferenceClass(PSParameter.RELATED);
                    nGroups++;
                    nSolved++;
                    identification.updateProteinMatchParameter(proteinKey, psParameter);

                    String mainMatch = proteinMatch.getMainMatch();
                    identification.loadPeptideMatches(proteinMatch.getPeptideMatches(), null);
                    identification.loadPeptideMatchParameters(proteinMatch.getPeptideMatches(), psParameter, null);
                    for (String peptideKey : proteinMatch.getPeptideMatches()) {
                        psParameter = (PSParameter) identification.getPeptideMatchParameter(peptideKey, psParameter);
                        PeptideMatch peptideMatch = identification.getPeptideMatch(peptideKey);
                        boolean unrelated = false;
                        for (String proteinAccession : peptideMatch.getTheoreticPeptide().getParentProteins(MATCHING_TYPE, searchParameters.getFragmentIonAccuracy())) {
                            if (!proteinKey.contains(proteinAccession)) {
                                if (!getSimilarity(mainMatch, proteinAccession)) {
                                    unrelated = true;
                                    break;
                                }
                            }
                        }
                        if (unrelated) {
                            psParameter.setProteinInferenceClass(PSParameter.RELATED_AND_UNRELATED);
                        } else {
                            psParameter.setProteinInferenceClass(PSParameter.RELATED);
                        }
                        identification.updatePeptideMatchParameter(peptideKey, psParameter);
                    }
                }
            } else {
                String mainMatch = proteinMatch.getMainMatch();
                identification.loadPeptideMatches(proteinMatch.getPeptideMatches(), null);
                identification.loadPeptideMatchParameters(proteinMatch.getPeptideMatches(), psParameter, null);

                for (String peptideKey : proteinMatch.getPeptideMatches()) {
                    psParameter = (PSParameter) identification.getPeptideMatchParameter(peptideKey, psParameter);
                    PeptideMatch peptideMatch = identification.getPeptideMatch(peptideKey);
                    boolean unrelated = false;
                    boolean otherProtein = false;
                    for (String protein : peptideMatch.getTheoreticPeptide().getParentProteins(MATCHING_TYPE, searchParameters.getFragmentIonAccuracy())) {
                        if (!proteinKey.contains(protein)) {
                            otherProtein = true;
                            if (!getSimilarity(mainMatch, protein)) {
                                unrelated = true;
                                break;
                            }
                        }
                    }
                    if (otherProtein) {
                        psParameter.setProteinInferenceClass(PSParameter.RELATED);
                    }
                    if (unrelated) {
                        psParameter.setProteinInferenceClass(PSParameter.UNRELATED);
                    }
                    identification.updatePeptideMatchParameter(peptideKey, psParameter);
                }
            }

            if (ProteinMatch.getNProteins(proteinKey) > 1) {
                if (!proteinMatch.getMainMatch().equals(mainKey)) {
                    proteinMatch.setMainMatch(mainKey);
                    identification.updateProteinMatch(proteinMatch);
                }
            }

            waitingHandler.increaseSecondaryProgressCounter();
            if (waitingHandler.isRunCanceled()) {
                return;
            }
        }

        ArrayList<String> proteinList = new ArrayList<String>();
        ArrayList<Double> scoreList = new ArrayList<Double>(orderMap.keySet());
        Collections.sort(scoreList);
        int maxPeptides = 0;
        int maxSpectra = 0;

        for (double currentScore : scoreList) {
            ArrayList<Integer> nPeptideList = new ArrayList<Integer>(orderMap.get(currentScore).keySet());
            Collections.sort(nPeptideList);
            if (nPeptideList.get(0) < maxPeptides) {
                maxPeptides = nPeptideList.get(0);
            }
            for (int currentNPeptides : nPeptideList) {
                ArrayList<Integer> nPsmList = new ArrayList<Integer>(orderMap.get(currentScore).get(currentNPeptides).keySet());
                Collections.sort(nPsmList);
                if (nPsmList.get(0) < maxSpectra) {
                    maxSpectra = nPsmList.get(0);
                }
                for (int currentNPsms : nPsmList) {
                    ArrayList<String> tempList = orderMap.get(currentScore).get(currentNPeptides).get(currentNPsms);
                    Collections.sort(tempList);
                    proteinList.addAll(tempList);

                    waitingHandler.increaseSecondaryProgressCounter(tempList.size());
                    if (waitingHandler.isRunCanceled()) {
                        return;
                    }
                }
            }
        }

        metrics.setProteinKeys(proteinList);
        metrics.setMaxNPeptides(-maxPeptides);
        metrics.setMaxNSpectra(-maxSpectra);
        metrics.setMaxMW(maxMW);
        metrics.setMaxProteinKeyLength(maxProteinKeyLength);

        waitingHandler.setSecondaryProgressCounterIndeterminate(true);
        waitingHandler.appendReport(nSolved + " conflicts resolved. " + nGroups + " protein groups remaining (" + nLeft + " suspicious).", true, true);
    }

    /**
     * Parses a protein description retaining only words longer than 3
     * characters.
     *
     * @param proteinAccession the accession of the inspected protein
     * @return description words longer than 3 characters
     */
    private ArrayList<String> parseDescription(String proteinAccession) throws IOException, IllegalArgumentException, InterruptedException, ClassNotFoundException {
        String description = sequenceFactory.getHeader(proteinAccession).getSimpleProteinDescription();

        if (description == null) {
            return new ArrayList<String>();
        }

        ArrayList<String> result = new ArrayList<String>();
        for (String component : description.split(" ")) {
            if (component.length() > 3) {
                result.add(component);
            }
        }
        return result;
    }

    /**
     * Checks whether a new main protein (newAccession) of the new protein match
     * (newProteinMatch) is better than another one main protein (oldAccession)
     * of another protein match (oldProteinMatch). First checks the protein
     * evidence level (if available), if not there then checks the protein
     * description and peptide enzymaticity.
     *
     *
     * @param oldProteinMatch the protein match of oldAccession
     * @param oldAccession the accession of the old protein
     * @param newProteinMatch the protein match of newAccession
     * @param newAccession the accession of the new protein
     * @param searchParameters the parameters used for the identification
     *
     * @return the product of the comparison: 1 better enzymaticity 2: better
     * evidence 3: better characterization 0: equal or not better
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws IllegalArgumentException
     */
    private int compareMainProtein(ProteinMatch oldProteinMatch, String oldAccession, ProteinMatch newProteinMatch, String newAccession, SearchParameters searchParameters) throws IOException, InterruptedException, IllegalArgumentException, ClassNotFoundException {

        Enzyme enzyme = searchParameters.getEnzyme();
        if (enzyme != null && !enzyme.isSemiSpecific()) { // null enzymes should not occur, but could happen with old search param files

            // @TODO: could semi-specific, top-down, whole protein, and non enzyme be handled better??
            double ms2tolerance = searchParameters.getFragmentIonAccuracy();
            boolean newEnzymatic = newProteinMatch.hasEnzymaticPeptide(newAccession, enzyme, MATCHING_TYPE, ms2tolerance);
            boolean oldEnzymatic = oldProteinMatch.hasEnzymaticPeptide(oldAccession, enzyme, MATCHING_TYPE, ms2tolerance);
            if (newEnzymatic && !oldEnzymatic) {
                return 1;
            } else if (!newEnzymatic && oldEnzymatic) {
                return 0;
            }
        }

        String evidenceLevelOld = sequenceFactory.getHeader(oldAccession).getProteinEvidence();
        String evidenceLevelNew = sequenceFactory.getHeader(newAccession).getProteinEvidence();

        if (evidenceLevelOld != null && evidenceLevelNew != null) {

            // compare protein evidence levels
            try {
                Integer levelOld = new Integer(evidenceLevelOld);
                Integer levelNew = new Integer(evidenceLevelNew);
                if (levelNew < levelOld) {
                    return 2;
                } else if (levelOld < levelNew) {
                    return 0;
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // protein evidence level missing, compare descriptions instead
        String oldDescription = sequenceFactory.getHeader(oldAccession).getSimpleProteinDescription();
        String newDescription = sequenceFactory.getHeader(newAccession).getSimpleProteinDescription();

        // note: this most likely means that we have a problem with the parsing of the db, but better than a null pointer...
        if (oldDescription == null) {
            return 0;
        }

        boolean oldUncharacterized = false,
                newUncharacterized = false;
        String[] keyWords = {"Uncharacterized", "putative"};
        for (String keyWord : keyWords) {
            if (newDescription.toLowerCase().contains(keyWord)) {
                newUncharacterized = true;
            }
            if (oldDescription.toLowerCase().contains(keyWord)) {
                oldUncharacterized = true;
            }
        }
        if (oldUncharacterized && !newUncharacterized) {
            return 3;
        } else if (!oldUncharacterized && newUncharacterized) {
            return 0;
        }

        return 0;
    }

    /**
     * Simplistic method comparing protein similarity. Returns true if both
     * proteins come from the same gene or if the descriptions are of same
     * length and present more than half similar words.
     *
     * @param primaryProteinAccession accession number of the first protein
     * @param secondaryProteinAccession accession number of the second protein
     * @return a boolean indicating whether the proteins are similar
     */
    private boolean getSimilarity(String primaryProteinAccession, String secondaryProteinAccession) throws IOException, IllegalArgumentException, InterruptedException, ClassNotFoundException {

        String geneNamePrimaryProtein = sequenceFactory.getHeader(primaryProteinAccession).getGeneName();
        String geneNameSecondaryProtein = sequenceFactory.getHeader(secondaryProteinAccession).getGeneName();
        boolean sameGene = false;

        // compare the gene names
        if (geneNamePrimaryProtein != null && geneNameSecondaryProtein != null) {
            sameGene = geneNamePrimaryProtein.equalsIgnoreCase(geneNameSecondaryProtein);
        }

        if (sameGene) {
            return true;
        } else {

            // compare gene names, similar gene names often means related proteins, like CPNE3 and CPNE2
            if (geneNamePrimaryProtein != null && geneNameSecondaryProtein != null) {

                // one gene name is a substring of the other, for example: EEF1A1 and EEF1A1P5
                if (geneNamePrimaryProtein.contains(geneNameSecondaryProtein) || geneNameSecondaryProtein.contains(geneNamePrimaryProtein)) {
                    return true;
                }

                // equal but for the last character, for example: CPNE3 and CPNE2
                if ((geneNameSecondaryProtein.length() > 2 && geneNamePrimaryProtein.contains(geneNameSecondaryProtein.substring(0, geneNameSecondaryProtein.length() - 2)))
                        || (geneNamePrimaryProtein.length() > 2 && geneNameSecondaryProtein.contains(geneNamePrimaryProtein.substring(0, geneNamePrimaryProtein.length() - 2)))) {
                    return true;
                }

                // equal but for the two last characters, for example: CPNE11 and CPNE12
                if ((geneNameSecondaryProtein.length() > 3 && geneNamePrimaryProtein.contains(geneNameSecondaryProtein.substring(0, geneNameSecondaryProtein.length() - 3)))
                        || (geneNamePrimaryProtein.length() > 3 && geneNameSecondaryProtein.contains(geneNamePrimaryProtein.substring(0, geneNamePrimaryProtein.length() - 3)))) {
                    return true;
                }

                // @TODO: support more complex gene families?
            }

            // compare the protein descriptions, less secure than gene names
            ArrayList<String> primaryDescription = parseDescription(primaryProteinAccession);
            ArrayList<String> secondaryDescription = parseDescription(secondaryProteinAccession);

            if (primaryDescription.size() > secondaryDescription.size()) {
                int nMatch = 0;
                for (int i = 0; i < secondaryDescription.size(); i++) {
                    if (primaryDescription.contains(secondaryDescription.get(i))) {
                        nMatch++;
                    }
                }
                return nMatch >= secondaryDescription.size() / 2;
            } else {
                int nMatch = 0;
                for (int i = 0; i < primaryDescription.size(); i++) {
                    if (secondaryDescription.contains(primaryDescription.get(i))) {
                        nMatch++;
                    }
                }
                return nMatch >= primaryDescription.size() / 2;
            }
        }
    }

    /**
     * Returns the metrics picked-up while loading the files.
     *
     * @return the metrics picked-up while loading the files
     */
    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * Returns the identification features generator used when loading the
     * files.
     *
     * @return the identification features generator used when loading the files
     */
    public IdentificationFeaturesGenerator getIdentificationFeaturesGenerator() {
        return identificationFeaturesGenerator;
    }

    /**
     * Sets the protein count map
     *
     * @param proteinCount the protein count map
     */
    public void setProteinCountMap(HashMap<String, Integer> proteinCount) {
        this.proteinCount = proteinCount;
    }

    /**
     * Adds a warning to the feedback list. If a feedback with the same title is
     * already loaded it will be ignored.
     *
     * @param feedback the feedback
     */
    public void addWarning(FeedBack feedback) {
        warnings.put(feedback.getTitle(), feedback);
    }

    /**
     * Returns the warnings.
     *
     * @return the warnings
     */
    public HashMap<String, FeedBack> getWarnings() {
        return warnings;
    }

    /**
     * Verifies that the modifications backed-up in the search parameters are
     * loaded and returns an error message if one was already loaded, null
     * otherwise.
     *
     * @param searchParameters the search parameters to load
     * @return an error message if one was already loaded, null otherwise
     */
    public static String loadModifications(SearchParameters searchParameters) {
        String error = null;
        ArrayList<String> toCheck = PTMFactory.getInstance().loadBackedUpModifications(searchParameters, true);
        if (!toCheck.isEmpty()) {
            error = "The definition of the following PTM(s) seems to have change and was overwritten:\n";
            for (int i = 0; i < toCheck.size(); i++) {
                if (i > 0) {
                    if (i < toCheck.size() - 1) {
                        error += ", ";
                    } else {
                        error += " and ";
                    }
                    error += toCheck.get(i);
                }
            }
            error += ".\nPlease verify the definition of the PTM(s) in the modifications editor.";
        }
        return error;
    }

    /**
     * Returns the default experiment file.
     *
     * @return the default experiment file
     */
    public static File getDefaultExperimentFile() {
        return new File(PeptideShaker.SERIALIZATION_DIRECTORY, PeptideShaker.experimentObjectName);
    }
}
