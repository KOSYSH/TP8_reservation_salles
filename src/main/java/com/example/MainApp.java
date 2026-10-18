package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MainApp {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

    public static void main(String[] args) throws Exception {
        EntityManagerFactory emf = null;
        EntityManager em = null;
        try {
            // La connexion est la même que dans persistence.xml : H2 en mémoire.
            Class.forName("org.h2.Driver");
            MigrationExecutor migration = new MigrationExecutor(
                    "jdbc:h2:mem:reservations;DB_CLOSE_DELAY=-1", "sa", "");
            migration.executeMigration("db/migration/v1_0_init.sql");
            migration.executeMigration("db/migration/v2_0_add_description.sql");

            emf = Persistence.createEntityManagerFactory("ReservationPU");
            em = emf.createEntityManager();
            new DataInitializer(em).initData();
            ReservationService service = new ReservationService(emf);
            System.out.println("\n=== RÉSERVATIONS ===");
            for (Reservation reservation : service.listerReservations()) {
                System.out.println(reservation.getId() + " | " + reservation.getSalle().getNom()
                        + " | " + reservation.getUtilisateur().getNom()
                        + " | " + reservation.getDateDebut() + " -> " + reservation.getDateFin());
            }

            System.out.println("\n=== RECHERCHES JPQL ET CRITERIA ===");
            LocalDateTime date = LocalDateTime.now();
            List<Reservation> jpql = service.rechercherParSalleJPQL("Salle A", date);
            List<Reservation> criteria = service.rechercherParSalleCriteria("Salle A", date);
            if (!jpql.stream().map(Reservation::getId).collect(Collectors.toList())
                    .equals(criteria.stream().map(Reservation::getId).collect(Collectors.toList()))) {
                throw new IllegalStateException("Les deux recherches doivent donner les mêmes résultats.");
            }
            System.out.println("JPQL : " + jpql.size() + " réservation(s) | Criteria : " + criteria.size());

            System.out.println("\n=== CACHE DE SECOND NIVEAU ===");
            Long salleId = jpql.get(0).getSalle().getId();
            emf.getCache().evict(Salle.class, salleId);
            Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
            long sqlAvant = stats.getPrepareStatementCount();
            lireSalle(emf, salleId);
            System.out.println("Premier chargement : " + (stats.getPrepareStatementCount() - sqlAvant)
                    + " instruction(s) SQL");
            sqlAvant = stats.getPrepareStatementCount();
            long hitsAvant = stats.getSecondLevelCacheHitCount();
            lireSalle(emf, salleId);
            System.out.println("Second chargement : " + (stats.getPrepareStatementCount() - sqlAvant)
                    + " instruction(s) SQL | hits L2 : " + (stats.getSecondLevelCacheHitCount() - hitsAvant));

            new PerformanceReport(em).printReport();
            System.out.println("Application terminée avec succès.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Échec de l'application : " + e.getMessage(), e);
            throw e;
        } finally {
            if (em != null && em.isOpen()) em.close();
            if (emf != null && emf.isOpen()) emf.close();
        }
    }

    private static void lireSalle(EntityManagerFactory emf, Long id) {
        EntityManager lecture = emf.createEntityManager();
        try {
            System.out.println("Salle chargée : " + lecture.find(Salle.class, id).getNom());
        } finally {
            lecture.close();
        }
    }
}
