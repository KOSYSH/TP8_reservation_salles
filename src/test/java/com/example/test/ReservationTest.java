package com.example.test;

import com.example.model.*;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.MigrationExecutor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import javax.persistence.*;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

public class ReservationTest {

    private static EntityManagerFactory emf;
    private static String jdbcUrl;
    private EntityManager em;
    private ReservationService reservations;
    private Salle salle;
    private Utilisateur utilisateur;
    private LocalDateTime debut;

    @BeforeAll
    public static void init() throws Exception {
        Class.forName("org.h2.Driver");
        jdbcUrl = "jdbc:h2:mem:reservations_test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        MigrationExecutor migration = new MigrationExecutor(jdbcUrl, "sa", "");
        migration.executeMigration("db/migration/v1_0_init.sql");
        migration.executeMigration("db/migration/v2_0_add_description.sql");
        emf = Persistence.createEntityManagerFactory("ReservationPU",
                Map.of("javax.persistence.jdbc.url", jdbcUrl));
    }

    @BeforeEach
    public void setUp() {
        em = emf.createEntityManager();
        reservations = new ReservationService(emf);
        em.getTransaction().begin();
        salle = new Salle("Salle Test", 20, "Salle de test");
        utilisateur = new Utilisateur("Utilisateur Test", "test@example.com");
        em.persist(salle);
        em.persist(utilisateur);
        em.getTransaction().commit();
        em.clear();
        debut = LocalDate.now().plusDays(2).atTime(9, 0);
    }

    @AfterEach
    public void tearDown() {
        if (em != null && em.isOpen()) {
            try {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                em.clear();
                em.getTransaction().begin();
                em.createQuery("DELETE FROM Reservation").executeUpdate();
                em.createQuery("DELETE FROM Salle").executeUpdate();
                em.createQuery("DELETE FROM Utilisateur").executeUpdate();
                em.getTransaction().commit();
            } finally {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                em.close();
                emf.getCache().evictAll();
            }
        }
    }

