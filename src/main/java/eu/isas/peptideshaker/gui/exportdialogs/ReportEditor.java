package eu.isas.peptideshaker.gui.exportdialogs;

import eu.isas.peptideshaker.export.ExportFactory;
import eu.isas.peptideshaker.export.ExportFeature;
import eu.isas.peptideshaker.export.ExportScheme;
import eu.isas.peptideshaker.export.exportfeatures.AnnotationFeatures;
import eu.isas.peptideshaker.export.exportfeatures.InputFilterFeatures;
import eu.isas.peptideshaker.export.exportfeatures.PeptideFeatures;
import eu.isas.peptideshaker.export.exportfeatures.ProjectFeatures;
import eu.isas.peptideshaker.export.exportfeatures.ProteinFeatures;
import eu.isas.peptideshaker.export.exportfeatures.PsmFeatures;
import eu.isas.peptideshaker.export.exportfeatures.PtmScoringFeatures;
import eu.isas.peptideshaker.export.exportfeatures.SearchFeatures;
import eu.isas.peptideshaker.export.exportfeatures.SpectrumCountingFeatures;
import eu.isas.peptideshaker.export.exportfeatures.ValidationFeatures;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import javax.swing.table.DefaultTableModel;

/**
 * Dialog for editing reports.
 * 
 * @author Marc Vaudel
 */
public class ReportEditor extends javax.swing.JDialog {

    /**
     * The export factory.
     */
    private ExportFactory exportFactory = ExportFactory.getInstance();
    /**
     * The original name of the report.
     */
    private String oldName;
    /**
     * A boolean indicating whether the report can be edited.
     */
    private boolean editable = true;
    /**
     * The original selection.
     */
    private HashMap<String, ArrayList<ExportFeature>> selection = new HashMap<String, ArrayList<ExportFeature>>();
    /**
     * The selected section name.
     */
    private String sectionName = null;
    /**
     * The list of implemented features for the selected section.
     */
    private ArrayList<ExportFeature> featuresList;

    /**
     * Constructor.
     *
     * @param parent the parent frame
     * @param exportSchemeName the name of the export scheme to edit
     * @param editable  
     */
    public ReportEditor(java.awt.Frame parent, String exportSchemeName, boolean editable) {
        super(parent, true);
        initComponents();
        oldName = exportSchemeName;
        this.editable = editable;
        selection = new HashMap<String, ArrayList<ExportFeature>>();
        ExportScheme exportScheme = exportFactory.getExportScheme(exportSchemeName);
        for (String section : exportScheme.getSections()) {
            if (!selection.containsKey(section)) {
                selection.put(section, new ArrayList<ExportFeature>());
            }
            selection.get(section).addAll(exportScheme.getExportFeatures(section));
        }
        setUpGUI();
        this.setLocationRelativeTo(parent);
        setVisible(true);
    }

