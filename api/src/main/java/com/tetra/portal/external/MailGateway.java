package com.tetra.portal.external;

/**
 * 메일 발송(SES SendEmail). 운영은 SES, 로컬(profile local)은 build/local-mail/에 파일, 시험은 목.
 * 트랜잭션 밖에서 부른다. 본문은 로그에 남기지 않는다.
 */
public interface MailGateway {

	/** @return SES가 발송 요청을 접수했으면 true */
	boolean send(Mail mail);

	/** replyTo: 문의 메일처럼 답장을 보낸 사람에게 돌려야 할 때만(아니면 null). */
	record Mail(String to, String replyTo, String subject, String textBody) {

		@Override
		public String toString() {
			return "Mail[to=" + to + ", subject=" + subject + ", body=****]";
		}

	}

}
