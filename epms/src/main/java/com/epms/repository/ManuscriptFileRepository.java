package com.epms.repository;

import com.epms.entity.ManuscriptFile;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ManuscriptFileRepository extends JpaRepository<ManuscriptFile, Long> {

    List<ManuscriptFile> findByManuscriptIdOrderByFileVersionDesc(Long manuscriptId);

    long countByManuscriptIdAndFileCategory(Long manuscriptId, String fileCategory);

    @Query("SELECT COALESCE(MAX(f.fileVersion), 0) FROM ManuscriptFile f WHERE f.manuscriptId = :id")
    int maxVersion(@Param("id") Long manuscriptId);
}
