package com.example.license.mapper;

import com.example.license.model.LicenseAuditLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * License 审计日志 MyBatis Mapper。
 */
@Mapper
public interface LicenseAuditLogMapper {

    void insert(LicenseAuditLog log);

    List<LicenseAuditLog> findRecent(int limit);
}
