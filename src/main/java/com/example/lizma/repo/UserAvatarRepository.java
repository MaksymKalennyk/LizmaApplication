package com.example.lizma.repo;

import com.example.lizma.model.UserAvatar;
import com.example.lizma.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, String> {

    @Modifying
    @Query("delete from UserAvatar a where a.user = :user")
    void deleteByUser(@Param("user") Users user);
}
