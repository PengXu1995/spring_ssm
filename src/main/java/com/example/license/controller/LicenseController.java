package com.example.license.controller;

import com.example.license.model.LicenseAuditLog;
import com.example.license.model.LicenseStatusInfo;
import com.example.license.service.LicenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * License 管理 REST Controller。
 *
 * <ul>
 *   <li>GET    /api/license         — 查询当前 License 状态（Key 脱敏）</li>
 *   <li>GET    /api/license/full    — 查询当前 License 状态（Key 明文，需鉴权）</li>
 *   <li>POST   /api/license/validate — 预校验 License Key（不持久化）</li>
 *   <li>PUT    /api/license         — 更新 License Key</li>
 *   <li>GET    /api/license/audit   — 查询审计日志</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/license")
public class LicenseController {

    @Autowired
    private LicenseService licenseService;

    /** 查询当前 License 状态（License Key 脱敏展示） */
    @GetMapping
    public ResponseEntity<LicenseStatusInfo> getStatus() {
        return ResponseEntity.ok(licenseService.getStatus(true));
    }

    /** 查询当前 License 状态（License Key 明文，供管理员查看） */
    @GetMapping("/full")
    public ResponseEntity<LicenseStatusInfo> getStatusFull() {
        return ResponseEntity.ok(licenseService.getStatus(false));
    }

    /**
     * 预校验 License Key。
     * 前端在更新前先调用此接口展示校验结果，用户确认后再调用 PUT 更新。
     */
    @PostMapping("/validate")
    public ResponseEntity<LicenseStatusInfo> preValidate(@RequestBody Map<String, String> body) {
        String licenseKey = body.getOrDefault("licenseKey", "").trim();
        if (licenseKey.isEmpty()) {
            LicenseStatusInfo err = new LicenseStatusInfo();
            err.setStatus(com.example.license.model.LicenseStatus.INVALID);
            err.setErrorReason("licenseKey 不能为空");
            return ResponseEntity.badRequest().body(err);
        }
        return ResponseEntity.ok(licenseService.preValidate(licenseKey));
    }

    /** 更新 License Key */
    @PutMapping
    public ResponseEntity<LicenseStatusInfo> update(@RequestBody Map<String, String> body,
                                                     HttpServletRequest request) {
        String licenseKey = body.getOrDefault("licenseKey", "").trim();
        if (licenseKey.isEmpty()) {
            LicenseStatusInfo err = new LicenseStatusInfo();
            err.setStatus(com.example.license.model.LicenseStatus.INVALID);
            err.setErrorReason("licenseKey 不能为空");
            return ResponseEntity.badRequest().body(err);
        }
        String operator = body.getOrDefault("operator", "admin");
        String ip = getClientIp(request);
        LicenseStatusInfo result = licenseService.update(licenseKey, operator, ip);
        return ResponseEntity.ok(result);
    }

    /** 查询审计日志 */
    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "20") int limit) {
        List<LicenseAuditLog> logs = licenseService.getAuditLogs(limit);
        Map<String, Object> resp = new HashMap<>();
        resp.put("total", logs.size());
        resp.put("records", logs);
        return ResponseEntity.ok(resp);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
