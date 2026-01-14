package com.longdx.silre_backend.service.impl;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.longdx.silre_backend.dto.request.CreatePostRequest;
import com.longdx.silre_backend.dto.request.UpdatePostRequest;
import com.longdx.silre_backend.dto.response.PostResponse;
import com.longdx.silre_backend.model.*;
import com.longdx.silre_backend.repository.*;
import com.longdx.silre_backend.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service implementation for Post operations
 * 
 * Pattern:
 * - @Service annotation
 * - @Transactional for write operations
 * - Inject repositories
 * - Handle business logic (NanoID generation, authorization, etc.)
 * - Map entities to DTOs
 */
@Service
@Transactional
public class PostServiceImpl implements PostService {

    private static final Logger logger = LoggerFactory.getLogger(PostServiceImpl.class);
    private static final int PUBLIC_ID_LENGTH = 12; // Post publicId length

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final TopicRepository topicRepository;
    private final UserFollowRepository userFollowRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final UserTopicFollowRepository userTopicFollowRepository;

    public PostServiceImpl(
            PostRepository postRepository,
            PostLikeRepository postLikeRepository,
            UserRepository userRepository,
            CommunityRepository communityRepository,
            TopicRepository topicRepository,
            UserFollowRepository userFollowRepository,
            CommunityMemberRepository communityMemberRepository,
            UserTopicFollowRepository userTopicFollowRepository) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.topicRepository = topicRepository;
        this.userFollowRepository = userFollowRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.userTopicFollowRepository = userTopicFollowRepository;
    }

    @Override
    public PostResponse createPost(CreatePostRequest request, Long authorId) {
        logger.debug("Creating post for author: {}", authorId);

        // Get author
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authorId));

        // Create post entity
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(request.content());
        post.setTitle(request.title());
        post.setSlug(request.slug());
        post.setIsNsfw(request.isNsfw() != null ? request.isNsfw() : false);

        // Handle community (if provided)
        if (request.communityPublicId() != null && !request.communityPublicId().isEmpty()) {
            Community community = communityRepository.findByPublicId(request.communityPublicId())
                    .orElseThrow(() -> new IllegalArgumentException("Community not found: " + request.communityPublicId()));
            post.setCommunity(community);
            post.setIsNsfw(community.getIsNsfw()); // Inherit NSFW from community
        }

        // Handle topic (only for personal posts)
        if (request.topicSlug() != null && !request.topicSlug().isEmpty()) {
            if (post.getCommunity() != null) {
                throw new IllegalArgumentException("Topic can only be set for personal posts (not community posts)");
            }
            Topic topic = topicRepository.findBySlug(request.topicSlug())
                    .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + request.topicSlug()));
            post.setTopic(topic);
        }

        // Generate unique public ID (NanoID)
        String publicId = generateUniquePublicId();
        post.setPublicId(publicId);

        // Save post
        Post savedPost = postRepository.save(post);

        // Update community post count (if community post)
        if (savedPost.getCommunity() != null) {
            Community community = savedPost.getCommunity();
            community.setPostCount(community.getPostCount() + 1);
            communityRepository.save(community);
        }

        // Update topic post count (if has topic)
        if (savedPost.getTopic() != null) {
            Topic topic = savedPost.getTopic();
            topic.setPostCount(topic.getPostCount() + 1);
            topicRepository.save(topic);
        }

        logger.info("Post created successfully: {} (author: {})", publicId, authorId);
        return PostResponse.from(savedPost, false); // New post, not liked yet
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostByPublicId(String publicId, Long currentUserId) {
        Post post = postRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + publicId));

        // Check if current user liked this post
        Boolean isLiked = null;
        if (currentUserId != null) {
            isLiked = postLikeRepository.existsByUserIdAndPostId(currentUserId, post.getId());
        }

        return PostResponse.from(post, isLiked);
    }

    @Override
    public PostResponse updatePost(String publicId, UpdatePostRequest request, Long currentUserId) {
        Post post = postRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + publicId));

        // Authorization check: only author can update
        if (!post.getAuthor().getInternalId().equals(currentUserId)) {
            throw new IllegalArgumentException("Only the author can update this post");
        }

        // Update fields (only if provided)
        if (request.title() != null) {
            post.setTitle(request.title());
        }
        if (request.content() != null) {
            post.setContent(request.content());
        }
        if (request.slug() != null) {
            post.setSlug(request.slug());
        }
        if (request.isNsfw() != null) {
            post.setIsNsfw(request.isNsfw());
        }

        Post updatedPost = postRepository.save(post);

        // Check if current user liked this post
        Boolean isLiked = postLikeRepository.existsByUserIdAndPostId(currentUserId, updatedPost.getId());

        logger.info("Post updated: {} (author: {})", publicId, currentUserId);
        return PostResponse.from(updatedPost, isLiked);
    }

    @Override
    public void deletePost(String publicId, Long currentUserId) {
        Post post = postRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + publicId));

        // Authorization check: only author can delete
        if (!post.getAuthor().getInternalId().equals(currentUserId)) {
            throw new IllegalArgumentException("Only the author can delete this post");
        }

        // Update community post count (if community post)
        if (post.getCommunity() != null) {
            Community community = post.getCommunity();
            community.setPostCount(Math.max(0, community.getPostCount() - 1));
            communityRepository.save(community);
        }

        // Update topic post count (if has topic)
        if (post.getTopic() != null) {
            Topic topic = post.getTopic();
            topic.setPostCount(Math.max(0, topic.getPostCount() - 1));
            topicRepository.save(topic);
        }

        // Delete all likes for this post (using custom query)
        // Note: We delete likes individually since there's no bulk delete method
        // In production, consider adding a bulk delete method to repository
        postLikeRepository.findByPostId(post.getId()).forEach(postLikeRepository::delete);

        // Delete post
        postRepository.delete(post);

        logger.info("Post deleted: {} (author: {})", publicId, currentUserId);
    }

    @Override
    public void likePost(String publicId, Long userId) {
        Post post = postRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + publicId));

        // Check if already liked
        if (postLikeRepository.existsByUserIdAndPostId(userId, post.getId())) {
            throw new IllegalArgumentException("Post already liked");
        }

        // Create like
        PostLike like = new PostLike();
        like.setUserId(userId);
        like.setPostId(post.getId());
        postLikeRepository.save(like);

        // Update likes count
        post.setLikesCount(post.getLikesCount() + 1);
        postRepository.save(post);

        logger.debug("Post liked: {} (user: {})", publicId, userId);
    }

    @Override
    public void unlikePost(String publicId, Long userId) {
        Post post = postRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + publicId));

        // Check if liked
        if (!postLikeRepository.existsByUserIdAndPostId(userId, post.getId())) {
            throw new IllegalArgumentException("Post not liked");
        }

        // Delete like
        postLikeRepository.deleteByUserIdAndPostId(userId, post.getId());

        // Update likes count
        post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        postRepository.save(post);

        logger.debug("Post unliked: {} (user: {})", publicId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(Pageable pageable, Long currentUserId) {
        Page<Post> posts;
        
        if (currentUserId == null) {
            // Unauthenticated users: show all public posts (newest first)
            posts = postRepository.findPublicFeedPosts(pageable);
        } else {
            // Authenticated users: show posts from:
            // 1. Users they follow
            // 2. Communities they joined
            // 3. Topics they follow
            // 4. Their own posts
            
            // Get followed user IDs
            List<Long> followedUserIds = userFollowRepository
                    .findAcceptedFollowsByFollowerId(currentUserId)
                    .stream()
                    .map(follow -> follow.getTargetId())
                    .toList();
            
            // Get joined community IDs
            List<Long> joinedCommunityIds = communityMemberRepository
                    .findCommunityIdsByUserId(currentUserId);
            
            // Get followed topic IDs
            List<Long> followedTopicIds = userTopicFollowRepository
                    .findByUserId(currentUserId)
                    .stream()
                    .map(follow -> follow.getTopicId())
                    .toList();
            
            // Query feed posts
            // Use a non-existent ID (-1) for empty lists to avoid JPA IN clause issues with empty collections
            // The query will still work because -1 won't match any real IDs
            posts = postRepository.findFeedPosts(
                    followedUserIds.isEmpty() ? List.of(-1L) : followedUserIds,
                    joinedCommunityIds.isEmpty() ? List.of(-1L) : joinedCommunityIds,
                    followedTopicIds.isEmpty() ? List.of(-1L) : followedTopicIds,
                    currentUserId,
                    pageable
            );
        }
        
        return mapToPostResponsePage(posts, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getPostsByUser(String userPublicId, Pageable pageable, Long currentUserId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userPublicId));

        Page<Post> posts = postRepository.findByAuthor_InternalId(user.getInternalId(), pageable);
        return mapToPostResponsePage(posts, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getPostsByCommunity(String communityPublicId, Pageable pageable, Long currentUserId) {
        Community community = communityRepository.findByPublicId(communityPublicId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found: " + communityPublicId));

        Page<Post> posts = postRepository.findByCommunity_Id(community.getId(), pageable);
        return mapToPostResponsePage(posts, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getPersonalPostsByUser(String userPublicId, Pageable pageable, Long currentUserId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userPublicId));

        Page<Post> posts = postRepository.findPersonalPostsByAuthor(user.getInternalId(), pageable);
        return mapToPostResponsePage(posts, currentUserId);
    }

    /**
     * Generate unique public ID (NanoID) for post
     * 
     * @return Unique public ID
     */
    private String generateUniquePublicId() {
        String publicId;
        int maxRetries = 10;
        int retries = 0;

        do {
            // Generate NanoID with custom size: 12 characters
            // randomNanoId(Random, char[], int) - uses default alphabet and secure random
            publicId = NanoIdUtils.randomNanoId(
                NanoIdUtils.DEFAULT_NUMBER_GENERATOR,  // SecureRandom
                NanoIdUtils.DEFAULT_ALPHABET,         // URL-safe alphabet
                PUBLIC_ID_LENGTH                      // Size: 12 characters
            );
            retries++;
        } while (postRepository.findByPublicId(publicId).isPresent() && retries < maxRetries);

        if (retries >= maxRetries) {
            throw new IllegalStateException("Failed to generate unique public ID after " + maxRetries + " retries");
        }

        return publicId;
    }

    /**
     * Map Page<Post> to Page<PostResponse> with isLiked information
     * 
     * @param posts Page of Post entities
     * @param currentUserId Current user ID (null if not authenticated)
     * @return Page of PostResponse
     */
    private Page<PostResponse> mapToPostResponsePage(Page<Post> posts, Long currentUserId) {
        // Get all post IDs in current page
        List<Long> postIds = posts.getContent().stream()
                .map(Post::getId)
                .toList();

        // Get liked post IDs ONLY for posts in current page (not all posts user liked)
        // This is much more efficient: only query 20 posts instead of potentially thousands
        Set<Long> likedPostIds = new HashSet<>();
        if (currentUserId != null && !postIds.isEmpty()) {
            likedPostIds.addAll(postLikeRepository.findPostIdsByUserIdAndPostIdIn(currentUserId, postIds));
        }

        // Map to PostResponse with isLiked
        return posts.map(post -> {
            Boolean isLiked = currentUserId != null ? likedPostIds.contains(post.getId()) : null;
            return PostResponse.from(post, isLiked);
        });
    }
}
