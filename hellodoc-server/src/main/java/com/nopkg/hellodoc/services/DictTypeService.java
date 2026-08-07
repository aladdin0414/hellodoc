package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysDictType;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.repositories.SysDictDataRepository;
import com.nopkg.hellodoc.repositories.SysDictTypeRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DictTypeService {

    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictDataRepository dictDataRepository;

    public List<SysDictType> listDictTypes() {
        return dictTypeRepository.findAll();
    }

    public SysDictType getDictType(Long id) {
        return dictTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("dict.type_not_found", "Dictionary type not found")));
    }

    @Transactional
    public SysDictType createDictType(SysDictType dictType) {
        if (dictTypeRepository.existsByDictCode(dictType.getDictCode())) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("dict.code_exists", "Dictionary code already exists"));
        }
        dictType.setCreateTime(Instant.now());
        dictType.setUpdateTime(Instant.now());
        return dictTypeRepository.save(dictType);
    }

    @Transactional
    @CacheEvict(value = "dict", key = "#dictTypeDetails.dictCode")
    public SysDictType updateDictType(Long id, SysDictType dictTypeDetails) {
        SysDictType dictType = getDictType(id);

        // 如果修改了 dictCode，建议同步更新 dictData 表（虽然我们的模型中 dictData 也有冗余的 dictCode）
        // 这里简化处理，暂不建议修改 dictCode，或者修改后清除旧缓存

        dictType.setDictName(dictTypeDetails.getDictName());
        dictType.setDescription(dictTypeDetails.getDescription());
        dictType.setStatus(dictTypeDetails.getStatus());
        dictType.setUpdateTime(Instant.now());

        return dictTypeRepository.save(dictType);
    }

    @Transactional
    @CacheEvict(value = "dict", allEntries = true)
    public void deleteDictType(Long id) {
        SysDictType dictType = getDictType(id);
        if (Boolean.TRUE.equals(dictType.getIsSystem())) {
            throw new BusinessException(ApiResponse.Code.SYSTEM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("dict.system_dict_cannot_delete", "System built-in dictionary cannot be deleted"));
        }
        dictDataRepository.deleteByDictTypeId(id);
        dictTypeRepository.delete(dictType);
    }
}
