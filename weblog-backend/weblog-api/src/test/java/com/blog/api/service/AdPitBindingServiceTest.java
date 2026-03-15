package com.blog.api.service;

import com.blog.common.exception.BusinessException;
import com.blog.content.entity.AdPitBinding;
import com.blog.content.mapper.AdPitBindingMapper;
import com.blog.content.service.AdPitBindingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdPitBindingServiceTest {

    @Mock
    private AdPitBindingMapper adPitBindingMapper;

    @InjectMocks
    private AdPitBindingService adPitBindingService;

    @Test
    void shouldRejectWhenPitAlreadyOccupiedOnInsert() {
        when(adPitBindingMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("Duplicate entry"))
                .when(adPitBindingMapper)
                .insert(any(AdPitBinding.class));

        assertThrows(BusinessException.class,
                () -> adPitBindingService.bindExclusive(101L, 9001L, 2));
    }

    @Test
    void shouldRejectWhenRebindingToOccupiedPit() {
        AdPitBinding existing = new AdPitBinding();
        existing.setId(1L);
        existing.setApplyAdId(101L);
        existing.setPitAdId(8001L);

        when(adPitBindingMapper.selectOne(any())).thenReturn(existing);
        doThrow(new DuplicateKeyException("Duplicate entry"))
                .when(adPitBindingMapper)
                .updateById(any(AdPitBinding.class));

        assertThrows(BusinessException.class,
                () -> adPitBindingService.bindExclusive(101L, 9001L, 2));
    }

    @Test
    void shouldAllowSecondApplyToFailAsConcurrentConflict() {
        when(adPitBindingMapper.selectOne(any())).thenReturn(null);
        when(adPitBindingMapper.insert(any(AdPitBinding.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        adPitBindingService.bindExclusive(201L, 9001L, 1);
        assertThrows(BusinessException.class,
                () -> adPitBindingService.bindExclusive(202L, 9001L, 1));
    }
}
