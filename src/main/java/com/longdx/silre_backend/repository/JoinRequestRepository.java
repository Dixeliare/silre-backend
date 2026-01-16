package com.longdx.silre_backend.repository;

import com.longdx.silre_backend.model.JoinRequest;
import com.longdx.silre_backend.model.JoinRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    // Find requests by user
    List<JoinRequest> findByUser_InternalId(Long userId);

    // Find requests by community
    Page<JoinRequest> findByCommunity_Id(Long communityId, Pageable pageable);

    // Find pending requests by community
    @Query("SELECT jr FROM JoinRequest jr WHERE jr.community.id = :communityId AND jr.status = 'PENDING'")
    Page<JoinRequest> findPendingByCommunityId(@Param("communityId") Long communityId, Pageable pageable);

    // Find request by user and community
    Optional<JoinRequest> findByUser_InternalIdAndCommunity_Id(Long userId, Long communityId);

    // Count pending requests by community
    long countByCommunity_IdAndStatus(Long communityId, JoinRequestStatus status);
}

