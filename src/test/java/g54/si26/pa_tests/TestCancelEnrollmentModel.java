package g54.si26.pa_tests;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import g54.si26.cancelEnrollment.ModelCancelEnrollment;
import g54.si26.utils.ApplicationException;
import g54.si26.utils.Database;
import g54.si26.utils.Util;

public class TestCancelEnrollmentModel {

	private static Database db = new Database();
	private ModelCancelEnrollment model;

	private static final int INSC_ACTIVE_FAR     = 800;
	private static final int INSC_ACTIVE_BORDER7 = 801;
	private static final int INSC_ACTIVE_DAY3    = 802;
	private static final int INSC_ACTIVE_DAY6    = 803;
	private static final int INSC_ACTIVE_DAY2    = 804;
	private static final int INSC_ACTIVE_DAY0    = 805;
	private static final int INSC_FREE_FEE       = 806;
	private static final int INSC_ALREADY_CANC   = 807;
	private static final int INSC_PENDING_COMP   = 808;
	private static final int INSC_FA_CANCELLED   = 809;
	private static final int INSC_PAST_COURSE    = 810;

	private static final Date SIM_DATE = Util.isoStringToDate("2026-05-04");

	@BeforeEach
	public void setUp() {
		db.createDatabase(true);
		loadCleanDatabase();
		model = new ModelCancelEnrollment();
	}

	private void loadCleanDatabase() {
		db.executeBatch(new String[] {
			"delete from MoneyMovement",
			"delete from Invoice",
			"delete from Inscription",
			"delete from Teacher_FormativeAction",
			"delete from Fee",
			"delete from FormativeAction",
			"delete from Professional",
			"delete from Teacher",
			"delete from Community",

			"insert into Community(community_id,communityName) values (300,'General Public')",

			"insert into Professional(professional_id,name,surname,phone,email,community_id) "
				+ "values (500,'Ana','Perez','111111111','ana@uniovi.es',300)",

			"insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours,"
				+ "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values "
				+ "(900,'Course Far',5,'2026-08-01','2026-08-10','10','2026-04-01','2026-07-25','Online','ACTIVE')",
			"insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours,"
				+ "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values "
				+ "(901,'Course Border 7',5,'2026-05-11','2026-05-20','10','2026-04-01','2026-05-09','Online','ACTIVE')",
			"insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours,"
				+ "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values "
				+ "(902,'Course Day 3',5,'2026-05-07','2026-05-15','10','2026-04-01','2026-05-05','Online','ACTIVE')",
			"insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours,"
				+ "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values "
				+ "(903,'Course Day 6',5,'2026-05-10','2026-05-18','10','2026-04-01','2026-05-08','Online','ACTIVE')",
			"insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours,"
				+ "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values "
				+ "(904,'Course Day 2',5,'2026-05-06','2026-05-14','10','2026-04-01','2026-05-04','Online','ACTIVE')",
			"insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours,"
				+ "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values "
				+ "(905,'Course Day 0',5,'2026-05-04','2026-05-12','10','2026-04-01','2026-05-03','Online','ACTIVE')",
			"insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours,"
				+ "inscriptionPeriodStart,inscriptionPeriodEnd,location,status,cancelDate) values "
				+ "(906,'Course Cancelled FA',5,'2026-08-01','2026-08-10','10','2026-04-01','2026-07-25','Online','CANCELLED','2026-04-30')",
			"insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours,"
				+ "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values "
				+ "(907,'Course Already Started',5,'2026-04-20','2026-04-30','10','2026-03-01','2026-04-15','Online','CLOSED')",

			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(800,'2026-04-01 10:00:00',100.0,'CONFIRMED',500,900)",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(801,'2026-04-15 10:00:00',100.0,'CONFIRMED',500,901)",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(802,'2026-04-20 10:00:00',100.0,'CONFIRMED',500,902)",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(803,'2026-04-20 10:00:00',100.0,'CONFIRMED',500,903)",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(804,'2026-04-25 10:00:00',100.0,'CONFIRMED',500,904)",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(805,'2026-04-25 10:00:00',100.0,'RECEIVED',500,905)",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(806,'2026-04-01 10:00:00',0.0,'CONFIRMED',500,900)",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id,cancellation_date) values "
				+ "(807,'2026-04-01 10:00:00',100.0,'CANCELLED',500,900,'2026-04-15')",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(808,'2026-04-01 10:00:00',100.0,'PENDING_COMPENSATION',500,900)",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(809,'2026-04-01 10:00:00',100.0,'CONFIRMED',500,906)",
			"insert into Inscription(inscription_id,inscription_date,applied_fee,state,professional_id,action_id) values "
				+ "(810,'2026-03-15 10:00:00',100.0,'CONFIRMED',500,907)"
		});
	}

