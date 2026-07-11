package com.blog.api.portal.support;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchIdNormalizerTest {

  @Test
  void shouldReturnEmptyWhenIdsIsNull() {
    List<Long> result = BatchIdNormalizer.normalize(null, 100, "too many", "invalid");

    assertEquals(List.of(), result);
  }

  @Test
  void shouldReturnEmptyWhenIdsIsEmpty() {
    List<Long> result = BatchIdNormalizer.normalize(List.of(), 100, "too many", "invalid");

    assertEquals(List.of(), result);
  }

  @Test
  void shouldThrowWhenExceedsMaxCount() {
    List<Long> oversized = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();

    BusinessException ex = assertThrows(BusinessException.class,
      () -> BatchIdNormalizer.normalize(oversized, 100, "too many", "invalid"));

    assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    assertEquals("too many", ex.getMessage());
  }

  @Test
  void shouldThrowWhenContainsInvalidId() {
    BusinessException ex = assertThrows(BusinessException.class,
      () -> BatchIdNormalizer.normalize(List.of(1L, 0L, 2L), 100, "too many", "invalid"));

    assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    assertEquals("invalid", ex.getMessage());
  }

  @Test
  void shouldDeduplicateAndKeepOriginalOrder() {
    List<Long> result = BatchIdNormalizer.normalize(List.of(3L, 1L, 3L, 2L, 1L), 100, "too many", "invalid");

    assertEquals(List.of(3L, 1L, 2L), result);
  }
}
