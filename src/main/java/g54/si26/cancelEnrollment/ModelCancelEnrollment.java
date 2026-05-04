package g54.si26.cancelEnrollment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import g54.si26.utils.ApplicationException;
import g54.si26.utils.Database;
import g54.si26.utils.Util;

public class ModelCancelEnrollment {

	private Database db = new Database();

	public List<Object[]> getActiveEnrollments(int professionalId) {
		String sql =
			"SELECT i.inscription_id, fa.name, fa.startDate, fa.endDate, i.applied_fee, i.state " +
			"FROM Inscription i " +
			"JOIN FormativeAction fa ON i.action_id = fa.action_id " +
			"WHERE i.professional_id = ? AND UPPER(i.state) IN ('CONFIRMED', 'RECEIVED', 'PAID') " +
			"ORDER BY fa.startDate ASC";

		return db.executeQueryArray(sql, professionalId);
	}

	public double calculateRefund(int inscriptionId, Date simulatedDate) {
		if (simulatedDate == null)
			throw new ApplicationException("Simulated date cannot be null.");

		String sql =
			"SELECT i.applied_fee, i.state, fa.startDate, fa.status " +
			"FROM Inscription i JOIN FormativeAction fa ON i.action_id = fa.action_id " +
			"WHERE i.inscription_id = ?";
		List<Object[]> rows = db.executeQueryArray(sql, inscriptionId);
		if (rows.isEmpty())
			throw new ApplicationException("Inscription not found.");

		double feePaid = Double.parseDouble(rows.get(0)[0].toString());
		String state = rows.get(0)[1].toString();
		String startDateStr = rows.get(0)[2].toString().substring(0, 10);
		String faStatus = rows.get(0)[3].toString();

		if ("CANCELLED".equalsIgnoreCase(state))
			throw new ApplicationException("Inscription already cancelled.");
		if ("PENDING_COMPENSATION".equalsIgnoreCase(state))
			throw new ApplicationException("Pending compensation, cannot cancel.");
		if ("CANCELLED".equalsIgnoreCase(faStatus))
			throw new ApplicationException("Formative Action is cancelled.");

		LocalDate simDate = LocalDate.parse(Util.dateToIsoString(simulatedDate).substring(0, 10));
		LocalDate startDate = LocalDate.parse(startDateStr);
		long days = ChronoUnit.DAYS.between(simDate, startDate);

		if (days < 0)
			throw new ApplicationException("Course already started, cannot cancel.");
		if (days >= 7)
			return feePaid;
		if (days >= 3)
			return feePaid * 0.5;
		return 0.0;
	}

	public void cancelEnrollment(int inscriptionId, Date simulatedDate, double refundAmount, String reason) {
		String simulatedDateStr = Util.dateToIsoString(simulatedDate);

		try (Connection conn = db.getConnection()) {
			conn.setAutoCommit(false);
			try {
				try (PreparedStatement pstmt = conn.prepareStatement(
						"UPDATE Inscription SET state = 'CANCELLED', cancellation_date = ? WHERE inscription_id = ?")) {
					pstmt.setString(1, simulatedDateStr);
					pstmt.setInt(2, inscriptionId);
					pstmt.executeUpdate();
				}
				if (refundAmount > 0) {
					try (PreparedStatement pstmt = conn.prepareStatement(
							"INSERT INTO MoneyMovement (movement_date, amount, status, type, inscription_id) VALUES (?, ?, 'CONFIRMED', 'REFUND', ?)")) {
						pstmt.setString(1, simulatedDateStr);
						pstmt.setDouble(2, -refundAmount);
						pstmt.setInt(3, inscriptionId);
						pstmt.executeUpdate();
					}
				}
				conn.commit();
			} catch (SQLException e) {
				conn.rollback();
				throw new ApplicationException("Error applying cancellation logic: " + e.getMessage());
			}
		} catch (SQLException e) {
			throw new ApplicationException("DB Error: " + e.getMessage());
		}
	}
}