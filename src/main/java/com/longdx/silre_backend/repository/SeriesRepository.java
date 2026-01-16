package com.longdx.silre_backend.repository;

import com.longdx.silre_backend.model.Series;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Series entity
 * 
 * Series cho phép Creator gom các bài đăng lẻ thành một tập/chapter.
 */
@Repository
public interface SeriesRepository extends JpaRepository<Series, Long> {

    /**
     * Find series by public ID
     */
    Optional<Series> findByPublicId(String publicId);

    /**
     * Find series by slug
     */
    Optional<Series> findBySlug(String slug);

    /**
     * Find all series by creator
     */
    Page<Series> findByCreator_InternalId(Long creatorId, Pageable pageable);

    /**
     * Find series by creator and slug
     */
    @Query("SELECT s FROM Series s WHERE s.creator.internalId = :creatorId AND s.slug = :slug")
    Optional<Series> findByCreatorIdAndSlug(@Param("creatorId") Long creatorId, @Param("slug") String slug);
}
