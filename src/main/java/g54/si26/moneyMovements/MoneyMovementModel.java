package g54.si26.moneyMovements;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import g54.si26.DTOs.EnrollmentRecordDTO;
import g54.si26.DTOs.TeacherInvoiceDTO;
import g54.si26.DTOs.MoneyMovementDTO;
import g54.si26.utils.Database;
import g54.si26.utils.ApplicationException;
import g54.si26.utils.Util;

public class MoneyMovementModel {
    private Database db = new Database();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ====================================================================================
    // REFACTORIZACION PARA TESTING DE LOGICA DE NEGOCIO: Validaciones puras en el modelo
    // ====================================================================================

    public List<String> validateEnrollmentMovement(int inscriptionId, double amount, LocalDate moveDate, LocalDate simulatedDate) throws ApplicationException {
        List<String> warnings = new ArrayList<>();
        
        // DATE VALIDATION
        if (simulatedDate != null && moveDate.isAfter(simulatedDate)) {
            throw new ApplicationException("Movement date (" + moveDate + ") cannot be in the future (System date: " + simulatedDate + ").");
        }
        
        if (Math.abs(amount) < 0.001) {
            throw new ApplicationException("Amount cannot be zero.");
        }

        List<EnrollmentRecordDTO> all = getAllEnrollments();
        EnrollmentRecordDTO selected = all.stream().filter(e -> e.getInscriptionId() == inscriptionId).findFirst().orElse(null);
        
        if (selected == null) {
            throw new ApplicationException("Selected enrollment not found in database.");
        }

        if (amount > 0) {
            // Positive payment logic
            String rDateStr = selected.getRegistrationDate();
            if (rDateStr != null) {
                if (rDateStr.length() > 10) rDateStr = rDateStr.substring(0, 10);
                LocalDate regDate = LocalDate.parse(rDateStr, FORMATTER);
                
                if (moveDate.isBefore(regDate)) {
                    throw new ApplicationException("Movement date cannot be before inscription date (" + rDateStr + ").");
                }
                
                if (Util.isAfterTwoWorkingDays(regDate, moveDate)) {
                    throw new ApplicationException("Movement is more than 2 working days after inscription date (" + rDateStr + ").");
                }
            }
            
            // Overpayment warning: TotalPaid + amount > Fee
            if (selected.getNetBalance() + amount > selected.getFee() + 0.001) {
                warnings.add("This movement will result in an overpayment for the professional.");
            }
        } else if (amount < 0) {
            // Negative movement (compensation)
            double overpayment = selected.getNetBalance() - selected.getFee();
            if (overpayment <= 0.001) {
                throw new ApplicationException("Negative movements are only allowed for compensation (when an overpayment exists). Current overpayment is 0.00.");
            } else {
                double absAmount = Math.abs(amount);
                if (absAmount > overpayment + 0.001) {
                    throw new ApplicationException(String.format("Compensation movement (%.2f) cannot be greater than the overpaid amount (%.2f).", absAmount, overpayment));
                } else if (absAmount < overpayment - 0.001) {
                    warnings.add(String.format("This compensation movement (%.2f) is lower than the required amount to fully compensate (%.2f).", absAmount, overpayment));
                }
            }
        }
        
        return warnings;
    }

    public List<String> validateInvoiceMovement(int invoiceId, double amount, LocalDate moveDate, LocalDate simulatedDate) throws ApplicationException {
        List<String> warnings = new ArrayList<>();

        if (simulatedDate != null && moveDate.isAfter(simulatedDate)) {
            throw new ApplicationException("Movement date (" + moveDate + ") cannot be in the future (System date: " + simulatedDate + ").");
        }

        if (Math.abs(amount) < 0.001) {
            throw new ApplicationException("Amount cannot be zero.");
        }

        List<TeacherInvoiceDTO> all = getAllInvoices();
        TeacherInvoiceDTO selected = all.stream().filter(i -> i.getInvoiceId() == invoiceId).findFirst().orElse(null);
        
        if (selected == null) {
            throw new ApplicationException("Selected invoice not found in database.");
        }

        double currentNetPaid = selected.getNetBalance(); // Sum of movements (usually negative for expenses)
        double totalInvoice = selected.getTotalAmount();
        
        // DATE VALIDATION (Movement cannot be before invoice)
        String invDateStr = selected.getInvoiceDate();
        if (invDateStr != null) {
            if (invDateStr.length() > 10) invDateStr = invDateStr.substring(0, 10);
            LocalDate invDate = LocalDate.parse(invDateStr, FORMATTER);
            if (moveDate.isBefore(invDate)) {
                throw new ApplicationException("Movement date cannot be before invoice date (" + invDateStr + ").");
            }
        }
        
        if (amount > 0) {
            // Positive movement for an invoice (income/refund from teacher)
            if (Math.abs(currentNetPaid) <= totalInvoice + 0.001) {
                throw new ApplicationException("Outgoing movements (invoice payments) must be represented as negative numbers. Use a positive number only for teacher refunds of an overpayment.");
            }
        } else if (amount < 0) {
            // Overcharge warning (Expenses are negative): |currentNetPaid + amount| > totalInvoice
            if (Math.abs(currentNetPaid + amount) > totalInvoice + 0.001) {
                warnings.add("This movement will result in an overcharge (payment exceeding the invoice amount).");
            }
        }
        return warnings;
    }

