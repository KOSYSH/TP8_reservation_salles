package com.example.util;

import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import javax.persistence.EntityManager;

public class PerformanceReport {

    private final EntityManager em;

    public PerformanceReport(EntityManager em) {
        this.em = em;
    }

    public void printReport() {
        Statistics stats = em.unwrap(Session.class).getSessionFactory().getStatistics();
        // Hibernate 5.6 fournit la durée par requête, pas getQueryExecutionTotalTime().
        long dureeTotale = 0;
        for (String query : stats.getQueries()) {
            dureeTotale += stats.getQueryStatistics(query).getExecutionTotalTime();
        }
        System.out.println("\n=== RAPPORT DE PERFORMANCE ===");
        System.out.println("Requêtes JPQL/Criteria exécutées : " + stats.getQueryExecutionCount());
        System.out.println("Cache de second niveau hits : " + stats.getSecondLevelCacheHitCount());
        System.out.println("Cache de second niveau misses : " + stats.getSecondLevelCacheMissCount());
        System.out.println("Cache de second niveau mises en cache : " + stats.getSecondLevelCachePutCount());
        System.out.println("Transactions : " + stats.getTransactionCount());
        System.out.println("Durée cumulée des requêtes suivies (ms) : " + dureeTotale);
    }
}
