package g54.si26.pa_tests;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import g54.si26.DTOs.ProfessionalDTO;
import g54.si26.inscriptions.*;
import g54.si26.utils.ApplicationException;
import g54.si26.utils.Database;
import g54.si26.utils.Util;

public class TestInscriptionsModel {

    private static Database db = new Database();
    private InscriptionsModel model;

    private static final int FA_ACTIVE_FREE    = 200;
    private static final int FA_CLOSED_LAST    = 201;
    private static final int FA_CANCELLED      = 202;
    private static final int FA_FULL           = 203;
    private static final int FA_WITH_DUPLICATE = 204;

    private static final int COMM_PAID = 300;
    private static final int COMM_FREE = 301;

    private static final Date SIM_DATE = Util.isoStringToDate("2026-05-04");

    @BeforeEach
    public void setUp() {
        db.createDatabase(true);
        loadCleanDatabase();
        model = new InscriptionsModel();
    }

    private void loadCleanDatabase() {
        db.executeBatch(new String[] {
            //clean
            "delete from MoneyMovement",
            "delete from Invoice",
            "delete from Inscription",
            "delete from Teacher_FormativeAction",
            "delete from Fee",
            "delete from FormativeAction",
            "delete from Professional",
            "delete from Teacher",
            "delete from Community",

            // FAs
            "insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours," +
                "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values " +
                "(200,'FA Active Free',5,'2026-06-01','2026-06-10','10'," +
                "'2026-04-01','2026-05-25','Online','ACTIVE')",

            "insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours," +
                "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values " +
                "(201,'FA Closed Last',1,'2026-06-01','2026-06-10','10'," +
                "'2026-04-01','2026-05-25','Online','CLOSED')",

            "insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours," +
                "inscriptionPeriodStart,inscriptionPeriodEnd,location,status,cancelDate) values " +
                "(202,'FA Cancelled',5,'2026-06-01','2026-06-10','10'," +
                "'2026-04-01','2026-05-25','Online','CANCELLED','2026-04-15')",

            "insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours," +
                "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values " +
                "(203,'FA Full',1,'2026-06-01','2026-06-10','10'," +
                "'2026-04-01','2026-05-25','Online','ACTIVE')",

            "insert into FormativeAction(action_id,name,spots,startDate,endDate,numberOfHours," +
                "inscriptionPeriodStart,inscriptionPeriodEnd,location,status) values " +
                "(204,'FA With Duplicate',5,'2026-06-01','2026-06-10','10'," +
                "'2026-04-01','2026-05-25','Online','ACTIVE')",

            // Communities
            "insert into Community(community_id,communityName) values (300,'General Public')",
            "insert into Community(community_id,communityName) values (301,'Free Members')",

            // Fees
            "insert into Fee(amount,community_id,action_id) values (100.0, 300, 200)",
            "insert into Fee(amount,community_id,action_id) values (100.0, 300, 201)",
            "insert into Fee(amount,community_id,action_id) values (  0.0, 301, 201)",
            "insert into Fee(amount,community_id,action_id) values (100.0, 300, 203)",
            "insert into Fee(amount,community_id,action_id) values (100.0, 300, 204)",

            // Profesional existente
            "insert into Professional(professional_id,name,surname,phone,email,community_id) " +
                "values (500,'Ana','Pérez','111111111','ana@uniovi.es',300)",

            // Inscripción activa para FA_WITH_DUPLICATE
            "insert into Inscription(inscription_id,inscription_date,applied_fee,state," +
                "professional_id,action_id) values " +
                "(700,'2026-04-20 10:00:00',100.0,'RECEIVED',500,204)",

            // Inscripción CANCELLED previa para FA_CLOSED_LAST
            "insert into Inscription(inscription_id,inscription_date,applied_fee,state," +
                "professional_id,action_id,cancellation_date) values " +
                "(701,'2026-04-15 09:00:00',0.0,'CANCELLED',500,201,'2026-04-18')",

            // Profesional que llena FA_FULL
            "insert into Professional(professional_id,name,surname,phone,email,community_id) " +
                "values (501,'Luis','Gómez','222222222','luis@uniovi.es',300)",
            "insert into Inscription(inscription_id,inscription_date,applied_fee,state," +
                "professional_id,action_id) values " +
                "(702,'2026-04-22 10:00:00',100.0,'CONFIRMED',501,203)"
        });
    }

    private ProfessionalDTO newProfDto(String name, String surname, String phone, String email) {
        ProfessionalDTO p = new ProfessionalDTO();
        p.setName(name);
        p.setSurname(surname);
        p.setPhone(phone);
        p.setEmail(email);
        return p;
    }

