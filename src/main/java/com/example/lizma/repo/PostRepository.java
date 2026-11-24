package com.example.lizma.repo;

import com.example.lizma.model.Post;
import com.example.lizma.model.Users;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByAuthorInOrderByCreatedAtDesc(List<Users> authors, Pageable pageable);

    long countByAuthor(Users author);

    /** Posts whose links in the other services haven't been looked up yet, newest first. */
    @Query("select p.id from Post p where p.linksResolvedAt is null order by p.id desc")
    List<Long> findIdsWithoutLinks(Pageable pageable);

    /** A feed page, newest first, with the authors in the same query. */
    @Query("select p from Post p join fetch p.author where p.author.id in :authorIds order by p.id desc")
    List<Post> findFeedPage(@Param("authorIds") Collection<Long> authorIds, Pageable pageable);

    /** The posts after a cursor: older than the post {@code beforeId}. */
    @Query("select p from Post p join fetch p.author where p.author.id in :authorIds and p.id < :beforeId order by p.id desc")
    List<Post> findFeedPageBefore(@Param("authorIds") Collection<Long> authorIds, @Param("beforeId") Long beforeId,
                                  Pageable pageable);
}