    @AfterAll
    public static void close() throws Exception {
        try {
            if (emf != null) emf.close();
        } finally {
            if (jdbcUrl != null) {
                try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                     Statement statement = connection.createStatement()) {
                    statement.execute("SHUTDOWN");
                }
            }
        }
    }

    @Test
    void creerEtRelireUneReservation() {
        Reservation creee = reservations.creerReservation(nouvelleReservation());
        assertNotNull(creee.getId());
        Reservation relue = reservations.trouverParId(creee.getId());
        assertEquals(debut, relue.getDateDebut());
        assertEquals(salle.getNom(), relue.getSalle().getNom());
        assertEquals(utilisateur.getEmail(), relue.getUtilisateur().getEmail());
    }

    @Test
    void modifierUneReservationEtIncrementerSaVersion() {
        Reservation creee = reservations.creerReservation(nouvelleReservation());
        int version = creee.getVersion();
        creee.setDateDebut(debut.plusHours(2));
        creee.setDateFin(debut.plusHours(3));
        Reservation modifiee = reservations.modifierReservation(creee);
        assertEquals(version + 1, modifiee.getVersion());
        assertEquals(debut.plusHours(2), reservations.trouverParId(creee.getId()).getDateDebut());
    }

    @Test
    void supprimerUneReservation() {
        Reservation creee = reservations.creerReservation(nouvelleReservation());
        reservations.supprimerReservation(creee.getId());
        assertNull(reservations.trouverParId(creee.getId()));
    }

    @Test
    void refuserDatesInvalides() {
        Reservation invalide = nouvelleReservation();
        invalide.setDateFin(debut.minusHours(1));
        assertThrows(IllegalArgumentException.class, () -> reservations.creerReservation(invalide));
        invalide.setDateFin(debut);
        assertThrows(IllegalArgumentException.class, () -> reservations.creerReservation(invalide));
        invalide.setDateDebut(null);
        assertThrows(IllegalArgumentException.class, () -> reservations.creerReservation(invalide));
        assertTrue(reservations.listerReservations().isEmpty());
    }

    @Test
    void refuserUnChevauchementMaisAutoriserLesCreneauxConsecutifs() {
        reservations.creerReservation(nouvelleReservation());
        Reservation conflit = new Reservation(salle, utilisateur, debut.plusMinutes(30), debut.plusHours(2));
        assertThrows(IllegalArgumentException.class, () -> reservations.creerReservation(conflit));
        Reservation suivante = new Reservation(salle, utilisateur, debut.plusHours(1), debut.plusHours(2));
        assertNotNull(reservations.creerReservation(suivante).getId());
        assertEquals(2, reservations.listerReservations().size());
    }

    @Test
    void refuserUneVersionObsoleteEtConserverLaPremiereModification() {
        Reservation creee = reservations.creerReservation(nouvelleReservation());
        Reservation tx1 = reservations.trouverParId(creee.getId());
        Reservation tx2 = reservations.trouverParId(creee.getId());
        tx1.setDateFin(debut.plusHours(2));
        reservations.modifierReservation(tx1);
        tx2.setDateFin(debut.plusHours(3));
        assertThrows(OptimisticLockException.class, () -> reservations.modifierReservation(tx2));
        assertEquals(debut.plusHours(2), reservations.trouverParId(creee.getId()).getDateFin());
    }

    @Test
    void jpqlEtCriteriaRetournentLesMemesReservations() {
        Reservation cible = reservations.creerReservation(nouvelleReservation());
        List<Reservation> jpql = reservations.rechercherParSalleJPQL(salle.getNom(), debut.minusMinutes(1));
        List<Reservation> criteria = reservations.rechercherParSalleCriteria(salle.getNom(), debut.minusMinutes(1));
        assertEquals(List.of(cible.getId()), jpql.stream().map(Reservation::getId).collect(Collectors.toList()));
        assertEquals(jpql.stream().map(Reservation::getId).collect(Collectors.toList()),
                criteria.stream().map(Reservation::getId).collect(Collectors.toList()));
        assertEquals(salle.getNom(), criteria.get(0).getSalle().getNom());
        assertTrue(reservations.rechercherParSalleJPQL(salle.getNom(), debut).isEmpty());
    }

    @Test
    void cacheL2PartageEntreDeuxEntityManagers() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        emf.getCache().evict(Salle.class, salle.getId());
        lireSalle();
        long hitsAvant = stats.getSecondLevelCacheHitCount();
        long sqlAvant = stats.getPrepareStatementCount();
        assertEquals(salle.getNom(), lireSalle().getNom());
        assertTrue(stats.getSecondLevelCacheHitCount() > hitsAvant);
        assertEquals(sqlAvant, stats.getPrepareStatementCount());
        em.getTransaction().begin();
        em.find(Salle.class, salle.getId()).setCapacite(30);
        em.getTransaction().commit();
        assertEquals(30, lireSalle().getCapacite());
    }

    @Test
    void cacheDeRequetesUtilise() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        emf.unwrap(SessionFactory.class).getCache().evictQueryRegions();
        rechercherSalleCachee();
        long hitsAvant = stats.getQueryCacheHitCount();
        long sqlAvant = stats.getPrepareStatementCount();
        assertEquals(salle.getId(), rechercherSalleCachee().get(0).getId());
        assertTrue(stats.getQueryCacheHitCount() > hitsAvant);
        assertEquals(sqlAvant, stats.getPrepareStatementCount());
    }

    @Test
    void migrationRelancableEtScriptAbsentSignale() throws Exception {
        MigrationExecutor migration = new MigrationExecutor(jdbcUrl, "sa", "");
        migration.executeMigration("db/migration/v1_0_init.sql");
        migration.executeMigration("db/migration/v2_0_add_description.sql");
        assertEquals("Salle de test", lireSalle().getDescription());
        assertThrows(IOException.class, () -> migration.executeMigration("db/migration/introuvable.sql"));
    }

    @Test
    void garantirUniciteDesEmailsEtRollbackApresErreur() {
        em.getTransaction().begin();
        assertThrows(PersistenceException.class, () -> {
            em.persist(new Utilisateur("Doublon", utilisateur.getEmail()));
            em.flush();
        });
        em.getTransaction().rollback();
        em.clear();
        assertEquals(1L, em.createQuery("SELECT COUNT(u) FROM Utilisateur u", Long.class).getSingleResult());
    }

    @Test
    void initialisationPeutEtreRelanceeSansDoublons() {
        DataInitializer initializer = new DataInitializer(em);
        initializer.initData();
        initializer.initData();
        assertEquals(4L, em.createQuery("SELECT COUNT(u) FROM Utilisateur u", Long.class).getSingleResult());
        assertEquals(4L, em.createQuery("SELECT COUNT(s) FROM Salle s", Long.class).getSingleResult());
        assertEquals(6, reservations.listerReservations().size());
    }

    private Reservation nouvelleReservation() {
        return new Reservation(salle, utilisateur, debut, debut.plusHours(1));
    }

    private Salle lireSalle() {
        EntityManager lecture = emf.createEntityManager();
        try {
            return lecture.find(Salle.class, salle.getId());
        } finally {
            lecture.close();
        }
    }

    private List<Salle> rechercherSalleCachee() {
        EntityManager lecture = emf.createEntityManager();
        try {
            return lecture.createQuery("SELECT s FROM Salle s WHERE s.id = :id", Salle.class)
                    .setParameter("id", salle.getId()).setHint("org.hibernate.cacheable", true).getResultList();
        } finally {
            lecture.close();
        }
    }
}
