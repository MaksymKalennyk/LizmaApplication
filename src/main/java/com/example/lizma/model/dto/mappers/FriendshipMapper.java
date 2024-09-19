package com.example.lizma.model.dto.mappers;

import com.example.lizma.model.Friendship;
import com.example.lizma.model.Users;
import com.example.lizma.model.dto.FriendshipDto;
import org.springframework.stereotype.Component;

@Component
public class FriendshipMapper {

    public FriendshipDto toDto(Friendship friendship, Long currentUserId) {
        FriendshipDto dto = new FriendshipDto();
        dto.setId(friendship.getId());

        Users friend = friendship.getUser1().getId().equals(currentUserId)
                ? friendship.getUser2()
                : friendship.getUser1();

        dto.setFriendId(friend.getId());
        dto.setFriendUsername(friend.getUsername());
        return dto;
    }
}