package com.example.util;

import com.example.model.*;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.Logger;

public class DataInitializer {

    private static final Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());
    private final EntityManager em;

    public DataInitializer(EntityManager em) {
        this.em = em;
    }

    public void initData() {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Long existants = em.createQuery("SELECT COUNT(u) FROM Utilisateur u WHERE u.email = :email", Long.class)
                    .setParameter("email", "alice.dupont@example.com").getSingleResult();
            if (existants > 0) {
                tx.commit();
                System.out.println("Jeu de données déjà présent : aucune insertion supplémentaire.");
                return;
            }

            Utilisateur[] utilisateurs = {
                    new Utilisateur("Alice Dupont", "alice.dupont@example.com"),
                    new Utilisateur("Bob Martin", "bob.martin@example.com"),
                    new Utilisateur("Charlie Bernard", "charlie.bernard@example.com")
            };
            Salle[] salles = {
                    new Salle("Salle A", 20, "Salle de réunion avec vidéoprojecteur"),
                    new Salle("Salle B", 12, "Salle pour les réunions d'équipe"),
                    new Salle("Salle C", 40, "Salle de formation")
            };
            for (Utilisateur utilisateur : utilisateurs) {
                em.persist(utilisateur);
            }
            for (Salle salle : salles) {
                em.persist(salle);
            }
            LocalDateTime demain = LocalDate.now().plusDays(1).atTime(9, 0);
            for (int i = 0; i < salles.length; i++) {
                em.persist(new Reservation(salles[i], utilisateurs[i], demain, demain.plusHours(2)));
                em.persist(new Reservation(salles[i], utilisateurs[(i + 1) % utilisateurs.length],
                        demain.plusHours(5), demain.plusHours(7)));
            }
            tx.commit();
            System.out.println("Données initialisées : 3 utilisateurs, 3 salles et 6 réservations.");
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.severe("Échec de l'initialisation : " + e.getMessage());
            throw e;
        }
    }
}
