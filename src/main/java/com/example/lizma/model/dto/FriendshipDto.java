package com.example.lizma.model.dto;

import lombok.Data;

@Data
public class FriendshipDto {
    private Long id;
    private Long friendId;
    private String friendUsername;
}
