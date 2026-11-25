package com.example.lizma.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hard deletes of posts, comments, a user's content and whole accounts with everything that depends on
 * them, in an order that satisfies the foreign keys. Used by moderation and by account deletion.
 */
@Service
public class ContentCleanupService {

    @PersistenceContext
    private EntityManager em;

    /** Deletes a post with its comments (and their likes) and reactions. */
    @Transactional
    public void deletePost(Long postId) {
        deleteComments(commentIdsOfPosts(List.of(postId)));
        em.createQuery("delete from PostReaction r where r.post.id = :id").setParameter("id", postId).executeUpdate();
        em.createQuery("delete from Post p where p.id = :id").setParameter("id", postId).executeUpdate();
    }

    /** Deletes a comment with its replies and their likes. */
    @Transactional
    public void deleteComment(Long commentId) {
        deleteComments(List.of(commentId));
    }

    /**
     * Removes the user and every piece of data that belongs to or points at them (guideline 5.1.1(v)).
     * What they did as a moderator stays in the moderation log and on handled reports, without their name.
     */
    @Transactional
    public void deleteUser(Long userId) {
        removeUserContent(userId);
        update("delete from UserBlock b where b.blocked.id = :id", userId);
        // Screenshots: the user's own, and those on the reports and feedback deleted below.
        update("delete from Attachment a where a.owner.id = :id"
                + " or a.report.id in (select r.id from Report r where r.reporter.id = :id or r.targetUser.id = :id)"
                + " or a.feedback.id in (select f.id from Feedback f where f.author.id = :id)", userId);
        update("delete from Report r where r.reporter.id = :id or r.targetUser.id = :id", userId);
        update("update Report r set r.resolvedBy = null where r.resolvedBy.id = :id", userId);
        update("delete from Feedback f where f.author.id = :id", userId);
        update("update Feedback f set f.handledBy = null where f.handledBy.id = :id", userId);
        update("update ModerationAction a set a.moderator = null where a.moderator.id = :id", userId);
        update("delete from RefreshToken t where t.user.id = :id", userId);
        update("delete from Users u where u.id = :id", userId);
    }

    /**
     * Removes everything the user put into the app (posts, comments, likes, reactions, profile photo and name)
     * and their connections
     * (friends, requests, invite links, music accounts, the blocks they set, the phones that get their push
     * notifications). The account itself stays, and so
     * do reports and other people's blocks against it: this is what a suspension leaves behind.
     */
    @Transactional
    public void removeUserContent(Long userId) {
        List<Long> postIds = em.createQuery("select p.id from Post p where p.author.id = :id", Long.class)
                .setParameter("id", userId).getResultList();

        List<Long> commentIds = new ArrayList<>(commentIdsOfPosts(postIds));
        commentIds.addAll(em.createQuery("select c.id from PostComment c where c.author.id = :id", Long.class)
                .setParameter("id", userId).getResultList());
        deleteComments(commentIds);
        em.createNativeQuery("delete from comment_likes where user_id = :id").setParameter("id", userId).executeUpdate();

        em.createQuery("delete from PostReaction r where r.user.id = :id").setParameter("id", userId).executeUpdate();
        if (!postIds.isEmpty()) {
            em.createQuery("delete from PostReaction r where r.post.id in :ids").setParameter("ids", postIds).executeUpdate();
            em.createQuery("delete from Post p where p.id in :ids").setParameter("ids", postIds).executeUpdate();
        }

        update("delete from Friendship f where f.user1.id = :id or f.user2.id = :id", userId);
        update("delete from FriendRequest r where r.requester.id = :id or r.recipient.id = :id", userId);
        update("update FriendInvite i set i.acceptedBy = null where i.acceptedBy.id = :id", userId);
        update("delete from FriendInvite i where i.requester.id = :id", userId);
        update("delete from UserMusicAccount a where a.user.id = :id", userId);
        update("delete from UserBlock b where b.blocker.id = :id", userId);
        update("delete from UserAvatar a where a.user.id = :id", userId);
        update("delete from DeviceToken d where d.user.id = :id", userId);
        update("delete from ExtensionKey k where k.user.id = :id", userId);
        update("update Users u set u.avatarToken = null, u.displayName = null where u.id = :id", userId);
    }

    private void update(String jpql, Long userId) {
        em.createQuery(jpql).setParameter("id", userId).executeUpdate();
    }

    private List<Long> commentIdsOfPosts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select c.id from PostComment c where c.post.id in :ids", Long.class)
                .setParameter("ids", postIds).getResultList();
    }

    /** Deletes the comments, every reply below them and all their likes. */
    private void deleteComments(Collection<Long> ids) {
        Set<Long> all = new HashSet<>(ids);
        Collection<Long> frontier = List.copyOf(all);
        while (!frontier.isEmpty()) {
            List<Long> children = em.createQuery("select c.id from PostComment c where c.parentComment.id in :ids", Long.class)
                    .setParameter("ids", frontier).getResultList();
            frontier = children.stream().filter(all::add).toList();
        }
        if (all.isEmpty()) {
            return;
        }
        em.createNativeQuery("delete from comment_likes where comment_id in (:ids)").setParameter("ids", all).executeUpdate();
        em.createQuery("update PostComment c set c.parentComment = null where c.id in :ids").setParameter("ids", all).executeUpdate();
        em.createQuery("delete from PostComment c where c.id in :ids").setParameter("ids", all).executeUpdate();
    }
}
