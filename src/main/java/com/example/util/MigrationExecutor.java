package com.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MigrationExecutor {

    private static final Logger LOGGER = Logger.getLogger(MigrationExecutor.class.getName());
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MigrationExecutor(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void executeMigration(String scriptPath) throws IOException, SQLException {
        String sql;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(scriptPath)) {
            if (input == null) {
                throw new IOException("Script de migration introuvable : " + scriptPath);
            }
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.stripLeading().startsWith("--"))
                    .collect(Collectors.joining("\n"));
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            // Les deux scripts fournis sont relançables grâce à IF NOT EXISTS.
            // Chaque instruction est exécutée séparément ; H2 valide les DDL implicitement.
            for (String instruction : sql.split(";")) {
                if (!instruction.isBlank()) statement.execute(instruction);
            }
            LOGGER.info("Migration exécutée : " + scriptPath);
        } catch (SQLException e) {
            LOGGER.severe("Échec de la migration " + scriptPath + " : " + e.getMessage());
            throw e;
        }
    }
}
