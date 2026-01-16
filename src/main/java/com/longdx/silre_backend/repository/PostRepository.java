package com.longdx.silre_backend.repository;

import com.longdx.silre_backend.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Post entity
 * 
 * Pattern:
 * - Use Pageable for pagination
 * - Custom queries for complex filtering
 * - Join with related entities when needed
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // Find by public_id
    Optional<Post> findByPublicId(String publicId);

    // Find posts by author
    Page<Post> findByAuthor_InternalId(Long authorId, Pageable pageable);

    // Find posts by community
    Page<Post> findByCommunity_Id(Long communityId, Pageable pageable);

    // Find posts by topic
    Page<Post> findByTopic_Id(Long topicId, Pageable pageable);

    // Find posts by series
    Page<Post> findBySeries_Id(Long seriesId, Pageable pageable);

    // Find posts by series ordered by creation (for series viewer)
    @Query("SELECT p FROM Post p WHERE p.series.id = :seriesId ORDER BY p.createdAt ASC")
    Page<Post> findSeriesPostsOrdered(@Param("seriesId") Long seriesId, Pageable pageable);

    // Find personal posts (community_id IS NULL)
    @Query("SELECT p FROM Post p WHERE p.community IS NULL AND p.author.internalId = :authorId")
    Page<Post> findPersonalPostsByAuthor(@Param("authorId") Long authorId, Pageable pageable);

    // Find community posts
    @Query("SELECT p FROM Post p WHERE p.community.id = :communityId")
    Page<Post> findCommunityPosts(@Param("communityId") Long communityId, Pageable pageable);

    // Find posts by viral score (for ranking algorithm)
    @Query("SELECT p FROM Post p WHERE p.viralScore > 0 ORDER BY p.viralScore DESC")
    Page<Post> findTopViralPosts(Pageable pageable);

    // Find posts by topic with pagination
    @Query("SELECT p FROM Post p WHERE p.topic.id = :topicId ORDER BY p.createdAt DESC")
    Page<Post> findPostsByTopic(@Param("topicId") Long topicId, Pageable pageable);

    // Find feed posts: posts from followed users, joined communities, followed topics, and own posts
    // This is the main feed query that filters posts based on user's follows/joins
    // Note: Empty lists are passed as List.of(-1L) from service layer to avoid JPA IN clause issues
    @Query("SELECT DISTINCT p FROM Post p " +
           "WHERE (" +
           "  (p.author.internalId IN :followedUserIds AND NOT (p.author.internalId = -1)) OR " +
           "  (p.community.id IN :joinedCommunityIds AND NOT (p.community.id = -1)) OR " +
           "  (p.topic.id IN :followedTopicIds AND NOT (p.topic.id = -1)) OR " +
           "  p.author.internalId = :currentUserId" +
           ") " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findFeedPosts(
            @Param("followedUserIds") List<Long> followedUserIds,
            @Param("joinedCommunityIds") List<Long> joinedCommunityIds,
            @Param("followedTopicIds") List<Long> followedTopicIds,
            @Param("currentUserId") Long currentUserId,
            Pageable pageable);

    // Find feed posts for all users (public posts only, sorted by newest)
    // Excludes posts from private communities (only public communities and personal posts)
    @Query("SELECT p FROM Post p " +
           "WHERE (p.community IS NULL OR p.community.isPrivate = false) " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findPublicFeedPosts(Pageable pageable);
}



