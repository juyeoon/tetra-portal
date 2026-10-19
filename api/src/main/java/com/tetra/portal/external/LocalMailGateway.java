package com.tetra.portal.external;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 로컬(profile local) — 메일을 보내지 않고 build/local-mail/에 파일로 남긴다(Console과 같은 방식). */
@Component
@Profile("local")
class LocalMailGateway implements MailGateway {

	private static final Logger log = LoggerFactory.getLogger(LocalMailGateway.class);

	static final Path DIR = Path.of("build", "local-mail");

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final AtomicInteger sequence = new AtomicInteger();

	@Override
	public boolean send(Mail mail) {
		try {
			Files.createDirectories(DIR);
			Path file = DIR.resolve(STAMP.format(LocalDateTime.now()) + "-" + sequence.incrementAndGet() + ".txt");
			String content = "To: " + mail.to() + "\nReply-To: " + (mail.replyTo() == null ? "" : mail.replyTo())
					+ "\nSubject: " + mail.subject() + "\n\n" + mail.textBody();
			Files.writeString(file, content, StandardCharsets.UTF_8);
			log.info("[local] 메일 발송 대신 파일로 저장: to={}, subject={}, file={}", mail.to(), mail.subject(),
					file.toAbsolutePath());
			return true;
		}
		catch (IOException ex) {
			log.error("[local] 메일 파일 저장 실패: {}", ex.getClass().getSimpleName());
			return false;
		}
	}

}