    // ====================================================================================
    // METODOS PRE-EXISTENTES (Consultas y transacciones a BBDD)
    // ====================================================================================

    public List<EnrollmentRecordDTO> getAllEnrollments() {
        String sql = "SELECT " +
                     "i.inscription_id AS inscriptionId, " +
                     "fa.name AS courseName, " +
                     "p.name || ' ' || p.surname AS professionalName, " +
                     "i.applied_fee AS fee, " +
                     "i.state AS state, " +
                     "i.inscription_date AS registrationDate, " +
                     "(SELECT COALESCE(SUM(amount), 0) FROM MoneyMovement WHERE inscription_id = i.inscription_id) AS netBalance " +
                     "FROM Inscription i " +
                     "JOIN FormativeAction fa ON i.action_id = fa.action_id " +
                     "JOIN Professional p ON i.professional_id = p.professional_id";
        return db.executeQueryPojo(EnrollmentRecordDTO.class, sql);
    }

    public List<EnrollmentRecordDTO> getEnrollmentsPendingCompensation() {
        String sql = "SELECT * FROM (" +
                     "SELECT i.inscription_id AS inscriptionId, fa.name AS courseName, p.name || ' ' || p.surname AS professionalName, " +
                     "i.applied_fee AS fee, i.state AS state, i.inscription_date AS registrationDate, " +
                     "((SELECT COALESCE(SUM(amount), 0) FROM MoneyMovement WHERE inscription_id = i.inscription_id) - i.applied_fee) AS netBalance " +
                     "FROM Inscription i JOIN FormativeAction fa ON i.action_id = fa.action_id " +
                     "JOIN Professional p ON i.professional_id = p.professional_id" +
                     ") WHERE netBalance > 0.001";
        return db.executeQueryPojo(EnrollmentRecordDTO.class, sql);
    }

    public List<TeacherInvoiceDTO> getAllInvoices() {
        String sql = "SELECT " +
                     "i.invoice_id AS invoiceId, " +
                     "t.name AS teacherName, " +
                     "fa.name AS courseName, " +
                     "i.netAmount AS netAmount, " +
                     "i.vat AS vat, " +
                     "i.totalAmount AS totalAmount, " +
                     "i.invoice_date AS invoiceDate, " +
                     "i.status AS status, " +
                     "(SELECT COALESCE(SUM(amount), 0) FROM MoneyMovement WHERE invoice_id = i.invoice_id) AS netBalance " +
                     "FROM Invoice i " +
                     "JOIN Teacher t ON i.teacher_id = t.teacher_id " +
                     "JOIN FormativeAction fa ON i.action_id = fa.action_id";
        return db.executeQueryPojo(TeacherInvoiceDTO.class, sql);
    }

    public List<TeacherInvoiceDTO> getInvoicesPendingCompensation() {
        String sql = "SELECT * FROM (" +
                     "SELECT i.invoice_id AS invoiceId, t.name AS teacherName, fa.name AS courseName, " +
                     "i.netAmount AS netAmount, i.vat AS vat, i.totalAmount AS totalAmount, " +
                     "(ABS((SELECT COALESCE(SUM(amount), 0) FROM MoneyMovement WHERE invoice_id = i.invoice_id)) - i.totalAmount) AS netBalance " +
                     "FROM Invoice i JOIN Teacher t ON i.teacher_id = t.teacher_id " +
                     "JOIN FormativeAction fa ON i.action_id = fa.action_id" +
                     ") WHERE netBalance > 0.001";
        return db.executeQueryPojo(TeacherInvoiceDTO.class, sql);
    }

