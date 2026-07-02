package com.blog.system.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.DesensitizeUtil;
import com.blog.common.util.LogMaskUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Properties;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 邮件发送服务
 * SMTP 配置从数据库 t_system_config 读取
 * 支持主/备双 SMTP 通道 failover：主通道发送失败时自动切换备用通道
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String DEFAULT_FROM_NAME = "Weblog";
    private static final String DEFAULT_MAIL_LOGO_URL = "";

    private final SystemConfigService configService;

    /** 发送上下文：封装当前通道的 sender 和发件人地址 */
    public record SendContext(JavaMailSender sender, String fromAddress) {}

    /** SMTP channel config — username/password for auth, fromAddress for the visible sender */
    private record SmtpConfig(String host, int port, String username, String password,
                              boolean ssl, String fromAddress) {
        boolean isConfigured() {
            return host != null && !host.isBlank() && username != null && !username.isBlank();
        }
    }

    private SmtpConfig loadSmtpConfig(String keySuffix) {
        String portStr = configService.getValue("mail" + keySuffix + "_smtp_port");
        int port = 587;
        if (portStr != null && !portStr.isBlank()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                log.warn("邮件SMTP端口配置无效: {}={}, 使用默认587", "mail" + keySuffix + "_smtp_port", portStr);
            }
        }

        String username = configService.getValue("mail" + keySuffix + "_username");
        // 发件人地址优先取独立配置，未配则回退到 username（兼容 QQ 邮箱等 username 即邮箱的场景）
        String fromAddress = configService.getValue("mail" + keySuffix + "_from_address");
        if (fromAddress == null || fromAddress.isBlank()) {
            fromAddress = username;
        }

        return new SmtpConfig(
                configService.getValue("mail" + keySuffix + "_smtp_host"),
                port,
                username,
                configService.getValue("mail" + keySuffix + "_password"),
                "true".equalsIgnoreCase(configService.getValue("mail" + keySuffix + "_ssl_enabled")),
                fromAddress
        );
    }

    private SendContext buildSendContext(SmtpConfig config, String label) {
        if (!config.isConfigured()) {
            throw new BusinessException(ResultCode.MAIL_NOT_CONFIGURED, "请先在系统配置中设置邮件服务");
        }

        log.info("邮件SMTP配置({}): host={}, port={}, username={}, ssl={}",
                label, config.host, config.port, config.username, config.ssl);

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host);
        sender.setPort(config.port);
        sender.setUsername(config.username);
        sender.setPassword(config.password);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");

        if (config.ssl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        return new SendContext(sender, config.fromAddress);
    }

    /**
     * 主要发送入口：尝试主通道，失败则切备通道
     * 返回 "主通道" 或 "备用通道"
     */
    private String sendWithFallback(java.util.function.Function<SendContext, Void> sendAction) {
        SmtpConfig primary = loadSmtpConfig("");
        SmtpConfig fallback = loadSmtpConfig("_fallback");
        String fallbackEnabled = configService.getValue("mail_fallback_enabled");

        Exception primaryError = null;

        // 主通道发送
        try {
            SendContext ctx = buildSendContext(primary, "主通道");
            sendAction.apply(ctx);
            return "主通道";
        } catch (MailAuthenticationException e) {
            log.error("邮件发送认证失败（主通道）: {}", LogMaskUtil.mask(e.getMessage()), e);
            throw e;
        } catch (Exception e) {
            primaryError = e;
            log.warn("邮件发送失败（主通道），尝试切换备用通道: {}", LogMaskUtil.mask(e.getMessage()));
            if (!"true".equalsIgnoreCase(fallbackEnabled) || !fallback.isConfigured()) {
                throw new BusinessException(ResultCode.VERIFY_CODE_SEND_FAIL, "验证码发送失败，请稍后重试");
            }
        }

        // 备用通道发送
        try {
            SendContext ctx = buildSendContext(fallback, "备用通道");
            sendAction.apply(ctx);
            return "备用通道";
        } catch (MailAuthenticationException e) {
            log.error("邮件发送认证失败（备用通道）: {}", LogMaskUtil.mask(e.getMessage()), e);
            throw e;
        } catch (Exception e) {
            log.error("邮件发送失败（备用通道），主通道原因: {}",
                    primaryError != null ? LogMaskUtil.mask(primaryError.getMessage()) : "无", e);
            throw new BusinessException(ResultCode.VERIFY_CODE_SEND_FAIL, "验证码发送失败，请稍后重试");
        }
    }

    /**
     * 发送验证码邮件
     */
    public void sendVerifyCode(String toEmail, String code) {
        String fromName = getFromName();
        int expireMinutes = configService.getIntValue("mail_code_expire_minutes", 5);

        String channel = sendWithFallback(ctx -> {
            try {
                MimeMessage message = ctx.sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(ctx.fromAddress, fromName);
                helper.setTo(toEmail);
                helper.setSubject("邮箱验证码 - " + fromName);

                String html = wrapHtmlWithBrand("""
                        <div style="max-width:420px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                          <p style="color:#334155;font-size:15px;line-height:1.6;margin:0 0 20px;">您正在进行身份验证，验证码为：</p>
                          <div style="background:linear-gradient(135deg,#eff6ff,#f0f9ff);border:1px solid #bfdbfe;border-radius:12px;padding:20px 16px;text-align:center;margin:0 0 20px;">
                            <span style="font-size:32px;font-weight:800;letter-spacing:8px;color:#2563eb;">%s</span>
                          </div>
                          <p style="color:#94a3b8;font-size:12px;line-height:1.5;margin:0;">验证码 %d 分钟内有效，请勿泄露给他人。如非本人操作，请忽略此邮件。</p>
                        </div>
                        """.formatted(code, expireMinutes), fromName);

                helper.setText(html, true);
                ctx.sender.send(message);
                return null;
            } catch (MessagingException | java.io.UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        });
        log.info("验证码邮件发送成功: to={}, channel={}", DesensitizeUtil.email(toEmail), channel);
    }

    /**
     * 发送密码重置通知邮件
     */
    public void sendResetPassword(String toEmail, String password) {
        String fromName = getFromName();

        String channel = sendWithFallback(ctx -> {
            try {
                MimeMessage message = ctx.sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(ctx.fromAddress, fromName);
                helper.setTo(toEmail);
                helper.setSubject("密码已重置 - " + fromName);

                String html = wrapHtmlWithBrand("""
                        <div style="max-width:420px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                          <p style="color:#334155;font-size:15px;line-height:1.6;margin:0 0 20px;">管理员已为您重置密码，新密码如下：</p>
                          <div style="background:linear-gradient(135deg,#eff6ff,#f0f9ff);border:1px solid #bfdbfe;border-radius:12px;padding:20px 16px;text-align:center;margin:0 0 20px;">
                            <span style="font-size:20px;font-weight:700;letter-spacing:2px;color:#2563eb;">%s</span>
                          </div>
                          <p style="color:#ef4444;font-size:14px;font-weight:600;margin:0 0 12px;">⚠ 请尽快登录后修改密码！</p>
                          <p style="color:#94a3b8;font-size:12px;line-height:1.5;margin:0;">如果这不是您的操作，请联系管理员。</p>
                        </div>
                        """.formatted(password), fromName);

                helper.setText(html, true);
                ctx.sender.send(message);
                return null;
            } catch (MessagingException | java.io.UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        });
        log.info("重置密码邮件发送成功: to={}, channel={}", DesensitizeUtil.email(toEmail), channel);
    }

    /**
     * 发送初始密码通知邮件（验证码登录自动创建账户时）
     */
    public void sendInitialPassword(String toEmail, String password) {
        String fromName = getFromName();

        String channel = sendWithFallback(ctx -> {
            try {
                MimeMessage message = ctx.sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(ctx.fromAddress, fromName);
                helper.setTo(toEmail);
                helper.setSubject("账号创建成功 - " + fromName);

                String html = wrapHtmlWithBrand("""
                        <div style="max-width:420px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                          <p style="color:#334155;font-size:15px;line-height:1.6;margin:0 0 8px;">欢迎加入 %s 🎉</p>
                          <p style="color:#334155;font-size:15px;line-height:1.6;margin:0 0 20px;">您已通过验证码登录自动创建了账号，初始密码如下：</p>
                          <div style="background:linear-gradient(135deg,#eff6ff,#f0f9ff);border:1px solid #bfdbfe;border-radius:12px;padding:20px 16px;text-align:center;margin:0 0 20px;">
                            <span style="font-size:20px;font-weight:700;letter-spacing:2px;color:#2563eb;">%s</span>
                          </div>
                          <p style="color:#ef4444;font-size:14px;font-weight:600;margin:0 0 12px;">⚠ 请尽快登录后在个人中心修改密码！</p>
                          <p style="color:#94a3b8;font-size:12px;line-height:1.5;margin:0;">如果这不是您的操作，请忽略此邮件。</p>
                        </div>
                        """.formatted(HtmlUtils.htmlEscape(fromName), password), fromName);

                helper.setText(html, true);
                ctx.sender.send(message);
                return null;
            } catch (MessagingException | java.io.UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        });
        log.info("初始密码邮件发送成功: to={}, channel={}", DesensitizeUtil.email(toEmail), channel);
    }

    @Async
    public void sendResetPasswordAsync(String toEmail, String password) {
        try {
            sendResetPassword(toEmail, password);
        } catch (Exception e) {
            log.error("异步重置密码邮件发送失败: to={}, error={}",
                    DesensitizeUtil.email(toEmail), LogMaskUtil.mask(e.getMessage()), e);
        }
    }

    @Async
    public void sendInitialPasswordAsync(String toEmail, String password) {
        try {
            sendInitialPassword(toEmail, password);
        } catch (Exception e) {
            log.error("异步初始密码邮件发送失败: to={}, error={}",
                    DesensitizeUtil.email(toEmail), LogMaskUtil.mask(e.getMessage()), e);
        }
    }

    @Async
    public void sendHtmlMailAsync(String toEmail, String subject, String htmlBody) {
        try {
            String fromName = getFromName();
            String channel = sendWithFallback(ctx -> {
                try {
                    MimeMessage message = ctx.sender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                    helper.setFrom(ctx.fromAddress, fromName);
                    helper.setTo(toEmail);
                    helper.setSubject(subject);
                    helper.setText(wrapHtmlWithBrand(htmlBody, fromName), true);
                    ctx.sender.send(message);
                    return null;
                } catch (MessagingException | java.io.UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            });
            log.info("HTML邮件发送成功: subject={}, to={}, channel={}", subject, DesensitizeUtil.email(toEmail), channel);
        } catch (Exception e) {
            log.error("HTML邮件发送失败: subject={}, to={}, error={}",
                    subject, DesensitizeUtil.email(toEmail), LogMaskUtil.mask(e.getMessage()), e);
        }
    }

    @Async
    public void sendSecurityAlertAsync(String subject, String content) {
        try {
            String enabled = configService.getValue("security_alert_enabled");
            if (!"true".equalsIgnoreCase(enabled)) {
                return;
            }

            String recipients = configService.getValue("security_alert_emails");
            if (recipients == null || recipients.isBlank()) {
                return;
            }

            List<String> toList = Arrays.stream(recipients.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(com.blog.common.util.ValidateUtil::isValidEmail)
                    .toList();

            if (toList.isEmpty()) {
                log.warn("安全告警邮件收件人为空或格式无效");
                return;
            }

            String fromName = getFromName();
            String channel = sendWithFallback(ctx -> {
                try {
                    MimeMessage message = ctx.sender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                    helper.setFrom(ctx.fromAddress, fromName);
                    helper.setTo(toList.toArray(new String[0]));
                    helper.setSubject(subject);
                    helper.setText(wrapPlainTextWithBrand(content, fromName), true);
                    ctx.sender.send(message);
                    return null;
                } catch (MessagingException | java.io.UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            });
            String maskedRecipients = toList.stream()
                    .map(DesensitizeUtil::email)
                    .collect(Collectors.joining(","));
            log.warn("安全告警邮件已发送: subject={}, recipients={}, channel={}", subject, maskedRecipients, channel);
        } catch (Exception e) {
            log.error("发送安全告警邮件失败: subject={}, error={}", subject, LogMaskUtil.mask(e.getMessage()), e);
        }
    }

    private String getFromName() {
        String name = configService.getValue("mail_from_name");
        return (name == null || name.isBlank()) ? DEFAULT_FROM_NAME : name;
    }

    private String wrapPlainTextWithBrand(String content, String fromName) {
        String safeContent = HtmlUtils.htmlEscape(content == null ? "" : content).replace("\n", "<br>");
        String html = """
                <div style="max-width:560px;margin:0 auto;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <div style="color:#334155;font-size:15px;line-height:1.7;white-space:normal;">%s</div>
                </div>
                """.formatted(safeContent);
        return wrapHtmlWithBrand(html, fromName);
    }

    private String wrapHtmlWithBrand(String html, String fromName) {
        String logoUrl = getMailLogoUrl();
        String safeFromName = HtmlUtils.htmlEscape(fromName == null || fromName.isBlank() ? DEFAULT_FROM_NAME : fromName);
        String logoHtml = (logoUrl != null && !logoUrl.isBlank())
                ? "<img src=\"" + HtmlUtils.htmlEscape(logoUrl) + "\" alt=\"" + safeFromName + " logo\" style=\"width:56px;height:56px;border-radius:14px;display:inline-block;object-fit:cover;\">"
                : "";
        return """
                <div style="background:#f8fafc;padding:28px 12px;">
                  <div style="max-width:560px;margin:0 auto 16px;text-align:center;">
                    %s
                    <div style="margin-top:10px;color:#0f172a;font-size:18px;font-weight:700;">%s</div>
                  </div>
                  %s
                </div>
                """.formatted(logoHtml, safeFromName, html);
    }

    private String getMailLogoUrl() {
        String logoUrl = configService.getValue("mail_logo_url");
        if (logoUrl == null || logoUrl.isBlank()) {
            return DEFAULT_MAIL_LOGO_URL;
        }
        return logoUrl.trim();
    }
}
