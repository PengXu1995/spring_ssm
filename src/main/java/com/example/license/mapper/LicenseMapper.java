package com.example.license.mapper;

import com.example.license.model.LicenseInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LicenseMapper {

    LicenseInfo selectByTenantId(@Param("tenantId") String tenantId);

    List<String> selectAllTenantIds();

    int insertOrUpdateLicense(LicenseInfo info);

    int updateLicenseStatus(LicenseInfo info);
}