    public List<MoneyMovementDTO> getAllMovements() {
        String sql = "SELECT mm.movement_id AS movementId, mm.amount, mm.movement_date AS movementDate, mm.status, mm.type, mm.inscription_id AS inscriptionId, mm.invoice_id AS invoiceId, " +
                     "COALESCE('Insc: ' || p.surname || ' (' || fa.name || ')', 'Inv: ' || t.name || ' (' || fa2.name || ')') AS relatedTo " +
                     "FROM MoneyMovement mm " +
                     "LEFT JOIN Inscription i ON mm.inscription_id = i.inscription_id " +
                     "LEFT JOIN Professional p ON i.professional_id = p.professional_id " +
                     "LEFT JOIN FormativeAction fa ON i.action_id = fa.action_id " +
                     "LEFT JOIN Invoice inv ON mm.invoice_id = inv.invoice_id " +
                     "LEFT JOIN Teacher t ON inv.teacher_id = t.teacher_id " +
                     "LEFT JOIN FormativeAction fa2 ON inv.action_id = fa2.action_id " +
                     "ORDER BY mm.movement_date DESC, mm.movement_id DESC";
        return db.executeQueryPojo(MoneyMovementDTO.class, sql);
    }

    public List<MoneyMovementDTO> getMovementsByInscription(int id) {
        String sql = "SELECT movement_id AS movementId, amount, movement_date AS movementDate, status, type, inscription_id AS inscriptionId " +
                     "FROM MoneyMovement WHERE inscription_id = ? ORDER BY movement_date DESC";
        return db.executeQueryPojo(MoneyMovementDTO.class, sql, id);
    }

    public List<MoneyMovementDTO> getMovementsByInvoice(int id) {
        String sql = "SELECT movement_id AS movementId, amount, movement_date AS movementDate, status, type, invoice_id AS invoiceId " +
                     "FROM MoneyMovement WHERE invoice_id = ? ORDER BY movement_date DESC";
        return db.executeQueryPojo(MoneyMovementDTO.class, sql, id);
    }

    public void registerMovement(Integer inscriptionId, Integer invoiceId, double amount, String date, String status) {
        String sql = "INSERT INTO MoneyMovement (amount, movement_date, status, type, inscription_id, invoice_id) VALUES (?, ?, ?, 'PAYMENT', ?, ?)";
        db.executeUpdate(sql, amount, date, status, inscriptionId, invoiceId);
        
        if (inscriptionId != null) updateInscriptionStatus(inscriptionId);
        if (invoiceId != null) updateInvoiceStatus(invoiceId);
    }

    private void updateInscriptionStatus(int id) {
        String sql = "SELECT applied_fee AS fee, (SELECT COALESCE(SUM(amount), 0) FROM MoneyMovement WHERE inscription_id = ?) AS netBalance FROM Inscription WHERE inscription_id = ?";
        List<EnrollmentRecordDTO> results = db.executeQueryPojo(EnrollmentRecordDTO.class, sql, id, id);
        if (!results.isEmpty()) {
            EnrollmentRecordDTO e = results.get(0);
            if (e.getNetBalance() > e.getFee()) {
                db.executeUpdate("UPDATE Inscription SET state = 'PENDING_COMPENSATION' WHERE inscription_id = ?", id);
            } else if (e.getNetBalance() >= e.getFee()) {
                db.executeUpdate("UPDATE Inscription SET state = 'CONFIRMED' WHERE inscription_id = ?", id);
            } else {
                db.executeUpdate("UPDATE Inscription SET state = 'RECEIVED' WHERE inscription_id = ?", id);
            }
        }
    }

    private void updateInvoiceStatus(int id) {
        String sql = "SELECT totalAmount, (SELECT COALESCE(SUM(amount), 0) FROM MoneyMovement WHERE invoice_id = ?) AS netBalance FROM Invoice WHERE invoice_id = ?";
        List<TeacherInvoiceDTO> results = db.executeQueryPojo(TeacherInvoiceDTO.class, sql, id, id);
        if (!results.isEmpty()) {
            TeacherInvoiceDTO i = results.get(0);
            if (Math.abs(i.getNetBalance()) >= i.getTotalAmount()) {
                db.executeUpdate("UPDATE Invoice SET status = 'PAID' WHERE invoice_id = ?", id);
            } else {
                db.executeUpdate("UPDATE Invoice SET status = 'PENDING' WHERE invoice_id = ?", id);
            }
        }
    }
}