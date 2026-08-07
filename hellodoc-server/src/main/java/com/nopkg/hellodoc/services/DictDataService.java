package com.nopkg.hellodoc.services;

import com.nopkg.hellodoc.entities.SysDictDatum;
import com.nopkg.hellodoc.entities.SysDictType;
import com.nopkg.hellodoc.exceptions.BusinessException;
import com.nopkg.hellodoc.i18n.LanguageContext;
import com.nopkg.hellodoc.repositories.SysDictDataRepository;
import com.nopkg.hellodoc.repositories.SysDictTypeRepository;
import com.nopkg.hellodoc.web.ApiResponse;
import com.nopkg.hellodoc.web.dto.dict.DictDataVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DictDataService {

    private final SysDictDataRepository dictDataRepository;
    private final SysDictTypeRepository dictTypeRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(value = "dict", key = "#dictCode")
    public List<SysDictDatum> getDictDataByCode(String dictCode) {
        return dictDataRepository.findByDictCodeAndStatusOrderBySortOrder(dictCode, (short) 0);
    }

    public List<SysDictDatum> listDictDataByType(Long typeId) {
        return dictDataRepository.findByDictTypeId(typeId);
    }

    public List<DictDataVO> getLocalizedDictDataByCode(String dictCode) {
        String locale = LanguageContext.getLocale();
        return getDictDataByCode(dictCode).stream()
                .map(item -> DictDataVO.from(item, locale, objectMapper))
                .toList();
    }

    public List<DictDataVO> listLocalizedDictDataByType(Long typeId) {
        String locale = LanguageContext.getLocale();
        return listDictDataByType(typeId).stream()
                .map(item -> DictDataVO.from(item, locale, objectMapper))
                .toList();
    }

    @Transactional
    @CacheEvict(value = "dict", key = "#dictDatum.dictCode")
    public SysDictDatum createDictData(SysDictDatum dictDatum) {
        SysDictType type = dictTypeRepository.findById(dictDatum.getDictType().getId())
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("dict.type_not_found", "Dictionary type not found")));
        if (dictDataRepository.existsByDictTypeIdAndValue(type.getId(), dictDatum.getValue())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR, com.nopkg.hellodoc.utils.MessageUtils.get("dict.value_exists", "Dictionary value already exists under this type: ") + dictDatum.getValue());
        }

        dictDatum.setDictType(type);
        dictDatum.setDictCode(type.getDictCode());
        dictDatum.setCreateTime(Instant.now());
        dictDatum.setUpdateTime(Instant.now());
        return dictDataRepository.save(dictDatum);
    }

    @Transactional
    @CacheEvict(value = "dict", key = "#dictDatumDetails.dictCode")
    public SysDictDatum updateDictData(Long id, SysDictDatum dictDatumDetails) {
        SysDictDatum dictDatum = dictDataRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ApiResponse.Code.RESOURCE_NOT_FOUND, com.nopkg.hellodoc.utils.MessageUtils.get("dict.data_not_found", "Dictionary data not found")));

        if (!dictDatum.getValue().equals(dictDatumDetails.getValue()) &&
                dictDataRepository.existsByDictTypeIdAndValue(dictDatum.getDictType().getId(),
                        dictDatumDetails.getValue())) {
            throw new BusinessException(ApiResponse.Code.PARAM_ERROR,
                    com.nopkg.hellodoc.utils.MessageUtils.get("dict.value_exists", "Dictionary value already exists under this type: ") + dictDatumDetails.getValue());
        }

        dictDatum.setLabel(dictDatumDetails.getLabel());
        dictDatum.setLabelI18n(dictDatumDetails.getLabelI18n());
        dictDatum.setValue(dictDatumDetails.getValue());
        dictDatum.setSortOrder(dictDatumDetails.getSortOrder());
        dictDatum.setIsDefault(dictDatumDetails.getIsDefault());
        dictDatum.setStatus(dictDatumDetails.getStatus());
        dictDatum.setRemark(dictDatumDetails.getRemark());
        dictDatum.setUpdateTime(Instant.now());

        return dictDataRepository.save(dictDatum);
    }

    @Transactional
    @CacheEvict(value = "dict", allEntries = true)
    public void deleteDictData(Long id) {
        dictDataRepository.deleteById(id);
    }

    @CacheEvict(value = "dict", allEntries = true)
    public void refreshAllCache() {
        // 仅触发 CacheEvict
    }
}
