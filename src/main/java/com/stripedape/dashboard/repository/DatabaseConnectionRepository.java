package com.stripedape.dashboard.repository;

import com.stripedape.dashboard.domain.DatabaseConnection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, Long> {
    List<DatabaseConnection> findAllByActiveTrueOrderByNameAsc();
}

