FROM eclipse-temurin:17-jre
WORKDIR /app

# 1. non-root 전용 유저 및 그룹 생성 (appuser)
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 2. 호스트(로컬 PC)에서 이미 빌드된 jar 파일을 복사 (파일 소유권 자동 지정)
COPY --chown=appuser:appuser build/libs/*-SNAPSHOT.jar app.jar

# 3. 컨테이너 실행 권한을 appuser로 전환
USER appuser

EXPOSE 19900

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
