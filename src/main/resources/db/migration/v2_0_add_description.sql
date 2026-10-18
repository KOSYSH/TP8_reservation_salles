-- Version 2.0 : colonne facultative, les données existantes sont conservées.
ALTER TABLE salles ADD COLUMN IF NOT EXISTS description VARCHAR(255);

-- Garantir l'unicité des adresses email.
CREATE UNIQUE INDEX IF NOT EXISTS idx_utilisateur_email ON utilisateurs(email);
