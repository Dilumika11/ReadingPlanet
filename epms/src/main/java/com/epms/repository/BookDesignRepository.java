package com.epms.repository;

import com.epms.entity.BookDesign;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookDesignRepository extends JpaRepository<BookDesign, Long> {

    List<BookDesign> findByManuscriptIdOrderByDesignVersionDesc(Long manuscriptId);

    Optional<BookDesign> findFirstByManuscriptIdOrderByDesignVersionDesc(Long manuscriptId);

    Optional<BookDesign> findFirstByManuscriptIdAndDesignStatusOrderByDesignVersionDesc(Long manuscriptId, String status);

    @Query("SELECT COALESCE(MAX(d.designVersion), 0) FROM BookDesign d WHERE d.manuscriptId = :id")
    int maxVersion(@Param("id") Long manuscriptId);
}
