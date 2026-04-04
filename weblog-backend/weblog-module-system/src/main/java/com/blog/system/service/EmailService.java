package com.blog.system.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.DesensitizeUtil;
import com.blog.common.util.LogMaskUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 邮件发送服务
 * SMTP 配置从数据库 t_system_config 读取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final SystemConfigService configService;

    /**
     * 根据数据库配置动态创建 JavaMailSender
     */
    private JavaMailSender createMailSender() {
        String host = configService.getValue("mail_smtp_host");
        String port = configService.getValue("mail_smtp_port");
        String username = configService.getValue("mail_username");
        String password = configService.getValue("mail_password");
        String sslEnabled = configService.getValue("mail_ssl_enabled");

        if (host == null || host.isBlank() || username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.MAIL_NOT_CONFIGURED, "请先在系统配置中设置邮件服务");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port != null ? Integer.parseInt(port) : 465);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");

        if ("true".equalsIgnoreCase(sslEnabled)) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }

        return sender;
    }

    /**
     * 发送验证码邮件
     */
    public void sendVerifyCode(String toEmail, String code) {
        String fromName = configService.getValue("mail_from_name");
        String username = configService.getValue("mail_username");
        int expireMinutes = configService.getIntValue("mail_code_expire_minutes", 5);

        if (fromName == null || fromName.isBlank()) fromName = "我的博客";

        try {
            JavaMailSender mailSender = createMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(username, fromName);
            helper.setTo(toEmail);
            helper.setSubject("邮箱验证码 - " + fromName);

            String html = """
                <div style="max-width:420px;margin:0 auto;padding:24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <h2 style="color:#1e293b;margin-bottom:16px;">邮箱验证码</h2>
                  <p style="color:#475569;font-size:14px;line-height:1.6;">您的验证码为：</p>
                  <div style="background:#f1f5f9;border-radius:8px;padding:16px;text-align:center;margin:16px 0;">
                    <span style="font-size:28px;font-weight:700;letter-spacing:6px;color:#3b82f6;">%s</span>
                  </div>
                  <p style="color:#94a3b8;font-size:12px;">验证码 %d 分钟内有效，请勿泄露给他人。</p>
                </div>
                """.formatted(code, expireMinutes);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("验证码邮件发送成功: to={}", DesensitizeUtil.email(toEmail));
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("验证码邮件发送失败: to={}, error={}",
                    DesensitizeUtil.email(toEmail), LogMaskUtil.mask(e.getMessage()));
            throw new BusinessException(ResultCode.VERIFY_CODE_SEND_FAIL, "验证码发送失败，请稍后重试");
        } catch (Exception e) {
            log.error("验证码邮件发送异常: to={}, error={}",
                    DesensitizeUtil.email(toEmail), LogMaskUtil.mask(e.getMessage()), e);
            throw new BusinessException(ResultCode.VERIFY_CODE_SEND_FAIL, "验证码发送失败，请检查邮箱地址是否正确");
        }
    }

    /**
     * 发送密码重置通知邮件（管理员重置密码时）
     */
    public void sendResetPassword(String toEmail, String password) {
        String fromName = configService.getValue("mail_from_name");
        String username = configService.getValue("mail_username");
        if (fromName == null || fromName.isBlank()) fromName = "我的博客";

        try {
            JavaMailSender mailSender = createMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(username, fromName);
            helper.setTo(toEmail);
            helper.setSubject("密码已重置 - " + fromName);

            String html = """
                <div style="max-width:420px;margin:0 auto;padding:24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <h2 style="color:#1e293b;margin-bottom:16px;">密码已重置</h2>
                  <p style="color:#475569;font-size:14px;line-height:1.6;">管理员已为您重置密码，以下是您的新密码：</p>
                  <div style="background:#f1f5f9;border-radius:8px;padding:16px;text-align:center;margin:16px 0;">
                    <span style="font-size:20px;font-weight:700;letter-spacing:2px;color:#3b82f6;">%s</span>
                  </div>
                  <p style="color:#ef4444;font-size:13px;font-weight:500;">⚠ 请尽快登录后修改密码！</p>
                  <p style="color:#94a3b8;font-size:12px;margin-top:12px;">如果这不是您的操作，请联系管理员。</p>
                </div>
                """.formatted(password);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("重置密码邮件发送成功: to={}", DesensitizeUtil.email(toEmail));
        } catch (Exception e) {
            log.error("重置密码邮件发送失败: to={}, error={}",
                    DesensitizeUtil.email(toEmail), LogMaskUtil.mask(e.getMessage()));
            throw new BusinessException(ResultCode.VERIFY_CODE_SEND_FAIL, "重置密码邮件发送失败");
        }
    }

    /**
     * 发送初始密码通知邮件（验证码登录自动创建账户时）
     */
    public void sendInitialPassword(String toEmail, String password) {
        String fromName = configService.getValue("mail_from_name");
        String username = configService.getValue("mail_username");
        if (fromName == null || fromName.isBlank()) fromName = "我的博客";

        try {
            JavaMailSender mailSender = createMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(username, fromName);
            helper.setTo(toEmail);
            helper.setSubject("账号创建成功 - " + fromName);

            String html = """
                <div style="max-width:420px;margin:0 auto;padding:24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <h2 style="color:#1e293b;margin-bottom:16px;">欢迎加入 %s</h2>
                  <p style="color:#475569;font-size:14px;line-height:1.6;">您已通过验证码登录自动创建了账号，以下是您的初始密码：</p>
                  <div style="background:#f1f5f9;border-radius:8px;padding:16px;text-align:center;margin:16px 0;">
                    <span style="font-size:20px;font-weight:700;letter-spacing:2px;color:#3b82f6;">%s</span>
                  </div>
                  <p style="color:#ef4444;font-size:13px;font-weight:500;">⚠ 请尽快登录后在个人中心修改密码！</p>
                  <p style="color:#94a3b8;font-size:12px;margin-top:12px;">如果这不是您的操作，请忽略此邮件。</p>
                </div>
                """.formatted(fromName, password);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("初始密码邮件发送成功: to={}", DesensitizeUtil.email(toEmail));
        } catch (Exception e) {
            // 初始密码邮件发送失败不影响登录流程，仅记录日志
            log.error("初始密码邮件发送失败: to={}, error={}",
                    DesensitizeUtil.email(toEmail), LogMaskUtil.mask(e.getMessage()));
        }
    }

    /**
     * 异步发送密码重置邮件
     */
    @Async
    public void sendResetPasswordAsync(String toEmail, String password) {
        try {
            sendResetPassword(toEmail, password);
        } catch (Exception e) {
            log.error("异步重置密码邮件发送失败: to={}, error={}",
                    DesensitizeUtil.email(toEmail), LogMaskUtil.mask(e.getMessage()));
        }
    }

    /**
     * 异步发送初始密码邮件
     */
    @Async
    public void sendInitialPasswordAsync(String toEmail, String password) {
        try {
            sendInitialPassword(toEmail, password);
        } catch (Exception e) {
            log.error("异步初始密码邮件发送失败: to={}, error={}",
                    DesensitizeUtil.email(toEmail), LogMaskUtil.mask(e.getMessage()));
        }
    }

    /**
     * 发送安全告警邮件（收件人由系统配置 security_alert_emails 指定，逗号分隔）
     */
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

            String fromName = configService.getValue("mail_from_name");
            String username = configService.getValue("mail_username");
            if (fromName == null || fromName.isBlank()) {
                fromName = "Weblog 安全中心";
            }

            JavaMailSender mailSender = createMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(username, fromName);
            helper.setTo(toList.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(content, false);
            mailSender.send(message);
            String maskedRecipients = toList.stream()
                    .map(DesensitizeUtil::email)
                    .collect(Collectors.joining(","));
            log.warn("安全告警邮件已发送: subject={}, recipients={}", subject, maskedRecipients);
        } catch (Exception e) {
            log.error("发送安全告警邮件失败: subject={}, error={}", subject, LogMaskUtil.mask(e.getMessage()), e);
        }
    }
}
