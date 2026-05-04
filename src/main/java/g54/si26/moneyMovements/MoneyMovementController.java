package g54.si26.moneyMovements;

import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import g54.si26.DTOs.EnrollmentRecordDTO;
import g54.si26.DTOs.TeacherInvoiceDTO;
import g54.si26.DTOs.MoneyMovementDTO;
import g54.si26.utils.ApplicationException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MoneyMovementController {
    private MoneyMovementModel model;
    private MoneyMovementView view;
    private String simulatedDate;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public MoneyMovementController(MoneyMovementModel model, MoneyMovementView view) {
        this.model = model;
        this.view = view;
    }

    public void setSimulatedDate(String simulatedDate) {
        this.simulatedDate = simulatedDate;
    }

    public void initController() {
        view.getRdEnrollments().addActionListener(e -> updateTables());
        view.getRdInvoices().addActionListener(e -> updateTables());
        view.getRdHistory().addActionListener(e -> updateTables());
        
        view.getTableMain().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateHistoryTable();
        });

        view.getBtnRegister().addActionListener(e -> registerMovement());

        updateTables();
        view.getFrame().setVisible(true);
    }

    private void updateTables() {
        updateMainTable();
        updatePendingTable();
        updateHistoryTable();
    }

    private void updateMainTable() {
        DefaultTableModel tableModel;
        if (view.getRdEnrollments().isSelected()) {
            List<EnrollmentRecordDTO> list = model.getAllEnrollments();
            String[] columns = {"ID", "Course", "Professional", "Fee", "Net Balance", "State"};
            tableModel = new DefaultTableModel(columns, 0);
            for (EnrollmentRecordDTO e : list) {
                tableModel.addRow(new Object[]{e.getInscriptionId(), e.getCourseName(), e.getProfessionalName(), String.format("%.2f", e.getFee()), e.getNetBalance(), e.getState()});
            }
        } else if (view.getRdInvoices().isSelected()) {
            List<TeacherInvoiceDTO> list = model.getAllInvoices();
            String[] columns = {"ID", "Course", "Teacher", "Amount", "Net Balance", "Status"};
            tableModel = new DefaultTableModel(columns, 0);
            for (TeacherInvoiceDTO i : list) {
                tableModel.addRow(new Object[]{i.getInvoiceId(), i.getCourseName(), i.getTeacherName(), String.format("%.2f", i.getTotalAmount()), i.getNetBalance(), i.getStatus()});
            }
        } else {
            // Full History
            List<MoneyMovementDTO> list = model.getAllMovements();
            String[] columns = {"ID", "Amount", "Date", "Status", "Type", "Related To"};
            tableModel = new DefaultTableModel(columns, 0);
            for (MoneyMovementDTO m : list) {
                tableModel.addRow(new Object[]{m.getMovementId(), m.getAmount(), m.getMovementDate(), m.getStatus(), m.getType(), m.getRelatedTo()});
            }
        }
        view.getTableMain().setModel(tableModel);
        
        // Hide ID and Net Balance
        hideColumn(view.getTableMain(), 0); // ID
        if (!view.getRdHistory().isSelected()) {
            hideColumn(view.getTableMain(), 4); // Net Balance
        }
    }

    private void updatePendingTable() {
        DefaultTableModel tableModel;
        if (view.getRdHistory().isSelected()) {
            view.getTablePending().setModel(new DefaultTableModel());
            return;
        }

        if (view.getRdEnrollments().isSelected()) {
            List<EnrollmentRecordDTO> list = model.getEnrollmentsPendingCompensation();
            String[] columns = {"ID", "Professional", "Course", "Amount"};
            tableModel = new DefaultTableModel(columns, 0);
            for (EnrollmentRecordDTO e : list) {
                tableModel.addRow(new Object[]{e.getInscriptionId(), e.getProfessionalName(), e.getCourseName(), String.format("%.2f", e.getNetBalance())});
            }
        } else {
            List<TeacherInvoiceDTO> list = model.getInvoicesPendingCompensation();
            String[] columns = {"ID", "Teacher", "Course", "Amount"};
            tableModel = new DefaultTableModel(columns, 0);
            for (TeacherInvoiceDTO i : list) {
                tableModel.addRow(new Object[]{i.getInvoiceId(), i.getTeacherName(), i.getCourseName(), String.format("%.2f", i.getNetBalance())});
            }
        }
        view.getTablePending().setModel(tableModel);
        hideColumn(view.getTablePending(), 0); // ID
    }

    private void updateHistoryTable() {
        int row = view.getTableMain().getSelectedRow();
        if (row == -1) {
            view.getTableHistory().setModel(new DefaultTableModel());
            return;
        }

        if (view.getRdHistory().isSelected()) {
            view.getTableHistory().setModel(new DefaultTableModel());
            return;
        }

        int id = (int) view.getTableMain().getValueAt(row, 0);
        List<MoneyMovementDTO> list;
        if (view.getRdEnrollments().isSelected()) list = model.getMovementsByInscription(id);
        else list = model.getMovementsByInvoice(id);

        String[] columns = {"ID", "Amount", "Date", "Status", "Type"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0);
        for (MoneyMovementDTO m : list) {
            tableModel.addRow(new Object[]{m.getMovementId(), m.getAmount(), m.getMovementDate(), m.getStatus(), m.getType()});
        }
        view.getTableHistory().setModel(tableModel);
        hideColumn(view.getTableHistory(), 0); // ID
    }

    private void hideColumn(javax.swing.JTable table, int index) {
        if (table.getColumnCount() > index) {
            table.getColumnModel().getColumn(index).setMinWidth(0);
            table.getColumnModel().getColumn(index).setMaxWidth(0);
            table.getColumnModel().getColumn(index).setPreferredWidth(0);
        }
    }

    private void registerMovement() {
        if (view.getRdHistory().isSelected()) {
            JOptionPane.showMessageDialog(view.getFrame(), "Please select the 'Enrollments' or 'Invoices' tab to register a new movement.");
            return;
        }

        int row = view.getTableMain().getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(view.getFrame(), "Please select a record from the top table.");
            return;
        }

        List<String> errors = new java.util.ArrayList<>();
        List<String> warnings = new java.util.ArrayList<>();

        try {
            // Parse Amount
            double amount = 0;
            String amountStr = view.getTxtAmount().getText().trim().replace(",", ".");
            if (amountStr.isEmpty()) {
                errors.add("Amount is required.");
            } else {
                try {
                    amount = Double.parseDouble(amountStr);
                } catch (NumberFormatException nfe) {
                    errors.add("Amount must be a valid number.");
                }
            }

            // Parse Date
            String dateStr = view.getTxtDate().getText().trim();
            LocalDate moveDate = null;
            if (dateStr.isEmpty()) {
                errors.add("Date is required.");
            } else {
                try {
                    moveDate = LocalDate.parse(dateStr, FORMATTER);
                } catch (Exception ex) {
                    errors.add("Invalid date format. Use YYYY-MM-DD.");
                }
            }

            // Parse Simulated Date
            LocalDate sysDate = null;
            if (simulatedDate != null && !simulatedDate.isEmpty()) {
                try {
                    sysDate = LocalDate.parse(simulatedDate, FORMATTER);
                } catch (Exception ex) {
                    // Ignoramos, será manejado por el modelo o asumimos now() si hiciera falta.
                }
            }

            int id = (int) view.getTableMain().getValueAt(row, 0);

            // ==============================================================================
            // AQUI OCURRE LA MAGIA DE LA REFACTORIZACIÓN
            // Delegamos tooooda la lógica de negocio, validaciones complejas y reglas al MODELO
            // ==============================================================================
            if (errors.isEmpty()) {
                try {
                    if (view.getRdEnrollments().isSelected()) {
                        warnings = model.validateEnrollmentMovement(id, amount, moveDate, sysDate);
                    } else {
                        warnings = model.validateInvoiceMovement(id, amount, moveDate, sysDate);
                    }
                } catch (ApplicationException ae) {
                    errors.add(ae.getMessage());
                }
            }

            // Display errors if any
            if (!errors.isEmpty()) {
                StringBuilder sb = new StringBuilder("The following errors were detected:\\n\\n");
                for (String err : errors) sb.append(" - ").append(err).append("\\n");
                JOptionPane.showMessageDialog(view.getFrame(), sb.toString(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Display warnings if no errors
            if (!warnings.isEmpty()) {
                StringBuilder sb = new StringBuilder("The following warnings were detected:\\n\\n");
                for (String warn : warnings) sb.append(" - ").append(warn).append("\\n");
                sb.append("\\nDo you want to proceed anyway?");
                int choice = JOptionPane.showConfirmDialog(view.getFrame(), sb.toString(), "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) return;
            }

            // Si llegamos hasta aquí, la validación del modelo ha pasado limpiamente
            model.registerMovement(
                view.getRdEnrollments().isSelected() ? id : null, 
                view.getRdInvoices().isSelected() ? id : null, 
                amount, dateStr, "EXECUTED"
            );

            updateTables();
            JOptionPane.showMessageDialog(view.getFrame(), "Movement registered successfully.");
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(view.getFrame(), "Unexpected error: " + e.getMessage());
        }
    }
}
