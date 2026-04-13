package com.example.license.mapper;

import com.example.license.model.LicenseInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LicenseMapper {

    LicenseInfo selectByTenantId(@Param("tenantId") String tenantId);

    int insertOrUpdateLicense(LicenseInfo info);

    int updateLicenseStatus(LicenseInfo info);
}
