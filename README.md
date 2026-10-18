# TP8 — Réservation de salles avec JPA et Hibernate

![Création, modification, conflit de version et suppression](Screenshot_20261017_165843.png)

Une réservation est créée puis modifiée, ce qui incrémente sa version. Un créneau déjà occupé est refusé. Deux modifications utilisent ensuite la même version : la première réussit et la seconde déclenche une `OptimisticLockException`. La réservation de démonstration est finalement supprimée.

