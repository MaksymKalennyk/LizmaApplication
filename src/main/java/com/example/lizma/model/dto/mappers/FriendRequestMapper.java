package com.example.lizma.model.dto.mappers;

import com.example.lizma.model.FriendRequest;
import com.example.lizma.model.dto.FriendRequestDto;
import com.example.lizma.model.dto.UserCard;
import org.springframework.stereotype.Component;

@Component
public class FriendRequestMapper {

    public FriendRequestDto toDto(FriendRequest friendRequest) {
        FriendRequestDto dto = new FriendRequestDto();
        dto.setId(friendRequest.getId());
        dto.setRequesterId(friendRequest.getRequester().getId());
        dto.setRequesterUsername(friendRequest.getRequester().getUsername());
        dto.setRequesterDisplayName(friendRequest.getRequester().getDisplayName());
        dto.setRequesterAvatarUrl(UserCard.avatarUrl(friendRequest.getRequester()));
        dto.setRecipientId(friendRequest.getRecipient().getId());
        dto.setRecipientUsername(friendRequest.getRecipient().getUsername());
        dto.setRecipientDisplayName(friendRequest.getRecipient().getDisplayName());
        dto.setRecipientAvatarUrl(UserCard.avatarUrl(friendRequest.getRecipient()));
        dto.setStatus(friendRequest.getStatus().name());
        return dto;
    }
}