    private List<Object[]> queryInscriptions(int actionId) {
        return db.executeQueryArray(
            "SELECT professional_id, applied_fee, state FROM Inscription " +
            "WHERE action_id = ? AND state != 'CANCELLED' ORDER BY inscription_id", actionId);
    }

    private String queryFaStatus(int actionId) {
        return db.executeQueryArray(
            "SELECT status FROM FormativeAction WHERE action_id = ?", actionId)
            .get(0)[0].toString();
    }

    @Test
    public void testEnrollNewProfessionalActiveCourse() {
        ProfessionalDTO p = newProfDto("Carlos","Ruiz","333333333","carlos@uniovi.es");

        model.enrollProfessional(p, FA_ACTIVE_FREE, COMM_PAID, SIM_DATE);

        List<Object[]> insc = queryInscriptions(FA_ACTIVE_FREE);
        assertEquals(1, insc.size(), "debe haber 1 inscripción activa");
        assertEquals("100.0", insc.get(0)[1].toString(), "fee aplicado incorrecto");
        assertEquals("RECEIVED", insc.get(0)[2].toString(), "estado debe ser RECEIVED al haber fee > 0");
    }

    @Test
    public void testEnrollExistingProfReactivatesClosedCourse() {
        ProfessionalDTO p = newProfDto("Ana","Pérez","111111111","ana@uniovi.es");

        model.enrollProfessional(p, FA_CLOSED_LAST, COMM_FREE, SIM_DATE);

        List<Object[]> insc = queryInscriptions(FA_CLOSED_LAST);
        assertEquals(1, insc.size(), "debe haber 1 inscripción activa nueva");
        assertEquals("500", insc.get(0)[0].toString(), "debe ser el profesional existente (id 500)");
        assertEquals("CONFIRMED", insc.get(0)[2].toString(), "estado debe ser CONFIRMED al ser fee = 0");
        assertEquals("ACTIVE", queryFaStatus(FA_CLOSED_LAST), "la FA debe haberse reactivado a ACTIVE");
    }

    @Test
    public void testEnrollFailsWhenCourseCancelled() {
        ProfessionalDTO p = newProfDto("Carlos","Ruiz","333333333","carlos@uniovi.es");
        ApplicationException e = assertThrows(ApplicationException.class,
            () -> model.enrollProfessional(p, FA_CANCELLED, COMM_PAID, SIM_DATE),
            "debe lanzar ApplicationException por FA cancelada");
        assertEquals("Not available.", e.getMessage());
    }

    @Test
    public void testEnrollFailsWhenCourseNotFound() {
        ProfessionalDTO p = newProfDto("Carlos","Ruiz","333333333","carlos@uniovi.es");
        ApplicationException e = assertThrows(ApplicationException.class,
            () -> model.enrollProfessional(p, 9999, COMM_PAID, SIM_DATE),
            "debe lanzar ApplicationException por FA inexistente");
        assertEquals("Course not found.", e.getMessage());
    }

    @Test
    public void testEnrollFailsWhenCourseFull() {
        ProfessionalDTO p = newProfDto("Carlos","Ruiz","333333333","carlos@uniovi.es");
        ApplicationException e = assertThrows(ApplicationException.class,
            () -> model.enrollProfessional(p, FA_FULL, COMM_PAID, SIM_DATE),
            "debe lanzar ApplicationException por FA llena");
        assertEquals("Course is full.", e.getMessage());
    }

    @Test
    public void testEnrollFailsWhenAlreadyEnrolled() {
        ProfessionalDTO p = newProfDto("Ana","Pérez","111111111","ana@uniovi.es");
        ApplicationException e = assertThrows(ApplicationException.class,
            () -> model.enrollProfessional(p, FA_WITH_DUPLICATE, COMM_PAID, SIM_DATE),
            "debe lanzar ApplicationException por inscripción duplicada");
        assertEquals("Already enrolled.", e.getMessage());
    }

    @Test
    public void testEnrollFailsWhenEmailAlreadyInUse() {
        ProfessionalDTO p = newProfDto("Otro","Nombre","999999999","ana@uniovi.es");
        ApplicationException e = assertThrows(ApplicationException.class,
            () -> model.enrollProfessional(p, FA_ACTIVE_FREE, COMM_PAID, SIM_DATE),
            "debe lanzar ApplicationException por email/teléfono duplicado");
        assertEquals("Error: Email or phone already in use.", e.getMessage());
    }
}