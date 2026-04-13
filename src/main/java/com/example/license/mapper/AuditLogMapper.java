package com.example.license.mapper;

import com.example.license.model.AuditLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper {

    int insertAuditLog(AuditLog log);

    List<AuditLog> selectByTenantId(Map<String, Object> params);
}
