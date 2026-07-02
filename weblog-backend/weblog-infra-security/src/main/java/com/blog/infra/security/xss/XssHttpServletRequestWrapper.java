package com.blog.infra.security.xss;

import com.blog.infra.security.util.XssUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * XSS 请求包装器
 * 对 getParameter / getParameterValues / getHeader 进行 XSS 清理
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return value == null ? null : XssUtil.cleanText(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = XssUtil.cleanText(values[i]);
        }
        return cleaned;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return value == null ? null : XssUtil.cleanText(value);
    }
}
