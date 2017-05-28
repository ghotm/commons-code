package code.ponfee.commons.mail;

/**
 * 邮件发送失败日志接口
 * @author fupf
 */
public interface MailSentFailLogger {

    void log(String logid, int retries, MailEnvelope envelope, Exception e);
}
