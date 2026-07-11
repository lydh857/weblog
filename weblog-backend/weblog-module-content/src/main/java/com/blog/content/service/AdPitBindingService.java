package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.AdPitBinding;
import com.blog.content.mapper.AdPitBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdPitBindingService {

    private final AdPitBindingMapper adPitBindingMapper;

    public List<AdPitBinding> listAll() {
        return adPitBindingMapper.selectList(new LambdaQueryWrapper<AdPitBinding>()
                .orderByAsc(AdPitBinding::getId));
    }

    public Map<String, Long> loadBindingMap() {
        List<AdPitBinding> rows = listAll();
        if (rows.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Long> result = new HashMap<>();
        for (AdPitBinding row : rows) {
            if (row == null || row.getApplyAdId() == null || row.getPitAdId() == null) {
                continue;
            }
            if (row.getApplyAdId() <= 0 || row.getPitAdId() <= 0) {
                continue;
            }
            result.put(String.valueOf(row.getApplyAdId()), row.getPitAdId());
        }
        return result;
    }

    public Long getPitAdIdByApplyAdId(Long applyAdId) {
        if (applyAdId == null || applyAdId <= 0) {
            return null;
        }

        AdPitBinding row = adPitBindingMapper.selectOne(new LambdaQueryWrapper<AdPitBinding>()
                .eq(AdPitBinding::getApplyAdId, applyAdId)
                .last("LIMIT 1"));
        return row == null ? null : row.getPitAdId();
    }

    public Map<Long, Long> mapByApplyAdIds(Set<Long> applyAdIds) {
        if (applyAdIds == null || applyAdIds.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = applyAdIds.stream().filter(Objects::nonNull).filter(id -> id > 0).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<AdPitBinding> rows = adPitBindingMapper.selectList(new LambdaQueryWrapper<AdPitBinding>()
                .in(AdPitBinding::getApplyAdId, ids));
        if (rows.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> result = new HashMap<>();
        for (AdPitBinding row : rows) {
            if (row == null || row.getApplyAdId() == null || row.getPitAdId() == null) {
                continue;
            }
            result.put(row.getApplyAdId(), row.getPitAdId());
        }
        return result;
    }

    public void bindExclusive(Long applyAdId, Long pitAdId, Integer pitIndex) {
        if (applyAdId == null || applyAdId <= 0 || pitAdId == null || pitAdId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "坑位绑定参数不合法");
        }

        AdPitBinding existing = adPitBindingMapper.selectOne(new LambdaQueryWrapper<AdPitBinding>()
                .eq(AdPitBinding::getApplyAdId, applyAdId)
                .last("LIMIT 1"));

        if (existing == null) {
            AdPitBinding row = new AdPitBinding();
            row.setApplyAdId(applyAdId);
            row.setPitAdId(pitAdId);
            try {
                adPitBindingMapper.insert(row);
            } catch (DuplicateKeyException ex) {
                throwPitOccupied(pitIndex);
            }
            return;
        }

        if (Objects.equals(existing.getPitAdId(), pitAdId)) {
            return;
        }

        try {
            existing.setPitAdId(pitAdId);
            adPitBindingMapper.updateById(existing);
        } catch (DuplicateKeyException ex) {
            throwPitOccupied(pitIndex);
        }
    }

    public void removeByApplyAdIds(List<Long> applyAdIds) {
        List<Long> ids = normalizeIds(applyAdIds);
        if (ids.isEmpty()) {
            return;
        }

        adPitBindingMapper.delete(new LambdaQueryWrapper<AdPitBinding>()
                .in(AdPitBinding::getApplyAdId, ids));
    }

    public void removeByPitAdIds(List<Long> pitAdIds) {
        List<Long> ids = normalizeIds(pitAdIds);
        if (ids.isEmpty()) {
            return;
        }

        adPitBindingMapper.delete(new LambdaQueryWrapper<AdPitBinding>()
                .in(AdPitBinding::getPitAdId, ids));
    }

    public void removeByApplyOrPitAdIds(List<Long> ids) {
        List<Long> normalized = normalizeIds(ids);
        if (normalized.isEmpty()) {
            return;
        }

        adPitBindingMapper.delete(new LambdaQueryWrapper<AdPitBinding>()
                .and(wrapper -> wrapper
                        .in(AdPitBinding::getApplyAdId, normalized)
                        .or()
                        .in(AdPitBinding::getPitAdId, normalized)));
    }

    private List<Long> normalizeIds(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }

        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : rawIds) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(unique);
    }

    private void throwPitOccupied(Integer pitIndex) {
        String pitIndexText = pitIndex != null && pitIndex > 0 ? "(#" + pitIndex + ")" : "";
        throw new BusinessException(ResultCode.BAD_REQUEST,
                "该坑位" + pitIndexText + "已被其他用户申请，请选择其他坑位");
    }
}