    /**
     * Constructor.
     *
     * @param parent the parent frame
     */
    public ReportEditor(java.awt.Frame parent) {
        super(parent, true);
        initComponents();
        setUpGUI();
        this.setLocationRelativeTo(parent);
        setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        nameTxt = new javax.swing.JTextField();
        jPanel1 = new javax.swing.JPanel();
        mainTitleTxt = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        sectionsTable = new javax.swing.JTable();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        featuresTable = new javax.swing.JTable();
        maintTitleCheckBox = new javax.swing.JCheckBox();
        sectionTitleCheckBox = new javax.swing.JCheckBox();
        separationLinesSpinner = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        separatorTxt = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        lineNumberCheckBox = new javax.swing.JCheckBox();
        headerCheckBox = new javax.swing.JCheckBox();
        cancelButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jLabel1.setText("Name of the report type:");

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Report structure"));

        mainTitleTxt.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        mainTitleTxt.setEnabled(false);

        sectionsTable.setModel(new SectionsTableModel());
        sectionsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                sectionsTableMouseReleased(evt);
            }
        });
        jScrollPane1.setViewportView(sectionsTable);

        jLabel3.setText("Sections:");

        jLabel4.setText("Section content:");

        featuresTable.setModel(new FeaturesTableModel());
        jScrollPane2.setViewportView(featuresTable);

        maintTitleCheckBox.setText("Include title:");
        maintTitleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                maintTitleCheckBoxActionPerformed(evt);
            }
        });

        sectionTitleCheckBox.setText("Include section titles");
        sectionTitleCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sectionTitleCheckBoxActionPerformed(evt);
            }
        });

        separationLinesSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(0), Integer.valueOf(0), null, Integer.valueOf(1)));
        separationLinesSpinner.setEnabled(false);

        jLabel5.setText("# separation lines:");

        separatorTxt.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        separatorTxt.setText("\\t");

        jLabel2.setText("Column separator:");

        lineNumberCheckBox.setText("Include line number");

        headerCheckBox.setText("Include header");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(maintTitleCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(mainTitleTxt, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 101, Short.MAX_VALUE)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(separatorTxt, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel4)
                            .addComponent(jLabel3))
                        .addGap(323, 323, 323))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(sectionTitleCheckBox)
                            .addComponent(lineNumberCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(headerCheckBox)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(separationLinesSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(mainTitleTxt, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(maintTitleCheckBox)
                    .addComponent(separatorTxt, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sectionTitleCheckBox)
                    .addComponent(separationLinesSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lineNumberCheckBox)
                    .addComponent(headerCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        okButton.setText("OK");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(nameTxt))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(nameTxt, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(okButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * @TODO; add JavaDoc.
     * 
     * @param evt 
     */
    private void sectionTitleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sectionTitleCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sectionTitleCheckBoxActionPerformed

    /**
     * Close the dialog without saving.
     * 
     * @param evt 
     */
    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    /**
     * Save the export scheme and close the dialog.
     * 
     * @param evt 
     */
    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        if (editable) {
            String newName = nameTxt.getText().trim();
            if (oldName != null && !oldName.contains(newName)) {
                exportFactory.removeExportScheme(oldName);
            }
            ArrayList<ExportFeature> features = new ArrayList<ExportFeature>();
            for (ArrayList<ExportFeature> exportFeatures : selection.values()) {
                features.addAll(exportFeatures);
            }
            if (maintTitleCheckBox.isSelected()) {
                ExportScheme exportScheme = new ExportScheme(newName, true, features, separatorTxt.getText(), 
                        lineNumberCheckBox.isSelected(), headerCheckBox.isSelected(), (Integer) separationLinesSpinner.getValue(), 
                        sectionTitleCheckBox.isSelected(), mainTitleTxt.getText().trim());
                exportFactory.addExportScheme(exportScheme);
            } else {
                ExportScheme exportScheme = new ExportScheme(newName, true, features, separatorTxt.getText(), 
                        lineNumberCheckBox.isSelected(), headerCheckBox.isSelected(), (Integer) separationLinesSpinner.getValue(), 
                        sectionTitleCheckBox.isSelected());
                exportFactory.addExportScheme(exportScheme);
            }
        }
        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    /**
     * @TODO; add JavaDoc.
     * 
     * @param evt 
     */
    private void maintTitleCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_maintTitleCheckBoxActionPerformed
        mainTitleTxt.setEnabled(maintTitleCheckBox.isSelected());
    }//GEN-LAST:event_maintTitleCheckBoxActionPerformed

    /**
     * @TODO; add JavaDoc.
     * 
     * @param evt 
     */
    private void sectionsTableMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_sectionsTableMouseReleased
        sectionName = (String) sectionsTable.getValueAt(sectionsTable.getSelectedRow(), 1);
        updateFeatureTableContent();
    }//GEN-LAST:event_sectionsTableMouseReleased
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JTable featuresTable;
    private javax.swing.JCheckBox headerCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JCheckBox lineNumberCheckBox;
    private javax.swing.JTextField mainTitleTxt;
    private javax.swing.JCheckBox maintTitleCheckBox;
    private javax.swing.JTextField nameTxt;
    private javax.swing.JButton okButton;
    private javax.swing.JCheckBox sectionTitleCheckBox;
    private javax.swing.JTable sectionsTable;
    private javax.swing.JSpinner separationLinesSpinner;
    private javax.swing.JTextField separatorTxt;
    // End of variables declaration//GEN-END:variables

    /**
     * Sets up the GUI components.
     */
    private void setUpGUI() {

        nameTxt.setEnabled(editable);
        maintTitleCheckBox.setEnabled(editable);
        separatorTxt.setEnabled(editable);
        sectionTitleCheckBox.setEnabled(editable);
        separationLinesSpinner.setEnabled(editable);
        sectionsTable.setEnabled(editable);
        featuresTable.setEnabled(editable);
        lineNumberCheckBox.setEnabled(editable);
        headerCheckBox.setEnabled(editable);

        if (oldName != null) {
            nameTxt.setText(oldName);

            ExportScheme exportScheme = exportFactory.getExportScheme(oldName);
            if (exportScheme.getMainTitle() != null) {
                maintTitleCheckBox.setSelected(true);
                mainTitleTxt.setText(exportScheme.getMainTitle());
            }
            separatorTxt.setText(exportScheme.getSeparator());
            sectionTitleCheckBox.setSelected(exportScheme.isIncludeSectionTitles());
            separationLinesSpinner.setValue(exportScheme.getSeparationLines());
        }

    }

    /**
     * Indicates whether a feature has been selected in the given section.
     *
     * @param section the section of interest
     * @param exportFeature the export feature of interest
     * @return a boolean indicating whether a feature has been selected in the
     * given section
     */
    private boolean isSelected(String section, ExportFeature exportFeature) {
        ArrayList<ExportFeature> selectedSectionFeatures = selection.get(section);
        if (selectedSectionFeatures != null && selectedSectionFeatures.contains(exportFeature)) {
            return true;
        }
        return false;
    }

    /**
     * Sets whether a feature is selected in the given section.
     *
     * @param section the section of interest
     * @param exportFeature the feature of interest
     * @param selected a boolean indicating whether the feature shall be
     * selected or not
     */
    private void setSelected(String section, ExportFeature exportFeature, Boolean selected) {
        if (selected) {
            if (!selection.containsKey(section)) {
                selection.put(section, new ArrayList<ExportFeature>());
            }
            if (!selection.get(section).contains(exportFeature)) {
                selection.get(section).add(exportFeature);
            }
        } else {
            if (selection.containsKey(section)) {
                selection.get(section).remove(exportFeature);
                if (selection.get(section).isEmpty()) {
                    selection.remove(section);
                }
            }
        }
    }

    /**
     * Updates the feature table content based on the section name.
     */
    private void updateFeatureTableContent() {
        featuresList = new ArrayList<ExportFeature>();
        if (sectionName != null) {
            if (sectionName.equals(AnnotationFeatures.type)) {
                featuresList.addAll(Arrays.asList(AnnotationFeatures.values()));
            } else if (sectionName.equals(InputFilterFeatures.type)) {
                featuresList.addAll(Arrays.asList(InputFilterFeatures.values()));
            } else if (sectionName.equals(PeptideFeatures.type)) {
                featuresList.addAll(Arrays.asList(PeptideFeatures.values()));
            } else if (sectionName.equals(ProjectFeatures.type)) {
                featuresList.addAll(Arrays.asList(ProjectFeatures.values()));
            } else if (sectionName.equals(ProteinFeatures.type)) {
                featuresList.addAll(Arrays.asList(ProteinFeatures.values()));
            } else if (sectionName.equals(PsmFeatures.type)) {
                featuresList.addAll(Arrays.asList(PsmFeatures.values()));
            } else if (sectionName.equals(PtmScoringFeatures.type)) {
                featuresList.addAll(Arrays.asList(PtmScoringFeatures.values()));
            } else if (sectionName.equals(SearchFeatures.type)) {
                featuresList.addAll(Arrays.asList(SearchFeatures.values()));
            } else if (sectionName.equals(SpectrumCountingFeatures.type)) {
                featuresList.addAll(Arrays.asList(SpectrumCountingFeatures.values()));
            } else if (sectionName.equals(ValidationFeatures.type)) {
                featuresList.addAll(Arrays.asList(ValidationFeatures.values()));
            }
        }
        ((DefaultTableModel) featuresTable.getModel()).fireTableDataChanged();
    }

    /**
     * Table model for the reports table.
     */
    private class SectionsTableModel extends DefaultTableModel {

        /**
         * The list of implemented sections.
         */
        private ArrayList<String> sectionList;

        /**
         * Constructor.
         */
        public SectionsTableModel() {
            sectionList = new ArrayList<String>();
            sectionList.addAll(ExportFactory.getImplementedSections());
        }

        @Override
        public int getRowCount() {
            if (sectionList == null) {
                return 0;
            }
            return sectionList.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return " ";
                case 1:
                    return "Name";
                default:
                    return "";
            }
        }

        @Override
        public Object getValueAt(int row, int column) {
            switch (column) {
                case 0:
                    return row + 1;
                case 1:
                    return sectionList.get(row);
                default:
                    return "";
            }
        }

        @Override
        public Class getColumnClass(int columnIndex) {
            for (int i = 0; i < getRowCount(); i++) {
                if (getValueAt(i, columnIndex) != null) {
                    return getValueAt(i, columnIndex).getClass();
                }
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    /**
     * Table model for the reports table.
     */
    private class FeaturesTableModel extends DefaultTableModel {

        /**
         * Constructor
         */
        public FeaturesTableModel() {
        }

        @Override
        public int getRowCount() {
            if (featuresList == null) {
                return 0;
            }
            return featuresList.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return " ";
                case 1:
                    return "Name";
                case 2:
                    return "Description";
                default:
                    return "";
            }
        }

        @Override
        public Object getValueAt(int row, int column) {
            switch (column) {
                case 0:
                    return isSelected(sectionName, featuresList.get(row));
                case 1:
                    return featuresList.get(row).getTitle();
                case 2:
                    return featuresList.get(row).getDescription();
                default:
                    return "";
            }
        }

        @Override
        public Class getColumnClass(int columnIndex) {
            for (int i = 0; i < getRowCount(); i++) {
                if (getValueAt(i, columnIndex) != null) {
                    return getValueAt(i, columnIndex).getClass();
                }
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int row, int column) {
            setSelected(sectionName, featuresList.get(row), (Boolean) aValue);
        }
    }
}