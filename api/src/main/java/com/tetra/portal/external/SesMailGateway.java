package com.tetra.portal.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.tetra.portal.config.TetraProperties;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Message;

/**
 * 실제 SES 발송(Console SesMailGateway와 같은 방식). 발신 = MAIL_FROM.
 * SES 샌드박스라 받는 주소도 SES에 인증된 주소여야 한다(아니면 MessageRejected → false).
 */
@Component
@Profile("!local")
class SesMailGateway implements MailGateway {

	private static final Logger log = LoggerFactory.getLogger(SesMailGateway.class);

	private final SesV2Client ses;

	private final String from;

	SesMailGateway(SesV2Client ses, TetraProperties properties) {
		this.ses = ses;
		this.from = properties.mail().from();
	}

	@Override
	public boolean send(Mail mail) {
		Message message = Message.builder()
			.subject(utf8(mail.subject()))
			.body(b -> b.text(utf8(mail.textBody())))
			.build();
		try {
			ses.sendEmail(b -> {
				b.fromEmailAddress(from).destination(d -> d.toAddresses(mail.to())).content(c -> c.simple(message));
				if (mail.replyTo() != null) {
					b.replyToAddresses(mail.replyTo());
				}
			});
			return true;
		}
		catch (SdkException ex) {
			// 예외 메시지에 본문이 들어가지 않지만 스택은 남기지 않고 종류만 적는다
			log.error("메일 발송 실패: subject={}, error={}", mail.subject(), ex.getClass().getSimpleName());
			return false;
		}
	}

	private static Content utf8(String data) {
		return Content.builder().data(data).charset("UTF-8").build();
	}

}