	@Test
	public void testRefundFullWhenManyDaysAhead() {
		double refund = model.calculateRefund(INSC_ACTIVE_FAR, SIM_DATE);
		assertEquals(100.0, refund, 0.001, "full refund expected when course is far in the future");
	}

	@Test
	public void testRefundFullAtSevenDayBoundary() {
		double refund = model.calculateRefund(INSC_ACTIVE_BORDER7, SIM_DATE);
		assertEquals(100.0, refund, 0.001, "full refund expected exactly at the 7-day boundary");
	}

	@Test
	public void testRefundHalfAtThreeDayBoundary() {
		double refund = model.calculateRefund(INSC_ACTIVE_DAY3, SIM_DATE);
		assertEquals(50.0, refund, 0.001, "half refund expected exactly at the 3-day boundary");
	}

	@Test
	public void testRefundHalfAtSixDayBoundary() {
		double refund = model.calculateRefund(INSC_ACTIVE_DAY6, SIM_DATE);
		assertEquals(50.0, refund, 0.001, "half refund expected exactly at the 6-day boundary");
	}

	@Test
	public void testNoRefundWhenTwoDaysAhead() {
		double refund = model.calculateRefund(INSC_ACTIVE_DAY2, SIM_DATE);
		assertEquals(0.0, refund, 0.001, "no refund expected when only 2 days remain");
	}

	@Test
	public void testNoRefundOnSameDay() {
		double refund = model.calculateRefund(INSC_ACTIVE_DAY0, SIM_DATE);
		assertEquals(0.0, refund, 0.001, "no refund expected when course starts the same day");
	}

	@Test
	public void testRefundIsZeroWhenFeeWasZero() {
		double refund = model.calculateRefund(INSC_FREE_FEE, SIM_DATE);
		assertEquals(0.0, refund, 0.001, "refund must be zero when the original fee was zero");
	}

	@Test
	public void testCannotRefundAlreadyCancelledInscription() {
		ApplicationException e = assertThrows(ApplicationException.class,
			() -> model.calculateRefund(INSC_ALREADY_CANC, SIM_DATE));
		assertEquals("Inscription already cancelled.", e.getMessage());
	}

	@Test
	public void testCannotRefundPendingCompensationInscription() {
		ApplicationException e = assertThrows(ApplicationException.class,
			() -> model.calculateRefund(INSC_PENDING_COMP, SIM_DATE));
		assertEquals("Pending compensation, cannot cancel.", e.getMessage());
	}

	@Test
	public void testCannotRefundWhenFormativeActionCancelled() {
		ApplicationException e = assertThrows(ApplicationException.class,
			() -> model.calculateRefund(INSC_FA_CANCELLED, SIM_DATE));
		assertEquals("Formative Action is cancelled.", e.getMessage());
	}

	@Test
	public void testCannotRefundWhenCourseAlreadyStarted() {
		ApplicationException e = assertThrows(ApplicationException.class,
			() -> model.calculateRefund(INSC_PAST_COURSE, SIM_DATE));
		assertEquals("Course already started, cannot cancel.", e.getMessage());
	}

	@Test
	public void testCannotRefundNonExistentInscription() {
		ApplicationException e = assertThrows(ApplicationException.class,
			() -> model.calculateRefund(99999, SIM_DATE));
		assertEquals("Inscription not found.", e.getMessage());
	}
}