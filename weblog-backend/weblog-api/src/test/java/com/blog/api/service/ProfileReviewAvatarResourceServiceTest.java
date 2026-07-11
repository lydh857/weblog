package com.blog.api.service;

import com.blog.content.entity.OssResource;
import com.blog.content.mapper.OssResourceMapper;
import com.blog.content.service.OssResourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileReviewAvatarResourceServiceTest {

    @Mock
    private OssResourceMapper ossResourceMapper;

    @Mock
    private OssResourceService ossResourceService;

    @InjectMocks
    private ProfileReviewAvatarResourceService profileReviewAvatarResourceService;

    @Test
    void shouldDeleteOldAvatarResourceAfterApproved() {
        OssResource oldResource = new OssResource();
        oldResource.setId(9L);
        when(ossResourceMapper.selectOne(any())).thenReturn(oldResource);

        profileReviewAvatarResourceService.deleteOldAvatar(
                1L,
                "https://cdn.example.com/uploads/avatar/old.webp",
                "https://cdn.example.com/uploads/avatar/new.webp");

        verify(ossResourceService).delete(9L, 1L, true);
    }

    @Test
    void shouldSkipWhenAvatarUrlDoesNotChange() {
        profileReviewAvatarResourceService.deleteOldAvatar(
                1L,
                "http://localhost:9091/uploads/avatar/current.webp",
                "http://localhost:9091/uploads/avatar/current.webp");

        verify(ossResourceMapper, never()).selectOne(any());
        verify(ossResourceService, never()).delete(any(), any(), anyBoolean());
    }
}
