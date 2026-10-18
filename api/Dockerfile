# 이미지에는 환경별 값·비밀 값을 넣지 않는다. 설정은 모두 실행 시 환경 변수(ECS 태스크 정의 environment · secrets)로 받는다.
# 빌드 순서: (web/) npm run build → (api/) ./gradlew bootJar  — web/dist가 jar의 static/에 들어간다
#          → (api/) docker build --platform linux/amd64 -t tetra-portal .
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY build/libs/app.jar /app/app.jar
USER 10001
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=Asia/Seoul", "-jar", "/app/app.jar"]
